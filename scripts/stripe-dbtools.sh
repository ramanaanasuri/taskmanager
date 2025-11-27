#!/usr/bin/env bash
# ============================================
# Stripe Integration DB Tool
# TaskManager - SriInfosoft Inc
# ============================================
# Menu-driven database tool for Stripe payment integration
# - View users and subscription data
# - Validate webhook updates
# - Debug and troubleshoot subscription issues
# - Clean up test data

set -euo pipefail

# ------------------------------
# Defaults & .env loading
# ------------------------------
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
ENV_FILE="$ROOT_DIR/.env"
CONTAINER="${CONTAINER:-taskmanager-db}"

# Load .env if present
if [[ -f "$ENV_FILE" ]]; then
  set -a
  # shellcheck disable=SC1090
  source "$ENV_FILE"
  set +a
fi

DB_NAME="${DB_NAME:-taskmanager}"
DB_USER="${DB_USER:-taskuser}"
DB_PASS="${DB_PASSWORD:-taskpassword}"

# ------------------------------
# CLI flags
# ------------------------------
SHOW_HELP=0
OVERRIDE_DB_NAME=""
OVERRIDE_CONTAINER=""
OVERRIDE_ROOT_PW=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    -h|--help) SHOW_HELP=1; shift;;
    -d|--db)   OVERRIDE_DB_NAME="${2:-}"; shift 2;;
    -c|--container) OVERRIDE_CONTAINER="${2:-}"; shift 2;;
    -p|--root-pw)   OVERRIDE_ROOT_PW="${2:-}"; shift 2;;
    *) echo "Unknown arg: $1"; SHOW_HELP=1; shift;;
  esac
done

if [[ $SHOW_HELP -eq 1 ]]; then
  cat <<EOF
╔══════════════════════════════════════════════════════════════════╗
║           Stripe Integration DB Tool - Help                      ║
╚══════════════════════════════════════════════════════════════════╝

Usage: ./scripts/stripe-dbtools.sh [options]

Options:
  -d, --db <name>         Override DB name (default: ${DB_NAME})
  -c, --container <name>  DB container name (default: ${CONTAINER})
  -p, --root-pw <value>   MariaDB root password (otherwise prompted)
  -h, --help              Show this help message

Environment (from .env if present):
  DB_NAME, DB_USER, DB_PASSWORD, DB_ROOT_PASSWORD

Examples:
  ./scripts/stripe-dbtools.sh
  ./scripts/stripe-dbtools.sh -p mypassword
  CONTAINER=mydb ./scripts/stripe-dbtools.sh
EOF
  exit 0
fi

[[ -n "$OVERRIDE_DB_NAME" ]] && DB_NAME="$OVERRIDE_DB_NAME"
[[ -n "$OVERRIDE_CONTAINER" ]] && CONTAINER="$OVERRIDE_CONTAINER"
if [[ -n "$OVERRIDE_ROOT_PW" ]]; then
  DB_ROOT_PASSWORD="$OVERRIDE_ROOT_PW"
fi

# ------------------------------
# Require/Prompt for root password
# ------------------------------
if [[ -z "${DB_ROOT_PASSWORD:-}" ]]; then
  read -rs -p "Enter MariaDB root password: " DB_ROOT_PASSWORD; echo
fi

# ------------------------------
# Helpers
# ------------------------------
sql_escape() { printf "%s" "$1" | sed "s/'/''/g"; }

db() {
  docker exec -i "$CONTAINER" mariadb \
    --protocol=TCP -h127.0.0.1 -P3306 \
    -uroot -p"$DB_ROOT_PASSWORD" "$@"
}

pretty() {
  docker exec -it "$CONTAINER" mariadb \
    --protocol=TCP -h127.0.0.1 -P3306 \
    -uroot -p"$DB_ROOT_PASSWORD" -t "$@"
}

pause() { 
  echo ""
  read -rp "Press Enter to continue…"; 
}

print_header() {
  echo ""
  echo "╔══════════════════════════════════════════════════════════════════╗"
  echo "║  $1"
  echo "╚══════════════════════════════════════════════════════════════════╝"
  echo ""
}

print_success() {
  echo "✅ $1"
}

print_error() {
  echo "❌ $1"
}

print_warning() {
  echo "⚠️  $1"
}

print_info() {
  echo "ℹ️  $1"
}

