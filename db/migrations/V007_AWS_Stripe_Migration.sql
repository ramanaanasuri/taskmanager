-- ============================================================
-- STRIPE INTEGRATION - AWS DATABASE MIGRATION
-- Task Manager Pro - SriInfosoft Inc
-- ============================================================
-- Run this script on AWS MariaDB to add Stripe payment support
-- 
-- Prerequisites:
--   1. Backup completed: mysqldump taskmanager > backup.sql
--   2. Connected to taskmanager database
--
-- Usage:
--   mysql -uroot -p taskmanager < V007_AWS_Stripe_Migration.sql
-- ============================================================

-- Start transaction for safety
START TRANSACTION;

-- ============================================================
-- STEP 1: Fix collation on push_subscriptions table
-- ============================================================
-- This prevents "Illegal mix of collations" error

SELECT '🔧 Step 1: Fixing push_subscriptions collation...' AS status;

ALTER TABLE push_subscriptions 
MODIFY user_email VARCHAR(255) CHARACTER SET utf8mb4 COLLATE utf8mb4_general_ci NOT NULL;

SELECT '✅ push_subscriptions.user_email collation fixed' AS status;

-- Verify the fix
-- SHOW FULL COLUMNS FROM push_subscriptions WHERE Field = 'user_email';

-- ============================================================
-- STEP 2: Create users table for Stripe integration
-- ============================================================

SELECT '🔧 Step 2: Creating users table...' AS status;

CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    
    -- Basic user info (from OAuth)
    email VARCHAR(255) UNIQUE,
    name VARCHAR(255),
    picture TEXT,
    
    -- Stripe customer info
    stripe_customer_id VARCHAR(255) UNIQUE,
    
    -- Subscription details
    subscription_status ENUM('free', 'active', 'canceled', 'past_due', 'trialing') DEFAULT 'free',
    subscription_plan ENUM('free', 'basic', 'pro', 'enterprise') DEFAULT 'free',
    stripe_subscription_id VARCHAR(255),
    subscription_start_date DATETIME,
    subscription_end_date DATETIME,
    
    -- Premium feature limits
    sms_credits_used INT DEFAULT 0,
    sms_credits_limit INT DEFAULT 0,
    ai_requests_used INT DEFAULT 0,
    ai_requests_limit INT DEFAULT 0,
    
    -- Timestamps
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Indexes
    INDEX idx_email (email),
    INDEX idx_stripe_customer_id (stripe_customer_id),
    INDEX idx_subscription_status (subscription_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

SELECT '✅ users table created' AS status;

-- ============================================================
-- STEP 3: Pre-populate users from existing task owners
-- ============================================================
-- This creates user records for everyone who has tasks

SELECT '🔧 Step 3: Creating user records for existing task owners...' AS status;

INSERT INTO users (email, subscription_status, subscription_plan, 
                   sms_credits_used, sms_credits_limit, 
                   ai_requests_used, ai_requests_limit,
                   created_at, updated_at)
SELECT DISTINCT 
    t.user_email,
    'free',
    'free',
    0, 0,
    0, 0,
    NOW(), NOW()
FROM tasks t
WHERE NOT EXISTS (
    SELECT 1 FROM users u WHERE u.email = t.user_email
);

SELECT CONCAT('✅ Created ', ROW_COUNT(), ' user records') AS status;

-- ============================================================
-- STEP 4: Verify the migration
-- ============================================================

SELECT '🔍 Step 4: Verifying migration...' AS status;

-- Show all collations match
SELECT 
    'tasks' AS table_name,
    'user_email' AS column_name,
    (SELECT Collation FROM information_schema.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'tasks' AND COLUMN_NAME = 'user_email') AS collation
UNION ALL
SELECT 
    'push_subscriptions',
    'user_email',
    (SELECT Collation FROM information_schema.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'push_subscriptions' AND COLUMN_NAME = 'user_email')
UNION ALL
SELECT 
    'notification_logs',
    'user_email',
    (SELECT Collation FROM information_schema.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'notification_logs' AND COLUMN_NAME = 'user_email')
UNION ALL
SELECT 
    'users',
    'email',
    (SELECT Collation FROM information_schema.COLUMNS 
     WHERE TABLE_SCHEMA = DATABASE() AND TABLE_NAME = 'users' AND COLUMN_NAME = 'email');

-- Show created users
SELECT '📋 Users created:' AS status;
SELECT id, email, subscription_status, subscription_plan, sms_credits_limit, ai_requests_limit 
FROM users 
ORDER BY id;

-- Show table counts
SELECT '📊 Table counts:' AS status;
SELECT 'users' AS table_name, COUNT(*) AS row_count FROM users
UNION ALL
SELECT 'tasks', COUNT(*) FROM tasks
UNION ALL
SELECT 'push_subscriptions', COUNT(*) FROM push_subscriptions
UNION ALL
SELECT 'notification_logs', COUNT(*) FROM notification_logs;

-- Commit the transaction
COMMIT;

SELECT '🎉 Migration completed successfully!' AS status;

-- ============================================================
-- POST-MIGRATION: Test query that was failing on GCP
-- ============================================================
-- This query should now work without collation errors

SELECT '🧪 Testing cross-table query...' AS status;

SELECT 
    u.email,
    u.subscription_plan AS plan,
    u.subscription_status AS status,
    (SELECT COUNT(*) FROM tasks WHERE user_email = u.email) AS tasks,
    (SELECT COUNT(*) FROM push_subscriptions WHERE user_email = u.email) AS push_subs,
    (SELECT COUNT(*) FROM notification_logs WHERE user_email = u.email) AS notif_logs,
    u.stripe_customer_id IS NOT NULL AS has_stripe
FROM users u
ORDER BY u.created_at DESC;

SELECT '✅ All tests passed! AWS database is ready for Stripe integration.' AS status;
