-- V008: admin visibility + AI guardrail
-- 1) Immutable activity log: signups, logins, AI calls (fraud-ops event trail)
-- 2) Sticky per-user AI block the velocity guardrail can set

CREATE TABLE IF NOT EXISTS user_activity (
    id          BIGINT AUTO_INCREMENT PRIMARY KEY,
    email       VARCHAR(255) NOT NULL,
    event_type  VARCHAR(20)  NOT NULL,          -- signup | login | ai_call
    detail      VARCHAR(255) NULL,
    source_ip   VARCHAR(64)  NULL,
    created_at  TIMESTAMP    NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_ua_email_type_time (email, event_type, created_at),
    INDEX idx_ua_time (created_at)
);

ALTER TABLE users
    ADD COLUMN IF NOT EXISTS ai_blocked     BOOLEAN      NOT NULL DEFAULT FALSE,
    ADD COLUMN IF NOT EXISTS blocked_reason VARCHAR(255) NULL;
