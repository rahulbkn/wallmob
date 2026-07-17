import type { Request, Response, NextFunction } from "express";
import type { ExecutionContext } from "@cloudflare/workers-types";
import { VideoUploadService } from "../services/VideoUploadService";
import { BadRequestError } from "../utils/errors";

interface MulterFiles {
  video?: Express.Multer.File[];
  thumbnail?: Express.Multer.File[];
}

export class VideoUploadController {
  constructor(private readonly uploadService: VideoUploadService) {}

  handle = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const files = req.files as MulterFiles;
      const videoFile = files?.video?.[0];
      if (!videoFile) throw new BadRequestError("No video file provided in 'video' field");
      const thumbFile = files?.thumbnail?.[0];

      const hashtags = typeof req.body.hashtags === "string"
        ? req.body.hashtags.split(",").map((h: string) => h.trim()).filter(Boolean)
        : Array.isArray(req.body.hashtags) ? req.body.hashtags : [];

      const ctx = (req as any).ctx as ExecutionContext | undefined;

      const result = await this.uploadService.upload({
        video: { buffer: videoFile.buffer, mimeType: videoFile.mimetype, filename: videoFile.originalname },
        thumbnail: thumbFile ? { buffer: thumbFile.buffer, mimeType: thumbFile.mimetype, filename: thumbFile.originalname } : undefined,
        title: req.body.title || "",
        description: req.body.description,
        hashtags,
        category: req.body.category || "",
        language: req.body.language,
        uploader: req.body.uploader || ""
      }, ctx ? (p) => ctx.waitUntil(p) : undefined);

      res.status(201).json({ success: true, data: result });
    } catch (error) {
      next(error);
    }
  };
}
