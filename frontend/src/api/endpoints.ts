import { api } from "./client";
import type {
  CommandReceipt,
  Device,
  DeviceDependencies,
  DeviceState,
  DeviceWrite,
  EventReceipt,
  Run,
  RunPage,
  Scenario,
  ScenarioWrite,
  Stimulus,
  SystemStatus,
  UjinStatus,
} from "./types";

export const endpoints = {
  system: {
    getStatus: () => api.get<SystemStatus>("/api/v1/system"),
  },
  devices: {
    list: () => api.get<Device[]>("/api/v1/devices"),
    get: (id: string) => api.get<Device>(`/api/v1/devices/${id}`),
    create: (data: DeviceWrite) => api.post<Device>("/api/v1/devices", data),
    update: (id: string, data: DeviceWrite & { expectedVersion: number }) =>
      api.put<Device>(`/api/v1/devices/${id}`, data),
    remove: (id: string) => api.delete(`/api/v1/devices/${id}`),
    getState: (id: string, mode: "LOCAL" | "UJIN") =>
      api.get<DeviceState>(`/api/v1/devices/${id}/state?mode=${mode}`),
    getDependencies: (id: string) =>
      api.get<DeviceDependencies>(`/api/v1/devices/${id}/dependencies`),
  },
  commands: {
    send: (deviceId: string, signalKey: string, value: number) =>
      api.post<CommandReceipt>(`/api/v1/devices/${deviceId}/commands`, {
        signalKey,
        value,
      }),
    get: (commandId: string) =>
      api.get<CommandReceipt>(`/api/v1/commands/${commandId}`),
    resolve: (commandId: string, note: string) =>
      api.post<CommandReceipt>(`/api/v1/commands/${commandId}/resolution`, {
        note,
      }),
  },
  stimuli: {
    send: (deviceId: string, stimulus: Omit<Stimulus, "eventId">) =>
      api.post<EventReceipt>(`/api/v1/devices/${deviceId}/stimuli`, stimulus),
  },
  events: {
    get: (eventId: string) =>
      api.get<EventReceipt>(`/api/v1/events/${eventId}`),
  },
  scenarios: {
    list: () => api.get<Scenario[]>("/api/v1/scenarios"),
    get: (id: string) => api.get<Scenario>(`/api/v1/scenarios/${id}`),
    create: (data: ScenarioWrite) => api.post<Scenario>("/api/v1/scenarios", data),
    update: (id: string, data: ScenarioWrite & { expectedVersion: number }) =>
      api.put<Scenario>(`/api/v1/scenarios/${id}`, data),
  },
  runs: {
    list: (params?: { eventId?: string; scenarioId?: string }) => {
      const query = params
        ? "?" +
          Object.entries(params)
            .filter(([, v]) => v)
            .map(([k, v]) => `${k}=${v}`)
            .join("&")
        : "";
      return api.get<RunPage>(`/api/v1/runs${query}`);
    },
    get: (runId: string) => api.get<Run>(`/api/v1/runs/${runId}`),
  },
  ujin: {
    getStatus: () => api.get<UjinStatus>("/api/v1/integrations/ujin/status"),
  },
};
