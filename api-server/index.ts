interface Env {
  AI?: { run: (model: string, input: Record<string, unknown>) => Promise<any> };
  PROXY_WORKER?: Fetcher;
  WALLMOB_CACHE?: KVNamespace;
  TELEGRAM_BOT_TOKEN?: string;
  TELEGRAM_CHAT_ID?: string;
  ADMIN_CHAT_ID?: string;
  CLOUDINARY_CLOUD_NAME?: string;
  CLOUDINARY_UPLOAD_PRESET?: string;
  CLOUDINARY_API_KEY?: string;
  CLOUDINARY_API_SECRET?: string;
  FIREBASE_DATABASE_URL?: string;
  FIREBASE_DATABASE_SECRET?: string;
  FIREBASE_API_KEY?: string;
  MODERATION_API_URL?: string;
  WORKER_HOST?: string;
  UNSPLASH_KEY?: string;
  PEXELS_KEY?: string;
  PIXABAY_KEY?: string;
  WALLHAVEN_KEY?: string;
}

interface TelegramPhoto {
  file_id: string;
  file_unique_id: string;
  width?: number;
  height?: number;
}

interface TelegramDocument {
  file_id: string;
  file_unique_id: string;
  mime_type: string;
  file_size: number;
}

interface TelegramChat {
  id: number;
  title?: string;
}

interface TelegramUser {
  username?: string;
  first_name?: string;
}

interface TelegramMessage {
  message_id: number;
  chat: TelegramChat;
  from?: TelegramUser;
  text?: string;
  reply_to_message?: TelegramMessage;
  photo?: TelegramPhoto[];
  document?: TelegramDocument;
}

interface TelegramUpdate {
  message?: TelegramMessage;
}

interface TelegramResponse<T = unknown> {
  ok: boolean;
  result?: T;
  description?: string;
}

interface TelegramFileInfo {
  file_id: string;
  file_path?: string;
  file_size?: number;
}

interface TelegramMessageResult {
  message_id: number;
  chat: TelegramChat;
  photo?: TelegramPhoto[];
}

interface QueryParams {
  page: number;
  perPage: number;
  source: string;
  keyword: string;
  orientation: string;
  sort: string;
}

interface WallpaperUrls {
  raw: string;
  regular: string;
  small: string;
}

interface WallpaperMeta {
  width: number;
  height: number;
  aspect_ratio: number | null;
  orientation: string;
  is_mobile_4k: boolean;
  quality: string;
  dominant_color: string;
}

interface WallpaperInfo {
  title: string;
  author: string;
  author_link: string;
}

interface WallpaperStats {
  downloads: number | null;
  likes: number | null;
  favorites: number | null;
  views: number | null;
}

interface WallpaperObject {
  id: string;
  source: string;
  urls: WallpaperUrls;
  meta: WallpaperMeta;
  info: WallpaperInfo;
  stats: WallpaperStats;
}

interface WallpaperData {
  items: WallpaperObject[];
  total: number;
}

interface WallpaperResult {
  wallpapers: WallpaperObject[];
  errors: Array<{ source: string; message: string }>;
  debug: Record<string, unknown>;
  totalAvailable: number;
}

interface ApiConfig {
  name: string;
  enabled: boolean;
  url: string;
  headers: Record<string, string>;
}

interface NormalizedItem {
  id: string;
  source: string;
  url: string;
  thumb: string;
  full: string;
  width: number;
  height: number;
  color: string | null;
  title: string;
  author: string;
  author_url: string;
  likes?: number;
  downloads?: number;
  favorites?: number;
  views?: number;
}

interface KeyValidation {
  valid: boolean;
  message?: string;
  requiredKeys?: string[];
}

interface ModerationResult {
  safe: boolean;
  unreachable?: boolean;
  nsfwScore?: number;
  detections?: Array<{ label: string; confidence: number }>;
}

interface CategorizeResult {
  category: string;
  quotaExceeded: boolean;
}

interface PendingItem {
  firebaseKey: string;
  fileUniqueId: string;
  imageUrl: string;
  chatId: number;
  messageId: number;
  waitMsgId?: number;
  skipAI?: boolean;
  categoryOnly?: boolean;
  moderationOnly?: boolean;
  queuedAt: number;
  retryAfter?: number;
  uploaderId?: string;
}

interface RetryResult {
  done: boolean;
  quotaExceeded: boolean;
}

interface PatchData {
  id: string;
  thumbnailUrl?: string;
  categorized?: boolean;
  category?: string;
}

interface TelegramFileInfoResponse {
  ok: boolean;
  result?: TelegramFileInfo;
}

interface TelegramSendResponse {
  ok: boolean;
  result?: TelegramMessageResult;
}

interface CloudinaryUploadResult {
  secure_url?: string;
}

interface CloudinaryDeleteResult {
  result?: string;
}

interface ScoredWallpaper {
  wallpaper: WallpaperObject;
  score: number;
}

const CORS_HEADERS: Record<string, string> = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type"
};

const CONFIG = {
  DEFAULT_PAGE: 1,
  DEFAULT_PER_PAGE: 100,
  MAX_PER_PAGE: 100,
  MIN_PER_PAGE: 1,
  MIN_WIDTH: 1080,
  MIN_HEIGHT: 1920,
  REQUEST_TIMEOUT: 8e3,
  CACHE_MAX_AGE: 3600,
  STALE_AGE: 86400
} as const;

const inFlightRequests = new Map<string, { promise: Promise<WallpaperResult>; ts: number }>();
const INFLIGHT_TTL = 60000;

export default {
  async fetch(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
    return handleRequest(request, env, ctx);
  },
  async scheduled(_event: ScheduledEvent, env: Env, ctx: ExecutionContext): Promise<void> {
    ctx.waitUntil(processPendingWallpapers(env));
    if (new Date().getUTCMinutes() % 10 === 0) {
      ctx.waitUntil(pingModerationApi(env));
    }
  }
};

async function handleRequest(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  if (request.method === "OPTIONS") {
    return new Response(null, { headers: CORS_HEADERS });
  }
  try {
    const url = new URL(request.url);

    if (url.pathname === "/upload") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST to upload images", 405);
      }
      return handleUpload(request, env);
    }

    if (url.pathname === "/profile") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST to upload profile image", 405);
      }
      return handleProfileUpload(request, env, ctx);
    }

    if (url.pathname === "/upload-wallpaper") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST to upload wallpaper", 405);
      }
      return handleWallpaperUpload(request, env, ctx);
    }

    if (url.pathname === "/delete-wallpaper") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST", 405);
      }
      return handleDeleteWallpaper(request, env, ctx);
    }

    if (url.pathname === "/accept-llama-license") {
      if (!env.AI) return createErrorResponse("No AI binding", "env.AI not configured", 500);
      try {
        const result = await env.AI.run("@cf/meta/llama-3.2-11b-vision-instruct", { prompt: "agree" });
        return new Response(JSON.stringify({ success: true, result }, null, 2), {
          headers: { "Content-Type": "application/json", ...CORS_HEADERS }
        });
      } catch (e: unknown) {
        const msg = e instanceof Error ? e.message : String(e);
        return createErrorResponse("License accept failed", msg, 500);
      }
    }

    if (url.pathname === "/telegram-webhook") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST", 405);
      }
      return handleTelegramWebhook(request, env, ctx);
    }

    if (url.pathname !== "/" && url.pathname !== "") {
      return createErrorResponse("Not Found", "Endpoint not found", 404);
    }

    const params = parseQueryParams(url);
    if (!params.keyword) {
      return createErrorResponse("Keyword Required", "Please provide a search keyword using ?query=your_keyword", 400, { example: `${url.origin}/?query=nature&page=1` });
    }

    const keyValidation = validateApiKeys(env, params.source);
    if (!keyValidation.valid) {
      return createErrorResponse("API Configuration Error", keyValidation.message!, 503, { required_keys: keyValidation.requiredKeys });
    }

    const cacheKey = `wallmob:${params.keyword}:${params.page}:${params.perPage}:${params.source}:${params.sort}:${params.orientation}`;

    if (env.WALLMOB_CACHE) {
      const cached = await env.WALLMOB_CACHE.get(cacheKey, "json") as { payload: { meta: Record<string, unknown>; data: WallpaperObject[]; debug?: Record<string, unknown>; partial_errors?: string[] }; cached_at: number } | null;
      if (cached) {
        const staleAge = (Date.now() - cached.cached_at) / 1e3;
        if (staleAge < CONFIG.CACHE_MAX_AGE) {
          return buildResponse(cached.payload, params);
        }
        if (staleAge < CONFIG.STALE_AGE) {
          ctx.waitUntil(refreshCache(cacheKey, params, env));
          return buildResponse(cached.payload, params);
        }
      }
    }

    const result = await fetchWithDedup(cacheKey, params, env);
    const responsePayload = {
      meta: {
        success: true,
        page: params.page,
        per_page: params.perPage,
        count: result.wallpapers.length,
        total_available: result.totalAvailable,
        source_filter: params.source,
        search_keyword: params.keyword,
        sort: params.sort,
        timestamp: new Date().toISOString()
      },
      data: result.wallpapers,
      debug: result.debug,
      partial_errors: result.errors.length > 0 ? result.errors : undefined
    };

    if (env.WALLMOB_CACHE) {
      ctx.waitUntil(env.WALLMOB_CACHE.put(cacheKey, JSON.stringify({ payload: responsePayload, cached_at: Date.now() }), { expirationTtl: CONFIG.STALE_AGE }));
    }

    return buildResponse(responsePayload, params);
  } catch (error: unknown) {
    console.error("Request handling error:", error);
    const msg = error instanceof Error ? error.message : String(error);
    return createErrorResponse("Internal Server Error", msg, 500, { stack: error instanceof Error ? error.stack : undefined });
  }
}

