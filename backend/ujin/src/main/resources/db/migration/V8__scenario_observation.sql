-- Baseline наблюдения показания Ujin для сценария; переживает рестарт.
CREATE TABLE scenario_observations (
    scenario_id UUID PRIMARY KEY,
    owner VARCHAR(120) NOT NULL,
    input_signal_key VARCHAR(64) NOT NULL,
    baseline_value DOUBLE PRECISION NOT NULL,
    updated_at VARCHAR(40) NOT NULL
);

-- Результаты poll с идемпотентностью по pollId.
CREATE TABLE scenario_polls (
    poll_id UUID PRIMARY KEY,
    scenario_id UUID NOT NULL,
    owner VARCHAR(120) NOT NULL,
    status VARCHAR(16) NOT NULL,
    reason VARCHAR(80),
    observed_value DOUBLE PRECISION,
    run_id UUID,
    created_at VARCHAR(40) NOT NULL
);

-- Run связан с командой UJIN: SUCCEEDED только после CONFIRMED команды.
ALTER TABLE runtime_runs ADD COLUMN command_id UUID;
