// Service Worker for Web Push Notifications
console.log('Service Worker loaded');

// Take control immediately
self.addEventListener('install', (event) => {
  console.log('[SW DEBUG] Installing...');
  self.skipWaiting(); // Activate immediately
});

self.addEventListener('activate', (event) => {
  console.log('[SW DEBUG] Activating...');
  event.waitUntil(clients.claim()); // Take control of all pages immediately
  console.log('[SW DEBUG] Claimed all clients');
});

// Handle push notification events
self.addEventListener('push', (event) => {
  console.log('Push notification received:', event);

  const data = event.data ? event.data.json() : {};
  const title = data.title || 'Task Manager Pro';

  //MODIFIED - Preserve task data from backend payload
  const notificationData = data.data || {
    dateOfArrival: Date.now(),
    primaryKey: 1
  };

  const options = {
    body: data.body || 'You have a notification',
    icon: '/logo192.png',
    badge: '/logo192.png',
    vibrate: [200, 100, 200],
    //ADDED - Stay on screen until dismissed. A task reminder that fades after
    // a few seconds is easy to miss if you have stepped away from the desk.
    requireInteraction: true,
    //ADDED - Repeat notifications for the same task replace the previous one
    // instead of stacking up.
    tag: notificationData.taskId ? `task-${notificationData.taskId}` : 'task-generic',
    renotify: true,
    data: notificationData,
    actions: [
      {
        action: 'view',
        title: 'View Task'
      },
      {
        action: 'close',
        title: 'Close'
      }
    ]
  };

  event.waitUntil(
    self.registration.showNotification(title, options)
  );
});

//MODIFIED - Complete rewrite of notification click handler to:
//  1. Extract task ID from notification data (for future use)
//  2. Navigate to existing window instead of opening new tab (preserves session)
//  3. Derive the origin from the service worker itself, so the same file works
//     on AWS and GCP without editing. A hardcoded host sends notifications
//     from one environment to the other.
self.addEventListener('notificationclick', async (event) => {
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
  console.log('[SW DEBUG] 1. Notification clicked at:', new Date().toISOString());
  console.log('[SW DEBUG] 2. Event:', event);
  console.log('[SW DEBUG] 3. Action:', event.action);
  console.log('[SW DEBUG] 4. Notification data:', event.notification.data);

  event.notification.close();
  console.log('[SW DEBUG] 5. Notification closed');

  //ADDED - The "Close" action button previously fell through and opened the
  // app, which is the opposite of what the button says.
  if (event.action === 'close') {
    console.log('[SW DEBUG] 5a. Close action — dismissing without navigating');
    return;
  }

  // Extract task ID
  const notificationData = event.notification.data || {};
  const taskId = notificationData.taskId;
  console.log('[SW DEBUG] 6. Extracted taskId:', taskId);

  // Build target URL
  // self.location.origin is the host this service worker was served from —
  // taskmanager.sriinfosoft.com on AWS, taskmanager.gcp.sriinfosoft.com on GCP.
  const origin = self.location.origin;
  let targetUrl = taskId
    ? `${origin}/?taskId=${taskId}`
    : `${origin}/`;

  console.log('[SW DEBUG] 7. Target URL (with taskId):', targetUrl);

  event.waitUntil(
    (async () => {
      try {
        console.log('[SW DEBUG] 8. Starting async handler');

        // Get all clients
        const allClients = await clients.matchAll({
          type: 'window',
          includeUncontrolled: true
        });
        console.log('[SW DEBUG] 9. Found clients:', allClients.length);

        allClients.forEach((client, i) => {
          console.log(`[SW DEBUG] 10.${i}. Client URL:`, client.url);
          console.log(`[SW DEBUG] 10.${i}. Client ID:`, client.id);
          console.log(`[SW DEBUG] 10.${i}. Client type:`, client.type);
        });

        // Find matching client
        for (const client of allClients) {
          const clientUrl = new URL(client.url);
          const targetUrlObj = new URL(targetUrl);

          console.log('[SW DEBUG] 11. Comparing origins:');
          console.log('[SW DEBUG] 11a. Client origin:', clientUrl.origin);
          console.log('[SW DEBUG] 11b. Target origin:', targetUrlObj.origin);

          if (clientUrl.origin === targetUrlObj.origin) {
            console.log('[SW DEBUG] 12. ✅ Found matching client!');

            // Focus the window
            console.log('[SW DEBUG] 13. Attempting to focus window...');
            await client.focus();
            console.log('[SW DEBUG] 14. ✅ Window focused');

            // Try navigate
            if ('navigate' in client) {
              console.log('[SW DEBUG] 15. client.navigate() is available');
              try {
                console.log('[SW DEBUG] 16. Calling client.navigate()...');
                const result = await client.navigate(targetUrl);
                console.log('[SW DEBUG] 17. ✅ Navigate result:', result);
                console.log('[SW DEBUG] 18. 🎉 SUCCESS - Used client.navigate()');
                return;
              } catch (navError) {
                console.warn('[SW DEBUG] 19. ❌ client.navigate() failed:', navError);
                console.warn('[SW DEBUG] 19a. Error name:', navError.name);
                console.warn('[SW DEBUG] 19b. Error message:', navError.message);
              }
            } else {
              console.log('[SW DEBUG] 15. ❌ client.navigate() NOT available');
            }

            // Fallback to postMessage
            console.log('[SW DEBUG] 20. Using postMessage fallback');
            const message = {
              type: 'NOTIFICATION_CLICK',
              taskId: taskId,
              timestamp: Date.now()
            };
            console.log('[SW DEBUG] 21. Sending message:', message);
            client.postMessage(message);
            console.log('[SW DEBUG] 22. ✅ Message sent');
            console.log('[SW DEBUG] 23. 🎉 SUCCESS - Used postMessage');
            return;
          }
        }

        // No matching client found
        console.log('[SW DEBUG] 24. ⚠️ No matching client found, opening new window');
        const newClient = await clients.openWindow(targetUrl);
        console.log('[SW DEBUG] 25. ✅ New window opened:', newClient);

      } catch (error) {
        console.error('[SW DEBUG] 26. ❌ ERROR in notification handler:', error);
        console.error('[SW DEBUG] 26a. Error name:', error.name);
        console.error('[SW DEBUG] 26b. Error message:', error.message);
        console.error('[SW DEBUG] 26c. Error stack:', error.stack);

        // Last resort - just open window
        console.log('[SW DEBUG] 27. Last resort - opening window directly');
        await clients.openWindow(targetUrl);
      }
    })()
  );

  console.log('[SW DEBUG] 28. Event handler complete');
  console.log('━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━━');
});

//ADDED - Handle messages from the main app (fallback for browsers that don't support client.navigate)
self.addEventListener('message', (event) => {
  console.log('[SW] Message received from app:', event.data);
  if (event.data && event.data.type === 'SKIP_WAITING') {
    self.skipWaiting();
  }
});

// Handle notification close
self.addEventListener('notificationclose', (event) => {
  console.log('Notification closed:', event);
});
