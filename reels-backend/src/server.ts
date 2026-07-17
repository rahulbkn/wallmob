import { Readable } from "stream";
import { EventEmitter } from "events";
import type { ExecutionContext, KVNamespace, D1Database } from "@cloudflare/workers-types";
import { createApp } from "./app";
import { readEnv } from "./config/env";

let cachedApp: ReturnType<typeof createApp> | null = null;

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext) {
    try {
      if (!cachedApp) {
        const config = readEnv(env as unknown as Record<string, string | undefined>);
        const workersEnv = env as unknown as { my_db_name?: D1Database; BINDING_NAME?: KVNamespace };
        cachedApp = createApp({ ...config, DB: workersEnv.my_db_name, KV: workersEnv.BINDING_NAME });
      }
      return await handleRequest(request, cachedApp!, ctx);
    } catch (err) {
      const msg = err instanceof Error ? err.stack || err.message : String(err);
      return new Response(JSON.stringify({ success: false, error: msg }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      });
    }
  },
};

async function handleRequest(request: Request, app: ReturnType<typeof createApp>, ctx: ExecutionContext): Promise<Response> {
  const url = new URL(request.url);
  const rawHeaders: Record<string, string> = {};
  request.headers.forEach((v, k) => { rawHeaders[k] = v; });

  // Fully buffer the inbound body before Express starts — avoids stream races
  // that hang body-parser on large chunk uploads in Workers.
  let bodyBuf = Buffer.alloc(0);
  if (request.method !== "GET" && request.method !== "HEAD" && request.body) {
    try {
      bodyBuf = Buffer.from(await request.arrayBuffer());
    } catch {
      bodyBuf = Buffer.alloc(0);
    }
  }

  const chunks: Buffer[] = [];
  let statusCode = 200;
  const respHeaders = new Headers();

  const req = new Readable({ read() {} });
  const origEmit = req.emit.bind(req);
  req.emit = function (ev: string, ...args: any[]) {
    if (ev === "aborted") return false;
    return origEmit(ev, ...args);
  } as any;

  Object.assign(req, {
    method: request.method,
    url: request.url,
    originalUrl: url.pathname + url.search,
    headers: rawHeaders,
    httpVersion: "1.1",
    query: Object.fromEntries(url.searchParams.entries()),
    params: {},
    body: undefined,
    files: {},
    path: url.pathname,
    hostname: url.hostname,
    protocol: url.protocol.slice(0, -1),
    secure: url.protocol === "https:",
    ip: rawHeaders["cf-connecting-ip"] || "127.0.0.1",
    socket: { remoteAddress: rawHeaders["cf-connecting-ip"] || "127.0.0.1" },
    ctx: ctx,
  });

  const res = new EventEmitter() as any;
  res.req = req;
  res.statusCode = 200;
  res._header = null;
  res.headersSent = false;
  res.finished = false;
  res.writableEnded = false;

  function buildResponse() {
    const body = chunks.length ? Buffer.concat(chunks) : null;
    return new Response(body, { status: statusCode, headers: respHeaders });
  }

  res.setHeader = (n: string, v: string) => respHeaders.set(n, v);
  res.getHeader = (n: string) => respHeaders.get(n);
  res.getHeaders = () => {
    const r: Record<string, string> = {};
    respHeaders.forEach((v, k) => { r[k] = v; });
    return r;
  };
  res.removeHeader = (n: string) => respHeaders.delete(n);
  res.writeHead = (s: number, h?: Record<string, string>) => {
    statusCode = s;
    res.headersSent = true;
    if (h) for (const [k, v] of Object.entries(h)) respHeaders.set(k, v);
  };
  res.write = (chunk: any) => {
    if (chunk != null) chunks.push(Buffer.from(chunk));
    return true;
  };
  res.end = (chunk?: any) => {
    if (chunk != null) chunks.push(Buffer.from(chunk));
    res.finished = true;
    res.writableEnded = true;
    res.emit("finish");
  };
  res.json = function (d: unknown) {
    respHeaders.set("Content-Type", "application/json; charset=utf-8");
    chunks.push(Buffer.from(JSON.stringify(d)));
    this.end();
    return this;
  };
  res.send = function (d: unknown) {
    if (typeof d === "object" && d !== null) {
      respHeaders.set("Content-Type", "application/json; charset=utf-8");
      chunks.push(Buffer.from(JSON.stringify(d)));
    } else {
      chunks.push(Buffer.from(String(d)));
    }
    this.end();
    return this;
  };
  res.sendStatus = function (s: number) {
    statusCode = s;
    this.end(String(s));
    return this;
  };
  res.status = function (s: number) { statusCode = s; return this; };
  res.type = (t: string) => respHeaders.set("Content-Type", t);
  res.set = function (n: string, v: string) { respHeaders.set(n, v); return this; };
  res.get = (n: string) => respHeaders.get(n);
  res.redirect = function (s: number, p?: string) {
    statusCode = s;
    if (p) respHeaders.set("Location", p);
    this.end();
    return this;
  };
  res.location = (p: string) => respHeaders.set("Location", p);
  res.attachment = () => {};
  res.cookie = () => {};
  res.clearCookie = () => {};
  res.render = () => {};
  res.format = () => {};
  res.vary = () => {};
  res.download = () => {};
  res.links = () => {};
  res.append = () => {};
  res.writeContinue = () => {};
  res.flushHeaders = () => {};
  res.addTrailers = () => {};
  res.writeProcessing = () => {};
  res._implicitHeader = () => {};

  (req as any).res = res;

  // Push the full body before Express attaches listeners so body-parser sees data.
  if (bodyBuf.length > 0) req.push(bodyBuf);
  req.push(null);

  return new Promise<Response>((resolve) => {
    let settled = false;
    const settle = (response: Response) => {
      if (settled) return;
      settled = true;
      resolve(response);
    };

    res.on("finish", () => settle(buildResponse()));

    try {
      app(req as any, res as any, (err: unknown) => {
        if (!res.finished) {
          if (err) {
            const msg = err instanceof Error ? err.message : String(err);
            settle(new Response(JSON.stringify({ error: msg }), {
              status: 500,
              headers: { "Content-Type": "application/json" },
            }));
          } else {
            settle(new Response(JSON.stringify({ error: "Not Found" }), {
              status: 404,
              headers: { "Content-Type": "application/json" },
            }));
          }
        }
      });
    } catch (e) {
      const msg = e instanceof Error ? e.message : String(e);
      settle(new Response(JSON.stringify({ error: msg }), {
        status: 500,
        headers: { "Content-Type": "application/json" },
      }));
    }
  });
}
