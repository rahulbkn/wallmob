var src_default = {
  async fetch(request, env, ctx) {
    return await handleRequest(request, env, ctx);
  },
  async scheduled(event, env, ctx) {
    ctx.waitUntil(processPendingWallpapers(env));
    // Render's free tier spins the moderation API down after ~15 min idle,
    // which then makes the NEXT real upload eat a 30-50s cold-start delay.
    // Ping it every ~10 min so it's usually already warm when needed.
    if (new Date().getUTCMinutes() % 10 === 0) {
      ctx.waitUntil(pingModerationApi(env));
    }
  }
};

var CORS_HEADERS = {
  "Access-Control-Allow-Origin": "*",
  "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
  "Access-Control-Allow-Headers": "Content-Type"
};

var CONFIG = {
  DEFAULT_PAGE: 1,
  DEFAULT_PER_PAGE: 100,
  MAX_PER_PAGE: 100,
  MIN_PER_PAGE: 1,
  MIN_WIDTH: 1080,
  MIN_HEIGHT: 1920,
  REQUEST_TIMEOUT: 8e3,
  CACHE_MAX_AGE: 3600,
  STALE_AGE: 86400
};

var inFlightRequests = new Map();
var INFLIGHT_TTL = 60000; // auto-clean entries after 60s

async function handleRequest(request, env, ctx) {
  if (request.method === "OPTIONS") {
    return new Response(null, { headers: CORS_HEADERS });
  }
  try {
    const url = new URL(request.url);

    if (url.pathname === "/upload") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST to upload images", 405);
      }
      return await handleUpload(request, env);
    }

    if (url.pathname === "/profile") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST to upload profile image", 405);
      }
      return await handleProfileUpload(request, env, ctx);
    }

    if (url.pathname === "/upload-wallpaper") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST to upload wallpaper", 405);
      }
      return await handleWallpaperUpload(request, env, ctx);
    }

    if (url.pathname === "/accept-llama-license") {
      // ONE-TIME setup route — hit this once in a browser, then you can
      // remove this block. Accepts Meta's license for the vision model
      // used by categorizeImage(). Without this, Workers AI returns error 5016.
      if (!env.AI) return createErrorResponse("No AI binding", "env.AI not configured", 500);
      try {
        const result = await env.AI.run("@cf/meta/llama-3.2-11b-vision-instruct", { prompt: "agree" });
        return new Response(JSON.stringify({ success: true, result }, null, 2), {
          headers: { "Content-Type": "application/json", ...CORS_HEADERS }
        });
      } catch (e) {
        return createErrorResponse("License accept failed", e.message, 500);
      }
    }

    if (url.pathname === "/telegram-webhook") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST", 405);
      }
      return await handleTelegramWebhook(request, env, ctx);
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
      return createErrorResponse("API Configuration Error", keyValidation.message, 503, { required_keys: keyValidation.requiredKeys });
    }
    const cacheKey = `wallmob:${params.keyword}:${params.page}:${params.perPage}:${params.source}:${params.sort}:${params.orientation}`;

    if (env.WALLMOB_CACHE) {
      const cached = await env.WALLMOB_CACHE.get(cacheKey, "json");
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
      partial_errors: result.errors.length > 0 ? result.errors : void 0
    };

    if (env.WALLMOB_CACHE) {
      ctx.waitUntil(env.WALLMOB_CACHE.put(cacheKey, JSON.stringify({ payload: responsePayload, cached_at: Date.now() }), { expirationTtl: CONFIG.STALE_AGE }));
    }

    return buildResponse(responsePayload, params);
  } catch (error) {
    console.error("Request handling error:", error);
    return createErrorResponse("Internal Server Error", error.message, 500, { stack: error.stack });
  }
}

function buildResponse(data, params) {
  return new Response(JSON.stringify(data, null, 2), {
    headers: {
      "Content-Type": "application/json",
      "Cache-Control": `public, max-age=${CONFIG.CACHE_MAX_AGE}, stale-while-revalidate=${CONFIG.STALE_AGE}`,
      ...CORS_HEADERS
    }
  });
}

async function refreshCache(cacheKey, params, env) {
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
      partial_errors: result.errors.length > 0 ? result.errors : void 0
    };
    await env.WALLMOB_CACHE.put(cacheKey, JSON.stringify({ payload, cached_at: Date.now() }), { expirationTtl: CONFIG.STALE_AGE });
  } catch (e) {
    console.error("Background refresh failed:", e);
  }
}

async function fetchWithDedup(cacheKey, params, env) {
  // Clean stale entries to prevent memory leak
  const now = Date.now();
  for (const [key, entry] of inFlightRequests) {
    if (now - entry.ts > INFLIGHT_TTL) inFlightRequests.delete(key);
  }

  if (inFlightRequests.has(cacheKey)) {
    return inFlightRequests.get(cacheKey).promise;
  }
  const promise = fetchAllWallpapers(params.page, params.perPage, params.source, params.keyword, params.orientation, params.sort, env).finally(() => {
    inFlightRequests.delete(cacheKey);
  });
  inFlightRequests.set(cacheKey, { promise, ts: now });
  return promise;
}

function parseQueryParams(url) {
  const page = Math.max(CONFIG.DEFAULT_PAGE, parseInt(url.searchParams.get("page")) || CONFIG.DEFAULT_PAGE);
  const perPage = Math.min(CONFIG.MAX_PER_PAGE, Math.max(CONFIG.MIN_PER_PAGE, parseInt(url.searchParams.get("per_page")) || CONFIG.DEFAULT_PER_PAGE));
  const source = url.searchParams.get("source") || "all";
  const keyword = (url.searchParams.get("query") || url.searchParams.get("keyword") || url.searchParams.get("q") || "").trim();
  const orientation = url.searchParams.get("orientation") || "all";
  const sort = (url.searchParams.get("sort") || "relevant").toLowerCase();
  return { page, perPage, source, keyword, orientation, sort };
}

