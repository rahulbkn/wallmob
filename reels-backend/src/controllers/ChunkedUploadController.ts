import type { Request, Response, NextFunction } from "express";
import type { ExecutionContext, KVNamespace } from "@cloudflare/workers-types";
import crypto from "crypto";
import { VideoUploadService } from "../services/VideoUploadService";
import { BadRequestError } from "../utils/errors";

const REASSEMBLY_CONCURRENCY = 8;

export class ChunkedUploadController {
  constructor(
    private readonly uploadService: VideoUploadService,
    private readonly kv: KVNamespace | null
  ) {}

  private async checkRateLimit(ip: string): Promise<void> {
    if (!this.kv) return;
    const key = `ratelimit:upload:${ip}`;
    const count = Number(await this.kv.get(key)) || 0;
    if (count >= 10) throw new BadRequestError("Upload limit reached, try again in an hour");
    await this.kv.put(key, String(count + 1), { expirationTtl: 3600 });
  }

  initUpload = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      await this.checkRateLimit(req.ip || "unknown");
      const { title, description, uploader, category, hashtags, fileName, fileSize, mimeType, totalChunks } = req.body;
      if (!title?.trim()) throw new BadRequestError("title is required");
      if (!uploader?.trim()) throw new BadRequestError("uploader is required");
      if (!category?.trim()) throw new BadRequestError("category is required");
      if (!totalChunks) throw new BadRequestError("totalChunks is required");

      const uploadId = crypto.randomUUID();
      const tags = typeof hashtags === "string"
        ? hashtags.split(",").map((h: string) => h.trim()).filter(Boolean)
        : Array.isArray(hashtags) ? hashtags : [];

      if (this.kv) {
        await this.kv.put(`upload:${uploadId}:meta`, JSON.stringify({
          title: title.trim(),
          description: description?.trim(),
          uploader: uploader.trim(),
          category: category.trim(),
          hashtags: tags,
          fileName: fileName || "",
          fileSize: Number(fileSize) || 0,
          mimeType: mimeType || "video/mp4",
          totalChunks: Number(totalChunks),
        }), { expirationTtl: 86400 });
      }

      res.json({ uploadId });
    } catch (error) {
      next(error);
    }
  };

  uploadChunk = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const uploadId = req.query.uploadId as string;
      const chunkIndex = req.query.index as string;
      if (!uploadId || chunkIndex === undefined) throw new BadRequestError("Missing uploadId or index query param");
      if (!this.kv) throw new BadRequestError("KV namespace not available");

      const chunk = this.parseChunkBody(req.body);
      if (chunk.length === 0) throw new BadRequestError("Empty chunk body");
      // Cloudflare KV value limit is 25 MiB; keep chunks well under that.
      if (chunk.length > 12 * 1024 * 1024) {
        throw new BadRequestError("Chunk too large (max 12MB)");
      }

      await this.kv.put(`upload:${uploadId}:chunk:${chunkIndex}`, chunk, { expirationTtl: 86400 });

      res.json({ success: true, chunkIndex: Number(chunkIndex), bytes: chunk.length });
    } catch (error) {
      next(error);
    }
  };

  /** Accept raw binary (preferred) or legacy base64 text. */
  private parseChunkBody(body: unknown): Buffer {
    if (Buffer.isBuffer(body)) return body;
    if (body instanceof ArrayBuffer) return Buffer.from(body);
    if (ArrayBuffer.isView(body)) {
      return Buffer.from(body.buffer, body.byteOffset, body.byteLength);
    }
    if (typeof body === "string") {
      if (!body.length) return Buffer.alloc(0);
      // Heuristic: pure base64 payloads from older clients
      const compact = body.replace(/\s+/g, "");
      if (/^[A-Za-z0-9+/]+=*$/.test(compact) && compact.length % 4 === 0) {
        return Buffer.from(compact, "base64");
      }
      return Buffer.from(body, "binary");
    }
    throw new BadRequestError("No chunk data in body");
  }

  /** Fetches one chunk from KV with backoff retries — chunks can briefly
   *  lag behind the client's upload confirmation due to KV's eventual
   *  consistency, so a few short retries avoid failing a whole upload
   *  over a transient miss. */
  private async fetchChunkWithRetry(uploadId: string, index: number): Promise<Buffer> {
    for (let retry = 0; retry < 5; retry++) {
      const chunkData = await this.kv!.get(`upload:${uploadId}:chunk:${index}`, { type: "arrayBuffer" });
      if (chunkData) return Buffer.from(chunkData);
      await new Promise((r) => setTimeout(r, 300 * (retry + 1)));
    }
    throw new BadRequestError(`Chunk ${index} not found after retries`);
  }

  completeUpload = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const { uploadId } = req.body;
      if (!uploadId) throw new BadRequestError("Missing uploadId");
      if (!this.kv) throw new BadRequestError("KV namespace not available");

      const metaStr = await this.kv.get(`upload:${uploadId}:meta`);
      if (!metaStr) throw new BadRequestError("Upload session not found or expired");
      const meta = JSON.parse(metaStr);

      const { title, description, uploader, category, hashtags, mimeType, totalChunks, fileName } = meta;

      // Fetch chunks in bounded-concurrency batches instead of one at a
      // time — this was previously a serial `for` loop awaiting each KV
      // get in turn, which meant total reassembly time scaled linearly
      // with chunk count regardless of how fast KV itself responded.
      const chunks: Buffer[] = new Array(totalChunks);
      for (let start = 0; start < totalChunks; start += REASSEMBLY_CONCURRENCY) {
        const end = Math.min(start + REASSEMBLY_CONCURRENCY, totalChunks);
        await Promise.all(
          Array.from({ length: end - start }, (_, j) => {
            const i = start + j;
            return this.fetchChunkWithRetry(uploadId, i).then((data) => {
              chunks[i] = data;
            });
          })
        );
      }

      const delPromises = [this.kv.delete(`upload:${uploadId}:meta`)];
      for (let i = 0; i < totalChunks; i++) {
        delPromises.push(this.kv.delete(`upload:${uploadId}:chunk:${i}`));
      }
      Promise.all(delPromises).catch(() => {});

      const ctx = (req as any).ctx as ExecutionContext | undefined;
      const waitUntil = ctx ? (p: Promise<unknown>) => ctx.waitUntil(p) : undefined;

      const fullBuffer = Buffer.concat(chunks);

      const result = await this.uploadService.upload({
        video: { buffer: fullBuffer, mimeType: mimeType || "video/mp4", filename: fileName || "video.mp4" },
        title,
        description,
        hashtags,
        category,
        uploader,
      }, waitUntil);

      res.status(201).json({ success: true, data: result });
    } catch (error) {
      next(error);
    }
  };
}