function buildResponse(data: Record<string, unknown>, _params: QueryParams): Response {
  return new Response(JSON.stringify(data, null, 2), {
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": `public, max-age=${CONFIG.CACHE_MAX_AGE}, stale-while-revalidate=${CONFIG.STALE_AGE}`,
      ...CORS_HEADERS
    }
  });
}

async function refreshCache(cacheKey: string, params: QueryParams, env: Env): Promise<void> {
  try {
    const result = await fetchAllWallpapers(params.page, params.perPage, params.source, params.keyword, params.orientation, params.sort, env);
    const payload = {
      meta: {
        success: true,
        page: params.page,
        per_page: params.perPage,
        count: result.wallpapers.length,
        total_available: result.totalAvailable,
        source_filter: params.source,
        search_keyword: params.keyword,
        sort: params.sort,
        timestamp: new Date().toISOString()
      },
      data: result.wallpapers,
      debug: result.debug,
      partial_errors: result.errors.length > 0 ? result.errors : undefined
    };
    await env.WALLMOB_CACHE!.put(cacheKey, JSON.stringify({ payload, cached_at: Date.now() }), { expirationTtl: CONFIG.STALE_AGE });
  } catch (e: unknown) {
    console.error("Background refresh failed:", e);
  }
}

async function fetchWithDedup(cacheKey: string, params: QueryParams, env: Env): Promise<WallpaperResult> {
  const now = Date.now();
  for (const [key, entry] of inFlightRequests) {
    if (now - entry.ts > INFLIGHT_TTL) inFlightRequests.delete(key);
  }

  if (inFlightRequests.has(cacheKey)) {
    return inFlightRequests.get(cacheKey)!.promise;
  }
  const promise = fetchAllWallpapers(params.page, params.perPage, params.source, params.keyword, params.orientation, params.sort, env).finally(() => {
    inFlightRequests.delete(cacheKey);
  });
  inFlightRequests.set(cacheKey, { promise, ts: now });
  return promise;
}

function parseQueryParams(url: URL): QueryParams {
  const page = Math.max(CONFIG.DEFAULT_PAGE, parseInt(url.searchParams.get("page") || "") || CONFIG.DEFAULT_PAGE);
  const perPage = Math.min(CONFIG.MAX_PER_PAGE, Math.max(CONFIG.MIN_PER_PAGE, parseInt(url.searchParams.get("per_page") || "") || CONFIG.DEFAULT_PER_PAGE));
  const source = url.searchParams.get("source") || "all";
  const keyword = (url.searchParams.get("query") || url.searchParams.get("keyword") || url.searchParams.get("q") || "").trim();
  const orientation = url.searchParams.get("orientation") || "all";
  const sort = (url.searchParams.get("sort") || "relevant").toLowerCase();
  return { page, perPage, source, keyword, orientation, sort };
}

function validateApiKeys(env: Env, sourceFilter: string): KeyValidation {
  const sources = ["Unsplash", "Pexels", "Pixabay", "Wallhaven"];
  const activeSources = sourceFilter === "all" ? sources : sources.filter((s) => s.toLowerCase() === sourceFilter.toLowerCase());
  if (activeSources.length === 0) {
    return { valid: false, message: `Invalid source: ${sourceFilter}. Valid options: ${sources.join(", ")}, all`, requiredKeys: [] };
  }
  const keyMap: Record<string, string | undefined> = { Unsplash: env.UNSPLASH_KEY, Pexels: env.PEXELS_KEY, Pixabay: env.PIXABAY_KEY, Wallhaven: env.WALLHAVEN_KEY };
  const missingKeys = activeSources.filter((source) => !keyMap[source]);
  if (missingKeys.length === activeSources.length) {
    return { valid: false, message: "No API keys configured for requested sources", requiredKeys: missingKeys.map((s) => `${s.toUpperCase()}_KEY`) };
  }
  return { valid: true };
}

function createErrorResponse(error: string, message: string, status: number, additionalData: Record<string, unknown> = {}): Response {
  return new Response(JSON.stringify({ success: false, error, message, ...additionalData }, null, 2), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS }
  });
}

async function fetchAllWallpapers(page: number, perPage: number, sourceFilter: string, keyword: string, orientation: string, sort: string, env: Env): Promise<WallpaperResult> {
  const sources = ["Unsplash", "Pexels", "Pixabay", "Wallhaven"];
  const activeSources = sourceFilter === "all" ? sources : sources.filter((s) => s.toLowerCase() === sourceFilter.toLowerCase());
  const itemsPerSource = Math.min(Math.ceil(perPage / activeSources.length) + 5, 100);
  const apiConfigs: ApiConfig[] = [
    {
      name: "Unsplash",
      enabled: activeSources.includes("Unsplash") && !!env.UNSPLASH_KEY,
      url: `https://api.unsplash.com/search/photos?query=${encodeURIComponent(keyword)}&page=${page}&per_page=${itemsPerSource}&order_by=${sort === 'popular' ? 'popular' : 'relevant'}`,
      headers: { Authorization: `Client-ID ${env.UNSPLASH_KEY || ""}` }
    },
    {
      name: "Pexels",
      enabled: activeSources.includes("Pexels") && !!env.PEXELS_KEY,
      url: `https://api.pexels.com/v1/search?query=${encodeURIComponent(keyword)}&page=${page}&per_page=${itemsPerSource}`,
      headers: { Authorization: env.PEXELS_KEY || "" }
    },
    {
      name: "Pixabay",
      enabled: activeSources.includes("Pixabay") && !!env.PIXABAY_KEY,
      url: `https://pixabay.com/api/?key=${env.PIXABAY_KEY || ""}&q=${encodeURIComponent(keyword)}&image_type=photo&min_width=${CONFIG.MIN_WIDTH}&min_height=${CONFIG.MIN_HEIGHT}&safesearch=true&page=${page}&per_page=${itemsPerSource}&order=${sort === 'popular' ? 'popular' : 'ec'}`,
      headers: {}
    },
    {
      name: "Wallhaven",
      enabled: activeSources.includes("Wallhaven") && !!env.WALLHAVEN_KEY,
      url: `https://wallhaven.cc/api/v1/search?q=${encodeURIComponent(keyword)}&categories=111&purity=100&atleast=1920x1080&page=${page}&sorting=${sort === 'popular' ? 'views' : 'relevance'}`,
      headers: env.WALLHAVEN_KEY ? { "X-API-Key": env.WALLHAVEN_KEY } : {}
    }
  ];
  const enabledApis = apiConfigs.filter((api) => api.enabled);
  const errors: Array<{ source: string; message: string }> = [];
  const debug: Record<string, unknown> = {
    enabled_sources: enabledApis.map((a) => a.name),
    disabled_sources: apiConfigs.filter((a) => !a.enabled).map((a) => a.name),
    has_keys: { unsplash: !!env.UNSPLASH_KEY, pexels: !!env.PEXELS_KEY, pixabay: !!env.PIXABAY_KEY, wallhaven: !!env.WALLHAVEN_KEY }
  };
  if (enabledApis.length === 0) {
    return { wallpapers: [], errors: [{ source: "config", message: "No API keys configured for requested sources" }], debug, totalAvailable: 0 };
  }
  const results = await Promise.allSettled(enabledApis.map((api) => fetchFromAPI(api, CONFIG.MIN_WIDTH, CONFIG.MIN_HEIGHT)));
  const seen = new Set<string>();
  const allWallpapers: WallpaperObject[] = [];
  let totalAvailable = 0;
  results.forEach((result, index) => {
    if (result.status === "fulfilled") {
      const sourceData = result.value;
      let sourceAdded = 0;
      for (const wp of sourceData.items) {
        const w = wp.meta?.width || 0;
        const h = wp.meta?.height || 0;
        if (orientation === "landscape" && h >= w) continue;
        if (orientation === "portrait" && w > h) continue;
        const urlKey = wp.urls.regular || wp.urls.raw;
        if (!seen.has(urlKey)) {
          seen.add(urlKey);
          allWallpapers.push(wp);
          sourceAdded++;
        }
      }
      totalAvailable += sourceData.total;
      debug[enabledApis[index].name] = { status: "success", count: sourceData.items.length, deduped: sourceData.items.length - sourceAdded, total: sourceData.total };
    } else {
      console.error(`Failed: ${enabledApis[index].name}`, result.reason);
      errors.push({ source: enabledApis[index].name, message: (result.reason as Error)?.message || "Request failed" });
      debug[enabledApis[index].name] = { status: "failed", error: (result.reason as Error)?.message };
    }
  });
  const scored: ScoredWallpaper[] = allWallpapers.map((wp) => {
    let score = 0;
    const w = wp.meta?.width || 0;
    const h = wp.meta?.height || 0;

    const likes = wp.stats?.likes || wp.stats?.favorites || 0;
    const downloads = wp.stats?.downloads || 0;
    const views = wp.stats?.views || 0;

    if (w >= 3840 && h >= 2160) score += 50;
    else if (w >= 2560 && h >= 1440) score += 40;
    else if (w >= 1920 && h >= 1080) score += 30;
    else if (w >= 1440 && h >= 1920) score += 20;
    if (wp.meta?.dominant_color && wp.meta.dominant_color !== "#E0E0E0") score += 5;

    if (sort === "popular") {
      score += (likes * 10);
      score += Math.floor(downloads / 500);
      score += Math.floor(views / 100);
    } else {
      if (likes > 100) score += 15;
      else if (likes > 10) score += 5;
      if (downloads > 10000) score += 15;
      else if (downloads > 1000) score += 5;
    }

    const aspect = w && h ? w / h : 0;
    const portraitIdeal = 9 / 16;
    const landscapeIdeal = 16 / 9;
    const ratioScore = aspect <= 1
      ? Math.max(0, 10 - Math.abs(aspect - portraitIdeal) * 50)
      : Math.max(0, 10 - Math.abs(aspect - landscapeIdeal) * 20);
    score += ratioScore;
    return { wallpaper: wp, score };
  });

  scored.sort((a, b) => b.score - a.score);
  const sorted = scored.map((s) => s.wallpaper);

  return {
    wallpapers: sorted.slice(0, perPage),
    errors,
    debug,
    totalAvailable
  };
}

