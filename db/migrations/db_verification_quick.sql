-- ============================================
-- QUICK VERIFICATION SCRIPT
-- Essential checks only - takes ~2 seconds
-- ============================================

USE taskmanager;

-- Quick Check 1: New columns exist
SELECT 'Check 1: New columns in tasks table' AS check_name;
SELECT COLUMN_NAME, DATA_TYPE, COLUMN_DEFAULT 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = 'taskmanager' 
  AND TABLE_NAME = 'tasks'
  AND COLUMN_NAME IN ('created_from_device', 'created_from_ip', 'user_agent', 'reminder_sent');

-- Quick Check 2: No NULL reminder_sent
SELECT 'Check 2: NULL reminder_sent count (should be 0)' AS check_name;
SELECT COUNT(*) as null_count FROM tasks WHERE reminder_sent IS NULL;

-- Quick Check 3: Device tracking working
SELECT 'Check 3: Recent tasks with device info' AS check_name;
SELECT id, title, created_from_device, 
       CASE WHEN reminder_sent = b'0' THEN 0 ELSE 1 END as reminder_sent
FROM tasks ORDER BY id DESC LIMIT 3;

-- Quick Check 4: Push subscriptions
SELECT 'Check 4: Push subscription count' AS check_name;
SELECT user_email, COUNT(*) as subscriptions 
FROM push_subscriptions 
GROUP BY user_email;

-- Quick Check 5: Indexes
SELECT 'Check 5: New indexes exist' AS check_name;
SELECT INDEX_NAME, COLUMN_NAME 
FROM INFORMATION_SCHEMA.STATISTICS 
WHERE TABLE_SCHEMA = 'taskmanager' 
  AND TABLE_NAME = 'tasks'
  AND COLUMN_NAME IN ('reminder_sent', 'created_from_device');

SELECT 'QUICK CHECK COMPLETE!' AS '';
SELECT 'If you see all 5 checks above with data, migration successful!' AS '';
