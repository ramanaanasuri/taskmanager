#!/bin/bash
# quick_verify.sh - Simple verification (no password needed for MariaDB default setup)

echo "🔍 Quick Migration Verification"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo ""

# Find database container
DB_CONTAINER=$(docker ps --format '{{.Names}}' | grep -i "db\|maria\|mysql" | head -1)

if [ -z "$DB_CONTAINER" ]; then
    echo "❌ Could not find database container"
    echo "Available containers:"
    docker ps --format "  {{.Names}}"
    exit 1
fi

echo "📦 Using database container: $DB_CONTAINER"
echo ""

# Check 1: Column exists
echo "1️⃣ Checking if 'created_from_ip' column exists..."
docker exec $DB_CONTAINER mysql -u root taskmanager -e "SHOW COLUMNS FROM push_subscriptions LIKE 'created_from_ip';" 2>/dev/null

if [ $? -eq 0 ]; then
    echo "   ✅ Column exists"
else
    echo "   ❌ Column NOT found"
    exit 1
fi
echo ""

# Check 2: Table structure
echo "2️⃣ Current table structure:"
docker exec $DB_CONTAINER mysql -u root taskmanager -e "DESCRIBE push_subscriptions;" 2>/dev/null
echo ""

# Check 3: Index exists
echo "3️⃣ Checking for index..."
docker exec $DB_CONTAINER mysql -u root taskmanager -e "SHOW INDEX FROM push_subscriptions WHERE Key_name='idx_push_subscriptions_ip';" 2>/dev/null
echo ""

# Check 4: Sample data
echo "4️⃣ Sample subscriptions with IP addresses:"
docker exec $DB_CONTAINER mysql -u root taskmanager -e "SELECT id, LEFT(user_email, 25) as user_email, browser, os, created_from_ip, DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') as created FROM push_subscriptions ORDER BY created_at DESC LIMIT 5;" 2>/dev/null
echo ""

# Check 5: Statistics
echo "5️⃣ Statistics:"
docker exec $DB_CONTAINER mysql -u root taskmanager -e "
    SELECT 
        COUNT(*) as total_subscriptions,
        SUM(CASE WHEN created_from_ip IS NOT NULL THEN 1 ELSE 0 END) as with_ip,
        SUM(CASE WHEN created_from_ip IS NULL THEN 1 ELSE 0 END) as without_ip
    FROM push_subscriptions;
" 2>/dev/null
echo ""

echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
echo "✅ Migration verification complete!"
echo ""
echo "Next: Deploy updated code and test subscription creation"
echo "━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━"
