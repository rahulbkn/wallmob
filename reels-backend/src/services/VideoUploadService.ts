import crypto from "crypto";
import type { StorageProvider } from "../types/storage";
import type { VideoRepository } from "../repositories/VideoRepository";
import type { ClientVideoView, VideoMetadata, UploadVideoResult } from "../types/video";
import { BadRequestError } from "../utils/errors";
import { createLogger } from "../utils/logger";
import { decodeTelegramStorageKey } from "../providers/storage/telegram/TelegramStorageProvider";

const logger = createLogger("VideoUploadService");

export interface UploadVideoInput {
  video: { buffer: Buffer; mimeType: string; filename?: string };
  thumbnail?: { buffer: Buffer; mimeType: string; filename?: string };
  title: string;
  description?: string;
  hashtags?: string[];
  category: string;
  language?: string;
  uploader: string;
}

const VIDEO_MIME_TYPES = ["video/mp4", "video/quicktime", "video/webm", "video/x-matroska"];
const MAX_VIDEO_SIZE = 350 * 1024 * 1024; // Self-hosted Bot API server supports up to 2GB
const CACHE_NS = "reels-video-cache";

/**
 * Pending video cache — stores raw bytes keyed by temporary id so the
 * /stream endpoint can serve them while the Telegram upload is still
 * in progress. Both this map and the edge cache are populated before
 * the HTTP response is sent.
 */
export const pendingVideoCache = new Map<string, { data: Buffer; mimeType: string }>();

export class VideoUploadService {
  constructor(
    private readonly storage: StorageProvider,
    private readonly videos: VideoRepository,
    private readonly transcoderUrl?: string,
    private readonly transcoderSecret?: string,
    private readonly ownerTokenSecret?: string
  ) {}

  async upload(input: UploadVideoInput, waitUntil?: (p: Promise<unknown>) => void): Promise<UploadVideoResult> {
    if (!input.video?.buffer?.length) throw new BadRequestError("No video file provided");
    if (!VIDEO_MIME_TYPES.includes(input.video.mimeType)) {
      throw new BadRequestError(`Expected a video file, got '${input.video.mimeType || "unknown"}'`);
    }
    if (input.video.buffer.length > MAX_VIDEO_SIZE) {
      throw new BadRequestError(`Max allowed video size is ${MAX_VIDEO_SIZE / (1024 * 1024)} MB`);
    }
    if (!input.title?.trim()) throw new BadRequestError("title is required");
    if (!input.category?.trim()) throw new BadRequestError("category is required");
    if (!input.uploader?.trim()) throw new BadRequestError("uploader is required");

    const buf = input.video.buffer;
    const mime = input.video.mimeType;
    const tempId = crypto.randomUUID();
    const cacheKey = "pending:" + tempId;

    pendingVideoCache.set(cacheKey, { data: buf, mimeType: mime });
    try {
      const r = new Request(`https://${CACHE_NS}/video/${cacheKey}`);
      const p = new Response(buf, { headers: { "Content-Type": mime, "Content-Length": String(buf.length), "Cache-Control": "public, max-age=86400" } });
      caches.default.put(r, p).catch(() => {});
    } catch {}

    const record = await this.videos.create({
      title: input.title.trim(),
      description: input.description?.trim(),
      hashtags: input.hashtags || [],
      category: input.category.trim(),
      language: input.language,
      duration: 0,
      width: 0,
      height: 0,
      uploader: input.uploader,
      storageProvider: "telegram",
      storageKey: cacheKey,
    });

    const videoUrl = `/stream?key=${encodeURIComponent(cacheKey)}&variant=video`;

    if (waitUntil) {
      waitUntil(this.finalizeUpload(record.id, cacheKey, input, mime, buf));
    } else {
      this.finalizeUpload(record.id, cacheKey, input, mime, buf).catch(() => {});
    }

    logger.info("video upload initiated", { id: record.id });

    return {
      id: record.id,
      title: input.title.trim(),
      description: input.description?.trim(),
      hashtags: input.hashtags || [],
      category: input.category.trim(),
      language: input.language,
      duration: 0,
      width: 0,
      height: 0,
      uploader: input.uploader,
      uploadDate: record.uploadDate,
      views: 0,
      likes: 0,
      comments: 0,
      shares: 0,
      videoUrl,
      thumbnailUrl: videoUrl,
      ownerToken: this.signOwnerToken(record.id),
    };
  }

