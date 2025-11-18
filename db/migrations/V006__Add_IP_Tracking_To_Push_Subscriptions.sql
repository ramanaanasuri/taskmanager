-- Add created_from_ip column
ALTER TABLE push_subscriptions 
ADD COLUMN created_from_ip VARCHAR(45) NULL 
COMMENT 'IP address from which subscription was created (supports IPv4 and IPv6)';

-- Add index for faster queries
CREATE INDEX idx_push_subscriptions_ip ON push_subscriptions(created_from_ip);
