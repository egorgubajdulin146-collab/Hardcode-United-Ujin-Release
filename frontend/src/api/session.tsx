import { useState, useCallback, useRef, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { SessionContext } from "./sessionContext";
import { clearCsrf } from "./authClient";

export function SessionProvider({ children }: { children: ReactNode }) {
  const [authed, setAuthed] = useState(false);
  const queryClient = useQueryClient();
  const sessionEpoch = useRef(0);

  const terminateSession = useCallback(() => {
    sessionEpoch.current += 1;
    setAuthed(false);
    clearCsrf();
    queryClient.cancelQueries();
    queryClient.clear();
  }, [queryClient]);

  const onLogout = useCallback(() => {
    terminateSession();
  }, [terminateSession]);

  const onLogin = useCallback(() => {
    sessionEpoch.current += 1;
    setAuthed(true);
    queryClient.clear();
  }, [queryClient]);

  return (
    <SessionContext.Provider
      value={{ authed, setAuthed, terminateSession, onLogout, onLogin }}
    >
      {children}
    </SessionContext.Provider>
  );
}
