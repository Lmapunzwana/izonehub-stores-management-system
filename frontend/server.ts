import { serve } from "bun";
import { join } from "path";
import { statSync } from "fs";

const BACKEND_URL = process.env.BACKEND_URL || "http://backend:8080";
const PORT = process.env.PORT || 3000;

/**
 * Headers that the proxy is allowed to forward to the backend.
 *
 * All other client-supplied headers are stripped before forwarding.
 * This prevents clients from injecting X-Forwarded-For, X-Real-IP, or
 * other headers that could bypass backend rate limiting or IP resolution.
 */
const ALLOWED_REQUEST_HEADERS = new Set([
  "content-type",
  "accept",
  "accept-encoding",
  "accept-language",
  "cookie",           // HttpOnly JWT cookies
  "x-xsrf-token",    // CSRF token
  "range",            // for file/range requests
]);

serve({
  port: PORT,
  async fetch(req) {
    const url = new URL(req.url);

    // Proxy API requests to the backend
    if (url.pathname.startsWith("/api")) {
      const targetUrl = new URL(url.pathname + url.search, BACKEND_URL);

      // Build a clean, whitelisted header set — never forward arbitrary
      // client-supplied headers to the backend (prevents X-Forwarded-For spoofing).
      const proxiedHeaders = new Headers();
      req.headers.forEach((value, key) => {
        if (ALLOWED_REQUEST_HEADERS.has(key.toLowerCase())) {
          proxiedHeaders.set(key, value);
        }
      });
      proxiedHeaders.set("Host", targetUrl.host);

      try {
        const proxyReq = new Request(targetUrl, {
          method: req.method,
          headers: proxiedHeaders,
          body: req.body,
          redirect: "manual",
        });

        return await fetch(proxyReq);
      } catch (err) {
        console.error("Proxy error:", err);
        return new Response("Bad Gateway", { status: 502 });
      }
    }

    // Serve static files from dist
    let filePath = join(process.cwd(), "dist", url.pathname);

    try {
      const stat = statSync(filePath);
      if (stat.isDirectory()) {
        filePath = join(filePath, "index.html");
      }
    } catch {
      // Fallback to index.html for SPA routing
      filePath = join(process.cwd(), "dist", "index.html");
    }

    const file = Bun.file(filePath);
    if (await file.exists()) {
      return new Response(file);
    }

    return new Response("Not Found", { status: 404 });
  }
});

console.log(`Bun server running on http://localhost:${PORT}`);
