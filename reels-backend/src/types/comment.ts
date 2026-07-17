export interface Comment {
  id: string;
  videoId: string;
  author: string;
  text: string;
  createdAt: number;
}

export type CreateCommentInput = Omit<Comment, "id" | "createdAt"> & { createdAt?: number };
