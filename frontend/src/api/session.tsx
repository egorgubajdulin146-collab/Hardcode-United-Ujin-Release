import { useState, useCallback, useRef, useEffect, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { SessionContext } from "./sessionContext";
import { clearCsrf, fetchCsrf } from "./authClient";

export function SessionProvider({ children }: { children: ReactNode }) {
  const [authed, setAuthed] = useState(false);
  const [checking, setChecking] = useState(true);
  const queryClient = useQueryClient();
  const epochRef = useRef(0);
  const [sessionEpoch, setSessionEpoch] = useState(0);

  const bumpEpoch = useCallback(() => {
    epochRef.current += 1;
    setSessionEpoch(epochRef.current);
  }, []);

  const terminateSession = useCallback(() => {
    bumpEpoch();
    setAuthed(false);
    clearCsrf();
    queryClient.cancelQueries();
    queryClient.clear();
  }, [bumpEpoch, queryClient]);

  const onLogout = useCallback(() => {
    terminateSession();
  }, [terminateSession]);

  const onLogin = useCallback(() => {
    bumpEpoch();
    setAuthed(true);
    queryClient.clear();
  }, [bumpEpoch, queryClient]);

  // Проверка сессии на сервере при загрузке
  useEffect(() => {
    let cancelled = false;

    async function checkSession() {
      try {
        // Получаем CSRF — если сервер отвечает, проверяем защищённый endpoint
        await fetchCsrf();
        const resp = await fetch("/api/v1/devices?size=1", {
          credentials: "include",
        });
        if (!cancelled) {
          if (resp.status === 200) {
            setAuthed(true);
          }
          // 401 — не авторизован, остаёмся на false
        }
      } catch {
        // Сервер недоступен — не авторизован
      } finally {
        if (!cancelled) {
          setChecking(false);
        }
      }
    }

    checkSession();
    return () => { cancelled = true; };
  }, []);

  return (
    <SessionContext.Provider
      value={{ authed, checking, sessionEpoch, setAuthed, terminateSession, onLogout, onLogin }}
    >
      {children}
    </SessionContext.Provider>
  );
}