function validateApiKeys(env, sourceFilter) {
  const sources = ["Unsplash", "Pexels", "Pixabay", "Wallhaven"];
  const activeSources = sourceFilter === "all" ? sources : sources.filter((s) => s.toLowerCase() === sourceFilter.toLowerCase());
  if (activeSources.length === 0) {
    return { valid: false, message: `Invalid source: ${sourceFilter}. Valid options: ${sources.join(", ")}, all`, requiredKeys: [] };
  }
  const keyMap = { Unsplash: env.UNSPLASH_KEY, Pexels: env.PEXELS_KEY, Pixabay: env.PIXABAY_KEY, Wallhaven: env.WALLHAVEN_KEY };
  const missingKeys = activeSources.filter((source) => !keyMap[source]);
  if (missingKeys.length === activeSources.length) {
    return { valid: false, message: "No API keys configured for requested sources", requiredKeys: missingKeys.map((s) => `${s.toUpperCase()}_KEY`) };
  }
  return { valid: true };
}

function createErrorResponse(error, message, status, additionalData = {}) {
  return new Response(JSON.stringify({ success: false, error, message, ...additionalData }, null, 2), {
    status,
    headers: { "Content-Type": "application/json", ...CORS_HEADERS }
  });
}

async function fetchAllWallpapers(page, perPage, sourceFilter, keyword, orientation, sort, env) {
  const sources = ["Unsplash", "Pexels", "Pixabay", "Wallhaven"];
  const activeSources = sourceFilter === "all" ? sources : sources.filter((s) => s.toLowerCase() === sourceFilter.toLowerCase());
  const itemsPerSource = Math.min(Math.ceil(perPage / activeSources.length) + 5, 100);
  const apiConfigs = [
    {
      name: "Unsplash",
      enabled: activeSources.includes("Unsplash") && env.UNSPLASH_KEY,
      url: `https://api.unsplash.com/search/photos?query=${encodeURIComponent(keyword)}&page=${page}&per_page=${itemsPerSource}&order_by=${sort === 'popular' ? 'popular' : 'relevant'}`,
      headers: { Authorization: `Client-ID ${env.UNSPLASH_KEY || ""}` }
    },
    {
      name: "Pexels",
      enabled: activeSources.includes("Pexels") && env.PEXELS_KEY,
      url: `https://api.pexels.com/v1/search?query=${encodeURIComponent(keyword)}&page=${page}&per_page=${itemsPerSource}`,
      headers: { Authorization: env.PEXELS_KEY || "" }
    },
    {
      name: "Pixabay",
      enabled: activeSources.includes("Pixabay") && env.PIXABAY_KEY,
      url: `https://pixabay.com/api/?key=${env.PIXABAY_KEY || ""}&q=${encodeURIComponent(keyword)}&image_type=photo&min_width=${CONFIG.MIN_WIDTH}&min_height=${CONFIG.MIN_HEIGHT}&safesearch=true&page=${page}&per_page=${itemsPerSource}&order=${sort === 'popular' ? 'popular' : 'ec'}`,
      headers: {}
    },
    {
      name: "Wallhaven",
      enabled: activeSources.includes("Wallhaven") && env.WALLHAVEN_KEY,
      url: `https://wallhaven.cc/api/v1/search?q=${encodeURIComponent(keyword)}&categories=111&purity=100&atleast=1920x1080&page=${page}&sorting=${sort === 'popular' ? 'views' : 'relevance'}`,
      headers: env.WALLHAVEN_KEY ? { "X-API-Key": env.WALLHAVEN_KEY } : {}
    }
  ];
  const enabledApis = apiConfigs.filter((api) => api.enabled);
  const errors = [];
  const debug = {
    enabled_sources: enabledApis.map((a) => a.name),
    disabled_sources: apiConfigs.filter((a) => !a.enabled).map((a) => a.name),
    has_keys: { unsplash: !!env.UNSPLASH_KEY, pexels: !!env.PEXELS_KEY, pixabay: !!env.PIXABAY_KEY, wallhaven: !!env.WALLHAVEN_KEY }
  };
  if (enabledApis.length === 0) {
    return { wallpapers: [], errors: ["No API keys configured for requested sources"], debug, totalAvailable: 0 };
  }
  const results = await Promise.allSettled(enabledApis.map((api) => fetchFromAPI(api, CONFIG.MIN_WIDTH, CONFIG.MIN_HEIGHT)));
  const seen = new Set();
  const allWallpapers = [];
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
      errors.push({ source: enabledApis[index].name, message: result.reason?.message || "Request failed" });
      debug[enabledApis[index].name] = { status: "failed", error: result.reason?.message };
    }
  });
  const scored = allWallpapers.map((wp) => {
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

async function fetchFromAPI(api, minWidth, minHeight) {
  try {
    const response = await fetch(api.url, { headers: api.headers, signal: AbortSignal.timeout(CONFIG.REQUEST_TIMEOUT) });
    const errorText = response.ok ? null : await response.text();
    if (errorText !== null) {
      console.error(`${api.name} returned ${response.status}: ${errorText}`);
      throw new Error(`HTTP ${response.status}: ${errorText.substring(0, 100)}`);
    }
    const data = await response.json();
    return normalizeData(api.name, data, minWidth, minHeight);
  } catch (error) {
    console.error(`Error ${api.name}:`, error.message);
    throw error;
  }
}

function normalizeData(source, data, minWidth, minHeight) {
  const wallpapers = [];
  let total = 0;
  try {
    switch (source) {
      case "Unsplash":
        total = data.total || 0;
        (data.results || []).forEach((item) => {
          wallpapers.push(createWallpaperObject({
              id: item.id, source: "Unsplash", url: item.urls.regular, thumb: item.urls.small,
              full: item.urls.full, width: item.width, height: item.height, color: item.color,
              title: item.alt_description || item.description || "Untitled",
              author: item.user?.name || "Unknown", author_url: item.user?.links?.html || "",
              likes: item.likes
            }));
        });
        break;
      case "Pexels":
        total = data.total_results || 0;
        if (data.photos) {
          data.photos.forEach((item) => {
              wallpapers.push(createWallpaperObject({
                id: item.id.toString(), source: "Pexels", url: item.src.large2x || item.src.large,
                thumb: item.src.medium, full: item.src.original, width: item.width, height: item.height,
                color: item.avg_color, title: item.alt || "Untitled", author: item.photographer,
                author_url: item.photographer_url
              }));
          });
        }
        break;
      case "Pixabay":
        total = data.totalHits || 0;
        if (data.hits) {
          data.hits.forEach((item) => {
              wallpapers.push(createWallpaperObject({
                id: item.id.toString(), source: "Pixabay", url: item.largeImageURL,
                thumb: item.webformatURL, full: item.imageURL || item.largeImageURL,
                width: item.imageWidth, height: item.imageHeight, color: null,
                title: item.tags || "Untitled", author: item.user,
                author_url: `https://pixabay.com/users/${item.user}-${item.user_id}/`,
                likes: item.likes, downloads: item.downloads
              }));
          });
        }
        break;
      case "Wallhaven":
        total = data.meta?.total || 0;
        if (data.data) {
          data.data.forEach((item) => {
              const color = item.colors && item.colors.length > 0 ? item.colors[0] : null;
              wallpapers.push(createWallpaperObject({
                id: item.id, source: "Wallhaven", url: item.path, thumb: item.thumbs?.small || item.path,
                full: item.path, width: item.dimension_x, height: item.dimension_y, color,
                title: item.category ? `${item.category} wallpaper` : "Wallpaper",
                author: "Wallhaven", author_url: item.url, favorites: item.favorites, views: item.views
              }));
          });
        }
        break;
    }
  } catch (e) {
    console.error(`Normalization error for ${source}:`, e);
  }
  return { items: wallpapers, total };
}

function createWallpaperObject({ id, source, url, thumb, full, width, height, color, title, author, author_url, downloads, likes, favorites, views }) {
  const is4K = (width >= 3840 && height >= 2160) || (width >= 2560 && height >= 1440);
  const aspectRatio = width && height ? width / height : null;
  return {
    id: `${source.toLowerCase()}-${id}`,
    source,
    urls: { raw: full, regular: url, small: thumb },
    meta: {
      width, height,
      aspect_ratio: aspectRatio ? parseFloat(aspectRatio.toFixed(2)) : null,
      orientation: height >= width ? "portrait" : "landscape",
      is_mobile_4k: is4K,
      quality: is4K ? "4K" : "HD",
      dominant_color: color || "#E0E0E0"
    },
    info: { title: title || "Untitled", author: author || "Unknown", author_link: author_url || "" },
    stats: { downloads: downloads || null, likes: likes || null, favorites: favorites || null, views: views || null }
  };
}

async function handleUpload(request, env) {
  try {
    const contentType = request.headers.get("Content-Type") || "";
    if (!contentType.includes("multipart/form-data")) {
      return createErrorResponse("Bad Request", "Content-Type must be multipart/form-data", 400);
    }

    const formData = await request.formData();
    const photo = formData.get("photo");
    const email = (formData.get("email") || "").toString().trim();

    if (!photo) {
      return createErrorResponse("Missing photo", "No photo file provided in 'photo' field", 400);
    }

    if (!email) {
      return createErrorResponse("Unauthorized", "Email is required", 401);
    }
    if (!await verifyEmailUser(email, env)) {
      return createErrorResponse("Unauthorized", "User not found", 401);
    }
    if (!checkRateLimit("upload:" + email, email, env)) {
      return createErrorResponse("Too Many Requests", "Rate limit exceeded. Try again later.", 429);
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

    const tgResult = await tgResponse.json();

    if (!tgResult.ok) {
      return createErrorResponse("Telegram error", tgResult.description, 502);
    }

    const photos = tgResult.result.photo;
    const largestPhoto = photos[photos.length - 1];
    const fileId = largestPhoto.file_id;

    const { url: cloudinaryUrl, error: cdError } = await uploadToCloudinary(photo, env);
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
  } catch (error) {
    console.error("Upload handler error:", error);
    return createErrorResponse("Upload failed", error.message, 500);
  }
}

async function handleProfileUpload(request, env, ctx) {
  try {
    const contentType = request.headers.get("Content-Type") || "";
    if (!contentType.includes("multipart/form-data")) {
      return createErrorResponse("Bad Request", "Content-Type must be multipart/form-data", 400);
    }

    const formData = await request.formData();
    const photo = formData.get("photo");
    const email = (formData.get("email") || "").toString().trim();
    const oldPhotoUrl = formData.get("oldPhotoUrl");

    if (!photo) {
      return createErrorResponse("Missing photo", "No photo file provided in 'photo' field", 400);
    }

    if (!email) {
      return createErrorResponse("Unauthorized", "Email is required", 401);
    }
    if (!await verifyEmailUser(email, env)) {
      return createErrorResponse("Unauthorized", "User not found", 401);
    }
    if (!checkRateLimit("profile:" + email, email, env)) {
      return createErrorResponse("Too Many Requests", "Rate limit exceeded. Try again later.", 429);
    }

    const { url: cloudinaryUrl, error: cdError } = await uploadToCloudinary(photo, env, "profiles");
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
  } catch (error) {
    console.error("Profile upload handler error:", error);
    return createErrorResponse("Upload failed", error.message, 500);
  }
}

const rateLimitMap = new Map();
const RATE_LIMIT_MAX = 10;
const RATE_LIMIT_WINDOW = 60000;

function checkRateLimit(key, email, env) {
  if (env.ADMIN_EMAIL && email === env.ADMIN_EMAIL) return true;
  const now = Date.now();
  const entry = rateLimitMap.get(key);
  if (!entry || now > entry.resetAt) {
    rateLimitMap.set(key, { count: 1, resetAt: now + RATE_LIMIT_WINDOW });
    return true;
  }
  if (entry.count >= RATE_LIMIT_MAX) return false;
  entry.count++;
  return true;
}

async function verifyEmailUser(email, env) {
  if (env.ADMIN_EMAIL && email === env.ADMIN_EMAIL) return true;
  if (!env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) return false;
  try {
    const auth = "auth=" + env.FIREBASE_DATABASE_SECRET;
    const resp = await fetch(
      `${env.FIREBASE_DATABASE_URL}/users.json?${auth}&orderBy="email"&equalTo="${encodeURIComponent(email)}"`,
      { signal: AbortSignal.timeout(10000) }
    );
    if (!resp.ok) return false;
    const users = await resp.json();
    return users !== null && Object.keys(users).length > 0;
  } catch {
    return false;
  }
}

function extractCloudinaryPublicId(url) {
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

async function deleteFromCloudinary(publicId, env) {
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
    const result = await resp.json();
    if (result.result === "ok") {
      console.log("Cloudinary image deleted:", publicId);
      return true;
    }
    console.error("Cloudinary delete failed:", result);
    return false;
  } catch (e) {
    console.error("Cloudinary delete error:", e);
    return false;
  }
}

const DEFAULT_MODERATION_API_URL = "https://tool-veyr.onrender.com";

// Calls the dedicated NudeNet-based moderation service (much more reliable
// for this than repurposing a general vision LLM — no safety-refusal
// ambiguity, gives a real confidence score). Needs a publicly reachable
// image URL, not raw bytes.
async function pingModerationApi(env) {
  const apiUrl = env.MODERATION_API_URL || DEFAULT_MODERATION_API_URL;
  try {
    const resp = await fetch(`${apiUrl}/health`, { signal: AbortSignal.timeout(20000) });
    console.log(`pingModerationApi: ${resp.status}`);
  } catch (e) {
    console.log(`pingModerationApi failed (Render likely cold-starting): ${e.message}`);
  }
}

async function moderateImage(imageUrl, env) {
  const apiUrl = env.MODERATION_API_URL || DEFAULT_MODERATION_API_URL;
  if (!apiUrl) return { safe: true };

  // First attempt: short timeout, covers the common "already warm" case fast.
  // If that fails/times out, it's likely a Render cold start — retry once
  // with a much longer timeout instead of immediately treating it as unsafe.
  const attempts = [15000, 30000];
  let lastError = null;

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
        continue; // try again with longer timeout (or exit loop if last attempt)
      }
      const result = await resp.json();
      if (!result.safe) {
        console.log(`moderateImage: REJECTED (nsfw_score: ${result.nsfw_score}, detections: ${JSON.stringify(result.detections)})`);
      }
      return { safe: !!result.safe, nsfwScore: result.nsfw_score, detections: result.detections };
    } catch (e) {
      lastError = e.message;
      console.log(`moderateImage attempt ${i + 1} failed (likely Render cold start): ${e.message}`);
    }
  }

  // Every attempt failed to even get a verdict — this is NOT the same as a
  // confirmed-unsafe result. Still fail closed for safety, but flag it so
  // callers can retry later instead of treating it as a permanent rejection.
  console.error(`moderateImage: could not reach moderation API after ${attempts.length} attempts: ${lastError}`);
  return { safe: false, unreachable: true };
}

