# Reels Backend — Pluggable Storage Architecture

A Shorts/Reels backend where the media storage layer is swappable behind a
single environment variable. Today `STORAGE_PROVIDER=telegram` stores videos
in a private Telegram channel via a bot. Migrating to R2, Supabase, S3, or
local disk later needs **zero** changes to routes, controllers, or services —
only `STORAGE_PROVIDER` changes, plus implementing the relevant provider
class (skeletons already in place).

## How it fits together

```
src/
  types/storage.ts              <- StorageProvider interface (the contract)
  types/video.ts                <- VideoMetadata (DB shape) / ClientVideoView (API shape)

  providers/storage/
    StorageFactory.ts           <- THE switch point: reads STORAGE_PROVIDER
    config.ts                   <- validates required env vars per provider
    telegram/
      TelegramStorageProvider.ts  <- implemented
      telegramClient.ts           <- only file that touches the bot token
    r2/R2StorageProvider.ts       <- skeleton, throws until implemented
    supabase/SupabaseStorageProvider.ts <- skeleton
    s3/S3StorageProvider.ts       <- skeleton
    local/LocalStorageProvider.ts <- fully implemented (disk-backed, for dev)

  repositories/VideoRepository.ts <- metadata persistence, separate from storage
  services/                       <- business logic, depends only on StorageProvider + VideoRepository
  controllers/                    <- HTTP glue, depends only on services
  routes/router.ts                <- Express route wiring
  di/container.ts                 <- builds everything once at startup
```

## The rule this codebase enforces

**Nothing outside `/providers/storage/telegram` imports Telegram APIs.**
Every controller, service, and repository talks only to the
`StorageProvider` interface (`uploadVideo`, `uploadThumbnail`,
`getVideoStream`, `getDownloadUrl`, `deleteFile`, `fileExists`). The bot
token never leaves `telegramClient.ts` and is never sent to a client —
clients only ever see `<server>/stream?key=...` URLs.

## Migrating to a new provider

1. Implement the methods in, e.g., `providers/storage/r2/R2StorageProvider.ts`.
2. Fill in the matching env vars (see `.env.example`).
3. Set `STORAGE_PROVIDER=r2`.
4. Deploy. No route, controller, or service changes.

`VideoMetadata.storageProvider` is stored per-video, so old Telegram-backed
videos and new R2-backed videos can coexist during a migration — the feed
resolves each video's URL through whichever provider it was actually
uploaded with... **with one caveat**: this scaffold's `StorageFactory`
builds a single active provider per process from `STORAGE_PROVIDER`. To
serve mixed-provider libraries simultaneously, extend `StorageFactory` to
return a small registry (`Map<providerName, StorageProvider>`) and have
services look up by `record.storageProvider` instead of using one global
instance — the interface doesn't change, only how many instances of it you
keep around.

## Metadata schema (DB, provider-agnostic)

```ts
{
  id, title, description, hashtags, category, language,
  duration, width, height, uploader, uploadDate,
  views, likes, comments, shares,
  storageProvider, storageKey, thumbnailKey
}
```

`storageKey`/`thumbnailKey` are opaque — never a raw URL. Only the active
`StorageProvider` knows how to turn them into bytes or a download URL.

## Metadata storage in this scaffold

`InMemoryVideoRepository` is the default — good for local dev, resets on
restart. Swap in a real database by implementing `VideoRepository`
(`repositories/VideoRepository.ts`) against Mongo/Postgres/whatever, and
changing one line in `di/container.ts`. This is a second, independent axis
of pluggability from storage — you can change the DB without touching
`StorageProvider`, and vice versa.

## Running locally

```bash
cp .env.example .env
# fill in TELEGRAM_BOT_TOKEN + TELEGRAM_CHAT_ID (or switch to STORAGE_PROVIDER=local
# for a zero-config local run against disk)
npm install
npm run dev
```

## API

| Method | Path                  | Notes                                   |
|--------|-----------------------|------------------------------------------|
| POST   | `/api/videos`         | multipart: `video` (required), `thumbnail`, `title`, `category`, `uploader`, `description`, `hashtags`, `language` |
| GET    | `/api/videos`         | `?page=&perPage=&category=&uploader=`   |
| GET    | `/api/videos/:id`     | single video, provider-independent view |
| POST   | `/api/videos/:id/view`| increment view count                    |
| POST   | `/api/videos/:id/like`| increment like count                    |
| POST   | `/api/videos/:id/share`| increment share count                  |
| DELETE | `/api/videos/:id`     | body: `{ "uploader": "..." }` for ownership check |
| GET    | `/stream?key=&variant=`| streams raw bytes, supports HTTP Range for scrubbing |
| GET    | `/health`              | `{ provider: "telegram" }`             |
