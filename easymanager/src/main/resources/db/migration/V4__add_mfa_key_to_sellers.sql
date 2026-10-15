-- RF-AU17: MFA (TOTP) required for ADMIN accounts. NULL means MFA has
-- not been provisioned for that seller yet — for ADMIN, login fails
-- fail-secure in that case (see PROGRESS.md Fase 1 decisions log; there
-- is no self-service admin registration/enrollment flow in this phase).
ALTER TABLE sellers
    ADD COLUMN mfa_secret VARCHAR(64) NULL;
