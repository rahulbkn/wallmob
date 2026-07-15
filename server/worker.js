export default {
  async fetch(request, env) {
    return handleRequest(request, env.BOT_TOKEN, env.ALLOWED_ORIGINS);
  }
};

async function handleRequest(request, BOT_TOKEN, ALLOWED_ORIGINS) {
  const url = new URL(request.url);
  const path = url.pathname;

  const origin = request.headers.get('Origin') || '';
  const allowed = ALLOWED_ORIGINS ? ALLOWED_ORIGINS.split(',').map(s => s.trim()) : [];
  const allowOrigin = allowed.includes(origin) ? origin : '';

  const corsHeaders = {
    "Access-Control-Allow-Origin": allowOrigin || 'null',
    "Access-Control-Allow-Methods": "GET, POST, OPTIONS",
    "Access-Control-Allow-Headers": "Content-Type",
  };

  if (request.method === "OPTIONS") {
    return new Response(null, { headers: corsHeaders });
  }

  if (!BOT_TOKEN) {
    return errorResponse("SERVER CONFIG ERROR: BOT_TOKEN secret not set in Cloudflare dashboard", corsHeaders);
  }

  if (path === "/get-image") return getImageBothQualities(url, corsHeaders, BOT_TOKEN);
  if (path === "/get-multiple") return getMultipleImages(url, corsHeaders, BOT_TOKEN);
  if (path === "/proxy-image") return proxyImage(url, corsHeaders, BOT_TOKEN);

  return new Response(JSON.stringify({
    endpoints: {
      "/get-image?file_id=XXX": "Get low + full quality URLs",
      "/get-multiple?file_ids=XXX,YYY": "Multiple images",
      "/proxy-image?file_id=XXX&quality=low|full": "Stream image",
    }
  }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });
}

async function getImageBothQualities(url, corsHeaders, BOT_TOKEN) {
  const fileId = url.searchParams.get("file_id");
  if (!fileId) return errorResponse("Missing file_id", corsHeaders);

  try {
    const result = await fetchTelegramFile(fileId, BOT_TOKEN);
    if (!result.ok) return errorResponse("Telegram getFile Error: " + result.description, corsHeaders);

    const filePath = result.result.file_path;

    return new Response(JSON.stringify({
      ok: true,
      file_id: fileId,
      file_size: result.result.file_size,
      file_path: filePath,
      images: {
        low_quality: {
          url: `/proxy-image?file_id=${fileId}&quality=low`,
          description: "Compressed WebP (max 320px)",
        },
        full_quality: {
          url: `/proxy-image?file_id=${fileId}&quality=full`,
          description: "Original quality",
        },
      },
    }), { headers: { ...corsHeaders, "Content-Type": "application/json" } });

  } catch (err) {
    return errorResponse("Internal JSON Error: " + err.message, corsHeaders);
  }
}

async function getMultipleImages(url, corsHeaders, BOT_TOKEN) {
  const fileIdsParam = url.searchParams.get("file_ids");
  if (!fileIdsParam) return errorResponse("Missing file_ids", corsHeaders);

  const fileIds = fileIdsParam.split(",").map(id => id.trim()).filter(Boolean);
  if (fileIds.length > 20) return errorResponse("Max 20 file_ids", corsHeaders);

  const results = await Promise.all(fileIds.map(async (fileId) => {
    try {
      const result = await fetchTelegramFile(fileId, BOT_TOKEN);
      if (!result.ok) return { file_id: fileId, error: result.description };
      return {
        file_id: fileId,
        file_size: result.result.file_size,
        images: {
          low_quality: { url: `/proxy-image?file_id=${fileId}&quality=low` },
          full_quality: { url: `/proxy-image?file_id=${fileId}&quality=full` },
        },
      };
    } catch (err) {
      return { file_id: fileId, error: err.message };
    }
  }));

  return new Response(JSON.stringify({ ok: true, total: fileIds.length, results }),
    { headers: { ...corsHeaders, "Content-Type": "application/json" } });
}

async function proxyImage(url, corsHeaders, BOT_TOKEN) {
  const fileId = url.searchParams.get("file_id");
  const quality = url.searchParams.get("quality") || "full";
  if (!fileId) return errorResponse("Missing file_id parameter", corsHeaders);

  try {
    // Step 1: Get file path from Telegram
    const result = await fetchTelegramFile(fileId, BOT_TOKEN);

    // Yahan exact error return hoga agar Telegram reject karega
    if (!result.ok) {
      return errorResponse(`Telegram API Error (getFile): ${result.description}`, corsHeaders);
    }

    const filePath = result.result.file_path;
    const telegramUrl = `https://api.telegram.org/file/bot${BOT_TOKEN}/${filePath}`;

    if (quality === "low") {
      const wsrvUrl = `https://images.weserv.nl/?url=${encodeURIComponent(telegramUrl)}&w=320&q=40&output=webp`;
      const lowRes = await fetch(wsrvUrl);
      if (lowRes.ok) {
        return new Response(lowRes.body, {
          headers: {
            ...corsHeaders,
            "Content-Type": "image/webp",
            "Cache-Control": "public, max-age=86400",
            "X-Quality": "low",
            "X-Original-Size": String(result.result.file_size),
          },
        });
      }
    }

    // Full quality — fetch directly from Telegram
    const fullRes = await fetch(telegramUrl);

    // Yahan HTTP status ke sath exact error pata chalega
    if (!fullRes.ok) {
      const errorText = await fullRes.text();
      return errorResponse(`Telegram Download Failed (HTTP ${fullRes.status}): ${errorText}`, corsHeaders);
    }

    return new Response(fullRes.body, {
      headers: {
        ...corsHeaders,
        "Content-Type": fullRes.headers.get("content-type") || "image/jpeg",
        "Cache-Control": "public, max-age=86400",
        "X-Quality": "full",
        "X-File-Size": String(result.result.file_size),
      },
    });

  } catch (err) {
    return errorResponse("Proxy Code Error: " + err.message, corsHeaders);
  }
}

async function fetchTelegramFile(fileId, BOT_TOKEN) {
  const res = await fetch(`https://api.telegram.org/bot${BOT_TOKEN}/getFile?file_id=${fileId}`);
  return await res.json();
}

function errorResponse(message, corsHeaders) {
  return new Response(JSON.stringify({ ok: false, error: message }), {
    status: 400,
    headers: { ...corsHeaders, "Content-Type": "application/json" },
  });
}