async function fetchFromAPI(api: ApiConfig, _minWidth: number, _minHeight: number): Promise<WallpaperData> {
  try {
    const response = await fetch(api.url, { headers: api.headers, signal: AbortSignal.timeout(CONFIG.REQUEST_TIMEOUT) });
    const errorText = response.ok ? null : await response.text();
    if (errorText !== null) {
      console.error(`${api.name} returned ${response.status}: ${errorText}`);
      throw new Error(`HTTP ${response.status}: ${errorText.substring(0, 100)}`);
    }
    const data = await response.json() as Record<string, unknown>;
    return normalizeData(api.name, data, _minWidth, _minHeight);
  } catch (error) {
    console.error(`Error ${api.name}:`, (error as Error).message);
    throw error;
  }
}

function normalizeData(source: string, data: Record<string, unknown>, _minWidth: number, _minHeight: number): WallpaperData {
  const wallpapers: WallpaperObject[] = [];
  let total = 0;
  try {
    switch (source) {
      case "Unsplash": {
        total = (data.total as number) || 0;
        const results = data.results as Array<Record<string, unknown>> | undefined;
        (results || []).forEach((item: Record<string, unknown>) => {
          wallpapers.push(createWallpaperObject({
            id: item.id as string,
            source: "Unsplash",
            url: (item.urls as Record<string, string>).regular,
            thumb: (item.urls as Record<string, string>).small,
            full: (item.urls as Record<string, string>).full,
            width: item.width as number,
            height: item.height as number,
            color: item.color as string | null,
            title: (item.alt_description || item.description || "Untitled") as string,
            author: ((item.user as Record<string, unknown>)?.name as string) || "Unknown",
            author_url: ((item.user as Record<string, unknown>)?.links as Record<string, string>)?.html || "",
            likes: item.likes as number
          }));
        });
        break;
      }
      case "Pexels": {
        total = (data.total_results as number) || 0;
        const photos = data.photos as Array<Record<string, unknown>> | undefined;
        if (photos) {
          photos.forEach((item: Record<string, unknown>) => {
            wallpapers.push(createWallpaperObject({
              id: (item.id as number).toString(),
              source: "Pexels",
              url: ((item.src as Record<string, string>).large2x || (item.src as Record<string, string>).large),
              thumb: (item.src as Record<string, string>).medium,
              full: (item.src as Record<string, string>).original,
              width: item.width as number,
              height: item.height as number,
              color: item.avg_color as string | null,
              title: (item.alt as string) || "Untitled",
              author: item.photographer as string,
              author_url: item.photographer_url as string
            }));
          });
        }
        break;
      }
      case "Pixabay": {
        total = (data.totalHits as number) || 0;
        const hits = data.hits as Array<Record<string, unknown>> | undefined;
        if (hits) {
          hits.forEach((item: Record<string, unknown>) => {
            wallpapers.push(createWallpaperObject({
              id: (item.id as number).toString(),
              source: "Pixabay",
              url: item.largeImageURL as string,
              thumb: item.webformatURL as string,
              full: (item.imageURL || item.largeImageURL) as string,
              width: item.imageWidth as number,
              height: item.imageHeight as number,
              color: null,
              title: (item.tags as string) || "Untitled",
              author: item.user as string,
              author_url: `https://pixabay.com/users/${item.user}-${item.user_id}/`,
              likes: item.likes as number,
              downloads: item.downloads as number
            }));
          });
        }
        break;
      }
      case "Wallhaven": {
        total = ((data.meta as Record<string, unknown>)?.total as number) || 0;
        const wallhavenData = data.data as Array<Record<string, unknown>> | undefined;
        if (wallhavenData) {
          wallhavenData.forEach((item: Record<string, unknown>) => {
            const colors = item.colors as string[] | undefined;
            const color = colors && colors.length > 0 ? colors[0] : null;
            wallpapers.push(createWallpaperObject({
              id: item.id as string,
              source: "Wallhaven",
              url: item.path as string,
              thumb: ((item.thumbs as Record<string, string>)?.small || item.path) as string,
              full: item.path as string,
              width: item.dimension_x as number,
              height: item.dimension_y as number,
              color,
              title: item.category ? `${item.category} wallpaper` : "Wallpaper",
              author: "Wallhaven",
              author_url: item.url as string,
              favorites: item.favorites as number,
              views: item.views as number
            }));
          });
        }
        break;
      }
    }
  } catch (e: unknown) {
    console.error(`Normalization error for ${source}:`, e);
  }
  return { items: wallpapers, total };
}

function createWallpaperObject(item: NormalizedItem): WallpaperObject {
  const is4K = (item.width >= 3840 && item.height >= 2160) || (item.width >= 2560 && item.height >= 1440);
  const aspectRatio = item.width && item.height ? item.width / item.height : null;
  return {
    id: `${item.source.toLowerCase()}-${item.id}`,
    source: item.source,
    urls: { raw: item.full, regular: item.url, small: item.thumb },
    meta: {
      width: item.width, height: item.height,
      aspect_ratio: aspectRatio ? parseFloat(aspectRatio.toFixed(2)) : null,
      orientation: item.height >= item.width ? "portrait" : "landscape",
      is_mobile_4k: is4K,
      quality: is4K ? "4K" : "HD",
      dominant_color: item.color || "#E0E0E0"
    },
    info: { title: item.title || "Untitled", author: item.author || "Unknown", author_link: item.author_url || "" },
    stats: { downloads: item.downloads || null, likes: item.likes || null, favorites: item.favorites || null, views: item.views || null }
  };
}

async function handleUpload(request: Request, env: Env): Promise<Response> {
  try {
    const contentType = request.headers.get("Content-Type") || "";
    if (!contentType.includes("multipart/form-data")) {
      return createErrorResponse("Bad Request", "Content-Type must be multipart/form-data", 400);
    }

    const formData = await request.formData();
    const photo = formData.get("photo");

    if (!photo) {
      return createErrorResponse("Missing photo", "No photo file provided in 'photo' field", 400);
    }

    if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_CHAT_ID) {
      return createErrorResponse("Server config error", "Telegram bot not configured", 500);
    }

    const tgFormData = new FormData();
    tgFormData.append("chat_id", env.TELEGRAM_CHAT_ID);
    tgFormData.append("photo", photo);

    const tgResponse = await fetch(
      `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendPhoto`,
      { method: "POST", body: tgFormData }
    );

    if (!tgResponse.ok) {
      const err = await tgResponse.text();
      console.error("Telegram sendPhoto failed:", err);
      return createErrorResponse("Telegram upload failed", err.substring(0, 200), 502);
    }

    const tgResult = await tgResponse.json() as TelegramResponse<TelegramMessageResult>;

    if (!tgResult.ok) {
      return createErrorResponse("Telegram error", tgResult.description || "Unknown error", 502);
    }

    const photos = tgResult.result!.photo!;
    const largestPhoto = photos[photos.length - 1];
    const fileId = largestPhoto.file_id;

    const { url: cloudinaryUrl, error: cdError } = await uploadToCloudinary(photo as File, env);
    if (cdError) {
      console.error("Cloudinary upload failed:", cdError);
      return createErrorResponse("Cloudinary upload failed", cdError, 502);
    }

    return new Response(JSON.stringify({
      success: true,
      url: cloudinaryUrl,
      file_id: fileId
    }), {
      headers: { "Content-Type": "application/json", ...CORS_HEADERS }
    });
  } catch (error: unknown) {
    console.error("Upload handler error:", error);
    return createErrorResponse("Upload failed", (error as Error).message, 500);
  }
}