# ------------------------------
# Actions: Table Structure
# ------------------------------
action_check_users_table() {
  print_header "Check if Users Table Exists"
  pretty -e "USE \`$DB_NAME\`; SHOW TABLES LIKE 'users';"
}

action_describe_users() {
  print_header "Users Table Structure"
  pretty -e "USE \`$DB_NAME\`; DESCRIBE users;"
}

action_show_all_tables() {
  print_header "All Tables in $DB_NAME"
  pretty -e "USE \`$DB_NAME\`; SHOW TABLES;"
}

# ------------------------------
# Actions: View Users
# ------------------------------
action_list_all_users() {
  print_header "All Users with Subscription Info"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      id,
      email,
      name,
      subscription_status AS status,
      subscription_plan AS plan,
      sms_credits_used AS sms_used,
      sms_credits_limit AS sms_limit,
      ai_requests_used AS ai_used,
      ai_requests_limit AS ai_limit,
      DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created
    FROM users
    ORDER BY created_at DESC;"
}

action_list_users_full() {
  print_header "Full User Details (All Columns)"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      id,
      email,
      name,
      picture,
      stripe_customer_id,
      stripe_subscription_id,
      subscription_status,
      subscription_plan,
      sms_credits_used,
      sms_credits_limit,
      ai_requests_used,
      ai_requests_limit,
      DATE_FORMAT(subscription_start_date, '%Y-%m-%d %H:%i') AS sub_start,
      DATE_FORMAT(subscription_end_date, '%Y-%m-%d %H:%i') AS sub_end,
      DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created,
      DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i') AS updated
    FROM users
    ORDER BY created_at DESC;"
}

action_find_user_by_email() {
  print_header "Find User by Email"
  local email
  read -rp "Enter email address: " email
  
  if [[ -z "$email" ]]; then
    print_error "Email is required"
    return 1
  fi
  
  pretty -e "USE \`$DB_NAME\`;
    SELECT * FROM users WHERE email = '$(sql_escape "$email")';"
}

action_find_user_by_customer_id() {
  print_header "Find User by Stripe Customer ID"
  local customer_id
  read -rp "Enter Stripe Customer ID (cus_xxx): " customer_id
  
  if [[ -z "$customer_id" ]]; then
    print_error "Customer ID is required"
    return 1
  fi
  
  pretty -e "USE \`$DB_NAME\`;
    SELECT * FROM users WHERE stripe_customer_id = '$(sql_escape "$customer_id")';"
}

# ------------------------------
# Actions: Subscription Views
# ------------------------------
action_list_premium_users() {
  print_header "Premium Users (Active Subscriptions)"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      email,
      name,
      subscription_status AS status,
      subscription_plan AS plan,
      stripe_customer_id AS cus_id,
      stripe_subscription_id AS sub_id,
      CONCAT(sms_credits_used, '/', sms_credits_limit) AS sms,
      CONCAT(ai_requests_used, '/', ai_requests_limit) AS ai
    FROM users 
    WHERE subscription_status IN ('active', 'trialing')
    ORDER BY subscription_plan, created_at DESC;"
}

action_list_free_users() {
  print_header "Free Users (No Active Subscription)"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      email,
      name,
      subscription_status AS status,
      stripe_customer_id AS cus_id,
      DATE_FORMAT(created_at, '%Y-%m-%d %H:%i') AS created
    FROM users 
    WHERE subscription_status = 'free' 
       OR subscription_status IS NULL
    ORDER BY created_at DESC;"
}

action_list_stripe_customers() {
  print_header "Users with Stripe Customer ID"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      email,
      stripe_customer_id AS customer_id,
      stripe_subscription_id AS subscription_id,
      subscription_status AS status,
      subscription_plan AS plan
    FROM users 
    WHERE stripe_customer_id IS NOT NULL
    ORDER BY created_at DESC;"
}

action_subscription_by_plan() {
  print_header "Users by Subscription Plan"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      subscription_plan AS plan,
      COUNT(*) AS user_count,
      GROUP_CONCAT(email SEPARATOR ', ') AS emails
    FROM users
    GROUP BY subscription_plan
    ORDER BY 
      CASE subscription_plan 
        WHEN 'enterprise' THEN 1 
        WHEN 'pro' THEN 2 
        WHEN 'basic' THEN 3 
        WHEN 'free' THEN 4 
      END;"
}

