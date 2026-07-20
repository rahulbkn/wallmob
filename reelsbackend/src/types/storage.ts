/**
 * StorageProvider is the ONLY contract the rest of the app depends on for
 * reading/writing video files. No service, controller, or route may import
 * Telegram/R2/Supabase/S3 APIs directly — everything goes through this
 * interface, built by StorageFactory and wired in via the DI container.
 */

import type { Readable } from "stream";

export interface MediaFile {
  data: Buffer;
  filename?: string;
  mimeType: string;
}

export interface UploadResult {
  /** Opaque key the caller persists in the metadata DB. Never a raw
   *  provider URL — providers may rotate/expire URLs internally. */
  storageKey: string;
  /** Provider-specific secondary id (e.g. Telegram's file_unique_id), used
   *  for dedup lookups. */
  storageUniqueId?: string;
  mimeType: string;
  fileSize: number;
  width: number;
  height: number;
  duration: number; // seconds
  provider: string;
  /** Set when the provider auto-generates a thumbnail as part of the
   *  primary upload (Telegram does this for sendVideo). Callers can use
   *  this directly instead of making a separate uploadThumbnail() call. */
  autoThumbnailKey?: string;
}

export interface ThumbnailUploadResult {
  thumbnailKey: string;
  provider: string;
}

export interface StreamResult {
  body: Readable | Buffer;
  contentType: string;
  contentLength?: number;
  /** Set when the provider supports HTTP range requests (video scrubbing) */
  acceptsRanges?: boolean;
  /** Full Content-Range header value (e.g. "bytes 0-100/1000") for 206 responses */
  contentRange?: string;
}

export interface DownloadUrlOptions {
  /** Seconds until the returned URL should be treated as stale. Providers
   *  that only support static/public URLs may ignore this. */
  expiresInSeconds?: number;
  variant?: "video" | "thumbnail";
}

/**
 * Every StorageProvider implementation (Telegram today; R2 / Supabase /
 * S3 / Local tomorrow) must implement this exact surface. Method names
 * match the original spec verbatim.
 */
export interface StorageProvider {
  readonly name: string;

  uploadVideo(file: MediaFile, key: string): Promise<UploadResult>;
  uploadThumbnail(file: MediaFile, key: string): Promise<ThumbnailUploadResult>;
  getVideoStream(storageKey: string, range?: { start: number; end?: number }): Promise<StreamResult>;
  getDownloadUrl(storageKey: string, options?: DownloadUrlOptions): Promise<string>;
  deleteFile(storageKey: string): Promise<boolean>;
  fileExists(storageKey: string): Promise<boolean>;
}
