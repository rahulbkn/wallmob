import crypto from "crypto";
import type { StorageProvider } from "../types/storage";
import type { VideoRepository } from "../repositories/VideoRepository";
import type { CommentRepository } from "../repositories/CommentRepository";
import { ForbiddenError, NotFoundError } from "../utils/errors";
import { createLogger } from "../utils/logger";

const logger = createLogger("VideoDeleteService");

export class VideoDeleteService {
  constructor(
    private readonly storage: StorageProvider,
    private readonly videos: VideoRepository,
    private readonly ownerTokenSecret?: string,
    private readonly comments?: CommentRepository
  ) {}

  async delete(id: string, ownerToken?: string): Promise<void> {
    const record = await this.videos.getById(id);
    if (!record) throw new NotFoundError(`Video "${id}" not found`);

    const secret = this.ownerTokenSecret || "dev-only-insecure-secret-set-OWNER_TOKEN_SECRET";
    const expected = crypto.createHmac("sha256", secret).update(id).digest("hex");
    if (!ownerToken || ownerToken !== expected) {
      throw new ForbiddenError("Invalid or missing ownerToken");
    }

    if (record.thumbnailKey && record.thumbnailKey !== record.storageKey) {
      await this.storage.deleteFile(record.thumbnailKey).catch((e: unknown) =>
        logger.error("thumbnail delete failed", { error: (e as Error).message })
      );
    }

    await this.storage.deleteFile(record.storageKey).catch((e: unknown) =>
      logger.error("video delete failed", { error: (e as Error).message, storageKey: record.storageKey })
    );

    if (this.comments) {
      await this.comments.deleteAllForVideo(id).catch((e: unknown) =>
        logger.error("comment cleanup failed", { error: (e as Error).message, id })
      );
    }

    await this.videos.delete(id);
    logger.info("video deleted", { id });
  }
}
