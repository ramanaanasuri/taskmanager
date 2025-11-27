# Stripe Payment Integration for Task Manager Pro

## Overview

This integration adds subscription-based payment functionality to Task Manager Pro, enabling:
- Premium plans (Basic, Pro, Enterprise)
- SMS notification credits
- AI request credits  
- Stripe Checkout for payments
- Customer Portal for subscription management
- Webhook handling for subscription events

---

## File Structure

```
stripe-integration/
├── backend/
│   ├── model/
│   │   └── User.java                    # NEW - User entity with Stripe fields
│   ├── repository/
│   │   └── UserRepository.java          # NEW - User data access
│   ├── service/
│   │   └── StripeService.java           # NEW - Stripe business logic
│   ├── controller/
│   │   └── StripeController.java        # NEW - REST endpoints
│   ├── config/
│   │   └── SecurityConfig.java          # MODIFIED - Add webhook endpoint
│   ├── pom.xml                          # MODIFIED - Add Stripe dependency
│   └── application.properties           # MODIFIED - Add Stripe config
├── frontend/
│   └── src/
│       ├── SubscriptionModal.js         # NEW - React component
│       └── SubscriptionModal.css        # NEW - Styles
├── db/
│   └── V007__Add_Users_Table_For_Stripe.sql  # NEW - Database migration
├── docker-compose.yml                   # MODIFIED - Add Stripe env vars
└── .env.sample                          # MODIFIED - Add Stripe vars
```

---

## Installation Steps

### Step 1: Database Migration

Run the SQL migration to create the `users` table:

```bash
# Copy to your migrations folder
cp db/V007__Add_Users_Table_For_Stripe.sql /path/to/taskmanager/db/migrations/

# Apply migration (or let Hibernate auto-create with ddl-auto=update)
mysql -u taskuser -p taskmanager < db/V007__Add_Users_Table_For_Stripe.sql
```

### Step 2: Backend Files

Copy NEW files to your backend:

```bash
# Model
cp backend/model/User.java \
   apps/backend/src/main/java/com/sriinfosoft/taskmanager/model/

# Repository  
cp backend/repository/UserRepository.java \
   apps/backend/src/main/java/com/sriinfosoft/taskmanager/repository/

# Service
cp backend/service/StripeService.java \
   apps/backend/src/main/java/com/sriinfosoft/taskmanager/service/

# Controller
cp backend/controller/StripeController.java \
   apps/backend/src/main/java/com/sriinfosoft/taskmanager/controller/
```

Replace MODIFIED files:

```bash
cp backend/config/SecurityConfig.java \
   apps/backend/src/main/java/com/sriinfosoft/taskmanager/config/

cp backend/pom.xml apps/backend/

cp backend/application.properties apps/backend/src/main/resources/
```

### Step 3: Frontend Files

Copy NEW files:

```bash
cp frontend/src/SubscriptionModal.js apps/frontend/src/
cp frontend/src/SubscriptionModal.css apps/frontend/src/
```

### Step 4: App.js Changes (MINIMAL)

Add these **7 lines** to your existing `App.js`:

#### 4a. Add Import (at top of file, around line 6):

```javascript
// EXISTING IMPORTS...
import './App.css';
import { subscribeToPushNotifications } from './utils/pushNotifications';
import { convertLocalToUTC } from './utils/dateUtils';

// ✅ ADD THIS LINE:
import { SubscriptionModal, UpgradeButton, useSubscription } from './SubscriptionModal';
import './SubscriptionModal.css';
```

#### 4b. Add State (inside App function, around line 21):

```javascript
const [enableSms, setEnableSms] = useState(false);
const [enableEmail, setEnableEmail] = useState(false);

// ✅ ADD THIS LINE:
const [showSubscriptionModal, setShowSubscriptionModal] = useState(false);
```

#### 4c. Add Hook (after the state declarations, around line 24):

```javascript
// ✅ ADD THIS LINE:
const { subscription, refresh: refreshSubscription } = useSubscription(authToken);
```

#### 4d. Add Check for URL params (in useEffect for URL params, around line 35):

```javascript
// ✅ ADD THESE LINES after checking for token from URL:
// Check for subscription success/cancel from Stripe redirect
const subscriptionStatus = params.get('subscription');
if (subscriptionStatus === 'success') {
  console.log('✅ Subscription successful!');
  setTimeout(() => refreshSubscription(), 1000);
}
```

#### 4e. Add Upgrade Button (in header, around line 725 - before logout button):

Find this section:
```javascript
<div className="user-info">
  <span>Welcome, {user.name}</span>
  <button onClick={handleLogout} className="logout-btn">
```

Change to:
```javascript
<div className="user-info">
  <span>Welcome, {user.name}</span>
  {/* ✅ ADD THIS LINE: */}
  <UpgradeButton onClick={() => setShowSubscriptionModal(true)} subscription={subscription} />
  <button onClick={handleLogout} className="logout-btn">
```

#### 4f. Add Modal (before closing </div> of main app container, around line 1285):