action_subscription_by_status() {
  print_header "Users by Subscription Status"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      subscription_status AS status,
      COUNT(*) AS user_count,
      GROUP_CONCAT(email SEPARATOR ', ') AS emails
    FROM users
    GROUP BY subscription_status;"
}

# ------------------------------
# Actions: Usage & Credits
# ------------------------------
action_check_usage() {
  print_header "Credit Usage Summary"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      email,
      subscription_plan AS plan,
      sms_credits_used AS sms_used,
      sms_credits_limit AS sms_limit,
      CASE 
        WHEN sms_credits_limit = 0 THEN 'N/A'
        WHEN sms_credits_limit = 2147483647 THEN 'Unlimited'
        ELSE CONCAT(ROUND((sms_credits_used / sms_credits_limit) * 100, 1), '%')
      END AS sms_pct,
      ai_requests_used AS ai_used,
      ai_requests_limit AS ai_limit,
      CASE 
        WHEN ai_requests_limit = 0 THEN 'N/A'
        WHEN ai_requests_limit = 2147483647 THEN 'Unlimited'
        ELSE CONCAT(ROUND((ai_requests_used / ai_requests_limit) * 100, 1), '%')
      END AS ai_pct
    FROM users
    WHERE subscription_status = 'active'
    ORDER BY subscription_plan;"
}

action_users_near_limit() {
  print_header "Users Near Credit Limits (>80% used)"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      email,
      subscription_plan AS plan,
      CONCAT(sms_credits_used, '/', sms_credits_limit) AS sms,
      CONCAT(ai_requests_used, '/', ai_requests_limit) AS ai,
      'SMS' AS limit_type
    FROM users
    WHERE sms_credits_limit > 0 
      AND sms_credits_limit < 2147483647
      AND (sms_credits_used / sms_credits_limit) >= 0.8
    UNION ALL
    SELECT 
      email,
      subscription_plan,
      CONCAT(sms_credits_used, '/', sms_credits_limit),
      CONCAT(ai_requests_used, '/', ai_requests_limit),
      'AI'
    FROM users
    WHERE ai_requests_limit > 0 
      AND ai_requests_limit < 2147483647
      AND (ai_requests_used / ai_requests_limit) >= 0.8;"
}

# ------------------------------
# Actions: Health Check
# ------------------------------
action_health_check() {
  print_header "Stripe Integration Health Check"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      'Users Table' AS check_item,
      COUNT(*) AS count,
      CASE WHEN COUNT(*) > 0 THEN '✅ OK' ELSE '⚠️ Empty' END AS status
    FROM users
    UNION ALL
    SELECT 
      'Premium Users',
      COUNT(*),
      CASE WHEN COUNT(*) > 0 THEN '✅ Has Premium' ELSE '📋 No Premium Yet' END
    FROM users WHERE subscription_status = 'active'
    UNION ALL
    SELECT 
      'Stripe Customers',
      COUNT(*),
      CASE WHEN COUNT(*) > 0 THEN '✅ Stripe Connected' ELSE '⚠️ No Stripe' END
    FROM users WHERE stripe_customer_id IS NOT NULL
    UNION ALL
    SELECT 
      'With Subscription ID',
      COUNT(*),
      CASE WHEN COUNT(*) > 0 THEN '✅ Subscriptions' ELSE '📋 No Subs' END
    FROM users WHERE stripe_subscription_id IS NOT NULL
    UNION ALL
    SELECT 
      'Basic Plan',
      COUNT(*),
      CASE WHEN COUNT(*) > 0 THEN '✅' ELSE '—' END
    FROM users WHERE subscription_plan = 'basic'
    UNION ALL
    SELECT 
      'Pro Plan',
      COUNT(*),
      CASE WHEN COUNT(*) > 0 THEN '✅' ELSE '—' END
    FROM users WHERE subscription_plan = 'pro'
    UNION ALL
    SELECT 
      'Enterprise Plan',
      COUNT(*),
      CASE WHEN COUNT(*) > 0 THEN '✅' ELSE '—' END
    FROM users WHERE subscription_plan = 'enterprise';"
}

action_recent_updates() {
  print_header "Recent Subscription Updates (Last 24 Hours)"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      email,
      subscription_status AS status,
      subscription_plan AS plan,
      DATE_FORMAT(updated_at, '%Y-%m-%d %H:%i:%s') AS updated
    FROM users
    WHERE updated_at >= NOW() - INTERVAL 24 HOUR
    ORDER BY updated_at DESC;"
}