async function writeToFirebase(data, env) {
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
        addedAt: data.addedAt || Date.now(),
      }),
    });
    if (!resp.ok) {
      const err = await resp.text();
      console.error("Firebase write failed:", err);
      return null;
    }
    return await resp.json();
  } catch (e) {
    console.error("Firebase write error:", e);
    return null;
  }
}

// 🚀 MODIFIED: CPU time limit issue fixed by deferring heavy work to the cron job
async function handleWallpaperUpload(request, env, ctx) {
  try {
    const contentType = request.headers.get("Content-Type") || "";
    if (!contentType.includes("multipart/form-data")) {
      return createErrorResponse("Bad Request", "Content-Type must be multipart/form-data", 400);
    }

    const formData = await request.formData();
    const photo = formData.get("photo");
    const email = (formData.get("email") || "").toString().trim();
    const rawTitle = (formData.get("title") || "").toString().trim();
    const title = rawTitle.length >= 2 ? rawTitle : "Untitled";
    const rawCategoryInput = (formData.get("category") || "").toString().trim();
    const validCategory = AI_CATEGORY_GROUPS.find(
      (c) => c.toLowerCase() === rawCategoryInput.toLowerCase()
    );
    const rawCategory = validCategory || "";
    const photographer = (formData.get("photographer") || "").toString().trim();
    const uploaderId = (formData.get("uploader_id") || "").toString().trim();

    if (!email) {
      return createErrorResponse("Unauthorized", "Email is required", 401);
    }
    if (!await verifyEmailUser(email, env)) {
      return createErrorResponse("Unauthorized", "User not found", 401);
    }
    if (!checkRateLimit("wallpaper:" + email, email, env)) {
      return createErrorResponse("Too Many Requests", "Rate limit exceeded. Try again later.", 429);
    }

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

    // Upload original to Telegram immediately to get the persistent file_id
    const tgFormData = new FormData();
    tgFormData.append("chat_id", env.TELEGRAM_CHAT_ID);
    tgFormData.append("photo", photo);

    let tgResponse;
    try {
      tgResponse = await fetch(
        `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendPhoto`,
        { method: "POST", body: tgFormData, signal: AbortSignal.timeout(20000) }
      );
    } catch (e) {
      return createErrorResponse("Telegram upload failed", e.message, 502);
    }

    if (!tgResponse.ok) {
      const err = await tgResponse.text();
      console.error("Telegram sendPhoto failed:", err);
      
      // NEW: Catch the dimension error cleanly
      if (err.includes("PHOTO_INVALID_DIMENSIONS")) {
        return createErrorResponse("Invalid Image Dimensions", "Image resolution is too high. The combined width and height must be less than 10,000 pixels.", 400);
      }
      
      return createErrorResponse("Telegram upload failed", err.substring(0, 200), 502);
    }

    const tgResult = await tgResponse.json();
    if (!tgResult.ok) {
      return createErrorResponse("Telegram error", tgResult.description, 502);
    }

    const photos = tgResult.result.photo;
    const largestPhoto = photos[photos.length - 1];
    const fileId = largestPhoto.file_id;
    const fileUniqueId = largestPhoto.file_unique_id;
    const imgWidth = largestPhoto.width || 0;
    const imgHeight = largestPhoto.height || 0;
    const messageId = tgResult.result.message_id;

    if (await checkDuplicateByFileUniqueId(fileUniqueId, env)) {
      return createErrorResponse("Duplicate", "This image already exists in the database", 409);
    }

    const workerHost = env.WORKER_HOST || "server.rahulkumarbknv.workers.dev";
    const finalImageUrl = `https://${workerHost}/proxy-image?file_id=${encodeURIComponent(fileId)}&quality=full`;
    // Fallback thumbnail until cron uploads a real one to Cloudinary
    const initialThumbnailUrl = `https://${workerHost}/proxy-image?file_id=${encodeURIComponent(fileId)}&quality=low`;

    const initialPayload = {
      telegramFileId: fileId,
      fileUniqueId,
      imageUrl: finalImageUrl, 
      thumbnailUrl: initialThumbnailUrl,
      title,
      category,
      categorized: false, // 🚀 Changed to false so cron job picks it up
      categorizationAttempts: 0,
      photographer,
      addedAt: Date.now(),
      source: "User Uploaded",
      premium: false,
      width: imgWidth,
      height: imgHeight,
      uploaderId,
      chatId: env.TELEGRAM_CHAT_ID, // Added: Needed for moderation deletion
      messageId                     // Added: Needed for moderation deletion
    };

    let firebaseKey = null;
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
        }).catch((e) => console.error("Firebase id patch failed:", e));

        // 🚀 Queue it up for the cron job to handle Moderation, AI Category, and Cloudinary
        ctx.waitUntil((async () => {
          await fetch(`${env.FIREBASE_DATABASE_URL}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, {
            method: "PUT",
            headers: { "Content-Type": "application/json" },
            body: JSON.stringify({ 
              firebaseKey, 
              fileUniqueId, 
              imageUrl: finalImageUrl, 
              chatId: env.TELEGRAM_CHAT_ID, 
              messageId, 
              queuedAt: Date.now(),
              uploaderId: uploaderId,
              skipAI: !needsAutoCategory // Don't run AI if user gave a category manually
            })
          }).catch((e) => console.error("Failed to queue pending item:", e));
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
  } catch (error) {
    console.error("Wallpaper upload handler error:", error);
    return createErrorResponse("Upload failed", error.message, 500);
  }
}

// Uploads a photo to Cloudinary and returns { url, error }. Never throws —
// callers can Promise.all this alongside other independent work.
async function uploadToCloudinary(photo, env, folder) {
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
    const cloudResult = await cloudResp.json();
    return { url: cloudResult.secure_url, error: null };
  } catch (e) {
    console.error("Cloudinary catch error:", e);
    return { url: "", error: e.message };
  }
}

const IMAGE_MIME_TYPES = ["image/jpeg", "image/png", "image/webp", "image/gif", "image/bmp"];
const MAX_FILE_SIZE = 18 * 1024 * 1024; // 18 MB — Telegram bot API limit is 20 MB

async function handleTelegramWebhook(request, env, ctx) {
  try {
    const update = await request.json();
    const msg = update.message;
    if (!msg) return new Response("OK");

    const chat = msg.chat;
    const from = msg.from || {};
    const chatId = chat.id;
    const senderName = from.username || from.first_name || "Unknown";

    // --- Admin: /delete command (reply to an image message to remove it) ---
    if (msg.text && msg.text.trim() === "/delete" && msg.reply_to_message) {
      const adminId = env.ADMIN_CHAT_ID;
      if (!adminId || String(chatId) !== String(adminId)) {
        await replyToChat(chatId, "Unauthorized.", env);
        return new Response("OK");
      }
      const replied = msg.reply_to_message;
      let fileUniqueId = null;
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
      const indexData = await indexResp.json();
      const firebaseKey = indexData?.firebaseKey;
      if (!firebaseKey) {
        await replyToChat(chatId, "Image not found in database.", env);
        return new Response("OK");
      }
      const recordResp = await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`);
      const record = await recordResp.json();
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

    // --- Extract photo info (photo array or document) ---
    let file_id, file_unique_id, width = 0, height = 0;

    if (msg.photo) {
      // Sent as compressed photo
      const largest = msg.photo[msg.photo.length - 1];
      file_id = largest.file_id;
      file_unique_id = largest.file_unique_id;
      width = largest.width || 0;
      height = largest.height || 0;
    } else if (msg.document && IMAGE_MIME_TYPES.includes(msg.document.mime_type)) {
      // Sent as file (original quality) — check size
      if (msg.document.file_size > MAX_FILE_SIZE) {
        await replyToChat(chatId, "File too large! Max 18 MB. Send as compressed photo instead.", env);
        return new Response("OK");
      }
      file_id = msg.document.file_id;
      file_unique_id = msg.document.file_unique_id;
      // NOTE: Telegram's Document object has no width/height field (unlike Photo).
      // Read the actual image bytes to get real dimensions.
      const dims = await getTelegramFileDimensions(file_id, env);
      width = dims.width;
      height = dims.height;
    } else {
      return new Response("OK");
    }

    const adminId = env.ADMIN_CHAT_ID;
    if (adminId && String(chatId) !== String(adminId)) {
      await replyToChat(chatId, "Forbidden: you are not authorized to upload.", env);
      return new Response("OK");
    }

    // --- Duplicate check via file_unique_id ---
    const isDuplicate = await checkDuplicateByFileUniqueId(file_unique_id, env);
    if (isDuplicate) {
      await replyToChat(chatId, "This image already exists (duplicate).", env);
      return new Response("OK");
    }

    // --- Build URLs via the proxy worker ---
    const workerHost = env.WORKER_HOST || "server.rahulkumarbknv.workers.dev";
    const imageUrl = `https://${workerHost}/proxy-image?file_id=${encodeURIComponent(file_id)}&quality=full`;
    const thumbnailUrl = `https://${workerHost}/proxy-image?file_id=${encodeURIComponent(file_id)}&quality=low`;

    const messageId = msg.message_id;
    const uploaderId = String(chatId);

    if (await isOverLimit(uploaderId, env)) {
      await replyToChat(chatId, "Too many uploads in queue. Please wait.", env);
      return new Response("OK");
    }

    const payload = {
      telegramFileId: file_id,
      fileUniqueId: file_unique_id,
      imageUrl,
      thumbnailUrl,
      title: chat.title || "Telegram Upload",
      category: "General",
      categorized: false, // flips to true once background/cron processing succeeds or gives up
      categorizationAttempts: 0,
      source: "Telegram Bot",
      photographer: senderName,
      width,
      height,
      chatId,
      messageId,
      uploaderId: uploaderId,
      addedAt: Date.now(),
      premium: false
    };

    if (!env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) {
      console.error("Firebase not configured — skipping save");
      await replyToChat(chatId, "Firebase not configured on the server.", env);
      return new Response("OK");
    }

    // --- Write to wallpapers/newly_added ---
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

    const pushResult = await pushResp.json();
    const firebaseKey = pushResult.name;

    // Always queue to pending_processing for the cron job to handle
    // (moderation, Cloudinary upload, AI categorization). The inline and
    // waitUntil paths caused "CPU time limit" and "waitUntil cancelled"
    // errors when multiple rapid uploads each waited 15+ seconds for the
    // moderation API. The cron job processes at most 10 items per tick
    // and respects exponential backoff, so it's safe from timeouts.
    const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
    const waitMsgId = await replyToChat(chatId, "Processing image, please wait...", env);
    await fetch(`${env.FIREBASE_DATABASE_URL}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ firebaseKey, fileUniqueId: file_unique_id, imageUrl, chatId, messageId, waitMsgId, queuedAt: Date.now(), uploaderId: uploaderId, skipAI: false })
    }).catch((e) => console.error("Failed to queue pending item:", e));
    return new Response("OK");
  } catch (error) {
    console.error("Telegram webhook error:", error);
    return new Response("OK");
  }
}

// Shared by both the webhook's immediate path and the cron retry job:
// downloads a small resized copy (not the full-res original — a 10MB+ photo
// blows past Cloudinary's free-tier upload limit and spreading it into a
// byte array for the AI model burns way too much CPU time), uploads that to
// Cloudinary, runs AI categorization on it, and patches the Firebase record.
// 🚀 MODIFIED: Added skipAI param
async function processWallpaperAssets(firebaseKey, fileUniqueId, imageUrl, chatId, messageId, env, skipAI = false, waitMsgId = null) {
  const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
  const base = env.FIREBASE_DATABASE_URL;

  // Moderate first, using the Telegram proxy URL directly — no need to fetch
  // or upload anything if the image gets rejected.
  const { safe, unreachable } = await moderateImage(imageUrl, env);
  if (!safe && unreachable) {
    // Couldn't get a real verdict (moderation API down/cold-starting even
    // after retries) — this is NOT a confirmed rejection. Requeue for the
    // cron job to try again shortly, rather than deleting a legit upload.
    console.log(`processWallpaperAssets: moderation API unreachable, requeueing ${firebaseKey}`);
    await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, {
      method: "PUT",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({
        firebaseKey, fileUniqueId, imageUrl, chatId, messageId, skipAI,
        moderationOnly: true, queuedAt: Date.now(), retryAfter: Date.now() + 3 * 60 * 1000
      })
    }).catch((e) => console.error("Failed to queue moderation retry:", e));
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

  let cloudinaryUrl = null;
  if (env.CLOUDINARY_CLOUD_NAME && env.CLOUDINARY_UPLOAD_PRESET) {
    try {
      const existingResp = await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`);
      if (existingResp.ok) {
        const existing = await existingResp.json();
        if (existing?.thumbnailUrl && typeof existing.thumbnailUrl === "string" && existing.thumbnailUrl.includes("cloudinary")) {
          cloudinaryUrl = existing.thumbnailUrl;
          console.log(`Cloudinary URL already exists for ${firebaseKey}, skipping re-upload`);
        }
      }
    } catch (e) {
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
          const cdResult = await cdResp.json();
          cloudinaryUrl = cdResult.secure_url;
        } else {
          const errText = await cdResp.text().catch(() => "<unreadable>");
          console.error("Cloudinary bg upload failed with status:", cdResp.status, errText.substring(0, 300));
        }
      } catch (e) {
        console.error("Cloudinary bg upload failed:", e);
      }
    }
  }

  const patchData = { id: firebaseKey };
  if (cloudinaryUrl) {
    patchData.thumbnailUrl = cloudinaryUrl.replace(
      "/upload/",
      "/upload/c_fill,w_480,h_854,q_auto,f_auto/"
    );
  }

  // 🚀 MODIFIED: Only run AI if binding exists AND skipAI is false
  if (env.AI && !skipAI) {
    const { category, quotaExceeded } = await categorizeImage(imageBuffer, env);
    if (quotaExceeded) {
      // Daily free neuron allocation used up — don't give up permanently.
      // Save what we have (thumbnail) and requeue just the categorization
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
      const retryAfter = Date.now() + msUntilNextUtcMidnight() + 5 * 60 * 1000; // +5 min buffer
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
    patchData.categorized = true; // no AI configured or category provided manually
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

// Lightweight retry used by the cron job when a wallpaper's thumbnail is
// already done but categorization was deferred due to the daily neuron
// quota. Skips Cloudinary entirely — just re-attempts categorization.
async function retryCategorizationOnly(firebaseKey, imageUrl, env) {
  const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
  const base = env.FIREBASE_DATABASE_URL;

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

const PENDING_MAX_AGE_MS = 20 * 60 * 1000; // give up after 20 min of retries
const MAX_PENDING_PER_TICK = 5; // free tier: 50 subrequest limit, each item needs ~8-10

// Cron entry point (see wrangler.jsonc triggers.crons). Retries any wallpapers
// whose image wasn't ready yet when the webhook first ran.
async function processPendingWallpapers(env) {
  if (!env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) return;
  const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
  const base = env.FIREBASE_DATABASE_URL;

  const listResp = await fetch(`${base}/wallpapers/pending_processing.json?${auth}`);
  if (!listResp.ok) return;
  const pending = await listResp.json();
  if (!pending) return;

  // Track moderation API health across items in this tick. If it's unreachable
  // for one item, skip moderation for the rest — no point hammering it and
  // burning CPU on more timeouts just to requeue everything anyway.
  let moderationUnreachable = false;

  const entries = Object.entries(pending).slice(0, MAX_PENDING_PER_TICK);
  for (const [firebaseKey, item] of entries) {
    try {
      // Not time yet (either a categoryOnly item waiting on quota reset, or
      // any item with an explicit retryAfter) — skip until then.
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
        // Moderation API was down in this tick — skip and requeue with a
        // longer backoff instead of eating CPU on another timeout cycle.
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
          // result === false: moderation API unreachable — mark it so
          // remaining items skip moderation and get requeued fast.
          moderationUnreachable = true;
        }
      } else if (Date.now() - (item.queuedAt || 0) > PENDING_MAX_AGE_MS) {
        // Give up — leave category as "General", stop retrying
        console.error(`Giving up on ${firebaseKey} after ${PENDING_MAX_AGE_MS / 60000} min`);
        await fetch(`${base}/wallpapers/newly_added/${firebaseKey}.json?${auth}`, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ categorized: true })
        }).catch((e) => console.error("Failed to mark abandoned item categorized:", e));
        await fetch(`${base}/wallpapers/pending_processing/${firebaseKey}.json?${auth}`, { method: "DELETE" });
      }
    } catch (e) {
      console.error(`Error processing pending wallpaper ${firebaseKey}:`, e);
    }
  }
}

