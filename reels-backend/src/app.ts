import express, { Express, NextFunction, Request, Response } from "express";
import type { KVNamespace, D1Database } from "@cloudflare/workers-types";
import { readEnv } from "./config/env";
import type { Env as ConfigEnv } from "./config/env";
import { buildContainer } from "./di/container";
import { buildRouter } from "./routes/router";
import { AppError } from "./utils/errors";
import { createLogger } from "./utils/logger";

const logger = createLogger("app");

export function createApp(env: ConfigEnv & { DB?: D1Database; KV?: KVNamespace }): Express {
  const app = express();

  // Binary chunks (preferred) — keeps CPU low on Workers (no base64 decode).
  // Limit raised from 6mb to 12mb to accommodate the larger (8MB) chunk
  // size used by the client uploader — fewer, bigger chunks means fewer
  // KV round trips per upload, which is the dominant source of latency.
  app.use(express.raw({ type: ["application/octet-stream", "application/offset+octet-stream"], limit: "12mb" }));
  // Legacy base64 text chunks
  app.use(express.text({ type: "text/plain", limit: "12mb" }));
  app.use(express.json({ limit: "2mb" }));
  app.use(express.static("public"));

  app.use((_req: Request, res: Response, next: NextFunction) => {
    res.setHeader("Access-Control-Allow-Origin", "*");
    res.setHeader("Access-Control-Allow-Methods", "GET,POST,DELETE,OPTIONS");
    res.setHeader("Access-Control-Allow-Headers", "Content-Type, X-Device-Id");
    if (_req.method === "OPTIONS") { res.status(204).end(); return; }
    next();
  });

  const container = buildContainer(env);
  app.use(buildRouter(container));

  app.use((_req: Request, res: Response) => {
    res.status(404).json({ success: false, error: "Not Found", message: "Endpoint not found" });
  });

  app.use((error: unknown, _req: Request, res: Response, _next: NextFunction) => {
    if (error instanceof AppError) {
      res.status(error.status).json({ success: false, error: error.name, message: error.message, code: error.code });
      return;
    }
    const message = error instanceof Error ? error.message : String(error);
    logger.error("Unhandled error", { error: message });
    res.status(500).json({ success: false, error: "Internal Server Error", message });
  });

  return app;
}
