-- V5__Add_Notification_Fields.sql
-- Add notification and phone fields for Web Push and future SMS

-- Add notification columns to tasks table
ALTER TABLE tasks 
ADD COLUMN IF NOT EXISTS notifications_enabled TINYINT(1) DEFAULT 0 COMMENT 'Web Push notifications enabled for this task',
ADD COLUMN IF NOT EXISTS push_endpoint TEXT NULL COMMENT 'Legacy push endpoint (deprecated)',
ADD COLUMN IF NOT EXISTS phone_number VARCHAR(20) NULL COMMENT 'Phone number for future SMS notifications',
ADD COLUMN IF NOT EXISTS sms_enabled TINYINT(1) DEFAULT 0 COMMENT 'SMS notifications enabled (future feature)';

-- Add indexes for better query performance
CREATE INDEX IF NOT EXISTS idx_notifications_enabled ON tasks(notifications_enabled);
CREATE INDEX IF NOT EXISTS idx_phone_number ON tasks(phone_number);

-- Create push_subscriptions table for Web Push
CREATE TABLE IF NOT EXISTS push_subscriptions (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_email VARCHAR(255) NOT NULL,
    endpoint TEXT NOT NULL,
    p256dh TEXT NOT NULL COMMENT 'Public key for encryption',
    auth TEXT NOT NULL COMMENT 'Authentication secret',
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Constraints
    UNIQUE KEY unique_endpoint (endpoint(255)),
    INDEX idx_user_email (user_email)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COMMENT='Web Push notification subscriptions';

-- Add comments to table
ALTER TABLE tasks COMMENT = 'Tasks with Web Push and SMS notification support';
