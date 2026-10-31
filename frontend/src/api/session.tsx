import { useState, useCallback, useRef, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { SessionContext } from "./sessionContext";
import { clearCsrf } from "./authClient";

export function SessionProvider({ children }: { children: ReactNode }) {
  const [authed, setAuthed] = useState(false);
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

  return (
    <SessionContext.Provider
      value={{ authed, sessionEpoch, setAuthed, terminateSession, onLogout, onLogin }}
    >
      {children}
    </SessionContext.Provider>
  );
}