async function handleProfileUpload(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  try {
    const contentType = request.headers.get("Content-Type") || "";
    if (!contentType.includes("multipart/form-data")) {
      return createErrorResponse("Bad Request", "Content-Type must be multipart/form-data", 400);
    }

    const formData = await request.formData();
    const photo = formData.get("photo");
    const email = (formData.get("email") as string | null)?.trim() || "";
    const oldPhotoUrl = formData.get("oldPhotoUrl") as string | null;

    if (!photo) {
      return createErrorResponse("Missing photo", "No photo file provided in 'photo' field", 400);
    }

    if (!email) {
      return createErrorResponse("Unauthorized", "Email is required", 401);
    }

    if (env.FIREBASE_DATABASE_URL && env.FIREBASE_DATABASE_SECRET) {
      const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
      const userResp = await fetch(
        `${env.FIREBASE_DATABASE_URL}/users.json?${auth}&orderBy="email"&equalTo="${encodeURIComponent(email)}"`,
        { signal: AbortSignal.timeout(10000) }
      );
      if (userResp.ok) {
        const users = await userResp.json() as Record<string, unknown> | null;
        if (!users || Object.keys(users).length === 0) {
          return createErrorResponse("Unauthorized", "User not found", 401);
        }
      }
    }

    const { url: cloudinaryUrl, error: cdError } = await uploadToCloudinary(photo as File, env, "profiles");
    if (cdError) {
      console.error("Cloudinary upload failed:", cdError);
      return createErrorResponse("Cloudinary upload failed", cdError, 502);
    }

    if (oldPhotoUrl) {
      const oldPublicId = extractCloudinaryPublicId(oldPhotoUrl);
      if (oldPublicId) {
        ctx.waitUntil(deleteFromCloudinary(oldPublicId, env));
        console.log(`Queued deletion of old profile image: ${oldPublicId}`);
      }
    }

    return new Response(JSON.stringify({
      success: true,
      url: cloudinaryUrl
    }), {
      headers: { "Content-Type": "application/json", ...CORS_HEADERS }
    });
  } catch (error: unknown) {
    console.error("Profile upload handler error:", error);
    return createErrorResponse("Upload failed", (error as Error).message, 500);
  }
}

function extractCloudinaryPublicId(url: string): string | null {
  try {
    const u = new URL(url);
    const match = u.pathname.match(/\/upload\/(?:.*\/)?v?\d+\/(.+)/);
    if (!match) return null;
    let publicId = match[1];
    const dot = publicId.lastIndexOf(".");
    if (dot > 0) publicId = publicId.substring(0, dot);
    return publicId;
  } catch { return null; }
}

async function deleteFromCloudinary(publicId: string, env: Env): Promise<boolean> {
  if (!env.CLOUDINARY_API_KEY || !env.CLOUDINARY_API_SECRET) {
    console.error("Cloudinary API credentials not configured for deletion");
    return false;
  }
  try {
    const timestamp = Math.floor(Date.now() / 1000);
    const params = `public_id=${publicId}&timestamp=${timestamp}${env.CLOUDINARY_API_SECRET}`;
    const signatureBytes = await crypto.subtle.digest(
      "SHA-1", new TextEncoder().encode(params)
    );
    const signature = Array.from(new Uint8Array(signatureBytes))
      .map(b => b.toString(16).padStart(2, "0")).join("");

    const body = new FormData();
    body.append("public_id", publicId);
    body.append("api_key", env.CLOUDINARY_API_KEY);
    body.append("timestamp", timestamp.toString());
    body.append("signature", signature);

    const resp = await fetch(
      `https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/destroy`,
      { method: "POST", body }
    );
    const result = await resp.json() as CloudinaryDeleteResult;
    if (result.result === "ok") {
      console.log("Cloudinary image deleted:", publicId);
      return true;
    }
    console.error("Cloudinary delete failed:", result);
    return false;
  } catch (e: unknown) {
    console.error("Cloudinary delete error:", e);
    return false;
  }
}

const DEFAULT_MODERATION_API_URL = "https://tool-veyr.onrender.com";

async function pingModerationApi(env: Env): Promise<void> {
  const apiUrl = env.MODERATION_API_URL || DEFAULT_MODERATION_API_URL;
  try {
    const resp = await fetch(`${apiUrl}/health`, { signal: AbortSignal.timeout(20000) });
    console.log(`pingModerationApi: ${resp.status}`);
  } catch (e: unknown) {
    console.log(`pingModerationApi failed (Render likely cold-starting): ${(e as Error).message}`);
  }
}

async function moderateImage(imageUrl: string, env: Env): Promise<ModerationResult> {
  const apiUrl = env.MODERATION_API_URL || DEFAULT_MODERATION_API_URL;
  if (!apiUrl) return { safe: true };

  const attempts = [15000, 30000];
  let lastError: string | null = null;

  for (let i = 0; i < attempts.length; i++) {
    try {
      const resp = await fetch(`${apiUrl}/moderate`, {
        method: "POST",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ image_url: imageUrl }),
        signal: AbortSignal.timeout(attempts[i])
      });
      if (!resp.ok) {
        const errText = await resp.text().catch(() => "<unreadable>");
        lastError = `HTTP ${resp.status}: ${errText.substring(0, 200)}`;
        console.error(`moderateImage attempt ${i + 1}: ${lastError}`);
        continue;
      }
      const result = await resp.json() as { safe: boolean; nsfw_score?: number; detections?: Array<{ label: string; confidence: number }> };
      if (!result.safe) {
        console.log(`moderateImage: REJECTED (nsfw_score: ${result.nsfw_score}, detections: ${JSON.stringify(result.detections)})`);
      }
      return { safe: !!result.safe, nsfwScore: result.nsfw_score, detections: result.detections };
    } catch (e: unknown) {
      lastError = (e as Error).message;
      console.log(`moderateImage attempt ${i + 1} failed (likely Render cold start): ${(e as Error).message}`);
    }
  }

  console.error(`moderateImage: could not reach moderation API after ${attempts.length} attempts: ${lastError}`);
  return { safe: false, unreachable: true };
}

async function writeToFirebase(data: Record<string, unknown>, env: Env): Promise<{ name: string } | null> {
  if (!env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) {
    console.error("Firebase not configured");
    return null;
  }
  try {
    const ref = `${env.FIREBASE_DATABASE_URL}/wallpapers/newly_added.json?auth=${env.FIREBASE_DATABASE_SECRET}`;
    const resp = await fetch(ref, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        ...data,
        addedAt: (data.addedAt as number) || Date.now(),
      }),
    });
    if (!resp.ok) {
      const err = await resp.text();
      console.error("Firebase write failed:", err);
      return null;
    }
    return await resp.json() as { name: string };
  } catch (e: unknown) {
    console.error("Firebase write error:", e);
    return null;
  }
}