# ------------------------------
# Actions: Cross-Table Validation
# ------------------------------
action_user_task_summary() {
  print_header "User-Task Relationship Summary"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      u.email,
      u.subscription_plan AS plan,
      u.subscription_status AS status,
      COUNT(t.id) AS task_count
    FROM users u
    LEFT JOIN tasks t ON u.email = t.user_email
    GROUP BY u.email, u.subscription_plan, u.subscription_status
    ORDER BY task_count DESC;"
}

action_orphaned_tasks() {
  print_header "Orphaned Tasks (No Matching User)"
  pretty -e "USE \`$DB_NAME\`;
    SELECT DISTINCT 
      t.user_email,
      COUNT(*) AS task_count,
      'No user record' AS issue
    FROM tasks t
    LEFT JOIN users u ON t.user_email = u.email
    WHERE u.id IS NULL
    GROUP BY t.user_email;"
}

action_full_user_summary() {
  print_header "Complete User Summary (All Related Data)"
  pretty -e "USE \`$DB_NAME\`;
    SELECT 
      u.email,
      u.subscription_plan AS plan,
      u.subscription_status AS status,
      (SELECT COUNT(*) FROM tasks WHERE user_email = u.email) AS tasks,
      (SELECT COUNT(*) FROM push_subscriptions WHERE user_email = u.email) AS push_subs,
      (SELECT COUNT(*) FROM notification_logs WHERE user_email = u.email) AS notif_logs,
      u.stripe_customer_id IS NOT NULL AS has_stripe
    FROM users u
    ORDER BY u.created_at DESC;"
}

# ------------------------------
# Actions: Modify Data
# ------------------------------
action_update_subscription_manual() {
  print_header "Manually Update User Subscription"
  
  local email status plan sms_limit ai_limit sub_id
  
  read -rp "Enter user email: " email
  if [[ -z "$email" ]]; then
    print_error "Email is required"
    return 1
  fi
  
  # Check if user exists
  local exists
  exists=$(db -N -e "USE \`$DB_NAME\`; SELECT COUNT(*) FROM users WHERE email = '$(sql_escape "$email")';" | tr -d '[:space:]')
  
  if [[ "$exists" == "0" ]]; then
    print_error "User not found: $email"
    return 1
  fi
  
  echo ""
  echo "Select subscription status:"
  echo "  1) free"
  echo "  2) active"
  echo "  3) trialing"
  echo "  4) past_due"
  echo "  5) canceled"
  read -rp "Enter choice (1-5): " status_choice
  
  case "$status_choice" in
    1) status="free";;
    2) status="active";;
    3) status="trialing";;
    4) status="past_due";;
    5) status="canceled";;
    *) print_error "Invalid choice"; return 1;;
  esac
  
  echo ""
  echo "Select subscription plan:"
  echo "  1) free      (0 SMS, 0 AI)"
  echo "  2) basic     (10 SMS, 50 AI)"
  echo "  3) pro       (50 SMS, 200 AI)"
  echo "  4) enterprise (Unlimited)"
  read -rp "Enter choice (1-4): " plan_choice
  
  case "$plan_choice" in
    1) plan="free"; sms_limit=0; ai_limit=0;;
    2) plan="basic"; sms_limit=10; ai_limit=50;;
    3) plan="pro"; sms_limit=50; ai_limit=200;;
    4) plan="enterprise"; sms_limit=2147483647; ai_limit=2147483647;;
    *) print_error "Invalid choice"; return 1;;
  esac
  
  read -rp "Enter Stripe Subscription ID (optional, press Enter to skip): " sub_id
  
  echo ""
  print_info "Updating user: $email"
  print_info "  Status: $status"
  print_info "  Plan: $plan"
  print_info "  SMS Limit: $sms_limit"
  print_info "  AI Limit: $ai_limit"
  [[ -n "$sub_id" ]] && print_info "  Subscription ID: $sub_id"
  echo ""
  
  read -rp "Confirm update? (y/N): " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    print_warning "Update cancelled"
    return 0
  fi
  
  local sub_id_sql=""
  if [[ -n "$sub_id" ]]; then
    sub_id_sql=", stripe_subscription_id = '$(sql_escape "$sub_id")'"
  fi
  
  db -e "USE \`$DB_NAME\`;
    UPDATE users SET
      subscription_status = '$status',
      subscription_plan = '$plan',
      sms_credits_limit = $sms_limit,
      ai_requests_limit = $ai_limit,
      updated_at = NOW()
      $sub_id_sql
    WHERE email = '$(sql_escape "$email")';"
  
  print_success "User updated successfully!"
  echo ""
  
  # Show updated record
  pretty -e "USE \`$DB_NAME\`;
    SELECT email, subscription_status, subscription_plan, sms_credits_limit, ai_requests_limit
    FROM users WHERE email = '$(sql_escape "$email")';"
}

