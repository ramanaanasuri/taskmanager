-- ============================================
-- DATABASE VERIFICATION SCRIPT
-- Run this to verify all changes are applied correctly
-- ============================================

USE taskmanager;

-- ============================================
-- SECTION 1: TABLE STRUCTURE VERIFICATION
-- ============================================

SELECT '============================================' AS '';
SELECT 'SECTION 1: TABLE STRUCTURE VERIFICATION' AS '';
SELECT '============================================' AS '';

-- Check 1: Verify tasks table has all required columns
SELECT '\n1. Checking tasks table structure...' AS '';
DESCRIBE tasks;

-- Check 2: Verify push_subscriptions table has device tracking columns
SELECT '\n2. Checking push_subscriptions table structure...' AS '';
DESCRIBE push_subscriptions;

-- Check 3: Verify notification_logs table exists
SELECT '\n3. Checking if notification_logs table exists...' AS '';
SHOW TABLES LIKE 'notification_logs';
DESCRIBE notification_logs;

-- ============================================
-- SECTION 2: DATA INTEGRITY CHECKS
-- ============================================

SELECT '\n============================================' AS '';
SELECT 'SECTION 2: DATA INTEGRITY CHECKS' AS '';
SELECT '============================================' AS '';

-- Check 4: Verify NO NULL values in reminder_sent
SELECT '\n4. Checking for NULL reminder_sent values (should be 0)...' AS '';
SELECT COUNT(*) as null_count 
FROM tasks 
WHERE reminder_sent IS NULL;

-- Check 5: Distribution of reminder_sent values
SELECT '\n5. Distribution of reminder_sent values...' AS '';
SELECT 
    CASE 
        WHEN reminder_sent = b'0' THEN 'Not Sent (0)'
        WHEN reminder_sent = b'1' THEN 'Sent (1)'
        WHEN reminder_sent IS NULL THEN 'NULL (BAD!)'
        ELSE 'Unknown'
    END as reminder_status,
    COUNT(*) as count
FROM tasks
GROUP BY reminder_sent;

-- Check 6: Tasks with notifications enabled
SELECT '\n6. Tasks with notifications enabled...' AS '';
SELECT 
    COUNT(*) as total_tasks_with_notifications,
    SUM(CASE WHEN reminder_sent = b'0' THEN 1 ELSE 0 END) as not_sent_yet,
    SUM(CASE WHEN reminder_sent = b'1' THEN 1 ELSE 0 END) as already_sent
FROM tasks
WHERE notifications_enabled = 1;

-- ============================================
-- SECTION 3: DEVICE TRACKING VERIFICATION
-- ============================================

SELECT '\n============================================' AS '';
SELECT 'SECTION 3: DEVICE TRACKING VERIFICATION' AS '';
SELECT '============================================' AS '';

-- Check 7: Device distribution in tasks
SELECT '\n7. Task creation by device type...' AS '';
SELECT 
    COALESCE(created_from_device, 'NULL') as device_type,
    COUNT(*) as task_count,
    SUM(CASE WHEN notifications_enabled = 1 THEN 1 ELSE 0 END) as with_notifications
FROM tasks
GROUP BY created_from_device
ORDER BY task_count DESC;

-- Check 8: Recent tasks with device information
SELECT '\n8. Last 5 tasks with device info...' AS '';
SELECT 
    id,
    title,
    COALESCE(created_from_device, 'NULL') as device,
    COALESCE(SUBSTRING(created_from_ip, 1, 20), 'NULL') as ip_preview,
    CASE 
        WHEN reminder_sent = b'0' THEN 'Not Sent'
        WHEN reminder_sent = b'1' THEN 'Sent'
        ELSE 'NULL'
    END as reminder_status,
    notifications_enabled,
    created_at
FROM tasks
ORDER BY id DESC
LIMIT 5;

-- Check 9: Push subscription device tracking
SELECT '\n9. Push subscriptions with device info...' AS '';
SELECT 
    id,
    user_email,
    COALESCE(device_type, 'NULL') as device,
    COALESCE(browser, 'NULL') as browser,
    COALESCE(os, 'NULL') as os,
    created_at,
    last_used_at
FROM push_subscriptions
ORDER BY id DESC
LIMIT 5;

-- ============================================
-- SECTION 4: INDEX VERIFICATION
-- ============================================

SELECT '\n============================================' AS '';
SELECT 'SECTION 4: INDEX VERIFICATION' AS '';
SELECT '============================================' AS '';

-- Check 10: Verify indexes on tasks table
SELECT '\n10. Indexes on tasks table...' AS '';
SHOW INDEX FROM tasks;

-- ============================================
-- SECTION 5: NOTIFICATION READINESS CHECK
-- ============================================

SELECT '\n============================================' AS '';
SELECT 'SECTION 5: NOTIFICATION READINESS CHECK' AS '';
SELECT '============================================' AS '';

