-- Migration Script: Add Due Date and Priority to Tasks (MINIMAL - NO DESCRIPTION)
-- Version: V002
-- Date: 2025-11-06
-- Description: Adds ONLY due_date and priority columns. No other changes.

-- Add due_date column
ALTER TABLE tasks 
ADD COLUMN due_date DATETIME NULL;

-- Add priority column
ALTER TABLE tasks 
ADD COLUMN priority ENUM('LOW', 'MEDIUM', 'HIGH') DEFAULT 'MEDIUM' NOT NULL;

-- Add indexes
ALTER TABLE tasks 
ADD INDEX idx_due_date (due_date),
ADD INDEX idx_priority (priority);

-- Verify
DESCRIBE tasks;