action_reset_usage() {
  print_header "Reset Monthly Usage for User"
  
  local email
  read -rp "Enter user email: " email
  
  if [[ -z "$email" ]]; then
    print_error "Email is required"
    return 1
  fi
  
  read -rp "Reset SMS and AI usage to 0 for $email? (y/N): " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    print_warning "Reset cancelled"
    return 0
  fi
  
  db -e "USE \`$DB_NAME\`;
    UPDATE users SET
      sms_credits_used = 0,
      ai_requests_used = 0,
      updated_at = NOW()
    WHERE email = '$(sql_escape "$email")';"
  
  print_success "Usage reset for $email"
  
  pretty -e "USE \`$DB_NAME\`;
    SELECT email, sms_credits_used, sms_credits_limit, ai_requests_used, ai_requests_limit
    FROM users WHERE email = '$(sql_escape "$email")';"
}

action_reset_all_usage() {
  print_header "Reset Monthly Usage for ALL Users"
  
  print_warning "This will reset SMS and AI usage counters for ALL users!"
  read -rp "Are you sure? (y/N): " confirm
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    print_warning "Reset cancelled"
    return 0
  fi
  
  db -e "USE \`$DB_NAME\`;
    UPDATE users SET
      sms_credits_used = 0,
      ai_requests_used = 0,
      updated_at = NOW();"
  
  print_success "Usage reset for all users"
  
  pretty -e "USE \`$DB_NAME\`;
    SELECT email, sms_credits_used, ai_requests_used FROM users;"
}

# ------------------------------
# Actions: Delete Data
# ------------------------------
action_delete_user() {
  print_header "Delete User (For Re-testing)"
  
  local email
  read -rp "Enter user email to delete: " email
  
  if [[ -z "$email" ]]; then
    print_error "Email is required"
    return 1
  fi
  
  # Show user first
  echo ""
  print_info "User to be deleted:"
  pretty -e "USE \`$DB_NAME\`;
    SELECT id, email, stripe_customer_id, subscription_status, subscription_plan
    FROM users WHERE email = '$(sql_escape "$email")';"
  
  echo ""
  print_warning "This will permanently delete this user!"
  print_warning "Remember to also delete/cancel in Stripe Dashboard!"
  read -rp "Are you sure? (y/N): " confirm
  
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    print_warning "Delete cancelled"
    return 0
  fi
  
  db -e "USE \`$DB_NAME\`;
    DELETE FROM users WHERE email = '$(sql_escape "$email")';"
  
  print_success "User deleted: $email"
  
  pretty -e "USE \`$DB_NAME\`;
    SELECT ROW_COUNT() AS deleted_rows;"
}

action_delete_all_users() {
  print_header "Delete ALL Users"
  
  print_error "⚠️  WARNING: This will delete ALL users from the database!"
  print_warning "Remember to also clean up in Stripe Dashboard!"
  echo ""
  
  read -rp "Type 'DELETE ALL' to confirm: " confirm
  
  if [[ "$confirm" != "DELETE ALL" ]]; then
    print_warning "Delete cancelled"
    return 0
  fi
  
  local count
  count=$(db -N -e "USE \`$DB_NAME\`; SELECT COUNT(*) FROM users;" | tr -d '[:space:]')
  
  db -e "USE \`$DB_NAME\`; DELETE FROM users;"
  
  print_success "Deleted $count users"
  
  # Reset auto-increment
  db -e "USE \`$DB_NAME\`; ALTER TABLE users AUTO_INCREMENT = 1;"
  print_info "Auto-increment reset to 1"
}

