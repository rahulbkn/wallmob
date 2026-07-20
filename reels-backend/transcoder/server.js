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

const QUALITIES = {
  "480p": { label: "480p", height: 480, bitrate: "800k" },
  "720p": { label: "720p", height: 720, bitrate: "2500k" },
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

function transcode(inputPath, outputPath, quality) {
  return new Promise((resolve, reject) => {
    ffmpeg(inputPath)
      .videoCodec("libx264")
      .audioCodec("aac")
      .outputOptions([`-vf scale=-2:${quality.height}`, "-preset fast", "-movflags +faststart"])
      .on("end", () => resolve())
      .on("error", reject)
      .save(outputPath);
  });
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

function sendCallback(videoId, qualities) {
  if (!CALLBACK_URL) return;
  return axios.post(CALLBACK_URL, { videoId, qualities, secret: SECRET })
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
    for (const [label, q] of Object.entries(QUALITIES)) {
      try {
        const outputPath = path.join(tmpDir, `${label}.mp4`);
        console.log(`[${videoId}] Transcoding to ${label}...`);
        await transcode(inputPath, outputPath, q);

        console.log(`[${videoId}] Uploading ${label} to Telegram...`);
        const result = await uploadToTelegram(outputPath, `${videoId}_${label}.mp4`);
        qualities[label] = result.video.file_id;

        await sendCallback(videoId, qualities);
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
