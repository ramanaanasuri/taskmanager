#!/bin/bash

# Test Backend: Verify SMS/Email fields are accepted
# This tests if the backend can properly receive and save notification settings

echo "🧪 Testing Backend API - Edit Task with Notifications"
echo "======================================================="

# Configuration
TASK_ID=157  # Change this to your actual task ID
API_URL="http://localhost:8080"  # Change to your backend URL
TOKEN="your-jwt-token-here"  # Replace with your actual JWT token

echo ""
echo "📝 Configuration:"
echo "   Task ID: $TASK_ID"
echo "   API URL: $API_URL"
echo "   Token: ${TOKEN:0:30}..."
echo ""

# Test 1: Update task with SMS enabled
echo "🧪 Test 1: Enable SMS with phone number"
echo "----------------------------------------"
curl -X PUT "$API_URL/api/tasks/$TASK_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "Test Task - SMS Enabled",
    "priority": "MEDIUM",
    "dueDate": "2025-11-23T22:19:00Z",
    "completed": false,
    "emailEnabled": false,
    "notificationsEnabled": true,
    "smsEnabled": true,
    "phoneNumber": "+15055550006"
  }' | jq .

echo -e "\n"

# Test 2: Verify the update
echo "🧪 Test 2: Fetch task to verify changes"
echo "----------------------------------------"
curl -X GET "$API_URL/api/tasks/$TASK_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '{
    id: .id,
    title: .title,
    emailEnabled: .emailEnabled,
    smsEnabled: .smsEnabled,
    phoneNumber: .phoneNumber,
    notificationsEnabled: .notificationsEnabled
  }'

echo -e "\n"

# Test 3: Disable SMS
echo "🧪 Test 3: Disable SMS"
echo "----------------------------------------"
curl -X PUT "$API_URL/api/tasks/$TASK_ID" \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer $TOKEN" \
  -d '{
    "title": "Test Task - SMS Disabled",
    "priority": "MEDIUM",
    "dueDate": "2025-11-23T22:19:00Z",
    "completed": false,
    "emailEnabled": false,
    "notificationsEnabled": true,
    "smsEnabled": false,
    "phoneNumber": null
  }' | jq .

echo -e "\n"

# Test 4: Verify the update
echo "🧪 Test 4: Verify SMS is disabled"
echo "----------------------------------------"
curl -X GET "$API_URL/api/tasks/$TASK_ID" \
  -H "Authorization: Bearer $TOKEN" | jq '{
    id: .id,
    title: .title,
    emailEnabled: .emailEnabled,
    smsEnabled: .smsEnabled,
    phoneNumber: .phoneNumber,
    notificationsEnabled: .notificationsEnabled
  }'

echo -e "\n"
echo "✅ Tests complete!"
echo ""
echo "Expected Results:"
echo "  Test 1: Should show smsEnabled: true, phoneNumber: +15055550006"
echo "  Test 2: Should show same values"
echo "  Test 3: Should show smsEnabled: false, phoneNumber: null"
echo "  Test 4: Should show same values"
echo ""
echo "If backend tests pass but UI doesn't work:"
echo "  → Problem is in the frontend (React state management)"
echo "  → Follow DEBUG_GUIDE_EDIT_MODAL.md"
echo ""
echo "If backend tests fail:"
echo "  → Check backend logs: docker logs taskmanager-backend"
echo "  → Verify Task entity has emailEnabled, smsEnabled fields"
echo "  → Check @RequestBody mapping in controller"
