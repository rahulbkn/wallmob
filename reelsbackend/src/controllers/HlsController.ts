import type { Request, Response, NextFunction } from "express";
import type { VideoRepository } from "../repositories/VideoRepository";
import { BadRequestError, NotFoundError } from "../utils/errors";
import { createLogger } from "../utils/logger";

const logger = createLogger("HlsController");

/**
 * Serves adaptive HLS without any per-segment object storage. Each quality
 * is transcoded by transcoder/server.js into ONE media file plus a
 * single-file playlist (EXT-X-BYTERANGE against that one file). Byte-range
 * playback is handled entirely by the existing /stream Range support —
 * this controller only needs to build the master playlist and fill in the
 * real stream URL on each variant playlist.
 */
export class HlsController {
  constructor(private readonly videos: VideoRepository) {}

  master = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const id = String(req.query.id || "");
      if (!id) throw new BadRequestError("Missing 'id' query parameter");

      const record = await this.videos.getById(id);
      if (!record) throw new NotFoundError(`Video "${id}" not found`);
      if (!record.hlsPlaylists || Object.keys(record.hlsPlaylists).length === 0) {
        throw new NotFoundError(`No HLS renditions available for video "${id}" yet`);
      }

      let playlist = "#EXTM3U\n#EXT-X-VERSION:4\n";
      for (const label of Object.keys(record.hlsPlaylists)) {
        const meta = record.qualityMeta?.[label];
        const bandwidth = meta?.bandwidth ?? 1000000;
        const resolution = meta ? `,RESOLUTION=${meta.width}x${meta.height}` : "";
        playlist += `#EXT-X-STREAM-INF:BANDWIDTH=${bandwidth}${resolution}\n`;
        playlist += `/hls/variant?id=${encodeURIComponent(id)}&q=${encodeURIComponent(label)}\n`;
      }

      res.setHeader("Content-Type", "application/vnd.apple.mpegurl");
      res.setHeader("Cache-Control", "public, max-age=300");
      res.send(playlist);
    } catch (error) {
      next(error);
    }
  };

  variant = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const id = String(req.query.id || "");
      const q = String(req.query.q || "");
      if (!id || !q) throw new BadRequestError("Missing 'id' or 'q' query parameter");

      const record = await this.videos.getById(id);
      if (!record) throw new NotFoundError(`Video "${id}" not found`);

      const template = record.hlsPlaylists?.[q];
      const storageKey = record.qualities?.[q];
      if (!template || !storageKey) throw new NotFoundError(`No rendition "${q}" for video "${id}"`);

      const streamUrl = `/stream?key=${encodeURIComponent(storageKey)}&variant=video`;
      const playlist = template.split("{{STREAM_URL}}").join(streamUrl);

      res.setHeader("Content-Type", "application/vnd.apple.mpegurl");
      res.setHeader("Cache-Control", "public, max-age=3600");
      res.send(playlist);
    } catch (error) {
      next(error);
    }
  };
}