async function handleWallpaperUpload(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  try {
    const contentType = request.headers.get("Content-Type") || "";
    if (!contentType.includes("multipart/form-data")) {
      return createErrorResponse("Bad Request", "Content-Type must be multipart/form-data", 400);
    }

    const formData = await request.formData();
    const photo = formData.get("photo");
    const rawTitle = (formData.get("title") || "").toString().trim();
    const title = rawTitle.length >= 2 ? rawTitle : "Untitled";
    const rawCategoryInput = (formData.get("category") || "").toString().trim();
    const validCategory = AI_CATEGORY_GROUPS.find(
      (c) => c.toLowerCase() === rawCategoryInput.toLowerCase()
    );
    const rawCategory = validCategory || "";
    const photographer = (formData.get("photographer") || "").toString().trim();
    const uploaderId = (formData.get("uploader_id") || "").toString().trim();

    if (await isOverLimit(uploaderId, env)) {
      return createErrorResponse("Too many uploads in queue", "Too many uploads in queue. Please wait.", 400);
    }

    if (!photo || typeof photo === "string") {
      return createErrorResponse("Missing photo", "No photo file provided in 'photo' field", 400);
    }
    if (!IMAGE_MIME_TYPES.includes(photo.type)) {
      return createErrorResponse("Invalid file type", `Expected an image, got '${photo.type || "unknown"}'`, 400);
    }
    if (photo.size > MAX_FILE_SIZE) {
      return createErrorResponse("File too large", `Max allowed size is ${MAX_FILE_SIZE / (1024 * 1024)} MB`, 400);
    }
    if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_CHAT_ID) {
      return createErrorResponse("Server config error", "Telegram bot not configured", 500);
    }

    const needsAutoCategory = !rawCategory;
    const category = rawCategory || DEFAULT_CATEGORY;

    const tgFormData = new FormData();
    tgFormData.append("chat_id", env.TELEGRAM_CHAT_ID);
    tgFormData.append("photo", photo);

    let tgResponse: Response;
    try {
      tgResponse = await fetch(
        `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendPhoto`,
        { method: "POST", body: tgFormData, signal: AbortSignal.timeout(20000) }
      );
    } catch (e: unknown) {
      return createErrorResponse("Telegram upload failed", (e as Error).message, 502);
    }

    if (!tgResponse.ok) {
      const err = await tgResponse.text();
      console.error("Telegram sendPhoto failed:", err);

      if (err.includes("PHOTO_INVALID_DIMENSIONS")) {
        return createErrorResponse("Invalid Image Dimensions", "Image resolution is too high. The combined width and height must be less than 10,000 pixels.", 400);
      }

      return createErrorResponse("Telegram upload failed", err.substring(0, 200), 502);
    }

    const tgResult = await tgResponse.json() as TelegramResponse<TelegramMessageResult>;
    if (!tgResult.ok) {
      return createErrorResponse("Telegram error", tgResult.description || "Unknown error", 502);
    }

    const photos = tgResult.result!.photo!;
    const largestPhoto = photos[photos.length - 1];
    const fileId = largestPhoto.file_id;
    const fileUniqueId = largestPhoto.file_unique_id;
    const imgWidth = largestPhoto.width || 0;
    const imgHeight = largestPhoto.height || 0;
    const messageId = tgResult.result!.message_id;

    const workerHost = env.WORKER_HOST || "server.rahulkumarbknv.workers.dev";
    const finalImageUrl = `https://${workerHost}/proxy-image?file_id=${encodeURIComponent(fileId)}&quality=full`;
    const initialThumbnailUrl = `https://${workerHost}/proxy-image?file_id=${encodeURIComponent(fileId)}&quality=low`;

    const initialPayload = {
      telegramFileId: fileId,
      fileUniqueId,
      imageUrl: finalImageUrl,
      thumbnailUrl: initialThumbnailUrl,
      title,
      category,
      categorized: false,
      categorizationAttempts: 0,
      photographer,
      addedAt: Date.now(),
      source: "User Uploaded",
      premium: false,
      width: imgWidth,
      height: imgHeight,
      uploaderId,
      chatId: env.TELEGRAM_CHAT_ID,
      messageId
    };

    let firebaseKey: string | null = null;
    if (env.FIREBASE_DATABASE_URL && env.FIREBASE_DATABASE_SECRET) {
      const fbResult = await writeToFirebase(initialPayload, env);
      if (fbResult && fbResult.name) {
        firebaseKey = fbResult.name;
        const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
        const updateRef = `${env.FIREBASE_DATABASE_URL}/wallpapers/newly_added/${firebaseKey}.json?${auth}`;

        await fetch(updateRef, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ id: firebaseKey }),
          signal: AbortSignal.timeout(8000)
        }).catch((e: unknown) => console.error("Firebase id patch failed:", e));

        await fetch(`${env.FIREBASE_DATABASE_URL}/wallpapers/file_index/${fileUniqueId}.json?${auth}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ firebaseKey, addedAt: Date.now() })
        }).catch((e: unknown) => console.error("Firebase file_index write failed:", e));

        ctx.waitUntil((async () => {
          await fetch(`${env.FIREBASE_DATABASE_URL!}/wallpapers/pending_processing/${firebaseKey!}.json?${auth}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({
              firebaseKey,
              fileUniqueId,
              imageUrl: finalImageUrl,
              chatId: env.TELEGRAM_CHAT_ID,
              messageId,
              queuedAt: Date.now(),
              uploaderId,
              skipAI: !needsAutoCategory
            })
          }).catch((e: unknown) => console.error("Failed to queue pending item:", e));
        })());
      }
    }

    return new Response(JSON.stringify({
      success: true,
      id: firebaseKey,
      telegramFileId: fileId,
      imageUrl: finalImageUrl,
      thumbnailUrl: initialThumbnailUrl,
      category,
      status: "pending_processing",
      width: imgWidth,
      height: imgHeight
    }), {
      headers: { "Content-Type": "application/json", ...CORS_HEADERS }
    });
  } catch (error: unknown) {
    console.error("Wallpaper upload handler error:", error);
    return createErrorResponse("Upload failed", (error as Error).message, 500);
  }
}

async function handleDeleteWallpaper(request: Request, env: Env, ctx: ExecutionContext): Promise<Response> {
  try {
    const body = await request.json() as { firebaseKey?: string; fileUniqueId?: string; email?: string };
    const { firebaseKey, fileUniqueId, email } = body;
    if (!firebaseKey) {
      return createErrorResponse("Bad Request", "firebaseKey is required", 400);
    }
    if (!env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) {
      return createErrorResponse("Server config error", "Firebase not configured", 500);
    }

    const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
    const base = env.FIREBASE_DATABASE_URL;

    const recordResp = await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`);
    const record = await recordResp.json() as Record<string, any> | null;
    if (!record) {
      return createErrorResponse("Not Found", "Wallpaper not found in database", 404);
    }

    const uploaderId = record.uploaderId || "";
    if (email && uploaderId && email !== uploaderId) {
      return createErrorResponse("Forbidden", "You can only delete your own wallpapers", 403);
    }

    const actualFileUniqueId = fileUniqueId || record.fileUniqueId || "";
    const chatId = record.chatId ? Number(record.chatId) : null;
    const messageId = record.messageId ? Number(record.messageId) : null;

    if (record.thumbnailUrl) {
      const publicId = extractCloudinaryPublicId(record.thumbnailUrl);
      if (publicId) {
        ctx.waitUntil(deleteFromCloudinary(publicId, env));
      }
    }

    ctx.waitUntil((async () => {
      await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`, { method: "DELETE" }).catch(() => {});
      if (actualFileUniqueId) {
        await fetch(`${base}/wallpapers/file_index/${actualFileUniqueId}.json?${auth}`, { method: "DELETE" }).catch(() => {});
      }
      await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, { method: "DELETE" }).catch(() => {});
      if (chatId && messageId) {
        await deleteTelegramMessage(chatId, messageId, env);
      }
      const title = record.title || "Untitled";
      const deleter = email || uploaderId || "Unknown";
      const adminChatId = env.ADMIN_CHAT_ID ? Number(env.ADMIN_CHAT_ID) : null;
      if (adminChatId && env.TELEGRAM_CHAT_ID) {
        const targetChat = Number(env.TELEGRAM_CHAT_ID);
        await replyToChat(targetChat, `🗑️ Wallpaper deleted by ${deleter}\nTitle: ${title}`, env);
      }
      if (env.WALLMOB_CACHE) {
        try {
          let cursor: string | undefined;
          do {
            const listResult = await env.WALLMOB_CACHE.list({ prefix: "wallmob:", cursor });
            for (const key of listResult.keys) {
              await env.WALLMOB_CACHE.delete(key.name);
            }
            cursor = listResult.cursor;
          } while (cursor);
        } catch (e) {
          console.error("Cache purge failed:", e);
        }
      }
    })());

    return new Response(JSON.stringify({ success: true }), {
      headers: { "Content-Type": "application/json", ...CORS_HEADERS }
    });
  } catch (error: unknown) {
    console.error("Delete wallpaper handler error:", error);
    return createErrorResponse("Delete failed", (error as Error).message, 500);
  }
}

async function uploadToCloudinary(photo: File, env: Env, folder?: string): Promise<{ url: string; error: string | null }> {
  if (!env.CLOUDINARY_CLOUD_NAME || !env.CLOUDINARY_UPLOAD_PRESET) {
    return { url: "", error: "Missing CLOUDINARY_CLOUD_NAME or CLOUDINARY_UPLOAD_PRESET in env variables" };
  }
  try {
    const cloudFormData = new FormData();
    cloudFormData.append("file", photo);
    cloudFormData.append("upload_preset", env.CLOUDINARY_UPLOAD_PRESET);
    if (folder) cloudFormData.append("folder", folder);

    const cloudResp = await fetch(
      `https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/upload`,
      { method: "POST", body: cloudFormData, signal: AbortSignal.timeout(20000) }
    );

    if (!cloudResp.ok) {
      const errText = await cloudResp.text();
      console.error("Cloudinary upload failed with status:", cloudResp.status, errText);
      return { url: "", error: errText };
    }
    const cloudResult = await cloudResp.json() as CloudinaryUploadResult;
    return { url: cloudResult.secure_url || "", error: null };
  } catch (e: unknown) {
    console.error("Cloudinary catch error:", e);
    return { url: "", error: (e as Error).message };
  }
}

const IMAGE_MIME_TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp"];
const MAX_FILE_SIZE = 18 * 1024 * 1024;

