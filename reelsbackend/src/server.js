const express = require("express");
const ffmpeg = require("fluent-ffmpeg");
const fs = require("fs");
const path = require("path");
const os = require("os");
const crypto = require("crypto");
const axios = require("axios");

const app = express();
app.use(express.json());

const PORT = process.env.PORT || 8080;
const BOT_TOKEN = process.env.TELEGRAM_BOT_TOKEN;
const CALLBACK_URL = process.env.CALLBACK_URL;
const SECRET = process.env.TRANSCODER_SECRET || crypto.randomBytes(16).toString("hex");

// bandwidth/width feed the EXT-X-STREAM-INF line in the master playlist
// built by HlsController — keep these roughly honest for real ABR behavior.
const QUALITIES = {
  "480p": { label: "480p", height: 480, bitrate: "800k", bandwidth: 800000, width: 854 },
  "720p": { label: "720p", height: 720, bitrate: "2500k", bandwidth: 2500000, width: 1280 },
};

function getTelegramFilePath(fileId) {
  return axios
    .get(`https://api.telegram.org/bot${BOT_TOKEN}/getFile?file_id=${fileId}`)
    .then((r) => r.data.result.file_path);
}

function downloadFile(filePath, dest) {
  const url = `https://api.telegram.org/file/bot${BOT_TOKEN}/${filePath}`;
  const writer = fs.createWriteStream(dest);
  return axios({ url, method: "GET", responseType: "stream" }).then((r) => {
    r.data.pipe(writer);
    return new Promise((resolve, reject) => {
      writer.on("finish", resolve);
      writer.on("error", reject);
    });
  });
}

/**
 * Transcodes to a single-file HLS rendition: one .ts media file plus a
 * .m3u8 that addresses it via EXT-X-BYTERANGE. This avoids needing to
 * store/manage dozens of tiny segment objects — the whole rendition is
 * one file, identical in shape to what uploadToTelegram already handles,
 * and byte-range playback is served by the existing /stream Range support.
 */
function transcodeToHls(inputPath, outDir, label, quality) {
  const segmentFilename = `${label}.ts`;
  const segmentPath = path.join(outDir, segmentFilename);
  const playlistPath = path.join(outDir, `${label}.m3u8`);
  return new Promise((resolve, reject) => {
    ffmpeg(inputPath)
      .videoCodec("libx264")
      .audioCodec("aac")
      .outputOptions([
        `-vf scale=-2:${quality.height}`,
        "-preset fast",
        "-g 48",
        "-keyint_min 48",
        "-sc_threshold 0",
        "-hls_time 6",
        "-hls_playlist_type vod",
        "-hls_flags single_file",
        "-hls_segment_filename", segmentPath
      ])
      .output(playlistPath)
      .on("end", () => resolve({ segmentPath, playlistPath }))
      .on("error", reject)
      .run();
  });
}

/**
 * Swaps the single-file media URI in the generated playlist for a
 * placeholder the backend fills in with the real /stream URL once it
 * knows the Telegram storageKey for that quality's uploaded .ts file.
 *
 * NOTE: ffmpeg writes whatever `-hls_segment_filename` was given — here
 * that's an absolute tmp path, not a bare filename — so string-matching
 * on the basename alone would miss it. With `-hls_flags single_file`
 * every non-comment, non-empty line in the playlist is the same media
 * URI, so we replace by line shape instead of by exact string match.
 */
function playlistWithPlaceholder(playlistPath) {
  const text = fs.readFileSync(playlistPath, "utf8");
  return text
    .split("\n")
    .map((line) => {
      const trimmed = line.trim();
      if (!trimmed || trimmed.startsWith("#")) return line;
      return "{{STREAM_URL}}";
    })
    .join("\n");
}

function uploadToTelegram(filePath, filename) {
  const FormData = require("form-data");
  const formData = new FormData();
  formData.append("video", fs.createReadStream(filePath), filename);
  formData.append("chat_id", process.env.TELEGRAM_CHAT_ID);
  formData.append("supports_streaming", "true");
  return axios
    .post(`https://api.telegram.org/bot${BOT_TOKEN}/sendVideo`, formData, {
      headers: formData.getHeaders(),
      maxContentLength: Infinity,
      maxBodyLength: Infinity,
    })
    .then((r) => r.data.result);
}

function sendCallback(videoId, qualities, playlists, qualityMeta) {
  if (!CALLBACK_URL) return;
  return axios
    .post(CALLBACK_URL, { videoId, qualities, playlists, qualityMeta, secret: SECRET })
    .then(() => console.log(`[${videoId}] Callback sent`))
    .catch((e) => console.error(`[${videoId}] Callback failed:`, e.message));
}

app.post("/transcode", async (req, res) => {
  try {
    const { fileId, videoId, secret } = req.body;
    if (secret !== SECRET) return res.status(403).json({ error: "Invalid secret" });
    if (!fileId || !videoId) return res.status(400).json({ error: "fileId and videoId required" });
    if (!BOT_TOKEN) return res.status(500).json({ error: "TELEGRAM_BOT_TOKEN not configured" });

    res.json({ status: "started" });

    const tmpDir = fs.mkdtempSync(path.join(os.tmpdir(), "transcode-"));
    const inputPath = path.join(tmpDir, "input.mp4");

    console.log(`[${videoId}] Getting file path...`);
    const filePath = await getTelegramFilePath(fileId);

    console.log(`[${videoId}] Downloading from Telegram (${filePath})...`);
    await downloadFile(filePath, inputPath);

    const qualities = {};
    const playlists = {};
    const qualityMeta = {};

    for (const [label, q] of Object.entries(QUALITIES)) {
      try {
        console.log(`[${videoId}] Transcoding to HLS ${label}...`);
        const { segmentPath, playlistPath } = await transcodeToHls(inputPath, tmpDir, label, q);

        console.log(`[${videoId}] Uploading ${label} rendition to Telegram...`);
        const result = await uploadToTelegram(segmentPath, `${videoId}_${label}.ts`);
        qualities[label] = result.video.file_id;
        playlists[label] = playlistWithPlaceholder(playlistPath);
        qualityMeta[label] = { bandwidth: q.bandwidth, width: q.width, height: q.height };

        await sendCallback(videoId, qualities, playlists, qualityMeta);
      } catch (e) {
        console.error(`[${videoId}] ${label} failed:`, e.message);
      }
    }

    fs.rmSync(tmpDir, { recursive: true, force: true });
    console.log(`[${videoId}] Done`);
  } catch (err) {
    console.error("Transcode error:", err.message);
  }
});

app.get("/health", (req, res) => res.json({ status: "ok" }));

app.listen(PORT, () => console.log(`Transcoder running on port ${PORT}`));
