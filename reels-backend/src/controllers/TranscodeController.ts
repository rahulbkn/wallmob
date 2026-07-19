import type { Request, Response, NextFunction } from "express";
import { VideoUploadService } from "../services/VideoUploadService";
import { BadRequestError } from "../utils/errors";

export class TranscodeController {
  constructor(
    private readonly uploadService: VideoUploadService,
    private readonly transcoderSecret: string
  ) {}

  callback = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const { videoId, qualities, playlists, qualityMeta, masterPlaylistUrl, secret } = req.body;
      if (secret !== this.transcoderSecret) {
        throw new BadRequestError("Invalid secret");
      }
      if (!videoId) {
        throw new BadRequestError("videoId required");
      }

      if (qualities && Object.keys(qualities).length > 0) {
        await this.uploadService.updateQualities(videoId, qualities, playlists, qualityMeta);
      }

      if (masterPlaylistUrl) {
        await this.uploadService.updateQualities(videoId, {}, undefined, undefined, masterPlaylistUrl);
      }

      res.json({ success: true });
    } catch (error) {
      next(error);
    }
  };
}
