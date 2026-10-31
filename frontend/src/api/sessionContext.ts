import { createContext, useContext } from "react";

interface SessionState {
  authed: boolean;
  setAuthed: (value: boolean) => void;
  onLogout: () => void;
  onLogin: () => void;
}

export const SessionContext = createContext<SessionState>({
  authed: false,
  setAuthed: () => {},
  onLogout: () => {},
  onLogin: () => {},
});

export function useSession() {
  return useContext(SessionContext);
}