// Reads just enough of the Telegram file to parse image width/height from
// the file header, without downloading the whole image.
async function getTelegramFileDimensions(fileId, env) {
  try {
    const fileInfoResp = await fetch(
      `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/getFile?file_id=${fileId}`,
      { signal: AbortSignal.timeout(8000) }
    );
    const fileInfo = await fileInfoResp.json();
    if (!fileInfo.ok) return { width: 0, height: 0 };

    const fileUrl = `https://api.telegram.org/file/bot${env.TELEGRAM_BOT_TOKEN}/${fileInfo.result.file_path}`;
    // First 64KB is enough for JPEG/PNG/WebP/GIF headers in the vast majority of cases.
    const resp = await fetch(fileUrl, {
      headers: { Range: "bytes=0-65535" },
      signal: AbortSignal.timeout(8000)
    });
    const buf = new Uint8Array(await resp.arrayBuffer());
    return parseImageDimensions(buf);
  } catch (e) {
    console.error("getTelegramFileDimensions failed:", e);
    return { width: 0, height: 0 };
  }
}

function parseImageDimensions(buf) {
  const view = new DataView(buf.buffer, buf.byteOffset, buf.byteLength);

  // PNG: signature + IHDR chunk holds width/height at fixed offsets
  if (buf.length >= 24 && view.getUint32(0) === 0x89504e47 && view.getUint32(4) === 0x0d0a1a0a) {
    return { width: view.getUint32(16), height: view.getUint32(20) };
  }

  // GIF: little-endian width/height right after the 6-byte signature
  if (buf.length >= 10 && buf[0] === 0x47 && buf[1] === 0x49 && buf[2] === 0x46) {
    return { width: view.getUint16(6, true), height: view.getUint16(8, true) };
  }

  // WebP (VP8 / VP8L / VP8X inside RIFF container)
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

  // JPEG: walk the marker segments until we hit an SOF marker
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
  "Nature",
  "Mountains",
  "Forest",
  "Flowers",
  "Beach",
  "Ocean",
  "Waterfall",
  "Desert",
  "Sky",
  "Sunset",

  "Space",
  "Galaxy",
  "Planets",
  "Stars",
  "Moon",

  "Abstract",
  "Minimal",
  "Gradient",
  "Geometric",
  "Texture",

  "Dark",
  "AMOLED",
  "Neon",
  "Cyberpunk",
  "Fantasy",

  "Anime",
  "Gaming",
  "Movies",
  "Superheroes",
  "Cartoons",

  "Cars",
  "Motorcycles",
  "Aircraft",
  "Ships",
  "Trains",

  "Animals",
  "Birds",
  "Cats",
  "Dogs",
  "Wildlife",

  "Architecture",
  "Cityscape",
  "Technology",
  "Robots",
  "AI",

  "Sports",
  "Music",
  "Food",
  "Travel",
  "People",

  "Quotes",
  "Art",
  "Macro",
  "Vintage",
  "Aesthetic",

  "Fire",
  "Ice",
  "Rain",
  "Snow",
  "Underwater"
];

