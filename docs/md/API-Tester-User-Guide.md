# Task Manager Pro - API Tester Console
## Complete User Guide & Reference Manual

---

## Table of Contents

1. [Overview](#overview)
2. [Getting Started](#getting-started)
3. [Login & Authentication](#login--authentication)
4. [Dashboard Overview](#dashboard-overview)
5. [Authentication Testing](#authentication-testing)
6. [Task CRUD Operations](#task-crud-operations)
7. [Notification Testing](#notification-testing)
8. [Stripe Payment Testing](#stripe-payment-testing)
9. [Bulk Operations](#bulk-operations)
10. [Task List Management](#task-list-management)
11. [Schedule & Due Date Testing](#schedule--due-date-testing)
12. [Activity Log](#activity-log)
13. [Testing Scenarios](#testing-scenarios)
14. [Troubleshooting](#troubleshooting)
15. [API Reference](#api-reference)

---

## Overview

The API Tester Console is a comprehensive web-based testing tool for the Task Manager Pro application. It provides an interactive interface to test all backend APIs including:

- User authentication (Google OAuth, Facebook OAuth, JWT)
- Task CRUD operations (Create, Read, Update, Delete)
- Notification services (Email, Push, SMS)
- Stripe payment integration (Subscriptions, Checkout, Portal)
- Bulk operations and stress testing

### Key Features

| Feature | Description |
|---------|-------------|
| 🔐 Secure Login | Password-protected access to prevent unauthorized testing |
| 🔄 Real-time Logging | Activity log tracks all API calls and responses |
| 📊 Visual Feedback | Color-coded status indicators and formatted responses |
| 🧪 Test Data Generation | Auto-generate test tasks with realistic data |
| 💳 Payment Testing | Complete Stripe integration testing with test cards |

---

## Getting Started

### Access the Tester

**URL Format:**
```
https://[your-domain]/api-tester.html

Examples:
- GCP: https://taskmanager.gcp.sriinfosoft.com/api-tester.html
- AWS: https://taskmanager.aws.sriinfosoft.com/api-tester.html
- Local: http://localhost:3000/api-tester.html
```

### System Requirements

- Modern web browser (Chrome, Firefox, Safari, Edge)
- Network access to the Task Manager backend API
- Valid tester password
- For OAuth testing: Google/Facebook developer credentials configured

---

## Login & Authentication

### Tester Console Login

The API Tester requires a password to access. This prevents unauthorized access to testing functions.

| Field | Description |
|-------|-------------|
| **Password** | The tester console password (default or custom) |
| **👁️ Toggle** | Click to show/hide password |

### Login Process

1. Navigate to the API Tester URL
2. Enter the tester password
3. Click **"🔓 Access Tester"**
4. If correct, the main dashboard appears
5. If incorrect, an error message displays

### Password Visibility Toggle

Click the **👁️** icon to toggle between:
- `🙈` - Password hidden (dots)
- `👁️` - Password visible (plain text)

---

## Dashboard Overview

After successful login, the dashboard displays several testing cards:

```
┌─────────────────────────────────────────────────────────────────┐
│  HEADER                                                         │
│  [Environment: GCP ▼] [API URL display] [👤 Logout]            │
├─────────────────────────────────────────────────────────────────┤
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ 🔐 Auth         │  │ 📝 Task CRUD    │  │ 🔔 Notifications│ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│                                                                 │
│  ┌─────────────────┐  ┌─────────────────┐  ┌─────────────────┐ │
│  │ 💳 Stripe       │  │ ⚡ Bulk Ops     │  │ 📋 Task List    │ │
│  └─────────────────┘  └─────────────────┘  └─────────────────┘ │
│                                                                 │
│  ┌─────────────────┐                                           │
│  │ 📅 Schedule     │                                           │
│  └─────────────────┘                                           │
│                                                                 │
├─────────────────────────────────────────────────────────────────┤
│  📜 ACTIVITY LOG                                                │
│  [Scrollable log of all API calls and responses]               │
└─────────────────────────────────────────────────────────────────┘
```

### Environment Selector

Located in the header, allows switching between different backend environments:

| Environment | API URL |
|-------------|---------|
| GCP | `https://api-taskmanager.gcp.sriinfosoft.com` |
| AWS | `https://api-taskmanager.aws.sriinfosoft.com` |
| Local | `http://localhost:8080` |

**Note:** Changing environment clears the current session and requires re-authentication.

---

## Authentication Testing

### Card: 🔐 Authentication

This card tests OAuth login flows and JWT token management.

### Available Buttons

| Button | Action | Description |
|--------|--------|-------------|
| **🔑 Google Login** | Initiates Google OAuth | Opens Google login popup, retrieves JWT token |
| **📘 Facebook Login** | Initiates Facebook OAuth | Opens Facebook login popup, retrieves JWT token |
| **👤 Get User Info** | `GET /api/auth/me` | Fetches current user profile using stored JWT |
| **🚪 Logout** | Clears session | Removes JWT token and user data |

### Google OAuth Testing

**Prerequisites:**
- Google OAuth configured in backend
- Valid Google Client ID in frontend

**Steps:**
1. Click **"🔑 Google Login"**
2. Google login popup appears
3. Sign in with your Google account
4. Popup closes automatically
5. JWT token is stored
6. User info displays in the result area

**Expected Response:**
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "user": {
    "email": "user@gmail.com",
    "name": "John Doe",
    "picture": "https://lh3.googleusercontent.com/..."
  }
}
```

### Facebook OAuth Testing

**Prerequisites:**
- Facebook OAuth configured in backend
- Valid Facebook App ID in frontend

**Steps:**
1. Click **"📘 Facebook Login"**
2. Facebook login popup appears
3. Sign in with your Facebook account
4. Popup closes automatically
5. JWT token is stored

### Get User Info

**Requires:** Active JWT token from OAuth login

**API Call:** `GET /api/auth/me`

**Expected Response:**
```json
{
  "email": "user@gmail.com",
  "name": "John Doe",
  "picture": "https://..."
}
```

### Token Status Indicators

| Indicator | Meaning |
|-----------|---------|
| ✅ Token found | JWT is stored and ready for API calls |
| ❌ No token | Need to login via OAuth first |
| ⚠️ Token expired | Re-authenticate required |

---

## Task CRUD Operations

### Card: 📝 Task CRUD

Test Create, Read, Update, Delete operations on tasks.

### Input Fields

| Field | Description | Example |
|-------|-------------|---------|
| **Task Title** | Title for new/updated task | "Complete project report" |
| **Priority** | Task priority level | HIGH, MEDIUM, LOW |
| **Task ID** | ID for update/delete operations | Auto-populated when selecting from list |

### Available Buttons

| Button | Method | Endpoint | Description |
|--------|--------|----------|-------------|
| **➕ Create** | POST | `/api/tasks` | Create new task |
| **📖 Read** | GET | `/api/tasks/{id}` | Get task by ID |
| **✏️ Update** | PUT | `/api/tasks/{id}` | Update existing task |
| **🗑️ Delete** | DELETE | `/api/tasks/{id}` | Delete task by ID |
| **📋 List All** | GET | `/api/tasks` | Get all user's tasks |

### Create Task

**Steps:**
1. Enter a title in "Task Title" field
2. Select priority (HIGH/MEDIUM/LOW)
3. Click **"➕ Create"**

**Request Body:**
```json
{
  "title": "Complete project report",
  "completed": false,
  "priority": "HIGH"
}
```

**Expected Response:**
```json
{
  "id": 42,
  "title": "Complete project report",
  "completed": false,
  "priority": "HIGH",
  "userEmail": "user@gmail.com",
  "createdAt": "2025-11-27T10:30:00Z",
  "updatedAt": "2025-11-27T10:30:00Z"
}
```

### Read Task

**Steps:**
1. Enter Task ID or select from Task List
2. Click **"📖 Read"**

**Expected Response:**
```json
{
  "id": 42,
  "title": "Complete project report",
  "completed": false,
  "priority": "HIGH",
  "dueDate": "2025-11-28T15:00:00Z",
  "emailNotification": true,
  "pushNotification": false,
  "smsNotification": false,
  "userEmail": "user@gmail.com"
}
```

### Update Task

**Steps:**
1. Enter/select Task ID
2. Modify title and/or priority
3. Click **"✏️ Update"**

**Note:** The tester fetches the current task first, then merges your changes.

### Delete Task

**Steps:**
1. Enter/select Task ID
2. Click **"🗑️ Delete"**
3. Confirm deletion in popup

**Expected Response:**
```
HTTP 204 No Content (success, no body)
```

### List All Tasks

**Steps:**
1. Click **"📋 List All"**
2. All tasks for current user are displayed

**Expected Response:**
```json
[
  {
    "id": 42,
    "title": "Task 1",
    "completed": false,
    "priority": "HIGH"
  },
  {
    "id": 43,
    "title": "Task 2",
    "completed": true,
    "priority": "LOW"
  }
]
```

---

## Notification Testing

### Card: 🔔 Notification Settings

Test email, push, and SMS notification settings for tasks.

### Notification Types

| Type | Icon | Description |
|------|------|-------------|
| **Email** | 📧 | Email notifications via AWS SES |
| **Push** | 🔔 | Browser push notifications via Firebase FCM |
| **SMS** | 📱 | SMS notifications via AWS SNS |

### Input Fields

| Field | Description | Format |
|-------|-------------|--------|
| **Email Enabled** | Toggle email notifications | Checkbox |
| **Push Enabled** | Toggle push notifications | Checkbox |
| **SMS Enabled** | Toggle SMS notifications | Checkbox |
| **Phone Number** | Phone for SMS | E.164 format: `+15055550006` |

### Available Buttons

| Button | Action | Description |
|--------|--------|-------------|
| **✅ Enable All** | Check all boxes | Enable all notification types |
| **❌ Disable All** | Uncheck all boxes | Disable all notification types |
| **💾 Save to Task** | `PUT /api/tasks/{id}` | Save notification settings to selected task |

### Testing Flow

1. **Select a Task** from the Task List card
2. **Configure notifications:**
   - Check/uncheck Email, Push, SMS
   - Enter phone number for SMS (E.164 format)
3. Click **"💾 Save to Task"**
4. Verify in response that settings are saved

### Phone Number Format (E.164)

```
Format: +[country code][number]

Examples:
- US:     +14155551234
- UK:     +442071234567
- India:  +919876543210
```

### Expected Response

```json
{
  "id": 42,
  "title": "Test Task",
  "emailNotification": true,
  "pushNotification": true,
  "smsNotification": true,
  "phoneNumber": "+15055550006"
}
```

---

## Stripe Payment Testing

### Card: 💳 Stripe Payment Testing

Test subscription management, checkout, and payment APIs.

### Subscription Status Display

Shows current user's subscription information:

```
┌────────────────────────────────────────────┐
│ [ACTIVE] [💎 BASIC]                        │
│ 📱 SMS: 2/10    🤖 AI: 15/50              │
│ Customer: cus_TUdvitKuUNpRZW              │
└────────────────────────────────────────────┘
```

| Field | Description |
|-------|-------------|
| **Status Badge** | FREE, ACTIVE, TRIALING, PAST_DUE, CANCELED |
| **Plan Badge** | BASIC, PRO, ENTERPRISE (if not free) |
| **SMS Usage** | Used/Limit for SMS credits |
| **AI Usage** | Used/Limit for AI request credits |
| **Customer ID** | Stripe Customer ID |

### Plan Selector

Dropdown to select plan for checkout testing:

| Plan | Price | SMS Credits | AI Credits |
|------|-------|-------------|------------|
| Basic | $4.99/month | 10/month | 50/month |
| Pro | $9.99/month | 50/month | 200/month |
| Enterprise | $29.99/month | Unlimited | Unlimited |

### Test Card Reference

Quick reference displayed in the card:

| Result | Card Number | Use Case |
|--------|-------------|----------|
| ✅ Success | `4242 4242 4242 4242` | Normal successful payment |
| ❌ Decline | `4000 0000 0000 0002` | Test declined payment |
| 🔐 3D Secure | `4000 0025 0000 3155` | Test 3D Secure authentication |

**Additional Test Data:**
- Expiry: Any future date (e.g., `12/28`)
- CVC: Any 3 digits (e.g., `123`)
- ZIP: Any 5 digits (e.g., `94105`)

### Available Buttons

| Button | Method | Endpoint | Description |
|--------|--------|----------|-------------|
| **📊 Get Subscription** | GET | `/api/stripe/subscription` | Fetch current subscription status |
| **📋 Get Plans** | GET | `/api/stripe/plans` | List available plans (public) |
| **📱 Can Send SMS?** | GET | `/api/stripe/can-send-sms` | Check SMS credit availability |
| **🤖 Can Use AI?** | GET | `/api/stripe/can-use-ai` | Check AI credit availability |
| **🛒 Create Checkout** | POST | `/api/stripe/create-checkout-session` | Create Stripe Checkout session |
| **⚙️ Customer Portal** | POST | `/api/stripe/create-portal-session` | Open subscription management portal |

### Get Subscription

**Steps:**
1. Ensure you're logged in (OAuth)
2. Click **"📊 Get Subscription"**
3. Subscription status displays above

**Expected Response:**
```json
{
  "status": "active",
  "plan": "basic",
  "customerId": "cus_TUdvitKuUNpRZW",
  "subscriptionId": "sub_1ABC2DEF",
  "smsUsed": 2,
  "smsLimit": 10,
  "aiUsed": 15,
  "aiLimit": 50,
  "startDate": "2025-11-01T00:00:00Z",
  "endDate": "2025-12-01T00:00:00Z"
}
```

### Get Plans

**Steps:**
1. Click **"📋 Get Plans"** (no auth required)

**Expected Response:**
```json
{
  "basic": {
    "name": "Basic",
    "price": 4.99,
    "smsLimit": 10,
    "aiLimit": 50,
    "features": ["Email notifications", "Push notifications", "Priority support"]
  },
  "pro": {
    "name": "Pro",
    "price": 9.99,
    "smsLimit": 50,
    "aiLimit": 200,
    "features": ["All Basic features", "SMS notifications", "Advanced analytics"]
  },
  "enterprise": {
    "name": "Enterprise",
    "price": 29.99,
    "smsLimit": -1,
    "aiLimit": -1,
    "features": ["All Pro features", "Unlimited SMS/AI", "Custom integrations", "SLA"]
  }
}
```

### Can Send SMS?

**Steps:**
1. Click **"📱 Can Send SMS?"**

**Expected Response (Can Send):**
```json
{
  "canSend": true,
  "remaining": 8,
  "limit": 10,
  "used": 2
}
```

**Expected Response (Cannot Send):**
```json
{
  "canSend": false,
  "reason": "SMS credit limit reached",
  "remaining": 0,
  "limit": 10,
  "used": 10
}
```

### Can Use AI?

**Steps:**
1. Click **"🤖 Can Use AI?"**

**Expected Response:**
```json
{
  "canUse": true,
  "remaining": 35,
  "limit": 50,
  "used": 15
}
```

### Create Checkout

**Steps:**
1. Select plan from dropdown (Basic/Pro/Enterprise)
2. Click **"🛒 Create Checkout"**
3. Confirm popup to open Stripe Checkout
4. Complete payment with test card

**Expected Response:**
```json
{
  "checkoutUrl": "https://checkout.stripe.com/c/pay/cs_test_..."
}
```

**After Clicking Confirm:**
- New browser tab opens with Stripe Checkout
- Complete payment with test card
- Redirected back to app with `?subscription=success`

### Customer Portal

**Steps:**
1. Click **"⚙️ Customer Portal"**
2. Confirm popup
3. Stripe Customer Portal opens

**Portal Features:**
- View subscription details
- Change plan (upgrade/downgrade)
- Update payment method
- Cancel subscription
- View billing history
- Download invoices

**Expected Response:**
```json
{
  "portalUrl": "https://billing.stripe.com/p/session/..."
}
```

### Stripe Dashboard Links

Quick links to Stripe Dashboard sections:

| Link | URL | Purpose |
|------|-----|---------|
| Subscriptions | `/test/subscriptions` | View all subscriptions |
| Customers | `/test/customers` | View customer records |
| Webhooks | `/test/webhooks` | Check webhook delivery |
| Events | `/test/events` | View all Stripe events |

---

## Bulk Operations

### Card: ⚡ Bulk Operations

Perform batch operations and stress testing.

### Input Fields

| Field | Description | Range |
|-------|-------------|-------|
| **Number of Tasks** | Tasks to create in bulk | 1-20 |

### Available Buttons

| Button | Action | Description |
|--------|--------|-------------|
| **📦 Bulk Create** | Create multiple tasks | Creates N tasks with auto-generated titles |
| **✅ Complete All** | Mark all complete | Sets `completed: true` for all tasks |
| **🗑️ Delete All** | Delete all tasks | Removes all tasks (requires double confirm) |

### Bulk Create Tasks

**Steps:**
1. Enter number of tasks (1-20)
2. Click **"📦 Bulk Create"**
3. Watch progress in Activity Log

**Generated Task Format:**
```
Title: "Bulk Task #1 - [timestamp]"
Priority: Random (HIGH/MEDIUM/LOW)
Completed: false
```

**Expected Response:**
```json
{
  "total": 5,
  "created": 5,
  "results": [
    { "index": 1, "status": "created", "id": 45 },
    { "index": 2, "status": "created", "id": 46 },
    { "index": 3, "status": "created", "id": 47 },
    { "index": 4, "status": "created", "id": 48 },
    { "index": 5, "status": "created", "id": 49 }
  ]
}
```

### Mark All Complete

**Steps:**
1. Click **"✅ Complete All"**
2. Confirm in popup

**Expected Response:**
```json
{
  "message": "Marked 5/5 tasks complete"
}
```

### Delete All Tasks

**Steps:**
1. Click **"🗑️ Delete All"**
2. Confirm first popup: "DELETE ALL TASKS?"
3. Confirm second popup: "Are you REALLY sure?"

**Expected Response:**
```json
{
  "message": "Deleted 5/5 tasks"
}
```

**⚠️ Warning:** This action is irreversible!

---

## Task List Management

### Card: 📋 Task List

View and select tasks for testing operations.

### Features

| Feature | Description |
|---------|-------------|
| **Refresh List** | Reload tasks from server |
| **Click to Select** | Click any task to select it for operations |
| **Visual Selection** | Selected task is highlighted |
| **Auto-populate** | Task ID field auto-fills on selection |

### Task Display

Each task shows:
```
┌────────────────────────────────────────────┐
│ ☐ Complete project report                  │
│ 🔴 HIGH | ID: 42 | Due: 2025-11-28 15:00  │
└────────────────────────────────────────────┘
```

| Icon | Meaning |
|------|---------|
| ☐ | Incomplete task |
| ☑ | Completed task |
| 🔴 | HIGH priority |
| 🟡 | MEDIUM priority |
| 🟢 | LOW priority |

### Selecting a Task

1. Click **"🔄 Refresh List"**
2. Click on any task in the list
3. Task becomes highlighted
4. Task ID auto-populates in CRUD card
5. Now you can Read, Update, Delete, or modify notifications

---

## Schedule & Due Date Testing

### Card: 📅 Schedule & Due Date

Test date/time handling and timezone conversions.

### Input Fields

| Field | Description | Format |
|-------|-------------|--------|
| **Due Date & Time** | Date picker for task due date | `YYYY-MM-DDTHH:MM` (local) |

### Available Buttons

| Button | Action | Description |
|--------|--------|-------------|
| **⏰ Now** | Set current time | Sets due date to current moment |
| **1️⃣ +1 Hour** | Set 1 hour ahead | Sets due date to now + 1 hour |
| **💾 Save to Task** | Update task | Saves due date to selected task |

### Timezone Handling

The tester handles timezone conversion automatically:

1. **Input:** Local browser time (what you see)
2. **Sent to API:** UTC time (ISO 8601 format)
3. **Response:** UTC time from server
4. **Display:** Converted back to local time

**Example:**
```
Local Input:    2025-11-27 10:30 (PST)
Sent to Server: 2025-11-27T18:30:00.000Z (UTC)
Server Returns: 2025-11-27T18:30:00.000Z (UTC)
```

### Expected Response

```json
{
  "localInput": "2025-11-27T10:30",
  "utcSent": "2025-11-27T18:30:00.000Z",
  "serverResponse": "2025-11-27T18:30:00.000Z"
}
```

---

## Activity Log

### Panel: 📜 Activity Log

Real-time log of all API calls, responses, and actions.

### Log Entry Format

```
[HH:MM:SS] [TYPE] Message
```

### Log Types

| Type | Color | Description |
|------|-------|-------------|
| **INFO** | Blue | Informational messages |
| **SUCCESS** | Green | Successful operations |
| **WARNING** | Orange | Warnings and notices |
| **ERROR** | Red | Errors and failures |

### Log Entry Counter

Shows total entries: `"X entries"`

### Example Log Entries

```
[10:30:15] INFO    Starting API call: GET /api/tasks
[10:30:16] SUCCESS Fetched 5 tasks
[10:30:20] INFO    Creating checkout session for basic plan...
[10:30:21] SUCCESS Checkout session created
[10:30:25] ERROR   Get subscription failed: 401 Unauthorized
[10:30:30] WARNING Cannot send SMS: Limit reached
```

---

## Testing Scenarios

### Scenario 1: New User Registration Flow

**Purpose:** Test complete new user journey

**Steps:**
1. Open API Tester in incognito browser
2. Click **"🔑 Google Login"**
3. Complete Google OAuth
4. Verify user info appears
5. Click **"📊 Get Subscription"** → Should show FREE plan
6. Click **"➕ Create"** to create a task
7. Verify task appears in Task List

**Expected Results:**
- JWT token obtained ✅
- User shows FREE subscription ✅
- Task created successfully ✅

---

### Scenario 2: Subscription Purchase Flow

**Purpose:** Test Stripe checkout and webhook processing

**Steps:**
1. Login via Google OAuth
2. Click **"📊 Get Subscription"** → Verify FREE
3. Select "Basic" from plan dropdown
4. Click **"🛒 Create Checkout"**
5. Complete checkout with test card `4242 4242 4242 4242`
6. Wait for redirect back to app
7. Click **"📊 Get Subscription"** again

**Expected Results:**
- Checkout page opens ✅
- Payment succeeds ✅
- Status changes to ACTIVE ✅
- Plan shows BASIC ✅
- SMS limit shows 10 ✅

---

### Scenario 3: Notification Configuration

**Purpose:** Test notification settings

**Steps:**
1. Login and create a task
2. Select the task from Task List
3. Check Email ☑️, Push ☑️, SMS ☑️
4. Enter phone number: `+15055550006`
5. Click **"💾 Save to Task"**
6. Click **"📖 Read"** to verify

**Expected Results:**
- All notification flags saved ✅
- Phone number stored ✅
- Task update successful ✅

---

### Scenario 4: Credit Usage Testing

**Purpose:** Test SMS/AI credit limits

**Steps:**
1. Subscribe to Basic plan (10 SMS, 50 AI)
2. Click **"📱 Can Send SMS?"** → Shows 10/10 remaining
3. (Trigger SMS from app or backend)
4. Click **"📱 Can Send SMS?"** → Shows 9/10 remaining
5. Repeat until limit reached
6. Verify `canSend: false` response

**Expected Results:**
- Credits decrement correctly ✅
- Limit enforced at 0 ✅
- Clear error message shown ✅

---

### Scenario 5: Declined Payment Testing

**Purpose:** Test failed payment handling

**Steps:**
1. Login via Google OAuth
2. Select "Pro" plan
3. Click **"🛒 Create Checkout"**
4. Use declined card: `4000 0000 0000 0002`
5. Attempt payment

**Expected Results:**
- Stripe shows "Card declined" ✅
- No subscription created ✅
- User remains on FREE plan ✅

---

### Scenario 6: 3D Secure Testing

**Purpose:** Test 3D Secure authentication flow

**Steps:**
1. Login via Google OAuth
2. Select "Enterprise" plan
3. Click **"🛒 Create Checkout"**
4. Use 3D Secure card: `4000 0025 0000 3155`
5. Complete 3D Secure challenge
6. Verify subscription

**Expected Results:**
- 3D Secure modal appears ✅
- Authentication completes ✅
- Subscription activates ✅

---

### Scenario 7: Bulk Task Stress Test

**Purpose:** Test system under load

**Steps:**
1. Login via Google OAuth
2. Enter "20" in bulk count field
3. Click **"📦 Bulk Create"**
4. Watch Activity Log for all 20 creations
5. Click **"🔄 Refresh List"**
6. Verify all 20 tasks appear

**Expected Results:**
- All 20 tasks created ✅
- No timeouts or errors ✅
- Task list loads completely ✅

---

### Scenario 8: Customer Portal Testing

**Purpose:** Test subscription management portal

**Steps:**
1. Login and purchase a subscription
2. Click **"⚙️ Customer Portal"**
3. In portal, try:
   - View subscription details
   - Click "Update plan"
   - Click "Cancel subscription"
   - View payment history

**Expected Results:**
- Portal opens correctly ✅
- Plan changes work ✅
- Cancellation works ✅
- Return to app works ✅

---

## Troubleshooting

### Common Issues

#### "401 Unauthorized" on all API calls

**Cause:** JWT token expired or invalid

**Solution:**
1. Click **"🚪 Logout"** in Auth card
2. Re-login via Google/Facebook OAuth
3. Retry the API call

---

#### Google Login popup doesn't appear

**Cause:** Popup blocked by browser

**Solution:**
1. Check browser's popup blocker
2. Allow popups for this site
3. Retry login

---

#### "No such price" error on checkout

**Cause:** Using Product ID instead of Price ID in backend

**Solution:**
1. Check `.env` file on server
2. Ensure `STRIPE_PRICE_*` values start with `price_` not `prod_`
3. Restart backend after fixing

---

#### Subscription status stays "FREE" after payment

**Cause:** Webhook not updating database

**Solution:**
1. Check Stripe Dashboard → Webhooks → Event deliveries
2. Look for failed deliveries
3. Check backend logs for webhook errors
4. Verify webhook signing secret matches

---

#### Task operations fail with 404

**Cause:** Task doesn't exist or wrong Task ID

**Solution:**
1. Click **"🔄 Refresh List"** in Task List
2. Select a task by clicking on it
3. Verify Task ID is populated
4. Retry the operation

---

#### Notifications not saving

**Cause:** Task not selected or invalid phone format

**Solution:**
1. Ensure a task is selected (highlighted in list)
2. Check phone number format: `+1XXXXXXXXXX`
3. Verify backend has notification endpoints

---

#### Environment switching fails

**Cause:** CORS or network issues

**Solution:**
1. Check if target environment is running
2. Verify CORS is configured for tester domain
3. Check browser console for errors

---

## API Reference

### Authentication Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/auth/google` | POST | No | Exchange Google token for JWT |
| `/api/auth/facebook` | POST | No | Exchange Facebook token for JWT |
| `/api/auth/me` | GET | JWT | Get current user info |

### Task Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/tasks` | GET | JWT | List all user's tasks |
| `/api/tasks` | POST | JWT | Create new task |
| `/api/tasks/{id}` | GET | JWT | Get task by ID |
| `/api/tasks/{id}` | PUT | JWT | Update task |
| `/api/tasks/{id}` | DELETE | JWT | Delete task |

### Stripe Endpoints

| Endpoint | Method | Auth | Description |
|----------|--------|------|-------------|
| `/api/stripe/plans` | GET | No | List available plans |
| `/api/stripe/subscription` | GET | JWT | Get user's subscription |
| `/api/stripe/can-send-sms` | GET | JWT | Check SMS credits |
| `/api/stripe/can-use-ai` | GET | JWT | Check AI credits |
| `/api/stripe/create-checkout-session` | POST | JWT | Create Stripe Checkout |
| `/api/stripe/create-portal-session` | POST | JWT | Create Customer Portal |
| `/api/stripe/webhook` | POST | Sig | Stripe webhook receiver |

### Request Headers

```
Authorization: Bearer <JWT_TOKEN>
Content-Type: application/json
```

### Response Codes

| Code | Meaning |
|------|---------|
| 200 | Success |
| 201 | Created |
| 204 | No Content (successful delete) |
| 400 | Bad Request (validation error) |
| 401 | Unauthorized (missing/invalid token) |
| 403 | Forbidden (not allowed) |
| 404 | Not Found |
| 500 | Server Error |

---

## Quick Reference Card

### Test Cards

| Card | Number | Result |
|------|--------|--------|
| Visa Success | `4242 4242 4242 4242` | ✅ Approved |
| Visa Decline | `4000 0000 0000 0002` | ❌ Declined |
| 3D Secure | `4000 0025 0000 3155` | 🔐 Auth Required |
| Insufficient | `4000 0000 0000 9995` | ❌ Insufficient Funds |

### Keyboard Shortcuts

| Key | Action |
|-----|--------|
| Enter | Submit form (in input fields) |
| Escape | Close popups |

### Status Colors

| Color | Meaning |
|-------|---------|
| 🟢 Green | Success / Active |
| 🔵 Blue | Info / Processing |
| 🟡 Yellow | Warning |
| 🔴 Red | Error / Danger |

---

*Document Version: 1.0*
*Last Updated: November 2025*
*Task Manager Pro - SriInfosoft Inc*
