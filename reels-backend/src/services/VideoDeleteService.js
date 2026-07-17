import crypto from "crypto";
import { ForbiddenError, NotFoundError } from "../utils/errors";
import { createLogger } from "../utils/logger";
const logger = createLogger("VideoDeleteService");
export class VideoDeleteService {
    storage;
    videos;
    ownerTokenSecret;
    comments;
    constructor(storage, videos, ownerTokenSecret, comments) {
        this.storage = storage;
        this.videos = videos;
        this.ownerTokenSecret = ownerTokenSecret;
        this.comments = comments;
    }
    async delete(id, ownerToken) {
        const record = await this.videos.getById(id);
        if (!record)
            throw new NotFoundError(`Video "${id}" not found`);
        const secret = this.ownerTokenSecret || "dev-only-insecure-secret-set-OWNER_TOKEN_SECRET";
        const expected = crypto.createHmac("sha256", secret).update(id).digest("hex");
        if (!ownerToken || ownerToken !== expected) {
            throw new ForbiddenError("Invalid or missing ownerToken");
        }
        if (record.thumbnailKey && record.thumbnailKey !== record.storageKey) {
            await this.storage.deleteFile(record.thumbnailKey).catch((e) => logger.error("thumbnail delete failed", { error: e.message }));
        }
        await this.storage.deleteFile(record.storageKey).catch((e) => logger.error("video delete failed", { error: e.message, storageKey: record.storageKey }));
        if (this.comments) {
            await this.comments.deleteAllForVideo(id).catch((e) => logger.error("comment cleanup failed", { error: e.message, id }));
        }
        await this.videos.delete(id);
        logger.info("video deleted", { id });
    }
}
