// apps/frontend/src/utils/pushNotifications.js
// Minimal Web Push Notification utility

const VAPID_PUBLIC_KEY = process.env.REACT_APP_VAPID_PUBLIC_KEY;

/**
 * Convert VAPID key from base64 to Uint8Array
 */
function urlBase64ToUint8Array(base64String) {
  const padding = '='.repeat((4 - (base64String.length % 4)) % 4);
  const base64 = (base64String + padding)
    .replace(/\-/g, '+')
    .replace(/_/g, '/');

  const rawData = window.atob(base64);
  const outputArray = new Uint8Array(rawData.length);

  for (let i = 0; i < rawData.length; ++i) {
    outputArray[i] = rawData.charCodeAt(i);
  }
  return outputArray;
}

/**
 * Subscribe to push notifications
 * This is the ONLY function you need to call from App.js
 * UPDATED: Now checks if subscription belongs to current user
 */
export async function subscribeToPushNotifications(apiBaseUrl, authToken) {
  try {
    console.log('🔔 Starting push notification subscription...');

    // Check if browser supports notifications
    if (!('Notification' in window) || !('serviceWorker' in navigator)) {
      console.error('❌ Browser does not support notifications');
      throw new Error('Browser does not support notifications');
    }

    // Request permission
    const permission = await Notification.requestPermission();
    if (permission !== 'granted') {
      console.log('❌ Notification permission denied');
      throw new Error('Notification permission denied');
    }

    console.log('✅ Notification permission granted');

    // Get existing registration or register if needed
    let registration = await navigator.serviceWorker.getRegistration();
    if (!registration) {
      console.log('[PUSH] SW not registered, registering now...');
      registration = await navigator.serviceWorker.register('/sw.js');
    }
    await registration.ready;
    console.log('[PUSH] ✅ Service worker ready');

    // Check if already subscribed
    let subscription = await registration.pushManager.getSubscription();
    
    if (subscription) {
      console.log('ℹ️ Found existing subscription, checking ownership...');
      
      // CHECK: Does this subscription belong to current user?
      const belongsToCurrentUser = await checkSubscriptionOwnership(
        subscription, 
        apiBaseUrl, 
        authToken
      );
      
      if (belongsToCurrentUser) {
        console.log('✅ Subscription already exists for current user');
        return subscription;
      } else {
        console.log('⚠️ Subscription belongs to different user, removing...');
        await subscription.unsubscribe();
        console.log('✅ Old subscription removed');
        subscription = null; // Will create new one below
      }
    }
	console.log('VAPID public key:', VAPID_PUBLIC_KEY);
	console.log('Type of VAPID key:', typeof VAPID_PUBLIC_KEY);
    // Create new subscription (either first time or after removing old one)
    console.log('📝 Creating new push subscription...');
    subscription = await registration.pushManager.subscribe({
      userVisibleOnly: true,
      applicationServerKey: urlBase64ToUint8Array(VAPID_PUBLIC_KEY)
    });

    console.log('✅ Push subscription created');

    // Send to backend
    await sendSubscriptionToBackend(subscription, apiBaseUrl, authToken);

    return subscription;
  } catch (error) {
    console.error('❌ Error subscribing to push notifications:', error);
    throw error;
  }
}
/**
 * Check if subscription belongs to current user
 * NEW HELPER FUNCTION
 */
async function checkSubscriptionOwnership(subscription, apiBaseUrl, authToken) {
  try {
    const response = await fetch(`${apiBaseUrl}/api/push/check-subscription`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify({
        endpoint: subscription.endpoint
      })
    });

    if (response.ok) {
      const data = await response.json();
      return data.belongsToUser === true;
    }

    // Non-OK (server hiccup, expired token mid-check): UNKNOWN, not "someone else's".
    // Fail open - keep the existing subscription; only an authoritative
    // belongsToUser:false may evict it. Prevents healthy-subscription churn.
    console.warn('⚠️ Ownership check inconclusive (HTTP ' + response.status + ') - keeping existing subscription');
    return true;
  } catch (error) {
    console.error('❌ Error checking subscription ownership (keeping existing subscription):', error);
    return true;
  }
}

/**
 * Send subscription to backend
 */
async function sendSubscriptionToBackend(subscription, apiBaseUrl, authToken) {
  try {
    console.log('📤 Sending subscription to backend...');
    
    const response = await fetch(`${apiBaseUrl}/api/push/subscribe`, {
      method: 'POST',
      headers: {
        'Content-Type': 'application/json',
        'Authorization': `Bearer ${authToken}`
      },
      body: JSON.stringify(subscription)
    });

    if (!response.ok) {
      const error = await response.text();
      throw new Error(`Failed to save subscription: ${error}`);
    }

    console.log('✅ Subscription saved to backend');
    return true;
  } catch (error) {
    console.error('❌ Error sending subscription to backend:', error);
    throw error;
  }
}