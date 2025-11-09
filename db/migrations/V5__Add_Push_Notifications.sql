-- V5__Add_Push_Notifications.sql
-- Add push notification support

-- 1. Create push_subscriptions table for multi-device support
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh TEXT NOT NULL,
    auth TEXT NOT NULL,
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    UNIQUE KEY unique_endpoint (endpoint(500))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- 2. Add notification fields to tasks table
ALTER TABLE tasks 
ADD COLUMN IF NOT EXISTS notifications_enabled TINYINT(1) DEFAULT 0,
ADD COLUMN IF NOT EXISTS push_endpoint TEXT NULL;

-- 3. Create index for faster queries
CREATE INDEX IF NOT EXISTS idx_user_email ON push_subscriptions(user_email);
CREATE INDEX IF NOT EXISTS idx_notifications_enabled ON tasks(notifications_enabled);
