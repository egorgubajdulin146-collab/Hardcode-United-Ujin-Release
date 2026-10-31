import { api } from "./client";
import type {
  Command,
  CommandReceipt,
  Device,
  DeviceDependencies,
  DevicePage,
  DeviceState,
  DeviceUpdate,
  DeviceWrite,
  EventReceipt,
  PlatformBinding,
  PlatformBindingWrite,
  Run,
  RunPage,
  Scenario,
  ScenarioPage,
  ScenarioUpdate,
  ScenarioWrite,
  Stimulus,
  SystemStatus,
  UjinStatus,
  UnknownResolutionRequest,
} from "./types";

interface PageParams {
  page?: number;
  size?: number;
}

function pageQuery(params?: PageParams): string {
  if (!params) return "";
  const q = new URLSearchParams();
  if (params.page !== undefined) q.set("page", String(params.page));
  if (params.size !== undefined) q.set("size", String(params.size));
  const s = q.toString();
  return s ? `?${s}` : "";
}

export const endpoints = {
  system: {
    getStatus: (signal?: AbortSignal) =>
      api.get<SystemStatus>("/api/v1/system", signal),
  },
  devices: {
    list: (params?: PageParams, signal?: AbortSignal) =>
      api.get<DevicePage>(`/api/v1/devices${pageQuery(params)}`, signal),
    get: (id: string, signal?: AbortSignal) =>
      api.get<Device>(`/api/v1/devices/${id}`, signal),
    create: (data: DeviceWrite) =>
      api.post<Device>("/api/v1/devices", data),
    update: (id: string, data: DeviceUpdate) =>
      api.put<Device>(`/api/v1/devices/${id}`, data),
    remove: (id: string, expectedVersion: number) =>
      api.delete(`/api/v1/devices/${id}?expectedVersion=${expectedVersion}`),
    getState: (id: string, mode: "LOCAL" | "UJIN", signal?: AbortSignal) =>
      api.get<DeviceState>(`/api/v1/devices/${id}/state?mode=${mode}`, signal),
    getBinding: (id: string, signal?: AbortSignal) =>
      api.get<PlatformBinding>(`/api/v1/devices/${id}/platform-binding`, signal),
    putBinding: (id: string, data: PlatformBindingWrite) =>
      api.put<PlatformBinding>(`/api/v1/devices/${id}/platform-binding`, data),
    setAvailability: (id: string, availability: "ONLINE" | "OFFLINE") =>
      api.put<DeviceState>(`/api/v1/devices/${id}/availability`, { availability, executionMode: "LOCAL" }),
    getDependencies: (id: string, signal?: AbortSignal) =>
      api.get<DeviceDependencies>(`/api/v1/devices/${id}/dependencies`, signal),
  },
  commands: {
    send: (deviceId: string, data: Command) =>
      api.post<CommandReceipt>(`/api/v1/devices/${deviceId}/commands`, data),
    get: (commandId: string, signal?: AbortSignal) =>
      api.get<CommandReceipt>(`/api/v1/commands/${commandId}`, signal),
    resolve: (commandId: string, data: UnknownResolutionRequest) =>
      api.post<CommandReceipt>(`/api/v1/commands/${commandId}/resolution`, data),
  },
  stimuli: {
    send: (deviceId: string, data: Stimulus) =>
      api.post<EventReceipt>(`/api/v1/devices/${deviceId}/stimuli`, data),
  },
  events: {
    get: (eventId: string, signal?: AbortSignal) =>
      api.get<EventReceipt>(`/api/v1/events/${eventId}`, signal),
  },
  scenarios: {
    list: (params?: PageParams, signal?: AbortSignal) =>
      api.get<ScenarioPage>(`/api/v1/scenarios${pageQuery(params)}`, signal),
    get: (id: string, signal?: AbortSignal) =>
      api.get<Scenario>(`/api/v1/scenarios/${id}`, signal),
    create: (data: ScenarioWrite) =>
      api.post<Scenario>("/api/v1/scenarios", data),
    update: (id: string, data: ScenarioUpdate) =>
      api.put<Scenario>(`/api/v1/scenarios/${id}`, data),
  },
  runs: {
    list: (
      params?: PageParams & { eventId?: string; scenarioId?: string },
      signal?: AbortSignal
    ) => {
      const q = new URLSearchParams();
      if (params?.page !== undefined) q.set("page", String(params.page));
      if (params?.size !== undefined) q.set("size", String(params.size));
      if (params?.eventId) q.set("eventId", params.eventId);
      if (params?.scenarioId) q.set("scenarioId", params.scenarioId);
      const s = q.toString();
      return api.get<RunPage>(`/api/v1/runs${s ? `?${s}` : ""}`, signal);
    },
    get: (runId: string, signal?: AbortSignal) =>
      api.get<Run>(`/api/v1/runs/${runId}`, signal),
    resolve: (runId: string, data: UnknownResolutionRequest) =>
      api.post<Run>(`/api/v1/runs/${runId}/resolution`, data),
  },
  ujin: {
    getStatus: (signal?: AbortSignal) =>
      api.get<UjinStatus>("/api/v1/integrations/ujin/status", signal),
  },
};