const DEFAULT_CATEGORY = "General";
// Compact category groups sent to the AI — keeps prompt small for free-tier
// Workers AI context limits while still covering all 1000 categories.
const AI_CATEGORY_GROUPS = WALLPAPER_CATEGORIES;
function arrayBufferToBase64(buf) {
  const bytes = new Uint8Array(buf);
  const chunks = [];
  for (let i = 0; i < bytes.length; i += 8192)
    chunks.push(String.fromCharCode(...bytes.subarray(i, i + 8192)));
  return btoa(chunks.join(""));
}

async function categorizeImage(imageBuffer, env) {
  if (!env.AI) return { category: DEFAULT_CATEGORY, quotaExceeded: false };
  try {
    const categories = AI_CATEGORY_GROUPS.join(", ");
    const result = await env.AI.run("@cf/moondream/moondream3.1-9B-A2B", {
      image: `data:image/jpeg;base64,${arrayBufferToBase64(imageBuffer)}`,
      question: `What category best describes this wallpaper? Choose exactly one word from: ${categories}. Reply with only the category name.`,
      max_tokens: 60,
      stream: false
    });
    console.log(`categorizeImage keys: ${Object.keys(result).join(",")}`);
    const nested = result.result || result;
    const raw = (nested.answer || nested.caption || nested.response || nested.result?.answer || JSON.stringify(result)).trim();
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
  } catch (e) {
    const quotaExceeded = /daily free allocation|10,000 neurons/i.test(e.message || "");
    console.error("categorizeImage failed:", e.message, quotaExceeded ? "(daily neuron quota exceeded)" : "");
    return { category: DEFAULT_CATEGORY, quotaExceeded };
  }
}

