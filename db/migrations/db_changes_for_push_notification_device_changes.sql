-- ============================================
-- FIX PUSH NOTIFICATION SCHEMA
-- ============================================

USE taskmanager;

-- Step 1: Fix reminder_sent field - change default to 0 and update NULL values
ALTER TABLE tasks 
MODIFY COLUMN reminder_sent BIT(1) DEFAULT b'0';

-- Step 2: Update all existing NULL values to 0
UPDATE tasks 
SET reminder_sent = 0 
WHERE reminder_sent IS NULL;

-- Step 3: Add device tracking column to tasks table
ALTER TABLE tasks 
ADD COLUMN created_from_device VARCHAR(50) DEFAULT 'web' AFTER user_email,
ADD COLUMN created_from_ip VARCHAR(45) DEFAULT NULL AFTER created_from_device,
ADD COLUMN user_agent TEXT DEFAULT NULL AFTER created_from_ip;

-- Step 4: Add index for better query performance
CREATE INDEX idx_reminder_sent ON tasks(reminder_sent);
CREATE INDEX idx_created_from_device ON tasks(created_from_device);

-- Step 5: Add device info to push_subscriptions table
ALTER TABLE push_subscriptions
ADD COLUMN device_type VARCHAR(50) DEFAULT 'unknown' AFTER user_email,
ADD COLUMN device_name VARCHAR(100) DEFAULT NULL AFTER device_type,
ADD COLUMN browser VARCHAR(50) DEFAULT NULL AFTER device_name,
ADD COLUMN os VARCHAR(50) DEFAULT NULL AFTER browser,
ADD COLUMN last_used_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP AFTER os;

-- Step 6: Create notification_logs table for tracking
CREATE TABLE IF NOT EXISTS notification_logs (
    id BIGINT PRIMARY KEY AUTO_INCREMENT,
    task_id BIGINT NOT NULL,
    user_email VARCHAR(255) NOT NULL,
    notification_type ENUM('push', 'email', 'sms') DEFAULT 'push',
    status ENUM('sent', 'failed', 'pending') NOT NULL,
    error_message TEXT,
    sent_to_endpoint TEXT,
    device_type VARCHAR(50),
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_task_id (task_id),
    INDEX idx_user_email (user_email),
    INDEX idx_status (status),
    INDEX idx_created_at (created_at),
    FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- Step 7: Verify changes
SHOW COLUMNS FROM tasks LIKE 'reminder_sent';
SHOW COLUMNS FROM tasks LIKE 'created_from%';
SHOW COLUMNS FROM push_subscriptions LIKE 'device%';
DESCRIBE notification_logs;

-- Step 8: Show current state
SELECT 
    COUNT(*) as total_tasks,
    SUM(CASE WHEN reminder_sent = 0 THEN 1 ELSE 0 END) as not_sent,
    SUM(CASE WHEN reminder_sent = 1 THEN 1 ELSE 0 END) as sent,
    SUM(CASE WHEN reminder_sent IS NULL THEN 1 ELSE 0 END) as null_values
FROM tasks
WHERE notifications_enabled = 1;

-- Step 9: Show subscription status
SELECT 
    user_email,
    COUNT(*) as subscription_count,
    MAX(created_at) as latest_subscription
FROM push_subscriptions
GROUP BY user_email;

-- Step 10: Show tasks ready for notification (in the next 2 minutes)
SELECT 
    id,
    title,
    due_date,
    reminder_sent,
    notifications_enabled,
    TIMESTAMPDIFF(MINUTE, NOW(), due_date) as minutes_until_due
FROM tasks
WHERE notifications_enabled = 1
  AND completed = 0
  AND due_date BETWEEN NOW() - INTERVAL 1 MINUTE AND NOW() + INTERVAL 2 MINUTE
  AND (reminder_sent = 0 OR reminder_sent IS NULL)
ORDER BY due_date;
