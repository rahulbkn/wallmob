import type { Env } from "../config/env";
import type { KVNamespace, D1Database } from "@cloudflare/workers-types";
import { createStorageProvider } from "../providers/storage/StorageFactory";
import type { StorageProvider } from "../types/storage";

import { InMemoryVideoRepository, VideoRepository } from "../repositories/VideoRepository";
import { D1VideoRepository } from "../repositories/D1VideoRepository";
import { InMemoryCommentRepository, CommentRepository } from "../repositories/CommentRepository";
import { D1CommentRepository } from "../repositories/D1CommentRepository";
import { InMemoryCacheStore, CacheStore } from "../utils/cache";
import { DeviceInteractionStore } from "../utils/deviceInteractions";

import { VideoUploadService } from "../services/VideoUploadService";
import { VideoFeedService } from "../services/VideoFeedService";
import { VideoDeleteService } from "../services/VideoDeleteService";
import { CommentService } from "../services/CommentService";

import { VideoUploadController } from "../controllers/VideoUploadController";
import { VideoFeedController } from "../controllers/VideoFeedController";
import { VideoDeleteController } from "../controllers/VideoDeleteController";
import { CommentController } from "../controllers/CommentController";
import { StreamController } from "../controllers/StreamController";
import { HlsController } from "../controllers/HlsController";
import { ChunkedUploadController } from "../controllers/ChunkedUploadController";
import { TranscodeController } from "../controllers/TranscodeController";

export interface AppContainer {
  storage: StorageProvider;
  videos: VideoRepository;
  comments: CommentRepository;
  videoUploadController: VideoUploadController;
  videoFeedController: VideoFeedController;
  videoDeleteController: VideoDeleteController;
  commentController: CommentController;
  streamController: StreamController;
  hlsController: HlsController;
  chunkedUploadController: ChunkedUploadController;
  transcodeController: TranscodeController;
}

/**
 * Built once at process startup. Uses D1 for persistent metadata when
 * available, otherwise falls back to in-memory storage.
 */
export function buildContainer(env: Env & { DB?: D1Database; KV?: KVNamespace }): AppContainer {
  const storage = createStorageProvider(env);

  const videos: VideoRepository = env.DB ? new D1VideoRepository(env.DB) : new InMemoryVideoRepository();
  const comments: CommentRepository = env.DB ? new D1CommentRepository(env.DB) : new InMemoryCommentRepository();
  const cache: CacheStore = new InMemoryCacheStore();
  const interactions = new DeviceInteractionStore(env.KV || null, cache);

  const uploadService = new VideoUploadService(storage, videos, env.TRANSCODER_URL, env.TRANSCODER_SECRET, env.OWNER_TOKEN_SECRET);
  const feedService = new VideoFeedService(storage, videos, cache, interactions);
  const deleteService = new VideoDeleteService(storage, videos, env.OWNER_TOKEN_SECRET, comments);
  const commentService = new CommentService(comments, videos, interactions);

  return {
    storage,
    videos,
    comments,
    videoUploadController: new VideoUploadController(uploadService),
    videoFeedController: new VideoFeedController(feedService),
    videoDeleteController: new VideoDeleteController(deleteService),
    commentController: new CommentController(commentService),
    streamController: new StreamController(storage),
    hlsController: new HlsController(videos),
    chunkedUploadController: new ChunkedUploadController(uploadService, env.KV || null),
    transcodeController: new TranscodeController(uploadService, env.TRANSCODER_SECRET || "")
  };
}
