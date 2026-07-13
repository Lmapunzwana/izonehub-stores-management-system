import { serve } from "bun";
import { join } from "path";
import { statSync } from "fs";

const BACKEND_URL = process.env.BACKEND_URL || "http://backend:8080";
const PORT = process.env.PORT || 3000;

serve({
  port: PORT,
  async fetch(req) {
    const url = new URL(req.url);

    // Proxy API requests to the backend
    if (url.pathname.startsWith("/api")) {
      const targetUrl = new URL(url.pathname + url.search, BACKEND_URL);
      console.log(`Proxying ${req.method} ${url.pathname} to ${targetUrl}`);
      
      const headers = new Headers(req.headers);
      headers.set("Host", targetUrl.host); // Some backends require host header to match
      
      try {
        const proxyReq = new Request(targetUrl, {
          method: req.method,
          headers,
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
