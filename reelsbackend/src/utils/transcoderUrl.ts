/**
 * The remote transcoder (FastAPI/uvicorn) often builds absolute playlist
 * URLs from its local bind address (e.g. http://localhost:8000/...).
 * Clients can't reach that. Rewrite those to the public origin derived
 * from TRANSCODER_URL (e.g. https://….onrender.com).
 */

export function transcoderPublicOrigin(transcoderUrl?: string): string | undefined {
  if (!transcoderUrl) return undefined;
  try {
    return new URL(transcoderUrl).origin;
  } catch {
    return undefined;
  }
}

/**
 * If `url` points at a loopback host, rewrite it onto the public
 * transcoder origin while keeping path/query/hash. Non-loopback URLs
 * and missing inputs pass through unchanged.
 */
export function rewriteLocalTranscoderUrl(
  url: string | undefined,
  transcoderUrl?: string
): string | undefined {
  if (!url) return undefined;
  const origin = transcoderPublicOrigin(transcoderUrl);
  if (!origin) return url;

  try {
    const parsed = new URL(url);
    const host = parsed.hostname.toLowerCase();
    if (host === "localhost" || host === "127.0.0.1" || host === "0.0.0.0" || host === "::1") {
      return `${origin}${parsed.pathname}${parsed.search}${parsed.hash}`;
    }
    return url;
  } catch {
    return url;
  }
}
