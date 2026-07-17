// src/api.js
let refreshPromise = null;

// Spring Security's CookieCsrfTokenRepository.withHttpOnlyFalse() writes a
// non-HttpOnly "XSRF-TOKEN" cookie (see CsrfCookieFilter.java) specifically so
// the frontend can read it and echo it back. Every unsafe request (anything
// but GET/HEAD/OPTIONS/TRACE) needs it in the "X-XSRF-TOKEN" header, or
// Spring's CsrfFilter rejects it with 403 before it even reaches the
// controller. Only the auth endpoints explicitly listed as
// ignoringRequestMatchers(...) in SecurityConfig are exempt.
function readCookie(name) {
  const match = document.cookie.match(new RegExp("(?:^|; )" + name + "=([^;]*)"));
  return match ? decodeURIComponent(match[1]) : null;
}

const CSRF_EXEMPT_PATHS = [
  "/api/auth/login",
  "/api/auth/refresh",
  "/api/auth/logout",
  "/api/auth/forgot-password",
  "/api/auth/reset-password",
];

export async function apiFetch(url, options = {}) {
  const method = (options.method || "GET").toUpperCase();
  const needsCsrf = !["GET", "HEAD", "OPTIONS", "TRACE"].includes(method)
    && !CSRF_EXEMPT_PATHS.some((p) => url.includes(p));

  const mergedOptions = {
    ...options,
    // The auth cookies (access_token/refresh_token/XSRF-TOKEN) are HttpOnly
    // or SameSite-scoped; without this the browser won't attach them, and
    // every request looks anonymous to the backend regardless of dev-proxy
    // same-origin setup.
    credentials: "include",
    headers: {
      "Content-Type": "application/json",
      ...(needsCsrf ? { "X-XSRF-TOKEN": readCookie("XSRF-TOKEN") || "" } : {}),
      ...options.headers,
    },
  };

  if (mergedOptions.body && typeof mergedOptions.body === "object") {
    mergedOptions.body = JSON.stringify(mergedOptions.body);
  }

  let response = await fetch(url, mergedOptions);

  // Intercept 401 Unauthorized for token refresh
  if (response.status === 401 && !url.includes('/api/auth/login') && !url.includes('/api/auth/refresh')) {
    if (!refreshPromise) {
      refreshPromise = fetch('/api/auth/refresh', { method: 'POST', credentials: 'include' }).finally(() => {
        refreshPromise = null;
      });
    }

    const refreshResponse = await refreshPromise;

    if (refreshResponse.ok) {
      // Token was successfully refreshed, retry the original request
      response = await fetch(url, mergedOptions);
    } else {
      // Refresh token is also expired or invalid, force logout/redirect
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
  }

  if (!response.ok) {
    if (response.status === 401 && !url.includes('/api/auth/login')) {
      if (window.location.pathname !== '/login') {
        window.location.href = '/login';
      }
    }
    const err = await response.json().catch(() => ({ message: response.statusText }));
    throw err;
  }

  // Return empty object on 204 No Content
  if (response.status === 204) return {};

  const contentType = response.headers.get("content-type");
  if (contentType && (contentType.includes("application/pdf") || contentType.includes("text/csv") || contentType.includes("application/octet-stream"))) {
    return response.blob();
  }

  const text = await response.text();
  if (!text) return {};
  try {
    return JSON.parse(text);
  } catch (e) {
    return text;
  }
}
