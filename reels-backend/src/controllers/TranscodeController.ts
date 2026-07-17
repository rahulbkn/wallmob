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
      const { videoId, qualities, playlists, qualityMeta, secret } = req.body;
      if (secret !== this.transcoderSecret) {
        throw new BadRequestError("Invalid secret");
      }
      if (!videoId || !qualities) {
        throw new BadRequestError("videoId and qualities required");
      }

      await this.uploadService.updateQualities(videoId, qualities, playlists, qualityMeta);
      res.json({ success: true });
    } catch (error) {
      next(error);
    }
  };
}