action_clear_stripe_data() {
  print_header "Clear Stripe Data (Keep User)"
  
  local email
  read -rp "Enter user email: " email
  
  if [[ -z "$email" ]]; then
    print_error "Email is required"
    return 1
  fi
  
  print_warning "This will clear Stripe IDs and reset to free plan"
  read -rp "Continue? (y/N): " confirm
  
  if [[ "$confirm" != "y" && "$confirm" != "Y" ]]; then
    print_warning "Cancelled"
    return 0
  fi
  
  db -e "USE \`$DB_NAME\`;
    UPDATE users SET
      stripe_customer_id = NULL,
      stripe_subscription_id = NULL,
      subscription_status = 'free',
      subscription_plan = 'free',
      sms_credits_used = 0,
      sms_credits_limit = 0,
      ai_requests_used = 0,
      ai_requests_limit = 0,
      subscription_start_date = NULL,
      subscription_end_date = NULL,
      updated_at = NOW()
    WHERE email = '$(sql_escape "$email")';"
  
  print_success "Stripe data cleared for $email"
  
  pretty -e "USE \`$DB_NAME\`;
    SELECT email, stripe_customer_id, subscription_status, subscription_plan
    FROM users WHERE email = '$(sql_escape "$email")';"
}

# ------------------------------
# Actions: Create Test User
# ------------------------------
action_create_test_user() {
  print_header "Create Test User"
  
  local email name
  read -rp "Enter email: " email
  read -rp "Enter name (optional): " name
  
  if [[ -z "$email" ]]; then
    print_error "Email is required"
    return 1
  fi
  
  [[ -z "$name" ]] && name="Test User"
  
  # Check if exists
  local exists
  exists=$(db -N -e "USE \`$DB_NAME\`; SELECT COUNT(*) FROM users WHERE email = '$(sql_escape "$email")';" | tr -d '[:space:]')
  
  if [[ "$exists" != "0" ]]; then
    print_error "User already exists: $email"
    return 1
  fi
  
  db -e "USE \`$DB_NAME\`;
    INSERT INTO users (email, name, subscription_status, subscription_plan, 
                       sms_credits_used, sms_credits_limit, ai_requests_used, ai_requests_limit,
                       created_at, updated_at)
    VALUES ('$(sql_escape "$email")', '$(sql_escape "$name")', 'free', 'free',
            0, 0, 0, 0, NOW(), NOW());"
  
  print_success "Test user created: $email"
  
  pretty -e "USE \`$DB_NAME\`;
    SELECT * FROM users WHERE email = '$(sql_escape "$email")';"
}

# ------------------------------
# Actions: Raw SQL
# ------------------------------
action_raw_sql() {
  print_header "Interactive SQL Shell"
  
  echo "Opening MariaDB shell for database: $DB_NAME"
  echo ""
  echo "Useful commands:"
  echo "  SELECT * FROM users;"
  echo "  SELECT * FROM users WHERE subscription_status = 'active';"
  echo "  DESCRIBE users;"
  echo "  Type 'exit' or 'quit' to return to menu"
  echo ""
  read -rp "Press Enter to open SQL shell..."
  
  docker exec -it "$CONTAINER" mariadb \
    --protocol=TCP -h127.0.0.1 -P3306 \
    -uroot -p"$DB_ROOT_PASSWORD" "$DB_NAME"
  
  print_success "SQL shell closed"
}

# ------------------------------
# Stripe Dashboard Links
# ------------------------------
action_show_stripe_links() {
  print_header "Stripe Dashboard Links"
  
  cat <<EOF
╔══════════════════════════════════════════════════════════════════╗
║                    STRIPE DASHBOARD LINKS                        ║
╠══════════════════════════════════════════════════════════════════╣
║                                                                  ║
║  📊 Main Dashboard                                               ║
║     https://dashboard.stripe.com/test                            ║
║                                                                  ║
║  🔑 API Keys                                                     ║
║     https://dashboard.stripe.com/test/apikeys                    ║
║                                                                  ║
║  📦 Products                                                     ║
║     https://dashboard.stripe.com/test/products                   ║
║                                                                  ║
║  💳 Subscriptions                                                ║
║     https://dashboard.stripe.com/test/subscriptions              ║
║                                                                  ║
║  👥 Customers                                                    ║
║     https://dashboard.stripe.com/test/customers                  ║
║                                                                  ║
║  🔗 Webhooks                                                     ║
║     https://dashboard.stripe.com/test/webhooks                   ║
║                                                                  ║
║  📋 Events                                                       ║
║     https://dashboard.stripe.com/test/events                     ║
║                                                                  ║
║  📜 Logs                                                         ║
║     https://dashboard.stripe.com/test/logs                       ║
║                                                                  ║
╚══════════════════════════════════════════════════════════════════╝

Test Card Numbers:
  ✅ Success:     4242 4242 4242 4242
  ❌ Declined:    4000 0000 0000 0002
  🔐 3D Secure:   4000 0025 0000 3155

EOF
}

