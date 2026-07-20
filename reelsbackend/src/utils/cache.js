/** Default cache used by this Node/Express deployment. Swappable for Redis
 *  etc. later without touching services — they only depend on CacheStore. */
export class InMemoryCacheStore {
    store = new Map();
    async get(key) {
        const entry = this.store.get(key);
        if (!entry)
            return null;
        if (Date.now() > entry.expiresAt) {
            this.store.delete(key);
            return null;
        }
        return entry.value;
    }
    async set(key, value, ttlSeconds) {
        this.store.set(key, { value, expiresAt: Date.now() + ttlSeconds * 1000 });
    }
    async delete(key) {
        this.store.delete(key);
    }
}
/**
 * If this backend is ever deployed as (or behind) a Cloudflare Worker —
 * e.g. an R2StorageProvider implementation running at the edge — swap in
 * an implementation like this for edge caching of hot video/thumbnail
 * responses:
 *
 *   class CloudflareEdgeCacheStore implements CacheStore {
 *     private cache = caches.default; // Workers-only global
 *     async get<T>(key: string) {
 *       const res = await this.cache.match(new Request(key));
 *       return res ? ((await res.json()) as T) : null;
 *     }
 *     async set<T>(key: string, value: T, ttlSeconds: number) {
 *       const res = new Response(JSON.stringify(value), {
 *         headers: { "Cache-Control": `max-age=${ttlSeconds}` }
 *       });
 *       await this.cache.put(new Request(key), res);
 *     }
 *     async delete(key: string) {
 *       await this.cache.delete(new Request(key));
 *     }
 *   }
 *
 * Not instantiated here since `caches.default` doesn't exist in Node — kept
 * as documentation so the swap is a copy-paste away, matching the same
 * CacheStore contract every other cache implementation uses.
 */
