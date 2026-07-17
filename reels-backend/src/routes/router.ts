import { Router, Request, Response } from "express";
import type { AppContainer } from "../di/container";
import { uploadMiddleware } from "../utils/upload";

export function buildRouter(container: AppContainer): Router {
  const router = Router();

  // --- Videos: upload, feed, detail, interactions, delete -----------------
  router.post("/api/videos", uploadMiddleware, container.videoUploadController.handle);
  router.get("/api/videos", container.videoFeedController.getFeed);
  router.get("/api/videos/:id", container.videoFeedController.getById);
  router.post("/api/videos/:id/view", container.videoFeedController.recordView);
  router.post("/api/videos/:id/like", container.videoFeedController.like);
  router.post("/api/videos/:id/share", container.videoFeedController.share);
  router.delete("/api/videos/:id", container.videoDeleteController.handle);

  // --- Comments -------------------------------------------------------------
  router.get("/api/videos/:id/comments", container.commentController.list);
  router.post("/api/videos/:id/comments", container.commentController.add);

  // --- Chunked upload (query params: ?uploadId=xxx&index=N) --------------
  router.post("/api/videos/upload-chunk", container.chunkedUploadController.uploadChunk);

  // --- Chunked upload metadata endpoints ---------------------------------
  router.post("/api/videos/init-upload", container.chunkedUploadController.initUpload);
  router.post("/api/videos/complete-upload", container.chunkedUploadController.completeUpload);

  // --- Transcode callback (called by the Render transcoder) -------------
  router.post("/api/videos/transcode-callback", container.transcodeController.callback);

  // --- Generic media stream — works for whichever provider is active -----
  router.get("/stream", container.streamController.handle);

  // --- HLS adaptive streaming — single-file-per-quality playlists, byte
  // ranges served by /stream, no per-segment object storage required.
  router.get("/hls/master", container.hlsController.master);
  router.get("/hls/variant", container.hlsController.variant);

  router.get("/health", (_req: Request, res: Response) => res.json({ success: true, provider: container.storage.name }));

  return router;
}
