import { NotFoundError } from "../utils/errors";
const URL_CACHE_TTL_SECONDS = 300;
/**
 * Turns internal `VideoMetadata` (which knows about storageProvider /
 * storageKey) into `ClientVideoView` (which only has resolved URLs).
 * Clients hitting the feed/detail endpoints never learn whether a video
 * lives in Telegram, R2, or Supabase.
 */
export class VideoFeedService {
    storage;
    videos;
    cache;
    constructor(storage, videos, cache) {
        this.storage = storage;
        this.videos = videos;
        this.cache = cache;
    }
    async getFeed(params) {
        const { items, total } = await this.videos.list(params);
        const views = await Promise.all(items.map((v) => this.toClientView(v)));
        return { items: views, total, page: params.page, perPage: params.perPage };
    }
    async getById(id) {
        const record = await this.videos.getById(id);
        if (!record)
            throw new NotFoundError(`Video "${id}" not found`);
        return this.toClientView(record);
    }
    async recordView(id) {
        await this.videos.incrementCounter(id, "views");
    }
    async like(id) {
        await this.videos.incrementCounter(id, "likes");
    }
    async share(id) {
        await this.videos.incrementCounter(id, "shares");
    }
    async toClientView(record) {
        const cacheKey = `download-url:${record.storageProvider}:${record.storageKey}:${record.thumbnailKey || ""}`;
        let urls = await this.cache.get(cacheKey);
        if (!urls) {
            const videoUrl = await this.storage.getDownloadUrl(record.storageKey, { variant: "video" });
            const thumbnailUrl = record.thumbnailKey
                ? await this.storage.getDownloadUrl(record.thumbnailKey, { variant: "thumbnail" })
                : await this.storage.getDownloadUrl(record.storageKey, { variant: "thumbnail" });
            urls = { videoUrl, thumbnailUrl };
            await this.cache.set(cacheKey, urls, URL_CACHE_TTL_SECONDS);
        }
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
            thumbnailUrl: urls.thumbnailUrl
        };
    }
}
