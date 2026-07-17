import type { KVNamespace } from "@cloudflare/workers-types";
import type { CacheStore } from "./cache";

export type InteractionKind = "like" | "share" | "comment";

const DEVICE_ID_MAX = 80;
/** Keep interaction marks for a long time so counts stay honest. */
const INTERACTION_TTL_SECONDS = 60 * 60 * 24 * 400; // ~400 days

export function normalizeDeviceId(raw: unknown): string | null {
  if (typeof raw !== "string") return null;
  const id = raw.trim().slice(0, DEVICE_ID_MAX);
  if (!id) return null;
  // Allow UUID / base64url-ish ids only
  if (!/^[A-Za-z0-9_-]{8,80}$/.test(id)) return null;
  return id;
}

function interactionKey(kind: InteractionKind, videoId: string, deviceId: string): string {
  return `interact:${kind}:${videoId}:${deviceId}`;
}

/**
 * Device-scoped interaction ledger (like ownerToken for delete).
 * Prefer Cloudflare KV when bound; fall back to CacheStore for local dev.
 */
export class DeviceInteractionStore {
  constructor(
    private readonly kv: KVNamespace | null,
    private readonly cache: CacheStore
  ) {}

  async has(kind: InteractionKind, videoId: string, deviceId: string): Promise<boolean> {
    const key = interactionKey(kind, videoId, deviceId);
    if (this.kv) {
      const value = await this.kv.get(key);
      return value != null;
    }
    return (await this.cache.get<string>(key)) != null;
  }

  /**
   * Marks the interaction if not already recorded.
   * @returns true when this is the first time (should count), false if already done.
   */
  async claim(kind: InteractionKind, videoId: string, deviceId: string): Promise<boolean> {
    const key = interactionKey(kind, videoId, deviceId);

    if (this.kv) {
      const existing = await this.kv.get(key);
      if (existing != null) return false;
      await this.kv.put(key, "1", { expirationTtl: INTERACTION_TTL_SECONDS });
      return true;
    }

    const existing = await this.cache.get<string>(key);
    if (existing != null) return false;
    await this.cache.set(key, "1", INTERACTION_TTL_SECONDS);
    return true;
  }
}
