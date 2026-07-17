export type StorageProviderName = "telegram" | "r2" | "supabase" | "s3" | "local";

export interface Env {
  PORT: number;
  STORAGE_PROVIDER: StorageProviderName;

  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_CHAT_ID?: string;
  PUBLIC_BASE_URL?: string;

  R2_ACCOUNT_ID?: string;
  R2_ACCESS_KEY_ID?: string;
  R2_SECRET_ACCESS_KEY?: string;
  R2_BUCKET?: string;
  R2_PUBLIC_BASE_URL?: string;

  SUPABASE_URL?: string;
  SUPABASE_SERVICE_KEY?: string;
  SUPABASE_BUCKET?: string;

  S3_ENDPOINT?: string;
  S3_REGION?: string;
  S3_ACCESS_KEY_ID?: string;
  S3_SECRET_ACCESS_KEY?: string;
  S3_BUCKET?: string;

  LOCAL_STORAGE_ROOT?: string;

  TRANSCODER_URL?: string;
  TRANSCODER_SECRET?: string;
  OWNER_TOKEN_SECRET?: string;

  DB?: import("@cloudflare/workers-types").D1Database;
}

export function readEnv(bindings: Record<string, string | undefined>): Env {
  const port = Number(bindings.PORT) || 3000;
  return {
    PORT: port,
    STORAGE_PROVIDER: (bindings.STORAGE_PROVIDER as StorageProviderName) || "telegram",

    TELEGRAM_BOT_TOKEN: bindings.TELEGRAM_BOT_TOKEN,
    TELEGRAM_CHAT_ID: bindings.TELEGRAM_CHAT_ID,
    PUBLIC_BASE_URL: bindings.PUBLIC_BASE_URL || `http://localhost:${port}`,

    R2_ACCOUNT_ID: bindings.R2_ACCOUNT_ID,
    R2_ACCESS_KEY_ID: bindings.R2_ACCESS_KEY_ID,
    R2_SECRET_ACCESS_KEY: bindings.R2_SECRET_ACCESS_KEY,
    R2_BUCKET: bindings.R2_BUCKET,
    R2_PUBLIC_BASE_URL: bindings.R2_PUBLIC_BASE_URL,

    SUPABASE_URL: bindings.SUPABASE_URL,
    SUPABASE_SERVICE_KEY: bindings.SUPABASE_SERVICE_KEY,
    SUPABASE_BUCKET: bindings.SUPABASE_BUCKET,

    S3_ENDPOINT: bindings.S3_ENDPOINT,
    S3_REGION: bindings.S3_REGION,
    S3_ACCESS_KEY_ID: bindings.S3_ACCESS_KEY_ID,
    S3_SECRET_ACCESS_KEY: bindings.S3_SECRET_ACCESS_KEY,
    S3_BUCKET: bindings.S3_BUCKET,

    LOCAL_STORAGE_ROOT: bindings.LOCAL_STORAGE_ROOT,

    TRANSCODER_URL: bindings.TRANSCODER_URL,
    TRANSCODER_SECRET: bindings.TRANSCODER_SECRET,
    OWNER_TOKEN_SECRET: bindings.OWNER_TOKEN_SECRET
  };
}
