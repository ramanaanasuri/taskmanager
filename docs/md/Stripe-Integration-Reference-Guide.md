# Stripe Payment Integration Reference Guide
## Task Manager Pro - SriInfosoft Inc

---

## Table of Contents

1. [Stripe Dashboard URLs](#stripe-dashboard-urls)
2. [Environment Variables](#environment-variables)
3. [Product vs Price ID](#product-vs-price-id)
4. [Webhook Configuration](#webhook-configuration)
5. [Test Card Numbers](#test-card-numbers)
6. [API Endpoints](#api-endpoints)
7. [Database Queries](#database-queries)
8. [Subscription Plans](#subscription-plans)
9. [Troubleshooting](#troubleshooting)
10. [Deployment Checklist](#deployment-checklist)

---

## Stripe Dashboard URLs

### Main Dashboard
| Page | URL |
|------|-----|
| Home | https://dashboard.stripe.com |
| Test Mode | https://dashboard.stripe.com/test |
| Live Mode | https://dashboard.stripe.com/live |

### API & Configuration
| Page | URL |
|------|-----|
| API Keys | https://dashboard.stripe.com/test/apikeys |
| Webhooks | https://dashboard.stripe.com/test/webhooks |
| Webhook Events | https://dashboard.stripe.com/test/webhooks/{webhook_id} |

### Products & Subscriptions
| Page | URL |
|------|-----|
| Products | https://dashboard.stripe.com/test/products |
| Subscriptions | https://dashboard.stripe.com/test/subscriptions |
| Customers | https://dashboard.stripe.com/test/customers |
| Invoices | https://dashboard.stripe.com/test/invoices |
| Payments | https://dashboard.stripe.com/test/payments |

### Developer Tools
| Page | URL |
|------|-----|
| Events | https://dashboard.stripe.com/test/events |
| Logs | https://dashboard.stripe.com/test/logs |
| Workbench | https://dashboard.stripe.com/test/workbench |

---

## Environment Variables

### Required Stripe Variables

```bash
# ============================================
# STRIPE PAYMENT INTEGRATION
# ============================================

# API Secret Key (from Dashboard → API Keys → Secret key)
# Test keys start with: sk_test_
# Live keys start with: sk_live_
STRIPE_SECRET_KEY=sk_test_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Webhook Signing Secret (from Dashboard → Webhooks → Signing secret)
# Starts with: whsec_
STRIPE_WEBHOOK_SECRET=whsec_xxxxxxxxxxxxxxxxxxxxxxxxxxxxxxxx

# Price IDs (from Dashboard → Products → [Product] → Pricing)
# IMPORTANT: Use PRICE IDs (price_xxx), NOT Product IDs (prod_xxx)
STRIPE_PRICE_BASIC=price_xxxxxxxxxxxxxxxxxxxxxxxx
STRIPE_PRICE_PRO=price_xxxxxxxxxxxxxxxxxxxxxxxx
STRIPE_PRICE_ENTERPRISE=price_xxxxxxxxxxxxxxxxxxxxxxxx
```

### Multi-Environment Setup

| Environment | API Key | Webhook Secret |
|-------------|---------|----------------|
| GCP | Same `sk_test_xxx` | Different `whsec_gcp_xxx` |
| AWS | Same `sk_test_xxx` | Different `whsec_aws_xxx` |

**Note:** API keys and Price IDs are the SAME for both environments. Only Webhook Secrets are different (each webhook endpoint has its own signing secret).

---

## Product vs Price ID

### Understanding the Difference

| ID Type | Prefix | Example | Usage |
|---------|--------|---------|-------|
| Product ID | `prod_` | `prod_TUbQK8FQUOm1ss` | ❌ DO NOT use for checkout |
| **Price ID** | `price_` | `price_1SXc2GFCBa9Gd3Zv` | ✅ REQUIRED for checkout |

### Common Error

```
Stripe error: No such price: 'prod_TUbQK8FQUOm1ss'
```

**Cause:** Using Product ID instead of Price ID in `.env` file.

### How to Find Price ID

1. Go to: https://dashboard.stripe.com/test/products
2. Click on the product (e.g., "Pro Plan")
3. Scroll to **Pricing** section
4. Click on the price row (e.g., "$9.99 USD / month")
5. Copy the **Price ID** (starts with `price_`)

### Structure in Stripe

```
Pro Plan
├── Product ID: prod_TUbQK8FQUOm1ss  ← DON'T use this
└── Pricing
    └── $9.99 USD / month
        └── Price ID: price_1ABC2DEF3GHI  ← USE THIS!
```

---

## Webhook Configuration

### Webhook Endpoints

| Environment | Webhook URL |
|-------------|-------------|
| GCP | `https://api-taskmanager.gcp.sriinfosoft.com/api/stripe/webhook` |
| AWS | `https://api-taskmanager.aws.sriinfosoft.com/api/stripe/webhook` |

### Required Events (6 total)

| Category | Event Name | Purpose |
|----------|------------|---------|
| Checkout | `checkout.session.completed` | Payment completed |
| Customer | `customer.subscription.created` | New subscription started |
| Customer | `customer.subscription.updated` | Plan changed or renewed |
| Customer | `customer.subscription.deleted` | Subscription cancelled |
| Invoice | `invoice.payment_succeeded` | Payment successful (reset credits) |
| Invoice | `invoice.payment_failed` | Payment failed (mark past_due) |

### How to Create Webhook

1. Go to: https://dashboard.stripe.com/test/webhooks
2. Click **"+ Add endpoint"**
3. Enter webhook URL
4. Select the 6 events listed above
5. Click **Create**
6. Copy the **Signing secret** (`whsec_xxx`)

### Webhook Flow

```
┌─────────────┐         ┌─────────────┐         ┌─────────────┐
│   Stripe    │         │   Backend   │         │  Database   │
│  (Payment)  │         │ (Spring Boot)│         │  (MariaDB)  │
└──────┬──────┘         └──────┬──────┘         └──────┬──────┘
       │                       │                       │
       │ 1. Payment completed  │                       │
       │──────────────────────>│                       │
       │                       │                       │
       │ 2. Webhook: checkout  │                       │
       │    .session.completed │                       │
       │──────────────────────>│                       │
       │                       │                       │
       │ 3. Webhook: customer  │                       │
       │    .subscription      │                       │
       │    .created           │                       │
       │──────────────────────>│ 4. Update user       │
       │                       │────────────────────>│
       │                       │                       │
       │                       │ 5. Set status=active │
       │                       │    plan=basic        │
       │                       │    sms_limit=10      │
       │                       │────────────────────>│
```

---

## Test Card Numbers

### Successful Payments

| Scenario | Card Number | Expiry | CVC | ZIP |
|----------|-------------|--------|-----|-----|
| ✅ Success (Visa) | `4242 4242 4242 4242` | Any future | Any 3 digits | Any 5 digits |
| ✅ Success (Mastercard) | `5555 5555 5555 4444` | Any future | Any 3 digits | Any 5 digits |
| ✅ Success (Amex) | `3782 822463 10005` | Any future | Any 4 digits | Any 5 digits |

### Declined Payments

| Scenario | Card Number |
|----------|-------------|
| ❌ Generic Decline | `4000 0000 0000 0002` |
| ❌ Insufficient Funds | `4000 0000 0000 9995` |
| ❌ Lost Card | `4000 0000 0000 9987` |
| ❌ Stolen Card | `4000 0000 0000 9979` |
| ❌ Expired Card | `4000 0000 0000 0069` |
| ❌ Incorrect CVC | `4000 0000 0000 0127` |
| ❌ Processing Error | `4000 0000 0000 0119` |

### Authentication Required

| Scenario | Card Number |
|----------|-------------|
| 🔐 3D Secure Required | `4000 0025 0000 3155` |
| 🔐 3D Secure 2 | `4000 0027 6000 3184` |

### Special Test Cases

| Scenario | Card Number |
|----------|-------------|
| 💳 Disputes/Chargebacks | `4000 0000 0000 0259` |
| ⏳ Delayed Success | `4000 0000 0000 0077` |

### Test Payment Example

```
Card Number: 4242 4242 4242 4242
Expiry:      12/28
CVC:         123
Name:        Test User
ZIP:         94105
```

---

## API Endpoints

### Stripe Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/stripe/plans` | GET | ❌ Public | Get available plans |
| `/api/stripe/subscription` | GET | ✅ JWT | Get current subscription |
| `/api/stripe/create-checkout-session` | POST | ✅ JWT | Create Stripe Checkout |
| `/api/stripe/create-portal-session` | POST | ✅ JWT | Open Customer Portal |
| `/api/stripe/can-send-sms` | GET | ✅ JWT | Check SMS credits |
| `/api/stripe/can-use-ai` | GET | ✅ JWT | Check AI credits |
| `/api/stripe/webhook` | POST | ❌* | Stripe webhook (signature verified) |

### Example API Calls

#### Get Subscription Info
```bash
curl -X GET "https://api-taskmanager.gcp.sriinfosoft.com/api/stripe/subscription" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

#### Create Checkout Session
```bash
curl -X POST "https://api-taskmanager.gcp.sriinfosoft.com/api/stripe/create-checkout-session" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"plan": "basic"}'
```

#### Check SMS Credits
```bash
curl -X GET "https://api-taskmanager.gcp.sriinfosoft.com/api/stripe/can-send-sms" \
  -H "Authorization: Bearer YOUR_JWT_TOKEN"
```

---

## Database Queries

### Connect to Database

```bash
# SSH to server, then:
docker exec -it taskmanager-db mysql -u taskuser -p taskmanager
```

### View Users Table Structure

```sql
DESCRIBE users;
```

### View All Users with Subscription Info

```sql
SELECT 
    id,
    email,
    name,
    stripe_customer_id,
    subscription_status,
    subscription_plan,
    stripe_subscription_id,
    sms_credits_used,
    sms_credits_limit,
    ai_requests_used,
    ai_requests_limit,
    created_at,
    updated_at
FROM users
ORDER BY created_at DESC;
```

### Check Specific User

```sql
SELECT * FROM users WHERE email = 'user@example.com';
```

### Check Premium Users

```sql
SELECT 
    email,
    subscription_status,
    subscription_plan,
    sms_credits_limit,
    ai_requests_limit
FROM users 
WHERE subscription_status = 'active';
```

### Quick Health Check

```sql
SELECT 
    'Users Table' as check_item,
    COUNT(*) as count,
    CASE WHEN COUNT(*) > 0 THEN '✅ OK' ELSE '⚠️ Empty' END as status
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
FROM users WHERE stripe_customer_id IS NOT NULL;
```

### Delete User (For Re-testing)

```sql
-- Delete specific user
DELETE FROM users WHERE email = 'user@example.com';

-- Delete ALL users (be careful!)
DELETE FROM users;

-- Reset auto-increment
ALTER TABLE users AUTO_INCREMENT = 1;
```

### Manually Update Subscription (Emergency Fix)

```sql
UPDATE users 
SET 
    subscription_status = 'active',
    subscription_plan = 'basic',
    sms_credits_limit = 10,
    ai_requests_limit = 50,
    stripe_subscription_id = 'sub_XXXXX'
WHERE email = 'user@example.com';
```

### Check Usage

```sql
SELECT 
    email,
    subscription_plan,
    sms_credits_used,
    sms_credits_limit,
    ai_requests_used,
    ai_requests_limit,
    CONCAT(sms_credits_used, '/', sms_credits_limit) as sms_usage,
    CONCAT(ai_requests_used, '/', ai_requests_limit) as ai_usage
FROM users
WHERE subscription_status = 'active';
```

---

## Subscription Plans

### Plan Details

| Plan | Price | SMS Credits | AI Requests | Features |
|------|-------|-------------|-------------|----------|
| Free | $0 | 0 | 0 | Basic features only |
| Basic | $4.99/month | 10/month | 50/month | Email, Push, Priority Support |
| Pro | $9.99/month | 50/month | 200/month | + Advanced Analytics |
| Enterprise | $29.99/month | Unlimited | Unlimited | + Custom Integrations, SLA |

### Plan Limits in Code

```java
// SMS Limits
free = 0
basic = 10
pro = 50
enterprise = Integer.MAX_VALUE (unlimited)

// AI Limits
free = 0
basic = 50
pro = 200
enterprise = Integer.MAX_VALUE (unlimited)
```

### Subscription Status Values

| Status | Description |
|--------|-------------|
| `free` | No active subscription |
| `active` | Subscription is active |
| `trialing` | In trial period |
| `past_due` | Payment failed |
| `canceled` | Subscription canceled |

---

## Troubleshooting

### Common Errors

#### 1. "No such price" Error
```
Stripe error: No such price: 'prod_TUbQK8FQUOm1ss'
```
**Cause:** Using Product ID instead of Price ID
**Fix:** Update `.env` with correct `price_xxx` IDs

#### 2. User Not Found After Payment
```
❌ User NOT FOUND with stripe_customer_id: cus_xxx
```
**Cause:** Webhook not updating database
**Fix:** Check webhook logs, verify StripeService code

#### 3. Webhook Signature Verification Failed
```
Webhook signature verification failed
```
**Cause:** Wrong webhook secret
**Fix:** Copy correct `whsec_xxx` from Stripe Dashboard

#### 4. Subscription Status Still "free"
**Cause:** Webhook handler not saving to database
**Fix:** 
1. Check backend logs for errors
2. Manually update via SQL:
```sql
UPDATE users SET subscription_status = 'active', subscription_plan = 'basic' 
WHERE email = 'user@example.com';
```

### Debug Commands

```bash
# Check backend logs
docker logs -f taskmanager-backend

# Check if webhook is received
docker logs taskmanager-backend | grep -i webhook

# Check database
docker exec -it taskmanager-db mysql -u taskuser -p taskmanager -e "SELECT * FROM users;"
```

### Stripe Dashboard Checks

1. **Check Webhook Events:**
   - Go to: https://dashboard.stripe.com/test/webhooks
   - Click on your webhook
   - Check "Event deliveries" tab
   - Look for failed deliveries (❌ red)

2. **Check Subscription Status:**
   - Go to: https://dashboard.stripe.com/test/subscriptions
   - Find the customer
   - Verify subscription is "Active"

3. **Check Customer:**
   - Go to: https://dashboard.stripe.com/test/customers
   - Find by email
   - Verify customer exists

---

## Deployment Checklist

### Before Deployment

- [ ] Stripe account created
- [ ] Test mode enabled (Sandbox)
- [ ] API keys copied (sk_test_xxx)
- [ ] Products created (Basic, Pro, Enterprise)
- [ ] Price IDs copied (price_xxx) - NOT product IDs
- [ ] Webhooks created for each environment
- [ ] Webhook secrets copied (whsec_xxx)

### Environment Setup

- [ ] `.env` file updated with all Stripe variables
- [ ] `docker-compose.yml` has Stripe environment variables
- [ ] `application.properties` has Stripe configuration
- [ ] `SecurityConfig.java` permits `/api/stripe/webhook`

### Database

- [ ] `users` table created (V007 migration)
- [ ] Table has all required columns

### Testing

- [ ] Checkout flow works (Basic plan)
- [ ] Checkout flow works (Pro plan)
- [ ] Checkout flow works (Enterprise plan)
- [ ] Webhook updates database
- [ ] Subscription status shows "active"
- [ ] SMS/AI limits set correctly
- [ ] Customer Portal accessible

### Going Live

- [ ] Switch to Live mode keys (sk_live_xxx)
- [ ] Create Live webhooks
- [ ] Update Live Price IDs
- [ ] Test with real card (small amount)
- [ ] Verify webhook delivery in Live mode

---

## Security Notes

### What's Stored in Your Database

| Stored | NOT Stored |
|--------|------------|
| Stripe Customer ID (`cus_xxx`) | Credit card numbers |
| Stripe Subscription ID (`sub_xxx`) | CVV/CVC codes |
| Subscription status | Card expiry dates |
| Plan name | Billing addresses |
| Usage counters | Full card details |

### PCI Compliance

Your application is **PCI compliant** because:
- Credit card data is handled entirely by Stripe
- No card numbers pass through your servers
- Stripe Checkout is hosted by Stripe
- Only tokens/IDs are stored in your database

### API Key Security

- ❌ NEVER commit `.env` file with real keys
- ❌ NEVER expose `sk_xxx` keys in frontend code
- ✅ Use `sk_test_` keys for development
- ✅ Use `sk_live_` keys only in production
- ✅ Webhook endpoint is secured by signature verification

---

## Quick Reference

### Key IDs Summary

| What | Prefix | Example | Where to Get |
|------|--------|---------|--------------|
| Secret Key | `sk_test_` or `sk_live_` | `sk_test_51ABC...` | API Keys page |
| Webhook Secret | `whsec_` | `whsec_abc123...` | Webhook details page |
| Price ID | `price_` | `price_1SXc2G...` | Product → Pricing |
| Product ID | `prod_` | `prod_TUbQK8...` | Product details (DON'T use) |
| Customer ID | `cus_` | `cus_TUdvit...` | Created automatically |
| Subscription ID | `sub_` | `sub_1SXc3H...` | Created on subscribe |

### Test Card Quick Reference

| Result | Card Number |
|--------|-------------|
| ✅ Success | `4242 4242 4242 4242` |
| ❌ Declined | `4000 0000 0000 0002` |
| 🔐 3D Secure | `4000 0025 0000 3155` |

---

*Document created for Task Manager Pro - SriInfosoft Inc © 2025*
*Last updated: November 2025*
