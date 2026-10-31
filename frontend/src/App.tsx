import { BrowserRouter, Routes, Route, Navigate, useLocation } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionProvider } from "./api/session";
import { useSession } from "./api/sessionContext";
import { Layout } from "./components/Layout";
import { DevicesCrudPage } from "./pages/DevicesCrudPage";
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

function AppRoutes() {
  const { authed } = useSession();
  const location = useLocation();

  // Без авторизации — только форма входа, без бокового меню
  if (!authed && location.pathname !== "/login") {
    return <LoginPage />;
  }

  if (!authed) {
    return <LoginPage />;
  }

  // После авторизации — полная оболочка
  return (
    <Layout>
      <Routes>
        <Route index element={<Navigate to="/devices" replace />} />
        <Route path="devices" element={isMock() ? <RoomDevicesPage /> : <DevicesCrudPage />} />
        <Route path="scenarios" element={<ScenariosPage />} />
        <Route path="runs" element={<RunsPage />} />
        <Route path="login" element={<Navigate to="/devices" replace />} />
        <Route path="adapter" element={<AdapterPage />} />
        <Route path="*" element={<Navigate to="/devices" replace />} />
      </Routes>
    </Layout>
  );
}

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <SessionProvider>
        <BrowserRouter>
          <AppRoutes />
        </BrowserRouter>
      </SessionProvider>
    </QueryClientProvider>
  );
}