async function handleTelegramWebhook(request: Request, env: Env, _ctx: ExecutionContext): Promise<Response> {
  try {
    const update = await request.json() as TelegramUpdate;
    const msg = update.message;
    if (!msg) return new Response("OK");

    const chat = msg.chat;
    const from = msg.from || {};
    const chatId = chat.id;
    const senderName = from.username || from.first_name || "Unknown";

    if (msg.text && msg.text.trim() === "/delete" && msg.reply_to_message) {
      const adminId = env.ADMIN_CHAT_ID;
      if (!adminId || String(chatId) !== String(adminId)) {
        await replyToChat(chatId, "Unauthorized.", env);
        return new Response("OK");
      }
      const replied = msg.reply_to_message;
      let fileUniqueId: string | null = null;
      if (replied.photo) {
        fileUniqueId = replied.photo[replied.photo.length - 1].file_unique_id;
      } else if (replied.document && IMAGE_MIME_TYPES.includes(replied.document.mime_type)) {
        fileUniqueId = replied.document.file_unique_id;
      }
      if (!fileUniqueId) {
        await replyToChat(chatId, "Reply to an image message with /delete.", env);
        return new Response("OK");
      }
      const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
      const base = env.FIREBASE_DATABASE_URL;
      const indexResp = await fetch(`${base}/wallpapers/file_index/${fileUniqueId}.json?${auth}`);
      const indexData = await indexResp.json() as { firebaseKey?: string } | null;
      let firebaseKey = indexData?.firebaseKey;
      if (!firebaseKey) {
        const allResp = await fetch(`${base}/wallpapers/newly_added.json?${auth}&orderBy="fileUniqueId"&equalTo="${fileUniqueId}"`);
        const allData = await allResp.json() as Record<string, any> | null;
        if (allData) {
          const keys = Object.keys(allData);
          if (keys.length > 0) firebaseKey = keys[0];
        }
      }
      if (!firebaseKey) {
        await replyToChat(chatId, "Image not found in database.", env);
        return new Response("OK");
      }
      const recordResp = await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`);
      const record = await recordResp.json() as { thumbnailUrl?: string; chatId?: number; messageId?: number } | null;
      if (record && record.thumbnailUrl) {
        const publicId = extractCloudinaryPublicId(record.thumbnailUrl);
        if (publicId) await deleteFromCloudinary(publicId, env);
      }
      await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`, { method: "DELETE" }).catch(() => {});
      await fetch(`${base}/wallpapers/file_index/${fileUniqueId}.json?${auth}`, { method: "DELETE" }).catch(() => {});
      await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, { method: "DELETE" }).catch(() => {});
      const origChat = record?.chatId || chatId;
      const origMsg = record?.messageId || replied.message_id;
      if (origChat && origMsg) await deleteTelegramMessage(origChat, origMsg, env);
      await deleteTelegramMessage(chatId, replied.message_id, env);
      await replyToChat(chatId, "Image deleted.", env);
      return new Response("OK");
    }

    let file_id: string | undefined;
    let file_unique_id: string | undefined;
    let width = 0;
    let height = 0;

    if (msg.photo) {
      const largest = msg.photo[msg.photo.length - 1];
      file_id = largest.file_id;
      file_unique_id = largest.file_unique_id;
      width = largest.width || 0;
      height = largest.height || 0;
    } else if (msg.document && IMAGE_MIME_TYPES.includes(msg.document.mime_type)) {
      if (msg.document.file_size > MAX_FILE_SIZE) {
        await replyToChat(chatId, "File too large! Max 18 MB. Send as compressed photo instead.", env);
        return new Response("OK");
      }
      file_id = msg.document.file_id;
      file_unique_id = msg.document.file_unique_id;
      const dims = await getTelegramFileDimensions(msg.document.file_id, env);
      width = dims.width;
      height = dims.height;
    } else {
      return new Response("OK");
    }

    const isDuplicate = await checkDuplicateByFileUniqueId(file_unique_id!, env);
    if (isDuplicate) {
      await replyToChat(chatId, "This image already exists (duplicate).", env);
      return new Response("OK");
    }

    const workerHost = env.WORKER_HOST || "server.rahulkumarbknv.workers.dev";
    const imageUrl = `https://${workerHost}/proxy-image?file_id=${encodeURIComponent(file_id!)}&quality=full`;
    const thumbnailUrl = `https://${workerHost}/proxy-image?file_id=${encodeURIComponent(file_id!)}&quality=low`;

    const messageId = msg.message_id;
    const uploaderId = String(chatId);

    if (await isOverLimit(uploaderId, env)) {
      await replyToChat(chatId, "Too many uploads in queue. Please wait.", env);
      return new Response("OK");
    }

    const payload: Record<string, unknown> = {
      telegramFileId: file_id,
      fileUniqueId: file_unique_id,
      imageUrl,
      thumbnailUrl,
      title: chat.title || "Telegram Upload",
      category: "General",
      categorized: false,
      categorizationAttempts: 0,
      source: "Telegram Bot",
      photographer: senderName,
      width,
      height,
      chatId,
      messageId,
      uploaderId,
      addedAt: Date.now(),
      premium: false
    };

    if (!env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) {
      console.error("Firebase not configured — skipping save");
      await replyToChat(chatId, "Firebase not configured on the server.", env);
      return new Response("OK");
    }

    const pushRef = `${env.FIREBASE_DATABASE_URL}/wallpapers/newly_added.json?auth=${env.FIREBASE_DATABASE_SECRET}`;
    const pushResp = await fetch(pushRef, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify(payload)
    });

    if (!pushResp.ok) {
      const errText = await pushResp.text();
      console.error("Firebase push failed:", errText);
      await replyToChat(chatId, "Firebase save failed: " + errText.substring(0, 100), env);
      return new Response("OK");
    }

    const pushResult = await pushResp.json() as { name: string };
    const firebaseKey = pushResult.name;

    const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
    const waitMsgId = await replyToChat(chatId, "Processing image, please wait...", env);
    await fetch(`${env.FIREBASE_DATABASE_URL}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ firebaseKey, fileUniqueId: file_unique_id, imageUrl, chatId, messageId, waitMsgId, queuedAt: Date.now(), uploaderId, skipAI: false })
    }).catch((e: unknown) => console.error("Failed to queue pending item:", e));
    return new Response("OK");
  } catch (error: unknown) {
    console.error("Telegram webhook error:", error);
    return new Response("OK");
  }
}

async function processWallpaperAssets(firebaseKey: string, fileUniqueId: string, imageUrl: string, chatId: number, messageId: number, env: Env, skipAI = false, waitMsgId: number | null = null): Promise<boolean | "rejected"> {
  const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
  const base = env.FIREBASE_DATABASE_URL!;

  const { safe, unreachable } = await moderateImage(imageUrl, env);
  if (!safe && unreachable) {
    console.log(`processWallpaperAssets: moderation API unreachable, requeueing ${firebaseKey}`);
    await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        firebaseKey, fileUniqueId, imageUrl, chatId, messageId, skipAI,
        moderationOnly: true, queuedAt: Date.now(), retryAfter: Date.now() + 3 * 60 * 1000
      })
    }).catch((e: unknown) => console.error("Failed to queue moderation retry:", e));
    return false;
  }
  if (!safe) {
    console.log(`processWallpaperAssets: NSFW detected, deleting ${firebaseKey}`);
    await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`, { method: "DELETE" }).catch(() => {});
    if (chatId && waitMsgId) await deleteTelegramMessage(chatId, waitMsgId, env);
    if (chatId && messageId) {
      await deleteTelegramMessage(chatId, messageId, env);
      await replyToChat(chatId, "Image rejected: contains inappropriate content (violence, drugs, nudity, etc). Please upload only clean, safe images.", env);
    }
    return "rejected";
  }

  const imageBuffer = await fetchResizedImage(imageUrl, env);
  if (!imageBuffer) {
    console.error(`processWallpaperAssets: could not fetch resized image for ${firebaseKey}`);
    if (chatId && waitMsgId) {
      await deleteTelegramMessage(chatId, waitMsgId, env);
      await replyToChat(chatId, "Failed to process image. The image may be too large or the source is unavailable.", env);
    }
    return false;
  }

  let cloudinaryUrl: string | null = null;
  if (env.CLOUDINARY_CLOUD_NAME && env.CLOUDINARY_UPLOAD_PRESET) {
    try {
      const existingResp = await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`);
      if (existingResp.ok) {
        const existing = await existingResp.json() as Record<string, unknown> | null;
        if (existing?.thumbnailUrl && typeof existing.thumbnailUrl === "string" && existing.thumbnailUrl.includes("cloudinary")) {
          cloudinaryUrl = existing.thumbnailUrl;
          console.log(`Cloudinary URL already exists for ${firebaseKey}, skipping re-upload`);
        }
      }
    } catch (e: unknown) {
      console.error(`Failed to check existing Cloudinary URL for ${firebaseKey}:`, e);
    }

    if (!cloudinaryUrl) {
      try {
        const cdForm = new FormData();
        cdForm.append("file", new Blob([imageBuffer]), "image.jpg");
        cdForm.append("upload_preset", env.CLOUDINARY_UPLOAD_PRESET);
        const cdResp = await fetch(
          `https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/upload`,
          { method: "POST", body: cdForm, signal: AbortSignal.timeout(20000) }
        );
        if (cdResp.ok) {
          const cdResult = await cdResp.json() as CloudinaryUploadResult;
          cloudinaryUrl = cdResult.secure_url || null;
        } else {
          const errText = await cdResp.text().catch(() => "<unreadable>");
          console.error("Cloudinary bg upload failed with status:", cdResp.status, errText.substring(0, 300));
        }
      } catch (e: unknown) {
        console.error("Cloudinary bg upload failed:", e);
      }
    }
  }

  const patchData: PatchData = { id: firebaseKey };
  if (cloudinaryUrl) {
    patchData.thumbnailUrl = cloudinaryUrl.replace(
      "/upload/",
      "/upload/c_fill,w_480,h_854,q_auto,f_auto/"
    );
  }

  if (env.AI && !skipAI) {
    const { category, quotaExceeded } = await categorizeImage(imageBuffer, env);
    if (quotaExceeded) {
      patchData.categorized = false;
      await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`, {
        method: "PATCH",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify(patchData)
      });
      await fetch(`${base}/wallpapers/file_index/${fileUniqueId}.json?${auth}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ firebaseKey, addedAt: Date.now() })
      });
      const retryAfter = Date.now() + msUntilNextUtcMidnight() + 5 * 60 * 1000;
      await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, {
        method: "PUT",
        headers: { "Content-Type": "application/json" },
        body: JSON.stringify({ firebaseKey, fileUniqueId, imageUrl, categoryOnly: true, queuedAt: Date.now(), retryAfter })
      });
      console.log(`${firebaseKey}: neuron quota hit, category retry queued for ${new Date(retryAfter).toISOString()}`);
      if (chatId && waitMsgId) {
        await deleteTelegramMessage(chatId, waitMsgId, env);
        await replyToChat(chatId, "Image uploaded successfully. Category will be assigned later (AI quota limit reached).", env);
      }
      return true;
    }
    patchData.category = category;
    patchData.categorized = true;
  } else {
    patchData.categorized = true;
  }

  await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify(patchData)
  });

  await fetch(`${base}/wallpapers/file_index/${fileUniqueId}.json?${auth}`, {
    method: "PUT",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ firebaseKey, addedAt: Date.now() })
  });
  if (chatId && waitMsgId) {
    await deleteTelegramMessage(chatId, waitMsgId, env);
    const cat = patchData.category ? ` (${patchData.category})` : "";
    await replyToChat(chatId, `Image uploaded successfully${cat}.`, env);
  }
  return true;
}

async function retryCategorizationOnly(firebaseKey: string, imageUrl: string, env: Env): Promise<RetryResult> {
  const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
  const base = env.FIREBASE_DATABASE_URL!;

  const imageBuffer = await fetchResizedImage(imageUrl, env);
  if (!imageBuffer) return { done: false, quotaExceeded: false };

  const { category, quotaExceeded } = await categorizeImage(imageBuffer, env);
  if (quotaExceeded) return { done: false, quotaExceeded: true };

  await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`, {
    method: "PATCH",
    headers: { "Content-Type": "application/json" },
    body: JSON.stringify({ id: firebaseKey, category, categorized: true })
  });
  return { done: true, quotaExceeded: false };
}

