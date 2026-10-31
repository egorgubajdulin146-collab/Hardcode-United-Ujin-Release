import { BrowserRouter, Routes, Route, Navigate } from "react-router-dom";
import { QueryClient, QueryClientProvider } from "@tanstack/react-query";
import { Layout } from "./components/Layout";
import { DevicesPage } from "./pages/DevicesPage";
import { ScenariosPage } from "./pages/ScenariosPage";
import { RunsPage } from "./pages/RunsPage";

const queryClient = new QueryClient({
  defaultOptions: {
    queries: {
      retry: 1,
      staleTime: 30_000,
    },
  },
});

export default function App() {
  return (
    <QueryClientProvider client={queryClient}>
      <BrowserRouter>
        <Routes>
          <Route path="/" element={<Layout />}>
            <Route index element={<Navigate to="/devices" replace />} />
            <Route path="devices" element={<DevicesPage />} />
            <Route path="scenarios" element={<ScenariosPage />} />
            <Route path="runs" element={<RunsPage />} />
            <Route path="*" element={<Navigate to="/devices" replace />} />
          </Route>
        </Routes>
      </BrowserRouter>
    </QueryClientProvider>
  );
}