// Milliseconds until the next Workers AI neuron quota reset (00:00 UTC).
function msUntilNextUtcMidnight() {
  const now = new Date();
  const nextMidnight = Date.UTC(now.getUTCFullYear(), now.getUTCMonth(), now.getUTCDate() + 1);
  return nextMidnight - now.getTime();
}

const MAX_IMAGE_BUFFER = 6 * 1024 * 1024; // 8MB — Cloudinary free tier limit is 10MB

// Extracts file_id from proxy URL: ...?file_id=XXX&...
function extractFileIdFromUrl(url) {
  try {
    return new URL(url).searchParams.get("file_id") || null;
  } catch { return null; }
}

// Downloads a resized copy of the image for Cloudinary upload and AI
// categorization. Strategy:
//   1. Extract file_id from the proxy URL
//   2. Get direct Telegram download URL via Bot API
//   3. Pass that to images.weserv.nl for resizing (weserv CAN reach Telegram CDN)
//   4. Fallback: direct fetch via PROXY_WORKER binding if weserv fails
async function fetchResizedImage(imageUrl, env) {
  const fileId = extractFileIdFromUrl(imageUrl);

  // Try weserv.nl with direct Telegram URL first (gives us a resized image)
  if (fileId && env.TELEGRAM_BOT_TOKEN) {
    try {
      const fileInfoResp = await fetch(
        `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/getFile?file_id=${fileId}`,
        { signal: AbortSignal.timeout(8000) }
      );
      const fileInfo = await fileInfoResp.json();
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
    } catch (e) {
      console.error("fetchResizedImage: Telegram/weserv path failed:", e.message);
    }
  }

  // Fallback: direct fetch via PROXY_WORKER (may return full-res, large images)
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
  } catch (e) {
    console.error("fetchResizedImage: direct fallback failed:", e.message);
    return null;
  }
}

