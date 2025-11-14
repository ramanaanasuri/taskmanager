docker exec -i $(docker ps -qf name=taskmanager-db) mariadb -uroot -p"$DB_ROOT_PASSWORD" taskmanager -e "
SHOW TABLES LIKE 'push_subscriptions';
DESCRIBE tasks;
SHOW INDEX FROM push_subscriptions;
SHOW INDEX FROM tasks;"
