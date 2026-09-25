-- InsightHub "Ask a question" topics: which subjects appear in the dropdown.
-- Run:   ./scripts/db_apply_aws.sh db/insighthub_topics.sql   (AWS)
--        ./scripts/db_apply.sh     db/insighthub_topics.sql   (GCP)
-- Then restart the backend so the mentor is granted review rights on the new topics:
--        ./scripts/build.sh 5
-- Safe to run again (no duplicates). A topic shows in the dropdown when active = 1 AND askable = 1.

-- Existing seeded topics that now have KB content behind them
UPDATE skill SET askable = 1 WHERE slug IN ('ai-ml', 'qa-automation');

-- New topics for the other KB areas
INSERT INTO skill (name, slug, active, askable, created_at)
SELECT 'Java & Engineering', 'java-engineering', 1, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE slug = 'java-engineering');

INSERT INTO skill (name, slug, active, askable, created_at)
SELECT 'Payments & Architecture', 'payments-architecture', 1, 1, NOW() FROM DUAL
WHERE NOT EXISTS (SELECT 1 FROM skill WHERE slug = 'payments-architecture');

-- Result: the rows marked askable = 1 are the dropdown entries
SELECT name, slug, active, askable FROM skill ORDER BY askable DESC, name;
