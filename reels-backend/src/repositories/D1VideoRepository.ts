import crypto from "crypto";
import type { D1Database } from "@cloudflare/workers-types";
import type { CreateVideoMetadata, VideoMetadata } from "../types/video";
import type { VideoRepository } from "./VideoRepository";
import { NotFoundError } from "../utils/errors";

interface Row {
  id: string;
  title: string;
  description: string | null;
  hashtags: string;
  category: string;
  language: string | null;
  duration: number;
  width: number;
  height: number;
  uploader: string;
  upload_date: number;
  views: number;
  likes: number;
  comments: number;
  shares: number;
  storage_provider: string;
  storage_key: string;
  thumbnail_key: string | null;
  storage_unique_id: string | null;
  qualities: string;
  hls_playlists: string | null;
  quality_meta: string | null;
}

function rowToMetadata(row: Row): VideoMetadata {
  return {
    id: row.id,
    title: row.title,
    description: row.description ?? undefined,
    hashtags: JSON.parse(row.hashtags || "[]"),
    category: row.category,
    language: row.language ?? undefined,
    duration: row.duration,
    width: row.width,
    height: row.height,
    uploader: row.uploader,
    uploadDate: row.upload_date,
    views: row.views,
    likes: row.likes,
    comments: row.comments,
    shares: row.shares,
    storageProvider: row.storage_provider,
    storageKey: row.storage_key,
    thumbnailKey: row.thumbnail_key ?? undefined,
    qualities: row.qualities ? JSON.parse(row.qualities) : undefined,
    hlsPlaylists: row.hls_playlists ? JSON.parse(row.hls_playlists) : undefined,
    qualityMeta: row.quality_meta ? JSON.parse(row.quality_meta) : undefined
  };
}

const CREATE_TABLE_SQL =
  "CREATE TABLE IF NOT EXISTS videos (" +
  "id TEXT PRIMARY KEY, title TEXT NOT NULL, description TEXT, " +
  "hashtags TEXT NOT NULL DEFAULT '[]', category TEXT NOT NULL, language TEXT, " +
  "duration INTEGER NOT NULL DEFAULT 0, width INTEGER NOT NULL DEFAULT 0, " +
  "height INTEGER NOT NULL DEFAULT 0, uploader TEXT NOT NULL, " +
  "upload_date INTEGER NOT NULL, views INTEGER NOT NULL DEFAULT 0, " +
  "likes INTEGER NOT NULL DEFAULT 0, comments INTEGER NOT NULL DEFAULT 0, " +
  "shares INTEGER NOT NULL DEFAULT 0, storage_provider TEXT NOT NULL, " +
  "storage_key TEXT NOT NULL, thumbnail_key TEXT, " +
  "storage_unique_id TEXT UNIQUE, qualities TEXT NOT NULL DEFAULT '{}', " +
  "hls_playlists TEXT NOT NULL DEFAULT '{}', quality_meta TEXT NOT NULL DEFAULT '{}')";

const INDEX_STATEMENTS = [
  "CREATE INDEX IF NOT EXISTS idx_videos_upload_date ON videos(upload_date DESC)",
  "CREATE INDEX IF NOT EXISTS idx_videos_category ON videos(category)",
  "CREATE INDEX IF NOT EXISTS idx_videos_uploader ON videos(uploader)",
  "CREATE INDEX IF NOT EXISTS idx_videos_storage_unique_id ON videos(storage_unique_id)"
];

// Adds columns to tables created before this migration, without wiping data.
const MIGRATION_STATEMENTS = [
  "ALTER TABLE videos ADD COLUMN hls_playlists TEXT NOT NULL DEFAULT '{}'",
  "ALTER TABLE videos ADD COLUMN quality_meta TEXT NOT NULL DEFAULT '{}'"
];

let schemaInitialized = false;

async function ensureSchema(db: D1Database): Promise<void> {
  if (schemaInitialized) return;
  await db.prepare(CREATE_TABLE_SQL).run();
  for (const sql of INDEX_STATEMENTS) {
    try { await db.prepare(sql).run(); } catch {}
  }
  for (const sql of MIGRATION_STATEMENTS) {
    try { await db.prepare(sql).run(); } catch {} // no-op if column already exists
  }
  schemaInitialized = true;
}

export class D1VideoRepository implements VideoRepository {
  constructor(private readonly db: D1Database) {}

  async create(data: CreateVideoMetadata): Promise<VideoMetadata> {
    await ensureSchema(this.db);
    const id = crypto.randomUUID();
    const uploadDate = data.uploadDate ?? Date.now();
    const hashtags = JSON.stringify(data.hashtags || []);

    await this.db
      .prepare(
        `INSERT INTO videos (id, title, description, hashtags, category, language, duration, width, height, uploader, upload_date, storage_provider, storage_key, thumbnail_key, qualities, hls_playlists, quality_meta)
         VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)`
      )
      .bind(
        id,
        data.title,
        data.description ?? null,
        hashtags,
        data.category,
        data.language ?? null,
        data.duration,
        data.width,
        data.height,
        data.uploader,
        uploadDate,
        data.storageProvider,
        data.storageKey,
        data.thumbnailKey ?? null,
        JSON.stringify(data.qualities ?? {}),
        JSON.stringify(data.hlsPlaylists ?? {}),
        JSON.stringify(data.qualityMeta ?? {})
      )
      .run();

    return {
      id,
      title: data.title,
      description: data.description,
      hashtags: data.hashtags || [],
      category: data.category,
      language: data.language,
      duration: data.duration,
      width: data.width,
      height: data.height,
      uploader: data.uploader,
      uploadDate,
      views: 0,
      likes: 0,
      comments: 0,
      shares: 0,
      storageProvider: data.storageProvider,
      storageKey: data.storageKey,
      thumbnailKey: data.thumbnailKey,
      qualities: data.qualities,
      hlsPlaylists: data.hlsPlaylists,
      qualityMeta: data.qualityMeta
    };
  }

