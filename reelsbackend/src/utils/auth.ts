import type { Request } from "express";
import { AppError } from "./errors";

const USER_ID_MAX = 160;

export class UnauthorizedError extends AppError {
  constructor(message = "Login is required") {
    super(message, 401, "UNAUTHORIZED");
  }
}

export function normalizeUserId(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const id = raw.trim().slice(0, USER_ID_MAX);
  if (!id) return null;
  if (["unknown", "anonymous", "guest"].includes(id.toLowerCase())) return null;
  if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(id) && !/^[A-Za-z0-9._:@-]{3,160}$/.test(id)) return null;
  return id;
}

export function userIdFromRequest(req: Request): string | null {
  return normalizeUserId(
    req.headers["x-user-id"] ??
    req.headers["x-user-email"] ??
    req.query?.userId ??
    req.body?.userId ??
    req.body?.userEmail
  );
}

export function requireLoggedUser(req: Request): string {
  const userId = userIdFromRequest(req);
  if (!userId) throw new UnauthorizedError();
  return userId;
}

export function parseAdminUserIds(raw?: string): Set<string> {
  return new Set((raw || "").split(",").map((id) => normalizeUserId(id)?.toLowerCase()).filter(Boolean) as string[]);
}

export function requireAdminUser(req: Request, adminUserIds: Set<string>): string {
  const userId = requireLoggedUser(req);
  if (adminUserIds.size === 0 || !adminUserIds.has(userId.toLowerCase())) {
    throw new UnauthorizedError("Admin access is required");
  }
  return userId;
}