const PENDING_MAX_AGE_MS = 20 * 60 * 1000;
const MAX_PENDING_PER_TICK = 4;

async function processPendingWallpapers(env: Env): Promise<void> {
  if (!env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) return;
  const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
  const base = env.FIREBASE_DATABASE_URL;

  const listResp = await fetch(`${base}/wallpapers/pending_processing.json?${auth}`);
  if (!listResp.ok) return;
  const pending = await listResp.json() as Record<string, PendingItem> | null;
  if (!pending) return;

  let moderationUnreachable = false;

  const entries = Object.entries(pending).slice(0, MAX_PENDING_PER_TICK);
  for (const [firebaseKey, item] of entries) {
    try {
      if (item.retryAfter && Date.now() < item.retryAfter) continue;

      if (item.categoryOnly) {
        const result = await retryCategorizationOnly(firebaseKey, item.imageUrl, env);
        if (result.done) {
          await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, { method: "DELETE" });
          console.log(`Category retry succeeded for ${firebaseKey}`);
        } else if (result.quotaExceeded) {
          const retryAfter = Date.now() + msUntilNextUtcMidnight() + 5 * 60 * 1000;
          await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ ...item, retryAfter })
          });
        }
        continue;
      }

      if (moderationUnreachable) {
        const retryAfter = Date.now() + 3 * 60 * 1000;
        await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, {
          method: "PUT",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ ...item, moderationOnly: true, retryAfter })
        }).catch(() => {});
        continue;
      }

      const imgResp = await fetchImageWithRetry(item.imageUrl, env, 2, 3000);
      if (imgResp) {
        imgResp.body?.cancel().catch(() => {});
        const result = await processWallpaperAssets(firebaseKey, item.fileUniqueId, item.imageUrl, item.chatId, item.messageId, env, item.skipAI, item.waitMsgId);
        if (result === true || result === "rejected") {
          await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, { method: "DELETE" });
          console.log(result === "rejected" ? `Confirmed NSFW on retry, cleared queue: ${firebaseKey}` : `Processed queued wallpaper ${firebaseKey}`);
        } else {
          moderationUnreachable = true;
        }
      } else if (Date.now() - (item.queuedAt || 0) > PENDING_MAX_AGE_MS) {
        console.error(`Giving up on ${firebaseKey} after ${PENDING_MAX_AGE_MS / 60000} min`);
        await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ categorized: true })
        }).catch((e: unknown) => console.error("Failed to mark abandoned item categorized:", e));
        await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, { method: "DELETE" });
      }
    } catch (e: unknown) {
      console.error(`Error processing pending wallpaper ${firebaseKey}:`, e);
    }
  }
}

async function getTelegramFileDimensions(fileId: string, env: Env): Promise<{ width: number; height: number }> {
  try {
    const fileInfoResp = await fetch(
      `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/getFile?file_id=${fileId}`,
      { signal: AbortSignal.timeout(8000) }
    );
    const fileInfo = await fileInfoResp.json() as TelegramResponse<TelegramFileInfo>;
    if (!fileInfo.ok) return { width: 0, height: 0 };

    const fileUrl = `https://api.telegram.org/file/bot${env.TELEGRAM_BOT_TOKEN}/${fileInfo.result!.file_path}`;
    const resp = await fetch(fileUrl, {
      headers: { Range: "bytes=0-65535" },
      signal: AbortSignal.timeout(8000)
    });
    const buf = new Uint8Array(await resp.arrayBuffer());
    return parseImageDimensions(buf);
  } catch (e: unknown) {
    console.error("getTelegramFileDimensions failed:", e);
    return { width: 0, height: 0 };
  }
}

function parseImageDimensions(buf: Uint8Array): { width: number; height: number } {
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);

  if (buf.length >= 24 && view.getUint32(0) === 0x89504e47 && view.getUint32(4) === 0x0d0a1a0a) {
    return { width: view.getUint32(16), height: view.getUint32(20) };
  }

  if (buf.length >= 10 && buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46) {
    return { width: view.getUint16(6, true), height: view.getUint16(8, true) };
  }

  if (buf.length >= 30 && view.getUint32(0) === 0x52494646 && view.getUint32(8) === 0x57454250) {
    const fourcc = String.fromCharCode(buf[12], buf[13], buf[14], buf[15]);
    if (fourcc === "VP8 ") {
      return { width: view.getUint16(26, true) & 0x3fff, height: view.getUint16(28, true) & 0x3fff };
    }
    if (fourcc === "VP8L") {
      const b = view.getUint32(21, true);
      return { width: (b & 0x3fff) + 1, height: ((b >> 14) & 0x3fff) + 1 };
    }
    if (fourcc === "VP8X") {
      const w = buf[24] | (buf[25] << 8) | (buf[26] << 16);
      const h = buf[27] | (buf[28] << 8) | (buf[29] << 16);
      return { width: w + 1, height: h + 1 };
    }
  }

  if (buf.length >= 4 && buf[0] === 0xff && buf[1] === 0xd8) {
    let offset = 2;
    while (offset < buf.length - 9) {
      if (buf[offset] !== 0xff) { offset++; continue; }
      const marker = buf[offset + 1];
      const isSOF = (marker >= 0xc0 && marker <= 0xc3) || (marker >= 0xc5 && marker <= 0xc7) ||
                    (marker >= 0xc9 && marker <= 0xcb) || (marker >= 0xcd && marker <= 0xcf);
      if (isSOF) {
        return { height: view.getUint16(offset + 5), width: view.getUint16(offset + 7) };
      }
      const segLength = view.getUint16(offset + 2);
      offset += 2 + segLength;
    }
  }

  return { width: 0, height: 0 };
}

