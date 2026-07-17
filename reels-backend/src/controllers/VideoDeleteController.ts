import type { Request, Response, NextFunction } from "express";
import { VideoDeleteService } from "../services/VideoDeleteService";

export class VideoDeleteController {
  constructor(private readonly deleteService: VideoDeleteService) {}

  handle = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      // Accept token from JSON body or query — some runtimes strip DELETE bodies.
      const fromBody = typeof req.body?.ownerToken === "string" ? req.body.ownerToken : undefined;
      const fromQuery = typeof req.query?.ownerToken === "string" ? req.query.ownerToken : undefined;
      const ownerToken = fromBody || fromQuery;
      await this.deleteService.delete(req.params.id as string, ownerToken);
      res.json({ success: true });
    } catch (error) {
      next(error);
    }
  };
}
