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
  const checkRef = useRef<AbortController | null>(null);
  const [sessionEpoch, setSessionEpoch] = useState(0);

  const bumpEpoch = useCallback(() => {
    checkRef.current?.abort();
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

  const checkSession = useCallback(async () => {
    checkRef.current?.abort();
    const controller = new AbortController();
    checkRef.current = controller;
    const epoch = epochRef.current;
    const current = () => checkRef.current === controller && epochRef.current === epoch;
    setChecking(true);
    setCheckFailed(false);
    const timeout = setTimeout(() => controller.abort(), 10_000);
    try {
      await fetchCsrf(controller.signal);
      const resp = await fetch("/api/v1/devices?size=1", {
        credentials: "include",
        signal: controller.signal,
      });
      if (!current()) return;
      if (resp.status === 200) setAuthed(true);
      else if (resp.status === 401) setAuthed(false);
      else throw new Error(`Session check: HTTP ${resp.status}`);
    } catch {
      if (current()) setCheckFailed(true);
    } finally {
      clearTimeout(timeout);
      if (current()) setChecking(false);
    }
  }, []);

  useEffect(() => {
    void checkSession();
    return () => {
      checkRef.current?.abort();
      checkRef.current = null;
    };
  }, [checkSession]);

  return (
    <SessionContext.Provider
      value={{ authed, checking, checkFailed, checkSession, sessionEpoch, setAuthed, terminateSession, onLogout, onLogin }}
    >
      {children}
    </SessionContext.Provider>
  );
}
