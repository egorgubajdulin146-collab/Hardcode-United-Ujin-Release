import { createContext, useContext } from "react";

interface SessionState {
  authed: boolean;
  checking: boolean;
  checkFailed: boolean;
  checkSession: () => Promise<void>;
  sessionEpoch: number;
  setAuthed: (value: boolean) => void;
  terminateSession: () => void;
  onLogout: () => void;
  onLogin: () => void;
}

export const SessionContext = createContext<SessionState>({
  authed: false,
  checking: true,
  checkFailed: false,
  checkSession: async () => {},
  sessionEpoch: 0,
  setAuthed: () => {},
  terminateSession: () => {},
  onLogout: () => {},
  onLogin: () => {},
});

export function useSession() {
  return useContext(SessionContext);
}
