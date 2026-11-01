import { createContext, useContext } from "react";

interface SessionState {
  authed: boolean;
  demo: boolean;
  demoAvailable: boolean;
  checking: boolean;
  checkFailed: boolean;
  checkSession: () => Promise<void>;
  sessionEpoch: number;
  setAuthed: (value: boolean) => void;
  terminateSession: () => void;
  onLogout: () => void;
  onLogin: (demo?: boolean) => void;
}

export const SessionContext = createContext<SessionState>({
  authed: false,
  demo: false,
  demoAvailable: false,
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
