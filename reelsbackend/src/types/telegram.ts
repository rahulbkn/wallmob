export interface TelegramVideo {
  file_id: string;
  file_unique_id: string;
  width?: number;
  height?: number;
  duration?: number;
  mime_type?: string;
  file_size?: number;
  thumbnail?: { file_id: string; file_unique_id: string; width: number; height: number };
}

export interface TelegramChat {
  id: number;
  title?: string;
}

export interface TelegramMessageResult {
  message_id: number;
  chat: TelegramChat;
  video?: TelegramVideo;
  photo?: Array<{ file_id: string; file_unique_id: string; width: number; height: number }>;
}

export interface TelegramResponse<T = unknown> {
  ok: boolean;
  result?: T;
  description?: string;
}

export interface TelegramFileInfo {
  file_id: string;
  file_path?: string;
  file_size?: number;
}