Find the footer section and add BEFORE it:
```javascript
      {/* ✅ ADD THIS BLOCK: */}
      {/* Subscription Modal */}
      <SubscriptionModal
        isOpen={showSubscriptionModal}
        onClose={() => setShowSubscriptionModal(false)}
        authToken={authToken}
        user={user}
      />

      {/* Footer */}
      <footer className="app-footer">
```

### Step 5: App.css Changes

Append the contents of `SubscriptionModal.css` to the end of your `App.css`:

```bash
cat frontend/src/SubscriptionModal.css >> apps/frontend/src/App.css
```

### Step 6: Docker Compose

Replace with the updated `docker-compose.yml`:

```bash
cp docker-compose.yml /path/to/taskmanager/
```

### Step 7: Environment Variables

Add to your `.env` file:

```bash
# Stripe Payment Integration
STRIPE_SECRET_KEY=sk_test_your_key_here
STRIPE_WEBHOOK_SECRET=whsec_your_webhook_secret
STRIPE_PRICE_BASIC=price_xxxxx
STRIPE_PRICE_PRO=price_xxxxx
STRIPE_PRICE_ENTERPRISE=price_xxxxx
```

---

## Stripe Dashboard Setup

### 1. Create Products

Go to https://dashboard.stripe.com/products and create:

| Product | Price | Interval |
|---------|-------|----------|
| Basic | $4.99 | monthly |
| Pro | $9.99 | monthly |
| Enterprise | $29.99 | monthly |

Copy the Price IDs (starts with `price_`) to your `.env` file.

### 2. Create Webhook

Go to https://dashboard.stripe.com/webhooks and create:

- **Endpoint URL**: `https://api-taskmanager.gcp.sriinfosoft.com/api/stripe/webhook`
- **Events to listen for**:
  - `checkout.session.completed`
  - `customer.subscription.created`
  - `customer.subscription.updated`
  - `customer.subscription.deleted`
  - `invoice.payment_succeeded`
  - `invoice.payment_failed`

Copy the Webhook Signing Secret (starts with `whsec_`) to your `.env` file.

### 3. Get API Keys

Go to https://dashboard.stripe.com/apikeys

- Copy **Secret key** (starts with `sk_test_` or `sk_live_`)
- Add to `.env` as `STRIPE_SECRET_KEY`

---

## API Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/stripe/plans` | GET | ❌ | Get available plans |
| `/api/stripe/subscription` | GET | ✅ | Get current subscription |
| `/api/stripe/create-checkout-session` | POST | ✅ | Create Stripe Checkout |
| `/api/stripe/create-portal-session` | POST | ✅ | Open Customer Portal |
| `/api/stripe/can-send-sms` | GET | ✅ | Check SMS credits |
| `/api/stripe/can-use-ai` | GET | ✅ | Check AI credits |
| `/api/stripe/webhook` | POST | ❌* | Stripe webhook (signature verified) |

---

## Testing

### Test Mode

Use Stripe test mode keys (starts with `sk_test_`).

### Test Card Numbers

| Card | Number |
|------|--------|
| Success | 4242 4242 4242 4242 |
| Declined | 4000 0000 0000 0002 |
| Requires Auth | 4000 0025 0000 3155 |

### Test Webhook Locally

Use Stripe CLI:

```bash
stripe listen --forward-to localhost:8080/api/stripe/webhook
```

---

## Summary of Changes

### NEW Files (6)
1. `User.java` - User entity
2. `UserRepository.java` - Data access
3. `StripeService.java` - Business logic
4. `StripeController.java` - REST API
5. `SubscriptionModal.js` - React component
6. `V007__Add_Users_Table_For_Stripe.sql` - Database

### MODIFIED Files (5)
1. `pom.xml` - Add 1 dependency
2. `application.properties` - Add 5 config lines
3. `SecurityConfig.java` - Add 3 lines for endpoints
4. `docker-compose.yml` - Add 5 env vars
5. `App.js` - Add ~15 lines total

### CSS (Append)
- `SubscriptionModal.css` → Append to `App.css`

---

## Troubleshooting

### "Invalid plan or price not configured"
- Check that `STRIPE_PRICE_BASIC`, `STRIPE_PRICE_PRO`, `STRIPE_PRICE_ENTERPRISE` are set in `.env`
- Verify the price IDs exist in your Stripe Dashboard

### "Webhook signature verification failed"
- Ensure `STRIPE_WEBHOOK_SECRET` matches the secret from Stripe Dashboard
- Check the webhook URL is exactly `https://your-domain/api/stripe/webhook`

### "User not found"
- The user must have logged in at least once after deployment
- Check that `User` entity is being created on OAuth login

---

## Security Notes

- Never commit `.env` file with real keys
- Use `sk_test_` keys for development
- Use `sk_live_` keys only in production
- Webhook endpoint is secured by signature verification (not JWT)
- All other Stripe endpoints require JWT authentication

---

*Generated for Task Manager Pro - SriInfosoft Inc © 2025*