async function fetchImageWithRetry(url, env, retries = 3, delayMs = 2000) {
  for (let attempt = 1; attempt <= retries; attempt++) {
    try {
      const resp = env.PROXY_WORKER
        ? await env.PROXY_WORKER.fetch(url, { signal: AbortSignal.timeout(15000) })
        : await fetch(url, { signal: AbortSignal.timeout(15000) });
      if (resp.ok) return resp;
      const bodyText = await resp.clone().text().catch(() => "<unreadable>");
      console.log(`fetchImageWithRetry attempt ${attempt}: status ${resp.status}, body: ${bodyText.substring(0, 300)}`);
    } catch (e) {
      console.log(`fetchImageWithRetry attempt ${attempt}: ${e.message}`);
    }
    if (attempt < retries) await new Promise((r) => setTimeout(r, delayMs * (2 ** (attempt - 1))));
  }
  return null;
}

async function replyToChat(chatId, text, env) {
  if (!env.TELEGRAM_BOT_TOKEN) return null;
  try {
    const resp = await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/sendMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chatId, text })
    });
    const data = await resp.json();
    return data?.result?.message_id || null;
  } catch (e) {
    console.error("sendMessage failed:", e);
    return null;
  }
}

async function deleteTelegramMessage(chatId, messageId, env) {
  if (!env.TELEGRAM_BOT_TOKEN || !chatId || !messageId) return;
  try {
    await fetch(`https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/deleteMessage`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ chat_id: chatId, message_id: messageId })
    });
  } catch (e) {
    console.error("deleteMessage failed:", e);
  }
}

async function checkDuplicateByFileUniqueId(fileUniqueId, env) {
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

async function isOverLimit(uploaderId, env) {
  if (!uploaderId || !env.FIREBASE_DATABASE_URL || !env.FIREBASE_DATABASE_SECRET) return false;
  try {
    const auth = `auth=${env.FIREBASE_DATABASE_SECRET}`;
    const listResp = await fetch(`${env.FIREBASE_DATABASE_URL}/wallpapers/pending_processing.json?${auth}`);
    if (!listResp.ok) return false;
    const pending = await listResp.json();
    if (!pending) return false;
    const count = Object.values(pending).filter(item => item.uploaderId === uploaderId).length;
    return count >= 20;
  } catch (e) {
    console.error("isOverLimit check failed:", e);
    return false;
  }
}

export { src_default as default };
