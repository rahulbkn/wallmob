import crypto from "crypto";
import type { Comment, CreateCommentInput } from "../types/comment";

/**
 * Comment persistence, deliberately separate from VideoRepository (same
 * "separate metadata from storage" rule the video metadata follows). Swap
 * for a real database by implementing this interface and changing one line
 * in di/container.ts.
 */
export interface CommentRepository {
  create(data: CreateCommentInput): Promise<Comment>;
  listByVideo(videoId: string, params: { page: number; perPage: number }): Promise<{ items: Comment[]; total: number }>;
  deleteAllForVideo(videoId: string): Promise<void>;
}

/**
 * In-memory implementation — good enough for local dev / demos. Comments
 * are stored newest-first per video so listing needs no extra sort step.
 */
export class InMemoryCommentRepository implements CommentRepository {
  private readonly store = new Map<string, Comment[]>(); // videoId -> comments (newest first)

  async create(data: CreateCommentInput): Promise<Comment> {
    const comment: Comment = {
      id: crypto.randomUUID(),
      videoId: data.videoId,
      author: data.author,
      text: data.text,
      createdAt: data.createdAt ?? Date.now()
    };
    const list = this.store.get(data.videoId) || [];
    list.unshift(comment);
    this.store.set(data.videoId, list);
    return comment;
  }

  async listByVideo(videoId: string, params: { page: number; perPage: number }): Promise<{ items: Comment[]; total: number }> {
    const all = this.store.get(videoId) || [];
    const total = all.length;
    const start = (params.page - 1) * params.perPage;
    const items = all.slice(start, start + params.perPage);
    return { items, total };
  }

  async deleteAllForVideo(videoId: string): Promise<void> {
    this.store.delete(videoId);
  }
}
