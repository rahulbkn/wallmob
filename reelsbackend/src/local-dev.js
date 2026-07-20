import { readFileSync } from "fs";
import { createApp } from "./app";
import { readEnv } from "./config/env";
process.on("uncaughtException", (err) => {
    console.error("[crash] uncaught exception:", err.message);
});
process.on("unhandledRejection", (err) => {
    console.error("[crash] unhandled rejection:", err.message);
});
function loadEnvFile(path) {
    const result = {};
    try {
        const content = readFileSync(path, "utf-8");
        for (const line of content.split("\n")) {
            const trimmed = line.trim();
            if (!trimmed || trimmed.startsWith("#"))
                continue;
            const eqIdx = trimmed.indexOf("=");
            if (eqIdx === -1)
                continue;
            const key = trimmed.slice(0, eqIdx).trim();
            const value = trimmed.slice(eqIdx + 1).trim();
            if (key)
                result[key] = value;
        }
    }
    catch { }
    return result;
}
const envFile = loadEnvFile(".env");
const env = readEnv({ ...envFile, ...process.env });
const app = createApp(env);
const port = env.PORT;
app.listen(port, "0.0.0.0", () => {
    console.log(`Local dev server running at http://0.0.0.0:${port}`);
    console.log(`Storage provider: ${env.STORAGE_PROVIDER}`);
});
