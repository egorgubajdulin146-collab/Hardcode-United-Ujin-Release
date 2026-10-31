import { useState, useCallback, useRef, useEffect, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { SessionContext } from "./sessionContext";
import { clearCsrf, fetchCsrf } from "./authClient";

export function SessionProvider({ children }: { children: ReactNode }) {
  const [authed, setAuthed] = useState(false);
  const [checking, setChecking] = useState(true);
  const [checkFailed, setCheckFailed] = useState(false);
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

  // Проверка сессии с таймаутом
  const checkSession = useCallback(async () => {
    setChecking(true);
    setCheckFailed(false);
    const controller = new AbortController();
    const timeout = setTimeout(() => controller.abort(), 10_000);

    try {
      await fetchCsrf();
      const resp = await fetch("/api/v1/devices?size=1", {
        credentials: "include",
        signal: controller.signal,
      });
      if (resp.status === 200) {
        setAuthed(true);
      }
    } catch {
      setCheckFailed(true);
    } finally {
      clearTimeout(timeout);
      setChecking(false);
    }
  }, []);

  useEffect(() => {
    checkSession();
  }, [checkSession]);

  return (
    <SessionContext.Provider
      value={{ authed, checking, checkFailed, checkSession, sessionEpoch, setAuthed, terminateSession, onLogout, onLogin }}
    >
      {children}
    </SessionContext.Provider>
  );
}
