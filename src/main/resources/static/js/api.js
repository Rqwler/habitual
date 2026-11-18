(() => {
  "use strict";

  const TOKEN_KEY = "habitual.token";
  const USER_KEY = "habitual.user";

  class ApiError extends Error {
    constructor(message, status, fieldErrors) {
      super(message);
      this.name = "ApiError";
      this.status = status;
      this.fieldErrors = fieldErrors || null;
    }
  }

  function getToken() {
    return localStorage.getItem(TOKEN_KEY);
  }

  function getUser() {
    const raw = localStorage.getItem(USER_KEY);
    if (!raw) return null;
    try {
      return JSON.parse(raw);
    } catch {
      return null;
    }
  }

  function setSession(auth) {
    localStorage.setItem(TOKEN_KEY, auth.token);
    localStorage.setItem(USER_KEY, JSON.stringify(auth.user));
  }

  function clearSession() {
    localStorage.removeItem(TOKEN_KEY);
    localStorage.removeItem(USER_KEY);
  }

  async function request(path, options = {}) {
    const { method = "GET", body, auth = true } = options;
    const headers = { Accept: "application/json" };
    if (body !== undefined) {
      headers["Content-Type"] = "application/json";
    }
    const token = getToken();
    if (auth && token) {
      headers.Authorization = `Bearer ${token}`;
    }

    let response;
    try {
      response = await fetch(`/api${path}`, {
        method,
        headers,
        body: body === undefined ? undefined : JSON.stringify(body),
      });
    } catch {
      throw new ApiError("Cannot reach the server. Please try again.", 0);
    }

    const text = await response.text();
    let payload = null;
    if (text) {
      try {
        payload = JSON.parse(text);
      } catch {
        payload = text;
      }
    }

    if (!response.ok) {
      const message =
        payload && typeof payload === "object" && payload.message
          ? payload.message
          : `Request failed (${response.status})`;
      const fieldErrors =
        payload && typeof payload === "object" ? payload.fieldErrors : null;

      if (response.status === 401 && auth) {
        clearSession();
        if (!/\/$|index\.html$/.test(window.location.pathname)) {
          window.location.replace("/?expired=1");
        }
      }
      throw new ApiError(message, response.status, fieldErrors);
    }

    return payload;
  }

  window.Habitual = {
    ApiError,
    getToken,
    getUser,
    setSession,
    clearSession,
    request,
    login(email, password) {
      return request("/auth/login", {
        method: "POST",
        body: { email, password },
        auth: false,
      });
    },
    register(payload) {
      return request("/auth/register", {
        method: "POST",
        body: payload,
        auth: false,
      });
    },
    me() {
      return request("/auth/me");
    },
    get(path) {
      return request(path);
    },
    post(path, body) {
      return request(path, { method: "POST", body });
    },
    put(path, body) {
      return request(path, { method: "PUT", body });
    },
    delete(path) {
      return request(path, { method: "DELETE" });
    },
  };
})();