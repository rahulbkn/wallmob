import type { Request, Response, NextFunction } from "express";
import { CommentService } from "../services/CommentService";

export class CommentController {
  constructor(private readonly commentService: CommentService) {}

  add = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const deviceId = req.body?.deviceId ?? req.headers["x-device-id"];
      const comment = await this.commentService.add(
        req.params.id as string,
        req.body?.author,
        req.body?.text,
        deviceId
      );
      res.status(201).json({ success: true, data: comment });
    } catch (error) {
      next(error);
    }
  };

  list = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const page = Math.max(1, parseInt(String(req.query.page || "1")) || 1);
      const perPage = Math.min(100, Math.max(1, parseInt(String(req.query.perPage || "20")) || 20));
      const result = await this.commentService.list(req.params.id as string, page, perPage);
      res.json({ success: true, ...result });
    } catch (error) {
      next(error);
    }
  };
}
