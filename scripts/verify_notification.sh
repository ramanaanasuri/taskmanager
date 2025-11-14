docker exec -i $(docker ps -qf name=taskmanager-db) mariadb -uroot -p"$DB_ROOT_PASSWORD" taskmanager -e "
SELECT 
    id,
    user_email,
    LEFT(endpoint, 60) as endpoint_preview,
    created_at
FROM push_subscriptions 
WHERE user_email = 'ranasuri@gmail.com'
ORDER BY created_at DESC
LIMIT 20;"
