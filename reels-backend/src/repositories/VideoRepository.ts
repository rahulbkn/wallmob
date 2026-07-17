import crypto from "crypto";
import type { CreateVideoMetadata, VideoMetadata } from "../types/video";
import { NotFoundError } from "../utils/errors";

/**
 * Metadata persistence is intentionally behind its own interface, separate
 * from StorageProvider (requirement 8: "separate metadata from storage").
 * This lets the DB be swapped (Mongo, Postgres, DynamoDB, ...) completely
 * independently of which StorageProvider is active — the two axes of
 * pluggability don't have to move together.
 */
export interface VideoRepository {
  create(data: CreateVideoMetadata): Promise<VideoMetadata>;
  getById(id: string): Promise<VideoMetadata | null>;
  list(params: { page: number; perPage: number; category?: string; uploader?: string }): Promise<{ items: VideoMetadata[]; total: number }>;
  patch(id: string, patch: Partial<VideoMetadata>): Promise<void>;
  delete(id: string): Promise<void>;
  incrementCounter(id: string, field: "views" | "likes" | "comments" | "shares", by?: number): Promise<void>;
  findByStorageUniqueId(storageUniqueId: string): Promise<VideoMetadata | null>;
  indexStorageUniqueId(storageUniqueId: string, id: string): Promise<void>;
}

/**
 * In-memory implementation — good enough for local dev / demos. Swap for a
 * real database by implementing this same interface (e.g.
 * `MongoVideoRepository`) and changing one line in the DI container.
 */
export class InMemoryVideoRepository implements VideoRepository {
  private readonly store = new Map<string, VideoMetadata>();
  private readonly uniqueIdIndex = new Map<string, string>(); // storageUniqueId -> id

  async create(data: CreateVideoMetadata): Promise<VideoMetadata> {
    const id = crypto.randomUUID();
    const record: VideoMetadata = {
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

  async getById(id: string): Promise<VideoMetadata | null> {
    return this.store.get(id) || null;
  }

  async list(params: { page: number; perPage: number; category?: string; uploader?: string }): Promise<{ items: VideoMetadata[]; total: number }> {
    let all = Array.from(this.store.values()).sort((a, b) => b.uploadDate - a.uploadDate);
    if (params.category) all = all.filter((v) => v.category.toLowerCase() === params.category!.toLowerCase());
    if (params.uploader) all = all.filter((v) => v.uploader === params.uploader);

    const total = all.length;
    const start = (params.page - 1) * params.perPage;
    const items = all.slice(start, start + params.perPage);
    return { items, total };
  }

  async patch(id: string, patch: Partial<VideoMetadata>): Promise<void> {
    const existing = this.store.get(id);
    if (!existing) throw new NotFoundError(`Video "${id}" not found`);
    this.store.set(id, { ...existing, ...patch });
  }

  async delete(id: string): Promise<void> {
    for (const [uniqueId, mappedId] of this.uniqueIdIndex) {
      if (mappedId === id) this.uniqueIdIndex.delete(uniqueId);
    }
    this.store.delete(id);
  }

  async incrementCounter(id: string, field: "views" | "likes" | "comments" | "shares", by = 1): Promise<void> {
    const existing = this.store.get(id);
    if (!existing) throw new NotFoundError(`Video "${id}" not found`);
    existing[field] += by;
  }

  async findByStorageUniqueId(storageUniqueId: string): Promise<VideoMetadata | null> {
    const id = this.uniqueIdIndex.get(storageUniqueId);
    if (!id) return null;
    return this.store.get(id) || null;
  }

  /** Registers a provider-specific dedup key against a video id. Called by
   *  VideoUploadService right after create(). */
  async indexStorageUniqueId(storageUniqueId: string, id: string): Promise<void> {
    this.uniqueIdIndex.set(storageUniqueId, id);
  }
}
