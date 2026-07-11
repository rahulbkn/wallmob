var src_default = {
  async fetch(request, env, ctx) {
    return await handleRequest(request, env, ctx);
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

var inFlightRequests = {};

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

    if (url.pathname === "/upload-wallpaper") {
      if (request.method !== "POST") {
        return createErrorResponse("Method Not Allowed", "Use POST to upload wallpaper", 405);
      }
      return await handleWallpaperUpload(request, env, ctx);
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
    const cacheKey = `wallmob:${params.keyword}:${params.page}:${params.perPage}:${params.source}`;

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
    const result = await fetchAllWallpapers(params.page, params.perPage, params.source, params.keyword, params.orientation, env);
    const payload = {
      meta: {
        success: true,
        page: params.page,
        per_page: params.perPage,
        count: result.wallpapers.length,
        total_available: result.totalAvailable,
        source_filter: params.source,
        search_keyword: params.keyword,
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
  if (inFlightRequests[cacheKey]) {
    return inFlightRequests[cacheKey];
  }
  const promise = fetchAllWallpapers(params.page, params.perPage, params.source, params.keyword, params.orientation, env).finally(() => {
    delete inFlightRequests[cacheKey];
  });
  inFlightRequests[cacheKey] = promise;
  return promise;
}

function parseQueryParams(url) {
  const page = Math.max(CONFIG.DEFAULT_PAGE, parseInt(url.searchParams.get("page") || CONFIG.DEFAULT_PAGE));
  const perPage = Math.min(CONFIG.MAX_PER_PAGE, Math.max(CONFIG.MIN_PER_PAGE, parseInt(url.searchParams.get("per_page") || CONFIG.DEFAULT_PER_PAGE)));
  const source = url.searchParams.get("source") || "all";
  const keyword = (url.searchParams.get("query") || url.searchParams.get("keyword") || url.searchParams.get("q") || "").trim();
  const orientation = url.searchParams.get("orientation") || "all";
  return { page, perPage, source, keyword, orientation };
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

async function fetchAllWallpapers(page, perPage, sourceFilter, keyword, orientation, env) {
  const sources = ["Unsplash", "Pexels", "Pixabay", "Wallhaven"];
  const activeSources = sourceFilter === "all" ? sources : sources.filter((s) => s.toLowerCase() === sourceFilter.toLowerCase());
  const itemsPerSource = Math.ceil(perPage / activeSources.length) + 15;
  const apiConfigs = [
    {
      name: "Unsplash",
      enabled: activeSources.includes("Unsplash") && env.UNSPLASH_KEY,
      url: `https://api.unsplash.com/search/photos?query=${encodeURIComponent(keyword)}&page=${page}&per_page=${itemsPerSource}`,
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
      url: `https://pixabay.com/api/?key=${env.PIXABAY_KEY || ""}&q=${encodeURIComponent(keyword)}&image_type=photo&min_width=${CONFIG.MIN_WIDTH}&min_height=${CONFIG.MIN_HEIGHT}&safesearch=true&page=${page}&per_page=${itemsPerSource}`,
      headers: {}
    },
    {
      name: "Wallhaven",
      enabled: activeSources.includes("Wallhaven") && env.WALLHAVEN_KEY,
      url: `https://wallhaven.cc/api/v1/search?q=${encodeURIComponent(keyword)}&categories=111&purity=100&atleast=1920x1080&page=${page}`,
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
      for (const wp of sourceData.items) {
        const w = wp.meta?.width || 0;
        const h = wp.meta?.height || 0;
        if (orientation === "landscape" && h >= w) continue;
        if (orientation === "portrait" && w > h) continue;
        const urlKey = wp.urls.regular || wp.urls.raw;
        if (!seen.has(urlKey)) {
          seen.add(urlKey);
          allWallpapers.push(wp);
        }
      }
      totalAvailable += sourceData.total;
      debug[enabledApis[index].name] = { status: "success", count: sourceData.items.length, deduped: sourceData.items.length - allWallpapers.length, total: sourceData.total };
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
    const resolution = w * h;
    if (w >= 3840 && h >= 2160) score += 50;
    else if (w >= 2560 && h >= 1440) score += 40;
    else if (w >= 1920 && h >= 1080) score += 30;
    else if (w >= 1440 && h >= 1920) score += 20;
    if (wp.stats?.likes && wp.stats.likes > 100) score += 15;
    else if (wp.stats?.likes && wp.stats.likes > 10) score += 5;
    if (wp.stats?.downloads && wp.stats.downloads > 1e4) score += 15;
    else if (wp.stats?.downloads && wp.stats.downloads > 1e3) score += 5;
    if (wp.meta?.dominant_color && wp.meta.dominant_color !== "#E0E0E0") score += 5;
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
  const pageSize = Math.min(perPage, 5);
  const groups = [];
  for (let i = 0; i < sorted.length; i += pageSize) {
    const group = sorted.slice(i, i + pageSize).sort(() => Math.random() - 0.5);
    groups.push(group);
  }
  const shuffled = groups.flat();
  return {
    wallpapers: shuffled.slice(0, perPage),
    errors,
    debug,
    totalAvailable
  };
}

async function fetchFromAPI(api, minWidth, minHeight) {
  const controller = new AbortController();
  const timeoutId = setTimeout(() => controller.abort(), CONFIG.REQUEST_TIMEOUT);
  try {
    const response = await fetch(api.url, { headers: api.headers, signal: controller.signal });
    clearTimeout(timeoutId);
    if (!response.ok) {
      const errorText = await response.text();
      console.error(`${api.name} returned ${response.status}: ${errorText}`);
      throw new Error(`HTTP ${response.status}: ${errorText.substring(0, 100)}`);
    }
    const data = await response.json();
    return normalizeData(api.name, data, minWidth, minHeight);
  } catch (error) {
    clearTimeout(timeoutId);
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
              downloads: item.downloads, likes: item.likes
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
                likes: item.likes,                 downloads: item.downloads
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
                author: "Wallhaven", author_url: item.url,                 favorites: item.favorites
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

function createWallpaperObject({ id, source, url, thumb, full, width, height, color, title, author, author_url, downloads, likes, favorites }) {
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
    stats: { downloads: downloads || null, likes: likes || null, favorites: favorites || null }
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

    const tgResult = await tgResponse.json();

    if (!tgResult.ok) {
      return createErrorResponse("Telegram error", tgResult.description, 502);
    }

    const photos = tgResult.result.photo;
    const largestPhoto = photos[photos.length - 1];
    const fileId = largestPhoto.file_id;

    const fileResponse = await fetch(
      `https://api.telegram.org/bot${env.TELEGRAM_BOT_TOKEN}/getFile?file_id=${fileId}`
    );
    const fileResult = await fileResponse.json();

    if (!fileResult.ok) {
      return createErrorResponse("Telegram file error", fileResult.description, 502);
    }

    const filePath = fileResult.result.file_path;
    const fileUrl = `https://api.telegram.org/file/bot${env.TELEGRAM_BOT_TOKEN}/${filePath}`;

    return new Response(JSON.stringify({
      success: true,
      url: fileUrl,
      file_id: fileId
    }), {
      headers: { "Content-Type": "application/json", ...CORS_HEADERS }
    });
  } catch (error) {
    console.error("Upload handler error:", error);
    return createErrorResponse("Upload failed", error.message, 500);
  }
}

function extractCloudinaryPublicId(url) {
  try {
    const u = new URL(url);
    const match = u.pathname.match(/\/upload\/v?\d+\/(.+)/);
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

async function moderateImage(imageUrl, env) {
  if (!env.MODERATION_API_URL) return null;
  try {
    const resp = await fetch(`${env.MODERATION_API_URL}/moderate`, {
      method: "POST",
      headers: { "Content-Type": "application/json" },
      body: JSON.stringify({ image_url: imageUrl }),
      signal: AbortSignal.timeout(15000),
    });
    if (!resp.ok) {
      console.error("Moderation API returned", resp.status);
      return null;
    }
    return await resp.json();
  } catch (e) {
    console.error("Moderation API call failed:", e);
    return null;
  }
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
        addedAt: Date.now(),
        source: "Firebase",
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

async function handleWallpaperUpload(request, env, ctx) {
  try {
    const contentType = request.headers.get("Content-Type") || "";
    if (!contentType.includes("multipart/form-data")) {
      return createErrorResponse("Bad Request", "Content-Type must be multipart/form-data", 400);
    }

    const formData = await request.formData();
    const photo = formData.get("photo");
    const title = formData.get("title") || "Untitled";
    const category = formData.get("category") || "General";
    const photographer = formData.get("photographer") || "";
    // NAYA: uploaderId catch kar rahe hain
    const uploaderId = formData.get("uploader_id") || ""; 

    if (!photo) {
      return createErrorResponse("Missing photo", "No photo file provided in 'photo' field", 400);
    }

    if (!env.TELEGRAM_BOT_TOKEN || !env.TELEGRAM_CHAT_ID) {
      return createErrorResponse("Server config error", "Telegram bot not configured", 500);
    }

    // 1. Upload to Cloudinary for thumbnail + moderation URL
    let cloudinaryUrl = "";
    let thumbnailUrl = "";
    let cloudinaryError = "Not attempted"; 

    if (env.CLOUDINARY_CLOUD_NAME && env.CLOUDINARY_UPLOAD_PRESET) {
      try {
        const cloudFormData = new FormData();
        cloudFormData.append("file", photo);
        cloudFormData.append("upload_preset", env.CLOUDINARY_UPLOAD_PRESET);
        // Added transformation parameter to ensure smaller image upload
        cloudFormData.append("transformation", "c_fill,w_480");

        const cloudResp = await fetch(
          `https://api.cloudinary.com/v1_1/${env.CLOUDINARY_CLOUD_NAME}/image/upload`,
          { method: "POST", body: cloudFormData }
        );

        if (cloudResp.ok) {
          const cloudResult = await cloudResp.json();
          cloudinaryUrl = cloudResult.secure_url;
          thumbnailUrl = cloudinaryUrl;
          cloudinaryError = null;
        } else {
          cloudinaryError = await cloudResp.text();
          console.error("Cloudinary upload failed with status:", cloudResp.status, cloudinaryError);
        }
      } catch (e) {
        cloudinaryError = e.message;
        console.error("Cloudinary catch error:", e);
      }
    } else {
      cloudinaryError = "Missing CLOUDINARY_CLOUD_NAME or CLOUDINARY_UPLOAD_PRESET in env variables";
    }

    // 2. Moderate via Cloudinary URL
    if (cloudinaryUrl && env.MODERATION_API_URL) {
      const modResult = await moderateImage(cloudinaryUrl, env);
      if (modResult && modResult.safe === false) {
        const publicId = extractCloudinaryPublicId(cloudinaryUrl);
        if (publicId) {
          ctx.waitUntil(deleteFromCloudinary(publicId, env));
        }
        return createErrorResponse("Content Rejected",
          `Image contains NSFW content (score: ${modResult.nsfw_score})`, 422);
      }
    }

    // 3. Upload to Telegram for original image URL
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
    // NAYA: Telegram result se width aur height automatically nikal li
    const imgWidth = largestPhoto.width || 0;
    const imgHeight = largestPhoto.height || 0;

    const finalImageUrl = `https://server.rahulkumarbknv.workers.dev/proxy-image?file_id=${encodeURIComponent(fileId)}&quality=full`;
    const finalThumbnailUrl = thumbnailUrl ? thumbnailUrl : `https://server.rahulkumarbknv.workers.dev/proxy-image?file_id=${encodeURIComponent(fileId)}&quality=low`;

    // 4. Write to Firebase Realtime Database
    const initialPayload = {
      telegramFileId: fileId,
      thumbnailUrl: finalThumbnailUrl,
      title,
      category,
      photographer,
      addedAt: Date.now(),
      source: "User Uploaded",
      premium: true,
      width: imgWidth,
      height: imgHeight,
      uploaderId: uploaderId // NAYA: App se received email id add kar diya
    };
    
    let firebaseKey = null;

    if (env.FIREBASE_DATABASE_URL && env.FIREBASE_DATABASE_SECRET) {
      const fbResult = await writeToFirebase(initialPayload, env);
      
      if (fbResult && fbResult.name) {
        firebaseKey = fbResult.name;
        
        const updateRef = `${env.FIREBASE_DATABASE_URL}/wallpapers/newly_added/${firebaseKey}.json?auth=${env.FIREBASE_DATABASE_SECRET}`;
        
        await fetch(updateRef, {
          method: "PATCH",
          headers: { "Content-Type": "application/json" },
          body: JSON.stringify({ 
            id: firebaseKey, 
            imageUrl: finalImageUrl 
          }),
        });
      }
    }

    return new Response(JSON.stringify({
      success: true,
      id: firebaseKey,
      telegramFileId: fileId,
      imageUrl: finalImageUrl,
      thumbnailUrl: finalThumbnailUrl,
      debug_cloudinary_error: cloudinaryError
    }), {
      headers: { "Content-Type": "application/json", ...CORS_HEADERS }
    });
  } catch (error) {
    console.error("Wallpaper upload handler error:", error);
    return createErrorResponse("Upload failed", error.message, 500);
  }
}

export { src_default as default };
