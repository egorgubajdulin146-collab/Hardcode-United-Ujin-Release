import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { SessionProvider } from "./api/session";
import { useSession } from "./api/sessionContext";
import { Layout } from "./components/Layout";
import { DevicesCrudPage } from "./pages/DevicesCrudPage";
import { ScenariosCrudPage } from "./pages/ScenariosCrudPage";
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
  const { authed, checking, checkFailed, checkSession } = useSession();

  // Проверка сессии на сервере — показываем загрузку без бокового меню
  if (checking) {
    return (
      <div style={{ display: "flex", alignItems: "center", justifyContent: "center", minHeight: "100vh" }}>
        <p style={{ color: "#6b7280" }}>Проверка сессии…</p>
      </div>
    );
  }

  // Ошибка проверки — показываем повтор
  if (checkFailed && !authed) {
    return (
      <div style={{ display: "flex", flexDirection: "column", alignItems: "center", justifyContent: "center", minHeight: "100vh", gap: 16 }}>
        <p style={{ color: "#6b7280" }}>Не удалось проверить сессию. Сервер недоступен.</p>
        <button
          type="button"
          onClick={() => checkSession()}
          style={{ padding: "8px 16px", background: "#00b4b4", color: "white", border: "none", borderRadius: 6, cursor: "pointer" }}
        >
          Повторить
        </button>
      </div>
    );
  }

  // Без авторизации — только форма входа, без бокового меню
  if (!authed) {
    return <LoginPage />;
  }

  // После авторизации — полная оболочка
  return (
    <Layout>
      <Routes>
        <Route index element={<Navigate to="/devices" replace />} />
        <Route path="devices" element={isMock() ? <RoomDevicesPage /> : <DevicesCrudPage />} />
        <Route path="scenarios" element={<ScenariosCrudPage />} />
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
