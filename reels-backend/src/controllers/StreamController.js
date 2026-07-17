import { BadRequestError } from "../utils/errors";
/**
 * Serves file bytes for whichever provider is active, without ever
 * revealing provider-internal URLs (e.g. api.telegram.org/file/<bot_token>/
 * ...) to the client. Clients only ever see `<server>/stream?key=...`.
 * Supports HTTP Range requests so mobile clients can scrub through videos.
 */
export class StreamController {
    storage;
    constructor(storage) {
        this.storage = storage;
    }
    handle = async (req, res, next) => {
        try {
            const key = String(req.query.key || "");
            const variant = req.query.variant || "video";
            if (!key)
                throw new BadRequestError("Missing 'key' query parameter");
            let range;
            const rangeHeader = req.headers.range;
            if (rangeHeader) {
                const match = /bytes=(\d+)-(\d*)/.exec(rangeHeader);
                if (match) {
                    range = { start: Number(match[1]), end: match[2] ? Number(match[2]) : undefined };
                }
            }
            const stream = await this.storage.getVideoStream(key, variant === "video" ? range : undefined);
            res.status(range ? 206 : 200);
            res.setHeader("Content-Type", stream.contentType);
            if (stream.contentLength)
                res.setHeader("Content-Length", String(stream.contentLength));
            if (stream.acceptsRanges)
                res.setHeader("Accept-Ranges", "bytes");
            res.setHeader("Cache-Control", "public, max-age=86400");
            if (Buffer.isBuffer(stream.body)) {
                res.end(stream.body);
            }
            else {
                const readable = stream.body;
                readable.on("error", (err) => {
                    if (!res.headersSent)
                        next(err);
                    else
                        res.destroy();
                });
                res.on("close", () => readable.destroy());
                readable.pipe(res);
            }
        }
        catch (error) {
            next(error);
        }
    };
}
