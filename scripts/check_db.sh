#!/bin/bash
set -a; source .env; set +a
echo "=== Task Manager Database Check ==="
echo ""

echo "1. Table Structure:"
docker exec -i taskmanager-db mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" -e "DESCRIBE tasks;"
echo ""

echo "2. All Tasks:"
docker exec -i taskmanager-db mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" -e "
SELECT 
    id,
    title,
    priority,
    DATE_FORMAT(due_date, '%Y-%m-%d %H:%i') as scheduled,
    completed
FROM tasks 
ORDER BY priority DESC, due_date ASC;"
echo ""

echo "3. Tasks by Priority:"
docker exec -i taskmanager-db mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" -e "
SELECT 
    priority, 
    COUNT(*) as count 
FROM tasks 
GROUP BY priority;"
echo ""

echo "Done!"
