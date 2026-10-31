import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionProvider } from "./api/session";
import { Layout } from "./components/Layout";
import { DevicesPage } from "./pages/DevicesPage";
import { ScenariosPage } from "./pages/ScenariosPage";
import { RunsPage } from "./pages/RunsPage";
import { RoomDevicesPage } from "./pages/RoomDevicesPage";
import { LoginPage } from "./pages/LoginPage";
import { AdapterPage } from "./pages/AdapterPage";
import { isMock } from "./api/dataSource";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
});

function DevicesRoute() {
  return isMock() ? <RoomDevicesPage /> : <DevicesPage />;
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <BrowserRouter>
          <Routes>
            <Route path="/" element={<Layout />}>
              <Route index element={<Navigate to="/devices" replace />} />
              <Route path="devices" element={<DevicesRoute />} />
              <Route path="scenarios" element={<ScenariosPage />} />
              <Route path="runs" element={<RunsPage />} />
              <Route path="login" element={<LoginPage />} />
              <Route path="adapter" element={<AdapterPage />} />
              <Route path="*" element={<Navigate to="/devices" replace />} />
            </Route>
          </Routes>
        </BrowserRouter>
      </SessionProvider>
    </QueryClientProvider>
  );
}
