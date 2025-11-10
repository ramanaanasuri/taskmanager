//To take a backup
ranasuri@gce-for-fullstack-apps-learning:~/sriinfo/taskmanager$ ./scripts/db_backup.sh 
📦 Creating backup: db/backups/taskmanager_backup_20251110T011150Z.sql
✅ Backup complete: db/backups/taskmanager_backup_20251110T011150Z.sql
//To roll back 
ranasuri@gce-for-fullstack-apps-learning:~/sriinfo/taskmanager$ ./scripts/db_rollback.sh db/backups/taskmanager_backup_20251110T011150Z.sql
⚠️  This will restore 'db/backups/taskmanager_backup_20251110T011150Z.sql' into database 'taskmanager'.
Type 'RESTORE' to proceed: RESTORE
📦 Taking safety backup before restore: db/backups/taskmanager_pre_rollback_20251110T011331Z.sql
♻️  Restoring from db/backups/taskmanager_backup_20251110T011150Z.sql ...
✅ Restore complete.
🔎 Sanity checks:
  Tables_in_taskmanager
  tasks
  
//To update changes related to push notifications, Copy the required sqls into the sql file(For e.g., V5__Add_Push_Notifications.sql) and run below
ranasuri@gce-for-fullstack-apps-learning:~/sriinfo/taskmanager$ docker exec -i $(docker ps -qf name=taskmanager-db) \
  mariadb -uroot -p"$DB_ROOT_PASSWORD" taskmanager < db/migrations/V5__Add_Push_Notifications.sql
  
//To verify if the db is updated for push notification changes
ranasuri@gce-for-fullstack-apps-learning:~/sriinfo/taskmanager$ ./scripts/verifydb_update_for_pushnotifications.sh 
Tables_in_taskmanager (push_subscriptions)
push_subscriptions
Field   Type    Null    Key     Default Extra
id      bigint(20)      NO      PRI     NULL    auto_increment
completed       bit(1)  NO              NULL
created_at      datetime(6)     YES             NULL
title   varchar(255)    NO              NULL
updated_at      datetime(6)     YES             NULL
user_email      varchar(255)    NO              NULL
due_date        datetime        YES     MUL     NULL
priority        enum('LOW','MEDIUM','HIGH')     NO      MUL     MEDIUM
notifications_enabled   tinyint(1)      YES     MUL     0
push_endpoint   text    YES             NULL
Table   Non_unique      Key_name        Seq_in_index    Column_name     Collation       Cardinality     Sub_part        Packed  Null    Index_type      Comment Index_comment   Ignored
push_subscriptions      0       PRIMARY 1       id      A       0       NULL    NULL            BTREE                   NO
push_subscriptions      0       unique_endpoint 1       endpoint        A       0       500     NULL            BTREE                   NO
push_subscriptions      1       idx_user_email  1       user_email      A       0       NULL    NULL            BTREE                   NO
Table   Non_unique      Key_name        Seq_in_index    Column_name     Collation       Cardinality     Sub_part        Packed  Null    Index_type      Comment Index_comment   Ignored
tasks   0       PRIMARY 1       id      A       15      NULL    NULL            BTREE                   NO
tasks   1       idx_due_date    1       due_date        A       15      NULL    NULL    YES     BTREE                   NO
tasks   1       idx_priority    1       priority        A       7       NULL    NULL            BTREE                   NO
tasks   1       idx_notifications_enabled       1       notifications_enabled   A       2       NULL    NULL    YES     BTREE                   NO
ranasuri@gce-for-fullstack-apps-learning:~/sriinfo/taskmanager$ 

//Describe Push subscriptions
docker exec taskmanager-db mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" -e "DESCRIBE push_subscriptions;"

ranasuri@gce-for-fullstack-apps-learning:~/sriinfo/taskmanager$ docker exec taskmanager-db mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" -e "
DESCRIBE push_subscriptions;
"
Field   Type    Null    Key     Default Extra
id      bigint(20)      NO      PRI     NULL    auto_increment
user_email      varchar(255)    NO      MUL     NULL
endpoint        text    NO      UNI     NULL
p256dh  text    NO              NULL
auth    text    NO              NULL
created_at      timestamp       YES             current_timestamp()
updated_at      timestamp       YES             current_timestamp()     on update current_timestamp()

//verification ot tasks table

docker exec taskmanager-db mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" -e "
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = '$DB_NAME' 
  AND TABLE_NAME = 'tasks' 
  AND COLUMN_NAME IN ('notifications_enabled', 'push_endpoint')
ORDER BY ORDINAL_POSITION;
"


ranasuri@gce-for-fullstack-apps-learning:~/sriinfo/taskmanager$ docker exec taskmanager-db mariadb -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME" -e "
SELECT COLUMN_NAME, COLUMN_TYPE, IS_NULLABLE, COLUMN_DEFAULT 
FROM INFORMATION_SCHEMA.COLUMNS 
WHERE TABLE_SCHEMA = '$DB_NAME' 
  AND TABLE_NAME = 'tasks' 
  AND COLUMN_NAME IN ('notifications_enabled', 'push_endpoint')
ORDER BY ORDINAL_POSITION;
"
COLUMN_NAME     COLUMN_TYPE     IS_NULLABLE     COLUMN_DEFAULT
notifications_enabled   tinyint(1)      YES     0
push_endpoint   text    YES     NULL
ranasuri@gce-for-fullstack-apps-learning:~/sriinfo/taskmanager$
