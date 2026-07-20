import express from "express";
import { buildContainer } from "./di/container";
import { buildRouter } from "./routes/router";
import { AppError } from "./utils/errors";
import { createLogger } from "./utils/logger";
const logger = createLogger("app");
export function createApp(env) {
    const app = express();
    app.use(express.json());
    app.use(express.static("public"));
    const container = buildContainer(env);
    app.use(buildRouter(container));
    app.use((_req, res) => {
        res.status(404).json({ success: false, error: "Not Found", message: "Endpoint not found" });
    });
    app.use((error, _req, res, _next) => {
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
