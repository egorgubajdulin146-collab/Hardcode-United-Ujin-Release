-- Transport completion order must not overwrite a newer accepted observation.
CREATE SEQUENCE scenario_poll_read_order_seq START WITH 1;
ALTER TABLE scenario_polls ADD COLUMN read_order BIGINT;
ALTER TABLE scenario_observations ADD COLUMN source_identity VARCHAR(64);
ALTER TABLE scenario_observations ADD COLUMN accepted_read_order BIGINT NOT NULL DEFAULT 0;
ALTER TABLE scenario_observations ADD COLUMN measured_at VARCHAR(40);
