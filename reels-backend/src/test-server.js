import http from "http";
import { createApp } from "./app";
import { readEnv } from "./config/env";
import { readFileSync } from "fs";
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
const env = readEnv({ ...process.env, ...envFile });
const app = createApp(env);
const port = env.PORT;
const server = app.listen(port, () => {
    console.log(`Server up on http://localhost:${port}`);
    // Test health
    http.get(`http://localhost:${port}/health`, (res) => {
        let data = "";
        res.on("data", chunk => data += chunk);
        res.on("end", () => {
            console.log("GET /health =>", data);
            server.close();
        });
    }).on("error", (err) => {
        console.log("Error:", err.message);
        server.close();
    });
});
