-- ============================================
-- V007__Add_Users_Table_For_Stripe.sql
-- Stripe Payment Integration - Users Table
-- ============================================

-- Create users table to track subscriptions
-- User email is the primary identifier (matches existing pattern in tasks table)
CREATE TABLE IF NOT EXISTS users (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    email VARCHAR(255) NOT NULL UNIQUE,
    name VARCHAR(255),
    picture VARCHAR(500),
    
    -- Stripe fields
    stripe_customer_id VARCHAR(255) UNIQUE,
    subscription_status ENUM('free', 'active', 'canceled', 'past_due', 'trialing') NOT NULL DEFAULT 'free',
    subscription_plan ENUM('free', 'basic', 'pro', 'enterprise') NOT NULL DEFAULT 'free',
    stripe_subscription_id VARCHAR(255),
    subscription_start_date DATETIME,
    subscription_end_date DATETIME,
    
    -- Premium feature limits
    sms_credits_used INT DEFAULT 0,
    sms_credits_limit INT DEFAULT 0,
    ai_requests_used INT DEFAULT 0,
    ai_requests_limit INT DEFAULT 0,
    
    -- Timestamps
    created_at DATETIME DEFAULT CURRENT_TIMESTAMP,
    updated_at DATETIME DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    
    -- Indexes for common queries
    INDEX idx_email (email),
    INDEX idx_stripe_customer_id (stripe_customer_id),
    INDEX idx_subscription_status (subscription_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci;

-- ============================================
-- Subscription Plans Reference:
-- ============================================
-- FREE:       0 SMS, 0 AI requests, basic features
-- BASIC:      10 SMS/month, 50 AI requests/month, $4.99/month
-- PRO:        50 SMS/month, 200 AI requests/month, $9.99/month  
-- ENTERPRISE: Unlimited SMS, Unlimited AI, $29.99/month
-- ============================================
