import type { Request, Response, NextFunction } from "express";
import { VideoFeedService } from "../services/VideoFeedService";

export class VideoFeedController {
  constructor(private readonly feedService: VideoFeedService) {}

  getFeed = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const page = Math.max(1, parseInt(String(req.query.page || "1")) || 1);
      const perPage = Math.min(50, Math.max(1, parseInt(String(req.query.perPage || "10")) || 10));
      const category = req.query.category ? String(req.query.category) : undefined;
      const uploader = req.query.uploader ? String(req.query.uploader) : undefined;

      const result = await this.feedService.getFeed({ page, perPage, category, uploader });
      res.json({ success: true, ...result });
    } catch (error) {
      next(error);
    }
  };

  getById = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const video = await this.feedService.getById(req.params.id as string);
      res.json({ success: true, data: video });
    } catch (error) {
      next(error);
    }
  };

  recordView = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      await this.feedService.recordView(req.params.id as string);
      res.json({ success: true });
    } catch (error) {
      next(error);
    }
  };

  like = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const deviceId = req.body?.deviceId ?? req.headers["x-device-id"];
      const result = await this.feedService.like(req.params.id as string, deviceId);
      res.json({ success: true, counted: result.counted, alreadyCounted: !result.counted });
    } catch (error) {
      next(error);
    }
  };

  share = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const deviceId = req.body?.deviceId ?? req.headers["x-device-id"];
      const result = await this.feedService.share(req.params.id as string, deviceId);
      res.json({ success: true, counted: result.counted, alreadyCounted: !result.counted });
    } catch (error) {
      next(error);
    }
  };
}
