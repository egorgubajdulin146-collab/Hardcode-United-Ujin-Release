import { useState, useCallback, type ReactNode } from "react";
import { useQueryClient } from "@tanstack/react-query";
import { SessionContext } from "./sessionContext";

export function SessionProvider({ children }: { children: ReactNode }) {
  const [authed, setAuthed] = useState(false);
  const queryClient = useQueryClient();

  const onLogout = useCallback(() => {
    setAuthed(false);
    queryClient.cancelQueries();
    queryClient.clear();
  }, [queryClient]);

  const onLogin = useCallback(() => {
    setAuthed(true);
    queryClient.clear();
  }, [queryClient]);

  return (
    <SessionContext.Provider value={{ authed, setAuthed, onLogout, onLogin }}>
      {children}
    </SessionContext.Provider>
  );
}
