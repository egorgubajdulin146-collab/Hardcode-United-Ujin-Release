import type { Problem } from "./types";

const BASE_URL = import.meta.env.VITE_API_URL ?? "";

// CSRF-токен для записи (POST/PUT/DELETE).
// Устанавливается из authClient после GET /api/auth/csrf.
let csrfToken: string | null = null;

export function setCsrfToken(token: string | null): void {
  csrfToken = token;
}

export function getCsrfToken(): string | null {
  return csrfToken;
}

export class ApiError extends Error {
  constructor(
    public status: number,
    public problem?: Problem
  ) {
    super(problem?.detail ?? `HTTP ${status}`);
    this.name = "ApiError";
  }
}

export class NetworkError extends Error {
  constructor(message = "Сервер недоступен") {
    super(message);
    this.name = "NetworkError";
  }
}

export class TimeoutError extends Error {
  constructor() {
    super("Превышено время ожидания");
    this.name = "TimeoutError";
  }
}

export class CancelledError extends Error {
  constructor() {
    super("Запрос отменён");
    this.name = "CancelledError";
  }
}

interface RequestOptions {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
}

const WRITE_METHODS = new Set(["POST", "PUT", "DELETE", "PATCH"]);

function isCancelled(signal?: AbortSignal): boolean {
  return signal?.aborted ?? false;
}

async function request<T>(
  path: string,
  options: RequestOptions = {}
): Promise<T> {
  const { method = "GET", body, signal } = options;

  if (isCancelled(signal)) {
    throw new CancelledError();
  }

  const controller = new AbortController();
  const timeoutMs = 15_000;
  let timedOut = false;

  const timeout = setTimeout(() => {
    timedOut = true;
    controller.abort();
  }, timeoutMs);

  const onAbort = () => controller.abort();
  signal?.addEventListener("abort", onAbort);

  // Формируем заголовки
  const headers: Record<string, string> = {};
  if (body) {
    headers["Content-Type"] = "application/json";
  }
  // CSRF-токен для методов записи
  if (WRITE_METHODS.has(method) && csrfToken) {
    headers["X-XSRF-TOKEN"] = csrfToken;
  }

  try {
    const response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: Object.keys(headers).length > 0 ? headers : undefined,
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
      credentials: "include",
    });

    // 204 — успешный ответ без тела
    if (response.status === 204) {
      return undefined as T;
    }

    if (!response.ok) {
      let problem: Problem | undefined;
      try {
        problem = await response.json();
      } catch {
        // тело не JSON
      }
      throw new ApiError(response.status, problem);
    }

    return await response.json();
  } catch (err) {
    if (err instanceof ApiError) throw err;

    if (err instanceof DOMException && err.name === "AbortError") {
      if (timedOut) throw new TimeoutError();
      throw new CancelledError();
    }

    if (err instanceof TypeError) {
      throw new NetworkError();
    }

    throw err;
  } finally {
    clearTimeout(timeout);
    signal?.removeEventListener("abort", onAbort);
  }
}

export const api = {
  get: <T>(path: string, signal?: AbortSignal) =>
    request<T>(path, { signal }),
  post: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "POST", body }),
  put: <T>(path: string, body: unknown) =>
    request<T>(path, { method: "PUT", body }),
  delete: (path: string) =>
    request<void>(path, { method: "DELETE" }),
};
