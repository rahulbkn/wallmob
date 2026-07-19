import type { Request, Response, NextFunction } from "express";
import { VideoDeleteService } from "../services/VideoDeleteService";
import { requireLoggedUser, userIdFromRequest } from "../utils/auth";

export class VideoDeleteController {
  constructor(
    private readonly deleteService: VideoDeleteService,
    private readonly adminUserIds: Set<string>
  ) {}

  handle = async (req: Request, res: Response, next: NextFunction): Promise<void> => {
    try {
      const userId = requireLoggedUser(req);
      const isAdmin = this.adminUserIds.has(userId.toLowerCase());
      // Accept token from JSON body or query — some runtimes strip DELETE bodies.
      const fromBody = typeof req.body?.ownerToken === "string" ? req.body.ownerToken : undefined;
      const fromQuery = typeof req.query?.ownerToken === "string" ? req.query.ownerToken : undefined;
      const ownerToken = fromBody || fromQuery;
      await this.deleteService.delete(req.params.id as string, ownerToken, isAdmin);
      res.json({ success: true });
    } catch (error) {
      next(error);
    }
  };
}
