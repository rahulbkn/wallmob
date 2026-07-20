import type { Request, Response, NextFunction } from "express";
import { Readable } from "stream";
import type { StorageProvider } from "../types/storage";
import { BadRequestError, UpstreamServiceError } from "../utils/errors";
import { pendingVideoCache } from "../services/VideoUploadService";

const MAX_CHUNK_BYTES = 90 * 1024 * 1024;

export class StreamController {
  constructor(private readonly storage: StorageProvider) {}

  handle = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const key = String(req.query.key || "");
      const variant = (req.query.variant as "video" | "thumbnail") || "video";
      if (!key) throw new BadRequestError("Missing 'key' query parameter");

      let range: { start: number; end?: number } | undefined;
      const rangeHeader = req.headers.range;
      if (rangeHeader) {
        const match = /bytes=(\d+)-(\d*)/.exec(rangeHeader);
        if (match) {
          let start = Number(match[1]);
          let end = match[2] ? Number(match[2]) : undefined;
          if (end === undefined || end - start + 1 > MAX_CHUNK_BYTES) {
            end = start + MAX_CHUNK_BYTES - 1;
          }
          range = { start, end };
        }
      } else {
        range = { start: 0, end: MAX_CHUNK_BYTES - 1 };
      }

      // Pending keys are served directly from the in-memory/edge cache
      // while the Telegram upload finishes in the background.
      if (key.startsWith("pending:")) {
        await this.servePending(key, range, res, next);
        return;
      }

      const stream = await this.storage.getVideoStream(key, variant === "video" ? range : undefined);

      res.status(range ? 206 : 200);
      res.setHeader("Content-Type", stream.contentType);
      if (stream.contentLength) res.setHeader("Content-Length", String(stream.contentLength));
      if (stream.contentRange) res.setHeader("Content-Range", stream.contentRange);
      if (stream.acceptsRanges) res.setHeader("Accept-Ranges", "bytes");
      res.setHeader("Cache-Control", "public, max-age=86400");

      if (Buffer.isBuffer(stream.body)) {
        res.end(stream.body);
      } else {
        const readable = stream.body as unknown as Readable;
        readable.on("error", (err) => {
          if (!res.headersSent) next(err);
          else res.destroy();
        });
        res.on("close", () => readable.destroy());
        readable.pipe(res);
      }
    } catch (error) {
      next(error);
    }
  };

  private async servePending(key: string, range: { start: number; end?: number } | undefined, res: any, next: any): Promise<void> {
    const cached = pendingVideoCache.get(key);
    let data: Buffer;
    let mime: string;
    if (cached) {
      data = cached.data;
      mime = cached.mimeType;
    } else {
      // Fallback to edge cache
      try {
        const r = new Request(`https://reels-video-cache/video/${key}`);
        const resp = await caches.default.match(r);
        if (!resp || !resp.ok) throw new Error("not found");
        const bytes = await resp.bytes();
        data = Buffer.from(bytes);
        mime = resp.headers.get("content-type") || "video/mp4";
      } catch {
        throw new UpstreamServiceError(`Pending video not found for key "${key}"`);
      }
    }

    const totalSize = data.length;
    let start = range?.start ?? 0;
    let end = range?.end !== undefined ? Math.min(range.end, totalSize - 1) : totalSize - 1;
    if (start >= totalSize) start = totalSize - 1;
    if (end >= totalSize) end = totalSize - 1;
    const chunk = data.slice(start, end + 1);

    res.status(range ? 206 : 200);
    res.setHeader("Content-Type", mime);
    res.setHeader("Content-Length", String(chunk.length));
    res.setHeader("Content-Range", `bytes ${start}-${end}/${totalSize}`);
    res.setHeader("Accept-Ranges", "bytes");
    res.setHeader("Cache-Control", "public, max-age=86400");
    res.end(chunk);
  }
}
