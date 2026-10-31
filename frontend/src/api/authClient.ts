import type { UjinCredentials, UjinAdapterSignalValue } from "./auth";

// Работа с cookie-сессией и CSRF по контракту 0.1.2-draft

let csrfToken: string | null = null;

export class AuthError extends Error {
  constructor(
    public kind: "invalid_credentials" | "forbidden" | "server_error" | "network" | "unknown",
    message: string
  ) {
    super(message);
    this.name = "AuthError";
  }
}

export async function fetchCsrf(): Promise<string> {
  const response = await fetch("/api/auth/csrf", {
    credentials: "include",
  });
  if (!response.ok) {
    throw new AuthError("server_error", `CSRF: HTTP ${response.status}`);
  }
  const token = await response.text();
  csrfToken = token;
  return token;
}

export async function login(credentials: UjinCredentials): Promise<void> {
  const token = csrfToken ?? (await fetchCsrf());
  try {
    const response = await fetch("/api/auth/login", {
      method: "POST",
      credentials: "include",
      headers: {
        "Content-Type": "application/json",
        "X-XSRF-TOKEN": token,
      },
      body: JSON.stringify(credentials),
    });
    if (response.status === 204) return;
    if (response.status === 401 || response.status === 403) {
      throw new AuthError("invalid_credentials", "Неверный логин или пароль");
    }
    if (response.status === 502 || response.status === 503) {
      throw new AuthError("server_error", "Сервис временно недоступен");
    }
    throw new AuthError("unknown", `Ошибка входа: HTTP ${response.status}`);
  } catch (err) {
    if (err instanceof AuthError) throw err;
    if (err instanceof TypeError) {
      throw new AuthError("network", "Сервер недоступен");
    }
    throw new AuthError("unknown", "Неизвестная ошибка");
  }
}

export async function logout(): Promise<void> {
  const token = csrfToken ?? (await fetchCsrf());
  try {
    const response = await fetch("/api/auth/logout", {
      method: "POST",
      credentials: "include",
      headers: {
        "X-XSRF-TOKEN": token,
      },
    });
    if (response.status === 204) {
      csrfToken = null;
      return;
    }
    throw new AuthError("unknown", `Ошибка выхода: HTTP ${response.status}`);
  } catch (err) {
    if (err instanceof AuthError) throw err;
    if (err instanceof TypeError) {
      throw new AuthError("network", "Сервер недоступен");
    }
    throw new AuthError("unknown", "Неизвестная ошибка");
  }
}

export async function readSignal(
  serialnumber: string,
  signalName: string
): Promise<UjinAdapterSignalValue> {
  try {
    const response = await fetch(
      `/api/v1/ujin/devices/${encodeURIComponent(serialnumber)}/signals?name=${encodeURIComponent(signalName)}`,
      { credentials: "include" }
    );
    if (response.status === 401) {
      throw new AuthError("invalid_credentials", "Требуется авторизация");
    }
    if (response.status === 403) {
      throw new AuthError("forbidden", "Нет доступа");
    }
    if (response.status === 404) {
      throw new AuthError("unknown", "Сигнал не найден");
    }
    if (response.status === 502 || response.status === 503) {
      throw new AuthError("server_error", "Upstream недоступен");
    }
    if (!response.ok) {
      throw new AuthError("unknown", `HTTP ${response.status}`);
    }
    return await response.json();
  } catch (err) {
    if (err instanceof AuthError) throw err;
    if (err instanceof TypeError) {
      throw new AuthError("network", "Сервер недоступен");
    }
    throw new AuthError("unknown", "Неизвестная ошибка");
  }
}

export function isAuthenticated(): boolean {
  return csrfToken !== null;
}

export function clearAuthState(): void {
  csrfToken = null;
}
