import crypto from "crypto";
import type { D1Database } from "@cloudflare/workers-types";
import type { Comment, CreateCommentInput } from "../types/comment";
import type { CommentRepository } from "./CommentRepository";

interface Row {
  id: string;
  video_id: string;
  author: string;
  text: string;
  created_at: number;
}

function rowToComment(row: Row): Comment {
  return {
    id: row.id,
    videoId: row.video_id,
    author: row.author,
    text: row.text,
    createdAt: row.created_at
  };
}

const CREATE_TABLE_SQL =
  "CREATE TABLE IF NOT EXISTS comments (" +
  "id TEXT PRIMARY KEY, video_id TEXT NOT NULL, author TEXT NOT NULL, " +
  "text TEXT NOT NULL, created_at INTEGER NOT NULL)";

const INDEX_SQL = "CREATE INDEX IF NOT EXISTS idx_comments_video_id ON comments(video_id, created_at DESC)";

let schemaInitialized = false;

async function ensureSchema(db: D1Database): Promise<void> {
  if (schemaInitialized) return;
  await db.prepare(CREATE_TABLE_SQL).run();
  try {
    await db.prepare(INDEX_SQL).run();
  } catch {}
  schemaInitialized = true;
}

export class D1CommentRepository implements CommentRepository {
  constructor(private readonly db: D1Database) {}

  async create(data: CreateCommentInput): Promise<Comment> {
    await ensureSchema(this.db);
    const id = crypto.randomUUID();
    const createdAt = data.createdAt ?? Date.now();

    await this.db
      .prepare("INSERT INTO comments (id, video_id, author, text, created_at) VALUES (?, ?, ?, ?, ?)")
      .bind(id, data.videoId, data.author, data.text, createdAt)
      .run();

    return { id, videoId: data.videoId, author: data.author, text: data.text, createdAt };
  }

  async listByVideo(videoId: string, params: { page: number; perPage: number }): Promise<{ items: Comment[]; total: number }> {
    await ensureSchema(this.db);
    const offset = (params.page - 1) * params.perPage;

    const countRow = await this.db
      .prepare("SELECT COUNT(*) as cnt FROM comments WHERE video_id = ?")
      .bind(videoId)
      .first<{ cnt: number }>();
    const total = countRow?.cnt ?? 0;

    const rows = await this.db
      .prepare("SELECT * FROM comments WHERE video_id = ? ORDER BY created_at DESC LIMIT ? OFFSET ?")
      .bind(videoId, params.perPage, offset)
      .all<Row>();

    return { items: (rows.results || []).map(rowToComment), total };
  }

  async deleteAllForVideo(videoId: string): Promise<void> {
    await ensureSchema(this.db);
    await this.db.prepare("DELETE FROM comments WHERE video_id = ?").bind(videoId).run();
  }
}
