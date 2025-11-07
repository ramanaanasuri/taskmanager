-- 1) Add due date and priority to tasks (non-breaking; default null/LOW)
ALTER TABLE tasks
  ADD COLUMN due_date DATETIME NULL AFTER updated_at,
  ADD COLUMN priority ENUM('LOW','MEDIUM','HIGH') NOT NULL DEFAULT 'LOW' AFTER due_date,
  ADD COLUMN tags VARCHAR(255) NULL AFTER priority;  -- comma-separated to keep schema minimal

-- 2) Create task_comments table (1:N)
CREATE TABLE IF NOT EXISTS task_comments (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  task_id BIGINT NOT NULL,
  author_email VARCHAR(255) NOT NULL,   -- reuse your existing user_email identity
  body TEXT NOT NULL,
  created_at DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  CONSTRAINT fk_task_comments_task FOREIGN KEY (task_id) REFERENCES tasks(id) ON DELETE CASCADE
);

-- 3) Helpful composite indexes
CREATE INDEX idx_tasks_user_priority_due ON tasks(user_email, priority, due_date);
CREATE INDEX idx_tasks_tags ON tasks(tags);
CREATE INDEX idx_task_comments_task ON task_comments(task_id);

