-- RF-AU15: per-account progressive login throttling (threshold/backoff
-- chosen in code, not specified in the docs — see PROGRESS.md Fase 1
-- decisions log).
ALTER TABLE sellers
    ADD COLUMN failed_login_attempts INT NOT NULL DEFAULT 0,
    ADD COLUMN locked_until DATETIME NULL;
