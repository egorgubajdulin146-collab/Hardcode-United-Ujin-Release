-- Preserve every successful task identity, not only the last task per signal.
CREATE TABLE platform_task_history (
    device_id UUID NOT NULL,
    signal_key VARCHAR(64) NOT NULL,
    task_key VARCHAR(255) NOT NULL,
    applied_at VARCHAR(40) NOT NULL,
    PRIMARY KEY (device_id, signal_key, task_key)
);
INSERT INTO platform_task_history (device_id, signal_key, task_key, applied_at)
SELECT device_id, signal_key, task_key, applied_at FROM platform_task_acks;
-- NULL means baseline unavailable; empty string means a verified empty task snapshot.
ALTER TABLE runtime_commands ADD COLUMN task_baseline_key VARCHAR(255);
