import { useState, useCallback, useRef, useEffect, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { SessionContext } from "./sessionContext";
import { clearCsrf, fetchCsrf } from "./authClient";
import { setDataSource } from "./dataSource";

export function SessionProvider({ children }: { children: ReactNode }) {
  const [authed, setAuthed] = useState(false);
  const [demo, setDemo] = useState(false);
  const [demoAvailable, setDemoAvailable] = useState(false);
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
    setChecking(false);
    setCheckFailed(false);
    bumpEpoch();
    setAuthed(false);
    setDemo(false);
    clearCsrf();
    queryClient.cancelQueries();
    queryClient.clear();
  }, [bumpEpoch, queryClient]);

  const onLogout = useCallback(() => {
    terminateSession();
  }, [terminateSession]);

  const onLogin = useCallback((guest = false) => {
    setChecking(false);
    setCheckFailed(false);
    setDemo(guest);
    if (guest) setDataSource("api");
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
      const resp = await fetch("/api/auth/session", {
        credentials: "include",
        signal: controller.signal,
      });
      if (!current()) return;
      if (resp.status !== 200) throw new Error(`Session check: HTTP ${resp.status}`);
      const info = await resp.json();
      if (!current()) return;
      setAuthed(info.authenticated === true);
      setDemo(info.demo === true);
      setDemoAvailable(info.demoAvailable === true);
      if (info.demo === true) setDataSource("api");
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
      value={{ authed, demo, demoAvailable, checking, checkFailed, checkSession, sessionEpoch, setAuthed, terminateSession, onLogout, onLogin }}
    >
      {children}
    </SessionContext.Provider>
  );
}
