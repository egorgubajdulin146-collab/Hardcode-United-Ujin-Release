-- Локальное состояние сигналов
CREATE TABLE local_signal_state (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    device_id UUID NOT NULL,
    signal_key VARCHAR(64) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    UNIQUE (device_id, signal_key)
);

-- События (стимулы)
CREATE TABLE runtime_events (
    event_id UUID PRIMARY KEY,
    device_id UUID NOT NULL,
    kind VARCHAR(16) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    processed_at TIMESTAMPTZ
);

-- Команды
CREATE TABLE runtime_commands (
    command_id UUID PRIMARY KEY,
    device_id UUID NOT NULL,
    signal_key VARCHAR(64) NOT NULL,
    value DOUBLE PRECISION NOT NULL,
    execution_mode VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    confirmed_at TIMESTAMPTZ
);

-- Запуски сценариев
CREATE TABLE runtime_runs (
    id UUID PRIMARY KEY,
    event_id UUID NOT NULL,
    scenario_id UUID NOT NULL,
    scenario_version BIGINT NOT NULL,
    execution_mode VARCHAR(8) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(120),
    created_at TIMESTAMPTZ NOT NULL DEFAULT now(),
    input_signal_key VARCHAR(64),
    input_value DOUBLE PRECISION,
    input_updated_at TIMESTAMPTZ,
    action_device_id UUID,
    action_signal_key VARCHAR(64),
    action_value DOUBLE PRECISION
);

-- Записи журнала запусков
CREATE TABLE runtime_run_entries (
    id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
    run_id UUID NOT NULL REFERENCES runtime_runs(id) ON DELETE CASCADE,
    at TIMESTAMPTZ NOT NULL,
    stage VARCHAR(16) NOT NULL,
    message VARCHAR(1000) NOT NULL
);

CREATE INDEX idx_local_signal_state_device ON local_signal_state(device_id);
CREATE INDEX idx_runtime_events_device ON runtime_events(device_id);
CREATE INDEX idx_runtime_runs_event ON runtime_runs(event_id);
CREATE INDEX idx_runtime_runs_scenario ON runtime_runs(scenario_id);
CREATE INDEX idx_runtime_run_entries_run ON runtime_run_entries(run_id);