  async getById(id: string): Promise<VideoMetadata | null> {
    await ensureSchema(this.db);
    const row = await this.db.prepare("SELECT * FROM videos WHERE id = ?").bind(id).first<Row>();
    if (!row) return null;
    return rowToMetadata(row);
  }

  async list(params: { page: number; perPage: number; category?: string; uploader?: string }): Promise<{ items: VideoMetadata[]; total: number }> {
    await ensureSchema(this.db);
    const conditions: string[] = [];
    const bindings: unknown[] = [];

    if (params.category) {
      conditions.push("LOWER(category) = LOWER(?)");
      bindings.push(params.category);
    }
    if (params.uploader) {
      conditions.push("uploader = ?");
      bindings.push(params.uploader);
    }

    const where = conditions.length > 0 ? `WHERE ${conditions.join(" AND ")}` : "";
    const offset = (params.page - 1) * params.perPage;

    const countRow = await this.db.prepare(`SELECT COUNT(*) as cnt FROM videos ${where}`).bind(...bindings).first<{ cnt: number }>();
    const total = countRow?.cnt ?? 0;

    const rows = await this.db
      .prepare(`SELECT * FROM videos ${where} ORDER BY upload_date DESC LIMIT ? OFFSET ?`)
      .bind(...bindings, params.perPage, offset)
      .all<Row>();

    return { items: (rows.results || []).map(rowToMetadata), total };
  }

  async patch(id: string, patch: Partial<VideoMetadata>): Promise<void> {
    await ensureSchema(this.db);
    const existing = await this.getById(id);
    if (!existing) throw new NotFoundError(`Video "${id}" not found`);

    const sets: string[] = [];
    const bindings: unknown[] = [];

    if (patch.title !== undefined) { sets.push("title = ?"); bindings.push(patch.title); }
    if (patch.description !== undefined) { sets.push("description = ?"); bindings.push(patch.description ?? null); }
    if (patch.hashtags !== undefined) { sets.push("hashtags = ?"); bindings.push(JSON.stringify(patch.hashtags)); }
    if (patch.category !== undefined) { sets.push("category = ?"); bindings.push(patch.category); }
    if (patch.language !== undefined) { sets.push("language = ?"); bindings.push(patch.language ?? null); }
    if (patch.duration !== undefined) { sets.push("duration = ?"); bindings.push(patch.duration); }
    if (patch.width !== undefined) { sets.push("width = ?"); bindings.push(patch.width); }
    if (patch.height !== undefined) { sets.push("height = ?"); bindings.push(patch.height); }
    if (patch.storageKey !== undefined) { sets.push("storage_key = ?"); bindings.push(patch.storageKey); }
    if (patch.thumbnailKey !== undefined) { sets.push("thumbnail_key = ?"); bindings.push(patch.thumbnailKey ?? null); }
    if (patch.qualities !== undefined) { sets.push("qualities = ?"); bindings.push(JSON.stringify(patch.qualities)); }
    if (patch.hlsPlaylists !== undefined) { sets.push("hls_playlists = ?"); bindings.push(JSON.stringify(patch.hlsPlaylists)); }
    if (patch.qualityMeta !== undefined) { sets.push("quality_meta = ?"); bindings.push(JSON.stringify(patch.qualityMeta)); }

    if (sets.length === 0) return;
    bindings.push(id);
    await this.db.prepare(`UPDATE videos SET ${sets.join(", ")} WHERE id = ?`).bind(...bindings).run();
  }

  async delete(id: string): Promise<void> {
    await ensureSchema(this.db);
    await this.db.prepare("DELETE FROM videos WHERE id = ?").bind(id).run();
  }

  async incrementCounter(id: string, field: "views" | "likes" | "comments" | "shares", by = 1): Promise<void> {
    await ensureSchema(this.db);
    const colMap: Record<string, string> = { views: "views", likes: "likes", comments: "comments", shares: "shares" };
    const col = colMap[field];
    if (!col) return;
    await this.db.prepare(`UPDATE videos SET ${col} = ${col} + ? WHERE id = ?`).bind(by, id).run();
  }

  async findByStorageUniqueId(storageUniqueId: string): Promise<VideoMetadata | null> {
    await ensureSchema(this.db);
    const row = await this.db.prepare("SELECT * FROM videos WHERE storage_unique_id = ?").bind(storageUniqueId).first<Row>();
    if (!row) return null;
    return rowToMetadata(row);
  }

  async indexStorageUniqueId(storageUniqueId: string, id: string): Promise<void> {
    await ensureSchema(this.db);
    await this.db.prepare("UPDATE videos SET storage_unique_id = ? WHERE id = ?").bind(storageUniqueId, id).run();
  }
}