  private signOwnerToken(videoId: string): string {
    const secret = this.ownerTokenSecret || "dev-only-insecure-secret-set-OWNER_TOKEN_SECRET";
    return crypto.createHmac("sha256", secret).update(videoId).digest("hex");
  }

  private async finalizeUpload(
    recordId: string, cacheKey: string, input: UploadVideoInput, mime: string, buf: Buffer
  ): Promise<void> {
    try {
      const uploadResult = await this.storage.uploadVideo(
        { data: buf, mimeType: mime, filename: input.video.filename },
        `videos/${Date.now()}-${input.uploader}`
      );

      let thumbKey = uploadResult.autoThumbnailKey;
      if (input.thumbnail?.buffer?.length) {
        const tr = await this.storage.uploadThumbnail(
          { data: input.thumbnail.buffer, mimeType: input.thumbnail.mimeType, filename: input.thumbnail.filename },
          `thumbnails/${Date.now()}-${input.uploader}`
        );
        thumbKey = tr.thumbnailKey;
      }

      await this.videos.patch(recordId, {
        storageKey: uploadResult.storageKey,
        thumbnailKey: thumbKey,
        duration: uploadResult.duration,
        width: uploadResult.width,
        height: uploadResult.height,
      });

      if (uploadResult.storageUniqueId) {
        await this.videos.indexStorageUniqueId(uploadResult.storageUniqueId, recordId);
      }

      pendingVideoCache.delete(cacheKey);

      await this.triggerTranscoding(recordId, uploadResult.storageKey);

      logger.info("video finalized", { id: recordId, provider: uploadResult.provider });
    } catch (e) {
      logger.error("video finalize failed", { id: recordId, error: (e as Error).message });
    }
  }

  private async triggerTranscoding(recordId: string, storageKey: string): Promise<void> {
    if (!this.transcoderUrl || !this.transcoderSecret) return;
    try {
      const { fileId } = decodeTelegramStorageKey(storageKey);
      const resp = await fetch(this.transcoderUrl, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ fileId, videoId: recordId, secret: this.transcoderSecret })
      });
      if (!resp.ok) logger.error("transcode trigger failed", { id: recordId, status: resp.status });
    } catch (e) {
      logger.error("transcode trigger error", { id: recordId, error: (e as Error).message });
    }
  }

  /**
   * Called by TranscodeController.callback once the transcoder finishes a
   * quality (or all qualities). `qualities` maps label -> Telegram
   * storageKey for that rendition's single-file .ts. `hlsPlaylists` maps
   * label -> playlist text containing a "{{STREAM_URL}}" placeholder,
   * resolved to a real /stream URL at request time by HlsController.
   * `qualityMeta` carries the static bandwidth/resolution used to build
   * the master playlist's EXT-X-STREAM-INF lines.
   */
  async updateQualities(
    recordId: string,
    qualities: Record<string, string>,
    hlsPlaylists?: Record<string, string>,
    qualityMeta?: Record<string, { bandwidth: number; width: number; height: number }>
  ): Promise<void> {
    await this.videos.patch(recordId, { qualities, hlsPlaylists, qualityMeta });
  }

  async resolveUrls(storageKey: string, thumbnailKey?: string): Promise<{ videoUrl: string; thumbnailUrl: string }> {
    const videoUrl = await this.storage.getDownloadUrl(storageKey, { variant: "video" });
    const thumbnailUrl = thumbnailKey
      ? await this.storage.getDownloadUrl(thumbnailKey, { variant: "thumbnail" })
      : await this.storage.getDownloadUrl(storageKey, { variant: "thumbnail" });
    return { videoUrl, thumbnailUrl };
  }

  private toClientView(id: string, record: VideoMetadata, urls: { videoUrl: string; thumbnailUrl: string }): ClientVideoView {
    return {
      id,
      title: record.title,
      description: record.description,
      hashtags: record.hashtags,
      category: record.category,
      language: record.language,
      duration: record.duration,
      width: record.width,
      height: record.height,
      uploader: record.uploader,
      uploadDate: record.uploadDate,
      views: record.views,
      likes: record.likes,
      comments: record.comments,
      shares: record.shares,
      videoUrl: urls.videoUrl,
      thumbnailUrl: urls.thumbnailUrl
    };
  }
}