-- Check 11: Tasks ready for notification (in next 5 minutes)
SELECT '\n11. Tasks ready for notification in next 5 minutes...' AS '';
SELECT 
    id,
    title,
    due_date,
    NOW() as current_utc_time,
    TIMESTAMPDIFF(MINUTE, NOW(), due_date) as minutes_until_due,
    CASE 
        WHEN reminder_sent = b'0' THEN 'Ready to send'
        WHEN reminder_sent = b'1' THEN 'Already sent'
        ELSE 'NULL'
    END as notification_status,
    COALESCE(created_from_device, 'NULL') as device
FROM tasks
WHERE notifications_enabled = 1
  AND completed = 0
  AND due_date IS NOT NULL
  AND due_date BETWEEN NOW() - INTERVAL 1 MINUTE AND NOW() + INTERVAL 5 MINUTE
ORDER BY due_date;

-- Check 12: Count of push subscriptions per user
SELECT '\n12. Push subscriptions per user...' AS '';
SELECT 
    user_email,
    COUNT(*) as subscription_count,
    MAX(created_at) as latest_subscription,
    MAX(last_used_at) as last_used
FROM push_subscriptions
GROUP BY user_email
ORDER BY subscription_count DESC;

-- ============================================
-- SECTION 6: POTENTIAL ISSUES CHECK
-- ============================================

SELECT '\n============================================' AS '';
SELECT 'SECTION 6: POTENTIAL ISSUES CHECK' AS '';
SELECT '============================================' AS '';

-- Check 13: Tasks with notifications enabled but no push subscriptions
SELECT '\n13. Users with notification tasks but no subscriptions...' AS '';
SELECT DISTINCT
    t.user_email,
    COUNT(t.id) as tasks_with_notifications,
    COALESCE(ps.sub_count, 0) as subscriptions
FROM tasks t
LEFT JOIN (
    SELECT user_email, COUNT(*) as sub_count
    FROM push_subscriptions
    GROUP BY user_email
) ps ON t.user_email = ps.user_email
WHERE t.notifications_enabled = 1
GROUP BY t.user_email, ps.sub_count
HAVING subscriptions = 0;

-- Check 14: Old tasks that were never notified
SELECT '\n14. Overdue tasks that were never notified...' AS '';
SELECT 
    id,
    title,
    due_date,
    TIMESTAMPDIFF(HOUR, due_date, NOW()) as hours_overdue,
    COALESCE(created_from_device, 'NULL') as device
FROM tasks
WHERE notifications_enabled = 1
  AND reminder_sent = b'0'
  AND due_date < NOW()
  AND completed = 0
ORDER BY due_date DESC
LIMIT 10;

-- ============================================
-- SECTION 7: SUMMARY STATISTICS
-- ============================================

SELECT '\n============================================' AS '';
SELECT 'SECTION 7: SUMMARY STATISTICS' AS '';
SELECT '============================================' AS '';

-- Check 15: Overall statistics
SELECT '\n15. Overall system statistics...' AS '';
SELECT 
    (SELECT COUNT(*) FROM tasks) as total_tasks,
    (SELECT COUNT(*) FROM tasks WHERE notifications_enabled = 1) as tasks_with_notifications,
    (SELECT COUNT(*) FROM tasks WHERE reminder_sent = b'1') as notifications_sent,
    (SELECT COUNT(*) FROM tasks WHERE reminder_sent = b'0' AND notifications_enabled = 1) as pending_notifications,
    (SELECT COUNT(*) FROM push_subscriptions) as total_subscriptions,
    (SELECT COUNT(DISTINCT user_email) FROM push_subscriptions) as users_with_subscriptions,
    (SELECT COUNT(*) FROM tasks WHERE created_from_device = 'mobile') as mobile_created_tasks,
    (SELECT COUNT(*) FROM tasks WHERE created_from_device = 'web') as web_created_tasks;

-- ============================================
-- SECTION 8: EXPECTED RESULTS SUMMARY
-- ============================================

SELECT '\n============================================' AS '';
SELECT 'EXPECTED RESULTS SUMMARY' AS '';
SELECT '============================================' AS '';
SELECT '\nIf migration was successful, you should see:' AS '';
SELECT '1. ✅ tasks table has: created_from_device, created_from_ip, user_agent columns' AS '';
SELECT '2. ✅ reminder_sent default is b\'0\' (this is correct!)' AS '';
SELECT '3. ✅ NULL count for reminder_sent = 0' AS '';
SELECT '4. ✅ push_subscriptions has: device_type, device_name, browser, os columns' AS '';
SELECT '5. ✅ notification_logs table exists' AS '';
SELECT '6. ✅ Indexes exist on reminder_sent and created_from_device' AS '';
SELECT '7. ✅ All tasks show device info when created' AS '';
SELECT '\nIf you see issues with any of these, report them!' AS '';
SELECT '============================================\n' AS '';
