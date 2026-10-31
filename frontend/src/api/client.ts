import type { Problem } from "./types";

const BASE_URL = import.meta.env.VITE_API_URL ?? "";

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

interface RequestOptions {
  method?: string;
  body?: unknown;
  signal?: AbortSignal;
}

async function request<T>(path: string, options: RequestOptions = {}): Promise<T> {
  const { method = "GET", body, signal } = options;

  const controller = new AbortController();
  const timeout = setTimeout(() => controller.abort(), 15_000);

  if (signal) {
    signal.addEventListener("abort", () => controller.abort());
  }

  try {
    const response = await fetch(`${BASE_URL}${path}`, {
      method,
      headers: body ? { "Content-Type": "application/json" } : undefined,
      body: body ? JSON.stringify(body) : undefined,
      signal: controller.signal,
    });

    if (!response.ok) {
      let problem: Problem | undefined;
      try {
        problem = await response.json();
      } catch {
        // тело не JSON — оставляем status
      }
      throw new ApiError(response.status, problem);
    }

    return await response.json();
  } catch (err) {
    if (err instanceof ApiError) throw err;
    if (err instanceof DOMException && err.name === "AbortError") {
      throw new TimeoutError();
    }
    throw new NetworkError();
  } finally {
    clearTimeout(timeout);
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