const WALLPAPER_CATEGORIES = [
  "Nature", "Mountains", "Forest", "Flowers", "Beach", "Ocean", "Waterfall", "Desert", "Sky", "Sunset",
  "Space", "Galaxy", "Planets", "Stars", "Moon",
  "Abstract", "Minimal", "Gradient", "Geometric", "Texture",
  "Dark", "AMOLED", "Neon", "Cyberpunk", "Fantasy",
  "Anime", "Gaming", "Movies", "Superheroes", "Cartoons",
  "Cars", "Motorcycles", "Aircraft", "Ships", "Trains",
  "Animals", "Birds", "Cats", "Dogs", "Wildlife",
  "Architecture", "Cityscape", "Technology", "Robots", "AI",
  "Sports", "Music", "Food", "Travel", "People",
  "Quotes", "Art", "Macro", "Vintage", "Aesthetic",
  "Fire", "Ice", "Rain", "Snow", "Underwater"
] as const;

const DEFAULT_CATEGORY = "General";
const AI_CATEGORY_GROUPS: readonly string[] = WALLPAPER_CATEGORIES;

function arrayBufferToBase64(buf: ArrayBuffer): string {
  const bytes = new Uint8Array(buf);
  const chunks: string[] = [];
  for (let i = 0; i < bytes.length; i += 8192)
    chunks.push(String.fromCharCode(...bytes.subarray(i, i + 8192)));
  return btoa(chunks.join(""));
}

async function categorizeImage(imageBuffer: ArrayBuffer, env: Env): Promise<CategorizeResult> {
  if (!env.AI) return { category: DEFAULT_CATEGORY, quotaExceeded: false };
  try {
    const categories = AI_CATEGORY_GROUPS.join(", ");
    const result = await env.AI.run("@cf/moondream/moondream3.1-9B-A2B", {
      image: `data:image/jpeg;base64,${arrayBufferToBase64(imageBuffer)}`,
      question: `What category best describes this wallpaper? Choose exactly one word from: ${categories}. Reply with only the category name.`,
      max_tokens: 80,
      reasoning: false,
      stream: false
    }) as Record<string, unknown>;
    console.log(`categorizeImage keys: ${Object.keys(result).join(",")}`);
    const nested = (result.result as Record<string, unknown>) || result;
    const raw = ((nested.answer as string) || (nested.caption as string) || (nested.response as string) || ((nested.result as Record<string, unknown>)?.answer as string) || JSON.stringify(result)).trim();
    console.log(`categorizeImage raw: "${raw.substring(0, 200)}"`);
    console.log(`categorizeImage answer field: "${nested.answer}", finish_reason: "${nested.finish_reason}"`);

    const lower = raw.toLowerCase();
    for (const cat of AI_CATEGORY_GROUPS) {
      if (lower.includes(cat.toLowerCase())) {
        console.log(`categorizeImage -> ${cat}`);
        return { category: cat, quotaExceeded: false };
      }
    }

    console.log(`categorizeImage: no category match, falls to "General"`);
    return { category: DEFAULT_CATEGORY, quotaExceeded: false };
  } catch (e: unknown) {
    const quotaExceeded = /daily free allocation|10,000 neurons/i.test((e as Error).message || "");
    console.error("categorizeImage failed:", (e as Error).message, quotaExceeded ? "(daily neuron quota exceeded)" : "");
    return { category: DEFAULT_CATEGORY, quotaExceeded };
  }
}

function msUntilNextUtcMidnight(): number {
  const now = new Date();
  const nextMidnight = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1);
  return nextMidnight - now.getTime();
}

const MAX_IMAGE_BUFFER = 6 * 1024 * 1024;

function extractFileIdFromUrl(url: string): string | null {
  try {
    return new URL(url).searchParams.get("file_id") || null;
  } catch { return null; }
}

async function fetchResizedImage(imageUrl: string, env: Env): Promise<ArrayBuffer | null> {
  const fileId = extractFileIdFromUrl(imageUrl);

  if (fileId && env.TELEGRAM_BOT_TOKEN) {
    try {
      const fileInfoResp = await fetch(
        `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/getFile?file_id=${fileId}`,
        { signal: AbortSignal.timeout(8000) }
      );
      const fileInfo = await fileInfoResp.json() as TelegramResponse<TelegramFileInfo>;
      if (fileInfo.ok && fileInfo.result?.file_path) {
        const directUrl = `https://api.telegram.org/file/bot${env.TELEGRAM_BOT_TOKEN}/${fileInfo.result.file_path}`;
        const wsrvUrl = `https://images.weserv.nl/?url=${encodeURIComponent(directUrl)}&w=1280&q=85&output=jpg`;
        const resp = await fetch(wsrvUrl, { signal: AbortSignal.timeout(20000) });
        if (resp.ok) {
          const buf = await resp.arrayBuffer();
          if (buf.byteLength <= MAX_IMAGE_BUFFER) return buf;
          console.error(`fetchResizedImage: weserv buffer too large (${buf.byteLength} bytes)`);
        } else {
          console.error(`fetchResizedImage: weserv status ${resp.status}`);
        }
      }
    } catch (e: unknown) {
      console.error("fetchResizedImage: Telegram/weserv path failed:", (e as Error).message);
    }
  }

  try {
    const fetcher = env.PROXY_WORKER ? env.PROXY_WORKER.fetch.bind(env.PROXY_WORKER) : fetch;
    const resp = await fetcher(imageUrl, { signal: AbortSignal.timeout(20000) });
    if (!resp.ok) {
      console.error(`fetchResizedImage: direct status ${resp.status}`);
      return null;
    }
    const buffer = await resp.arrayBuffer();
    if (buffer.byteLength > MAX_IMAGE_BUFFER) {
      console.error(`fetchResizedImage: buffer too large (${buffer.byteLength} bytes), skipping`);
      return null;
    }
    console.log(`fetchResizedImage: direct fallback OK (${buffer.byteLength} bytes)`);
    return buffer;
  } catch (e: unknown) {
    console.error("fetchResizedImage: direct fallback failed:", (e as Error).message);
    return null;
  }
}

async function fetchImageWithRetry(url: string, env: Env, retries = 3, delayMs = 2000): Promise<Response | null> {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      const resp = env.PROXY_WORKER
        ? await env.PROXY_WORKER.fetch(url, { signal: AbortSignal.timeout(15000) })
        : await fetch(url, { signal: AbortSignal.timeout(15000) });
      if (resp.ok) return resp;
      const bodyText = await resp.clone().text().catch(() => "<unreadable>");
      console.log(`fetchImageWithRetry attempt ${attempt}: status ${resp.status}, body: ${bodyText.substring(0, 300)}`);
    } catch (e: unknown) {
      console.log(`fetchImageWithRetry attempt ${attempt}: ${(e as Error).message}`);
    }
    if (attempt < retries) await new Promise((r) => setTimeout(r, delayMs * (2 ** (attempt - 1))));
  }
  return null;
}

async function replyToChat(chatId: number, text: string, env: Env): Promise<number | null> {
  if (!env.TELEGRAM_BOT_TOKEN) return null;
  try {
    const resp = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chatId, text })
    });
    const data = await resp.json() as { ok: boolean; result?: { message_id: number } };
    return data?.result?.message_id || null;
  } catch (e: unknown) {
    console.error("sendMessage failed:", e);
    return null;
  }
}

async function deleteTelegramMessage(chatId: number, messageId: number, env: Env): Promise<void> {
  if (!env.TELEGRAM_BOT_TOKEN || !chatId || !messageId) return;
  try {
    await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/deleteMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chatId, message_id: messageId })
    });
  } catch (e: unknown) {
    console.error("deleteMessage failed:", e);
  }
}

async function checkDuplicateByFileUniqueId(fileUniqueId: string, env: Env): Promise<boolean> {
  if (!env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) return false;
  try {
    const ref = `${env.FIREBASE_DATABASE_URL}/wallpapers/file_index/${fileUniqueId}.json?auth=${env.FIREBASE_DATABASE_SECRET}`;
    const resp = await fetch(ref);
    const data = await resp.json();
    return data !== null;
  } catch {
    return false;
  }
}

async function isOverLimit(uploaderId: string, env: Env): Promise<boolean> {
  if (!uploaderId || !env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) return false;
  try {
    const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
    const listResp = await fetch(`${env.FIREBASE_DATABASE_URL}/wallpapers/pending_processing.json?${auth}`);
    if (!listResp.ok) return false;
    const pending = await listResp.json() as Record<string, PendingItem> | null;
    if (!pending) return false;
    const count = Object.values(pending).filter(item => item.uploaderId === uploaderId).length;
    return count >= 20;
  } catch (e: unknown) {
    console.error("isOverLimit check failed:", e);
    return false;
  }
}
