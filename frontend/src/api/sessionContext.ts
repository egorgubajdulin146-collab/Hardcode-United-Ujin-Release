import { createContext, useContext } from "react";

interface SessionState {
  authed: boolean;
  setAuthed: (value: boolean) => void;
  terminateSession: () => void;
  onLogout: () => void;
  onLogin: () => void;
}

export const SessionContext = createContext<SessionState>({
  authed: false,
  setAuthed: () => {},
  terminateSession: () => {},
  onLogout: () => {},
  onLogin: () => {},
});

export function useSession() {
  return useContext(SessionContext);
}
