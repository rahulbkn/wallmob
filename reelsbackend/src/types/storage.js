/**
 * StorageProvider is the ONLY contract the rest of the app depends on for
 * reading/writing video files. No service, controller, or route may import
 * Telegram/R2/Supabase/S3 APIs directly — everything goes through this
 * interface, built by StorageFactory and wired in via the DI container.
 */
export {};
