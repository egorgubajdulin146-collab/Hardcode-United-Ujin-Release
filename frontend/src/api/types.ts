// Типы соответствуют контракту 0.1.2-draft (docs/contracts/openapi.json)

export type ExecutionMode = "LOCAL" | "UJIN";

export type SignalGroup = "property" | "capability" | "setting";
export type Management = "r" | "rw" | "w";
export type ValueType = "NUMBER";

export type PlatformLink = "NOT_CONFIGURED" | "BOUND" | "BLOCKED";

export type RunStatus =
  | "PENDING"
  | "SUCCEEDED"
  | "SKIPPED"
  | "FAILED"
  | "BLOCKED"
  | "UNKNOWN";

export type EventStatus = "ACCEPTED" | "PROCESSED" | "FAILED";

export type CommandStatus = "PENDING" | "CONFIRMED" | "FAILED" | "UNKNOWN";

export type IntegrationStatus = "UNKNOWN" | "AVAILABLE" | "BLOCKED";

export type RunStage = "EVENT" | "CONDITION" | "ACTION" | "RESULT";

export interface SignalDefinition {
  key: string;
  title: string;
  group: SignalGroup;
  management: Management;
  valueType: ValueType;
  control: string;
  unit?: string;
  min?: number;
  max?: number;
  enumValues?: number[];
}

export interface EventDefinition {
  key: string;
  title: string;
  signalKey: string;
  emission: "CHANGE";
}

export interface Device {
  id: string;
  version: number;
  title: string;
  family: string;
  signals: SignalDefinition[];
  platformLink: PlatformLink;
  events: EventDefinition[];
}

export interface DeviceWrite {
  title: string;
  family: string;
  signals: SignalDefinition[];
  events: EventDefinition[];
}

export interface DeviceUpdate extends DeviceWrite {
  expectedVersion: number;
}

export interface DevicePage {
  items: Device[];
  page: number;
  size: number;
  total: number;
}

export interface SignalValue {
  signalKey: string;
  value: number;
  updatedAt: string;
  measuredAt?: string;
}

export type SignalIssueStatus =
  | "MISSING_SIGNAL"
  | "MISSING_VALUE"
  | "BAD_SCHEMA"
  | "OUT_OF_RANGE";

export interface SignalReadIssue {
  signalKey: string;
  status: SignalIssueStatus;
  detail: string;
}

export interface DeviceState {
  deviceId: string;
  executionMode: ExecutionMode;
  values: SignalValue[];
  availability: string;
  observedAt?: string;
  signalIssues?: SignalReadIssue[];
}

export interface PlatformBindingMapping {
  signalKey: string;
  platformName: string;
}

export interface PlatformBinding {
  deviceId: string;
  serialNumber: string;
  signalMappings: PlatformBindingMapping[];
  version: number;
}

export interface PlatformBindingWrite {
  serialNumber: string;
  signalMappings: PlatformBindingMapping[];
  expectedVersion?: number;
}

export interface Trigger {
  deviceId: string;
  signalKey: string;
  operator: "EQ";
  value: number;
  eventKey: string;
}

export interface Action {
  deviceId: string;
  signalKey: string;
  value: number;
}

export interface Scenario {
  id: string;
  version: number;
  title: string;
  enabled: boolean;
  executionMode: ExecutionMode;
  trigger: Trigger;
  action: Action;
}

export interface ScenarioWrite {
  title: string;
  enabled: boolean;
  executionMode: ExecutionMode;
  trigger: Trigger;
  action: Action;
}

export interface ScenarioUpdate extends ScenarioWrite {
  expectedVersion: number;
}

export interface ScenarioPage {
  items: Scenario[];
  page: number;
  size: number;
  total: number;
}

export interface Stimulus {
  eventId: string;
  kind: "WET";
  value: 0 | 1;
}

export interface EventReceipt {
  eventId: string;
  status: EventStatus;
  runIds: string[];
}

export interface RunEntry {
  at: string;
  stage: RunStage;
  message: string;
}

export interface UnknownResolution {
  note: string;
  resolvedAt: string;
  kind: "ACKNOWLEDGED_UNKNOWN";
}

export interface UnknownResolutionRequest {
  note: string;
}

export interface Run {
  id: string;
  eventId: string;
  scenarioId: string;
  scenarioVersion: number;
  executionMode: ExecutionMode;
  status: RunStatus;
  createdAt: string;
  entries: RunEntry[];
  input?: SignalValue;
  action?: Action;
  reason?: string;
  resolution?: UnknownResolution;
}

export interface RunPage {
  items: Run[];
  page: number;
  size: number;
  total: number;
}

export interface Command {
  commandId: string;
  signalKey: string;
  value: number;
  executionMode: ExecutionMode;
}

export interface CommandReceipt {
  commandId: string;
  deviceId: string;
  status: CommandStatus;
  executionMode: ExecutionMode;
  resolution?: UnknownResolution;
}

export interface SystemStatus {
  contractVersion: string;
  supportedModes: ExecutionMode[];
}

export interface IntegrationCapability {
  status: IntegrationStatus;
  reason: string;
  checkedAt?: string;
}

export interface UjinStatus {
  publish: IntegrationCapability;
  read: IntegrationCapability;
  downlink: IntegrationCapability;
  autoscripts: IntegrationCapability;
}

export interface ProblemError {
  field: string;
  message: string;
}

export interface Problem {
  type: string;
  title: string;
  status: number;
  code: string;
  detail: string;
  errors?: ProblemError[];
}

export interface DeviceDependencies {
  deviceId: string;
  scenarioIds: string[];
  pendingOperationIds: string[];
}
