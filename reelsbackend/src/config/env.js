export function readEnv(bindings) {
    const port = Number(bindings.PORT) || 3000;
    return {
        PORT: port,
        STORAGE_PROVIDER: bindings.STORAGE_PROVIDER || "telegram",
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
        LOCAL_STORAGE_ROOT: bindings.LOCAL_STORAGE_ROOT
    };
}
