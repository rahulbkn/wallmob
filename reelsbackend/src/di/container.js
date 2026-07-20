import { createStorageProvider } from "../providers/storage/StorageFactory";
import { InMemoryVideoRepository } from "../repositories/VideoRepository";
import { InMemoryCacheStore } from "../utils/cache";
import { VideoUploadService } from "../services/VideoUploadService";
import { VideoFeedService } from "../services/VideoFeedService";
import { VideoDeleteService } from "../services/VideoDeleteService";
import { VideoUploadController } from "../controllers/VideoUploadController";
import { VideoFeedController } from "../controllers/VideoFeedController";
import { VideoDeleteController } from "../controllers/VideoDeleteController";
import { StreamController } from "../controllers/StreamController";
/**
 * Built once at process startup. `createStorageProvider(env)` is the ONLY
 * switch point — everything below it (repository, services, controllers)
 * only ever sees the `StorageProvider` interface, never a concrete
 * provider class. Migrating STORAGE_PROVIDER never requires touching this
 * wiring beyond StorageFactory itself.
 */
export function buildContainer(env) {
    const storage = createStorageProvider(env);
    const videos = new InMemoryVideoRepository();
    const cache = new InMemoryCacheStore();
    const uploadService = new VideoUploadService(storage, videos);
    const feedService = new VideoFeedService(storage, videos, cache);
    const deleteService = new VideoDeleteService(storage, videos);
    return {
        storage,
        videos,
        videoUploadController: new VideoUploadController(uploadService),
        videoFeedController: new VideoFeedController(feedService),
        videoDeleteController: new VideoDeleteController(deleteService),
        streamController: new StreamController(storage)
    };
}