# ------------------------------
# Menu
# ------------------------------
while true; do
  clear
  cat <<EOF
╔══════════════════════════════════════════════════════════════════╗
║        💳 STRIPE INTEGRATION DB TOOL                             ║
║        Container: $CONTAINER | DB: $DB_NAME
╚══════════════════════════════════════════════════════════════════╝

═══════════════════════════════════════════════════════════════════
  📋 TABLE STRUCTURE
═══════════════════════════════════════════════════════════════════
  1)  Check if users table exists
  2)  Describe users table structure
  3)  Show all tables in database

═══════════════════════════════════════════════════════════════════
  👥 VIEW USERS
═══════════════════════════════════════════════════════════════════
  10) List all users (summary)
  11) List all users (full details)
  12) Find user by email
  13) Find user by Stripe Customer ID

═══════════════════════════════════════════════════════════════════
  💎 SUBSCRIPTION VIEWS
═══════════════════════════════════════════════════════════════════
  20) List premium users (active subscriptions)
  21) List free users (no subscription)
  22) List users with Stripe Customer ID
  23) Group users by subscription plan
  24) Group users by subscription status

═══════════════════════════════════════════════════════════════════
  📊 USAGE & CREDITS
═══════════════════════════════════════════════════════════════════
  30) Check credit usage (SMS/AI)
  31) Find users near credit limits (>80%)

═══════════════════════════════════════════════════════════════════
  🏥 HEALTH CHECKS
═══════════════════════════════════════════════════════════════════
  40) Run health check
  41) Show recent updates (last 24h)
  42) User-task relationship summary
  43) Find orphaned tasks
  44) Full user summary (with related data)

═══════════════════════════════════════════════════════════════════
  ✏️  MODIFY DATA
═══════════════════════════════════════════════════════════════════
  50) Manually update user subscription
  51) Reset usage for user
  52) Reset usage for ALL users
  53) Create test user

═══════════════════════════════════════════════════════════════════
  🗑️  DELETE DATA
═══════════════════════════════════════════════════════════════════
  60) Delete user by email
  61) Delete ALL users (⚠️ dangerous!)
  62) Clear Stripe data (keep user)

═══════════════════════════════════════════════════════════════════
  🔧 OTHER
═══════════════════════════════════════════════════════════════════
  r)  Raw SQL shell
  s)  Show Stripe Dashboard links & test cards
  q)  Quit

EOF
  read -rp "Choose an option: " choice
  
  case "$choice" in
    # Table Structure
    1)  action_check_users_table; pause;;
    2)  action_describe_users; pause;;
    3)  action_show_all_tables; pause;;
    
    # View Users
    10) action_list_all_users; pause;;
    11) action_list_users_full; pause;;
    12) action_find_user_by_email; pause;;
    13) action_find_user_by_customer_id; pause;;
    
    # Subscription Views
    20) action_list_premium_users; pause;;
    21) action_list_free_users; pause;;
    22) action_list_stripe_customers; pause;;
    23) action_subscription_by_plan; pause;;
    24) action_subscription_by_status; pause;;
    
    # Usage & Credits
    30) action_check_usage; pause;;
    31) action_users_near_limit; pause;;
    
    # Health Checks
    40) action_health_check; pause;;
    41) action_recent_updates; pause;;
    42) action_user_task_summary; pause;;
    43) action_orphaned_tasks; pause;;
    44) action_full_user_summary; pause;;
    
    # Modify Data
    50) action_update_subscription_manual; pause;;
    51) action_reset_usage; pause;;
    52) action_reset_all_usage; pause;;
    53) action_create_test_user; pause;;
    
    # Delete Data
    60) action_delete_user; pause;;
    61) action_delete_all_users; pause;;
    62) action_clear_stripe_data; pause;;
    
    # Other
    r|R) action_raw_sql; pause;;
    s|S) action_show_stripe_links; pause;;
    q|Q) echo "Goodbye!"; exit 0;;
    
    *) print_error "Invalid option: $choice"; sleep 1;;
  esac
done