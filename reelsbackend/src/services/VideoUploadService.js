import { BadRequestError } from "../utils/errors";
import { createLogger } from "../utils/logger";
const logger = createLogger("VideoUploadService");
const VIDEO_MIME_TYPES = ["video/mp4", "video/quicktime", "video/webm", "video/x-matroska"];
const MAX_VIDEO_SIZE = 200 * 1024 * 1024; // 200 MB
/**
 * Orchestrates a video upload. Never imports Telegram/R2/etc directly —
 * only talks to `StorageProvider` and `VideoRepository`. Swapping
 * STORAGE_PROVIDER doesn't require touching this file (requirement 6).
 */
export class VideoUploadService {
    storage;
    videos;
    constructor(storage, videos) {
        this.storage = storage;
        this.videos = videos;
    }
    async upload(input) {
        if (!input.video?.buffer?.length)
            throw new BadRequestError("No video file provided");
        if (!VIDEO_MIME_TYPES.includes(input.video.mimeType)) {
            throw new BadRequestError(`Expected a video file, got '${input.video.mimeType || "unknown"}'`);
        }
        if (input.video.buffer.length > MAX_VIDEO_SIZE) {
            throw new BadRequestError(`Max allowed video size is ${MAX_VIDEO_SIZE / (1024 * 1024)} MB`);
        }
        if (!input.title?.trim())
            throw new BadRequestError("title is required");
        if (!input.category?.trim())
            throw new BadRequestError("category is required");
        if (!input.uploader?.trim())
            throw new BadRequestError("uploader is required");
        const uploadResult = await this.storage.uploadVideo({ data: input.video.buffer, mimeType: input.video.mimeType, filename: input.video.filename }, `videos/${Date.now()}-${input.uploader}`);
        let thumbnailKey = uploadResult.autoThumbnailKey;
        if (input.thumbnail?.buffer?.length) {
            const thumbResult = await this.storage.uploadThumbnail({ data: input.thumbnail.buffer, mimeType: input.thumbnail.mimeType, filename: input.thumbnail.filename }, `thumbnails/${Date.now()}-${input.uploader}`);
            thumbnailKey = thumbResult.thumbnailKey;
        }
        const record = await this.videos.create({
            title: input.title.trim(),
            description: input.description?.trim(),
            hashtags: input.hashtags || [],
            category: input.category.trim(),
            language: input.language,
            duration: uploadResult.duration,
            width: uploadResult.width,
            height: uploadResult.height,
            uploader: input.uploader,
            storageProvider: uploadResult.provider,
            storageKey: uploadResult.storageKey,
            thumbnailKey
        });
        if (uploadResult.storageUniqueId) {
            await this.videos.indexStorageUniqueId(uploadResult.storageUniqueId, record.id);
        }
        logger.info("video uploaded", { id: record.id, provider: uploadResult.provider });
        return this.toClientView(record.id, record, await this.resolveUrls(record.storageKey, record.thumbnailKey));
    }
    async resolveUrls(storageKey, thumbnailKey) {
        const videoUrl = await this.storage.getDownloadUrl(storageKey, { variant: "video" });
        const thumbnailUrl = thumbnailKey
            ? await this.storage.getDownloadUrl(thumbnailKey, { variant: "thumbnail" })
            : await this.storage.getDownloadUrl(storageKey, { variant: "thumbnail" });
        return { videoUrl, thumbnailUrl };
    }
    toClientView(id, record, urls) {
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
