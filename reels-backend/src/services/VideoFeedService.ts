import type { StorageProvider } from "../types/storage";
import type { VideoRepository } from "../repositories/VideoRepository";
import type { ClientVideoView, VideoMetadata } from "../types/video";
import { BadRequestError, NotFoundError } from "../utils/errors";
import type { CacheStore } from "../utils/cache";
import { DeviceInteractionStore, normalizeDeviceId } from "../utils/deviceInteractions";

const URL_CACHE_TTL_SECONDS = 300;

/**
 * Turns internal `VideoMetadata` (which knows about storageProvider /
 * storageKey) into `ClientVideoView` (which only has resolved URLs).
 * Clients hitting the feed/detail endpoints never learn whether a video
 * lives in Telegram, R2, or Supabase.
 */
export class VideoFeedService {
  constructor(
    private readonly storage: StorageProvider,
    private readonly videos: VideoRepository,
    private readonly cache: CacheStore,
    private readonly interactions: DeviceInteractionStore
  ) {}

  async getFeed(params: { page: number; perPage: number; category?: string; uploader?: string }): Promise<{ items: ClientVideoView[]; total: number; page: number; perPage: number }> {
    const { items, total } = await this.videos.list(params);
    const views = await Promise.all(items.map((v) => this.toClientView(v)));
    return { items: views, total, page: params.page, perPage: params.perPage };
  }

  async getById(id: string): Promise<ClientVideoView> {
    const record = await this.videos.getById(id);
    if (!record) throw new NotFoundError(`Video "${id}" not found`);
    return this.toClientView(record);
  }

  async recordView(id: string): Promise<void> {
    await this.videos.incrementCounter(id, "views");
  }

  /**
   * One like per device per video (deviceId from client, same idea as ownerToken).
   * Returns whether the global counter was incremented.
   */
  async like(id: string, deviceIdRaw: unknown): Promise<{ counted: boolean }> {
    const deviceId = normalizeDeviceId(deviceIdRaw);
    if (!deviceId) throw new BadRequestError("deviceId is required");

    const record = await this.videos.getById(id);
    if (!record) throw new NotFoundError(`Video "${id}" not found`);

    const claimed = await this.interactions.claim("like", id, deviceId);
    if (!claimed) return { counted: false };

    await this.videos.incrementCounter(id, "likes");
    return { counted: true };
  }

  /** One share count per device per video. */
  async share(id: string, deviceIdRaw: unknown): Promise<{ counted: boolean }> {
    const deviceId = normalizeDeviceId(deviceIdRaw);
    if (!deviceId) throw new BadRequestError("deviceId is required");

    const record = await this.videos.getById(id);
    if (!record) throw new NotFoundError(`Video "${id}" not found`);

    const claimed = await this.interactions.claim("share", id, deviceId);
    if (!claimed) return { counted: false };

    await this.videos.incrementCounter(id, "shares");
    return { counted: true };
  }

  private async toClientView(record: VideoMetadata): Promise<ClientVideoView> {
    const cacheKey = `download-url:${record.storageProvider}:${record.storageKey}:${record.thumbnailKey || ""}`;
    let urls = await this.cache.get<{ videoUrl: string; thumbnailUrl: string }>(cacheKey);
    if (!urls) {
      const videoUrl = await this.storage.getDownloadUrl(record.storageKey, { variant: "video" });
      const thumbnailUrl = record.thumbnailKey
        ? await this.storage.getDownloadUrl(record.thumbnailKey, { variant: "thumbnail" })
        : await this.storage.getDownloadUrl(record.storageKey, { variant: "thumbnail" });
      urls = { videoUrl, thumbnailUrl };
      await this.cache.set(cacheKey, urls, URL_CACHE_TTL_SECONDS);
    }

    // Quality URLs are no longer resolved to direct storage URLs here —
    // playback for HLS renditions goes through /hls/master, which itself
    // resolves each variant's storageKey to a /stream URL at request
    // time. We keep `qualities` as label->storageKey purely as an
    // existence signal (see hasHls below); it is NOT meant to be played
    // directly since it points at raw .ts bytes for HLS renditions.
    const hasHls = Boolean(record.hlsPlaylists && Object.keys(record.hlsPlaylists).length > 0);

    return {
      id: record.id,
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
      thumbnailUrl: urls.thumbnailUrl,
      qualityMeta: hasHls ? record.qualityMeta : undefined,
      hasHls
    };
  }
}
