import crypto from "crypto";
import { NotFoundError } from "../utils/errors";
/**
 * In-memory implementation — good enough for local dev / demos. Swap for a
 * real database by implementing this same interface (e.g.
 * `MongoVideoRepository`) and changing one line in the DI container.
 */
export class InMemoryVideoRepository {
    store = new Map();
    uniqueIdIndex = new Map(); // storageUniqueId -> id
    async create(data) {
        const id = crypto.randomUUID();
        const record = {
            ...data,
            id,
            uploadDate: data.uploadDate ?? Date.now(),
            views: 0,
            likes: 0,
            comments: 0,
            shares: 0
        };
        this.store.set(id, record);
        return record;
    }
    async getById(id) {
        return this.store.get(id) || null;
    }
    async list(params) {
        let all = Array.from(this.store.values()).sort((a, b) => b.uploadDate - a.uploadDate);
        if (params.category)
            all = all.filter((v) => v.category.toLowerCase() === params.category.toLowerCase());
        if (params.uploader)
            all = all.filter((v) => v.uploader === params.uploader);
        const total = all.length;
        const start = (params.page - 1) * params.perPage;
        const items = all.slice(start, start + params.perPage);
        return { items, total };
    }
    async patch(id, patch) {
        const existing = this.store.get(id);
        if (!existing)
            throw new NotFoundError(`Video "${id}" not found`);
        this.store.set(id, { ...existing, ...patch });
    }
    async delete(id) {
        for (const [uniqueId, mappedId] of this.uniqueIdIndex) {
            if (mappedId === id)
                this.uniqueIdIndex.delete(uniqueId);
        }
        this.store.delete(id);
    }
    async incrementCounter(id, field, by = 1) {
        const existing = this.store.get(id);
        if (!existing)
            throw new NotFoundError(`Video "${id}" not found`);
        existing[field] += by;
    }
    async findByStorageUniqueId(storageUniqueId) {
        const id = this.uniqueIdIndex.get(storageUniqueId);
        if (!id)
            return null;
        return this.store.get(id) || null;
    }
    /** Registers a provider-specific dedup key against a video id. Called by
     *  VideoUploadService right after create(). */
    async indexStorageUniqueId(storageUniqueId, id) {
        this.uniqueIdIndex.set(storageUniqueId, id);
    }
}
