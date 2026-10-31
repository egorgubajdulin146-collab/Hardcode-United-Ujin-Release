import { createContext, useContext } from "react";

interface SessionState {
  authed: boolean;
  checking: boolean;
  sessionEpoch: number;
  setAuthed: (value: boolean) => void;
  terminateSession: () => void;
  onLogout: () => void;
  onLogin: () => void;
}

export const SessionContext = createContext<SessionState>({
  authed: false,
  checking: true,
  sessionEpoch: 0,
  setAuthed: () => {},
  terminateSession: () => {},
  onLogout: () => {},
  onLogin: () => {},
});

export function useSession() {
  return useContext(SessionContext);
}
