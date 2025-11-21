package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.PushSubscription;
import com.sriinfosoft.taskmanager.repository.PushSubscriptionRepository;

import org.apache.http.HttpResponse;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.jose4j.lang.JoseException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.Security;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.ExecutionException;

/**
 * Service for sending Web Push notifications
 * VERSION: Uses userEmail instead of userId
 */
@Service
public class PushNotificationService {

    private static final Logger logger = LoggerFactory.getLogger(PushNotificationService.class);

    @Autowired
    private PushSubscriptionRepository pushSubscriptionRepository;

    @Value("${push.vapid.public}")
    private String vapidPublicKey;

    @Value("${push.vapid.private}")
    private String vapidPrivateKey;

    @Value("${push.vapid.subject}")
    private String vapidSubject;

    private PushService pushService;

    @PostConstruct
    public void init() throws GeneralSecurityException, IOException {
        logger.info("🔧 Initializing Push Notification Service...");
        
        // ⭐ ADD THIS ENTIRE TRY/CATCH BLOCK
        try {
            // Load BouncyCastleProvider class dynamically
            Class<?> bcProviderClass = Class.forName("org.bouncycastle.jce.provider.BouncyCastleProvider");
            
            // Create instance
            Object bcProviderInstance = bcProviderClass.getDeclaredConstructor().newInstance();
            
            // Cast to Provider
            java.security.Provider bcProvider = (java.security.Provider) bcProviderInstance;
            
            // Check if already registered
            if (Security.getProvider(bcProvider.getName()) == null) {
                Security.addProvider(bcProvider);
                logger.info("✅ BouncyCastle security provider registered via reflection");
            } else {
                logger.info("ℹ️ BouncyCastle security provider already registered");
            }
        } catch (ClassNotFoundException e) {
            logger.error("❌ BouncyCastle library not found in classpath");
            throw new RuntimeException("BouncyCastle library not found", e);
        } catch (Exception e) {
            logger.error("❌ Failed to register BouncyCastle provider: {}", e.getMessage(), e);
            throw new RuntimeException("BouncyCastle provider registration failed", e);
        }
        // ⭐ END OF ADDED CODE
        
        // Your existing code continues here
        pushService = new PushService();
        pushService.setPublicKey(vapidPublicKey);
        pushService.setPrivateKey(vapidPrivateKey);
        pushService.setSubject("mailto:support@sriinfosoft.com");
        
        logger.info("✅ Push Notification Service initialized successfully");
    }

    /**
     * Save push subscription for a user (by email)
     */
    public void saveSubscription(String userEmail, PushSubscription subscription) {
        try {
            Objects.requireNonNull(userEmail, "userEmail must not be null");
            Objects.requireNonNull(subscription, "subscription must not be null");

            subscription.setUserEmail(userEmail);
            pushSubscriptionRepository.save(subscription);
            logger.info("✅ Push subscription saved for user: {}", userEmail);
        } catch (Exception e) {
            logger.error("❌ Error saving push subscription: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to save subscription", e);
        }
    }

    /**
     * Delete push subscription
     */
    public void deleteSubscription(String userEmail, PushSubscription subscription) {
        try {
            Objects.requireNonNull(userEmail, "userEmail must not be null");
            Objects.requireNonNull(subscription, "subscription must not be null");

            List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserEmail(userEmail);
            for (PushSubscription sub : subscriptions) {
                if (sub.getEndpoint().equals(subscription.getEndpoint())) {
                    pushSubscriptionRepository.delete(sub);
                    logger.info("✅ Push subscription deleted for user: {}", userEmail);
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error deleting push subscription: {}", e.getMessage(), e);
            throw new RuntimeException("Failed to delete subscription", e);
        }
    }

    /**
     * Check if user has any push subscriptions (by email)
     */
    public boolean hasSubscription(String userEmail) {
        Objects.requireNonNull(userEmail, "userEmail must not be null");
        return !pushSubscriptionRepository.findByUserEmail(userEmail).isEmpty();
    }

    //ADDED - New overloaded method to send notification with task ID
    //This allows the frontend to navigate to specific task when notification is clicked
    /**
     * Send push notification to a specific user with task ID (by email)
     * The task ID is included in the notification data so the frontend can identify
     * which task triggered the notification
     * 
     * @param userEmail The user's email address
     * @param title The notification title
     * @param body The notification body text
     * @param taskId The ID of the task this notification is about
     */
    public void sendNotificationToUser(String userEmail, String title, String body, Long taskId) {
        if (pushService == null) {
            logger.error("❌ Push service not initialized. Cannot send notification.");
            return;
        }

        try {
            Objects.requireNonNull(userEmail, "userEmail must not be null");
            Objects.requireNonNull(taskId, "taskId must not be null");

            List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserEmail(userEmail);
            
            if (subscriptions.isEmpty()) {
                logger.warn("⚠️ No push subscriptions found for user: {}", userEmail);
                return;
            }

            logger.info("📤 Sending notification with task ID {} to {} subscription(s) for user: {}", 
                       taskId, subscriptions.size(), userEmail);

            for (PushSubscription subscription : subscriptions) {
                try {
                    //ADDED - Call the new method that includes task ID
                    sendNotificationWithTaskId(subscription, title, body, taskId);
                } catch (Exception e) {
                    logger.error("❌ Failed to send to subscription {}: {}", subscription.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error sending notification with task ID to user {}: {}", userEmail, e.getMessage(), e);
        }
    }

    //ADDED - New private method to send notification with task ID included in payload
    //The task ID is included in the "data" field so the service worker can extract it
    /**
     * Send push notification to a specific subscription with task ID
     * The notification payload includes task data in the "data" field
     */
    private void sendNotificationWithTaskId(PushSubscription subscription, String title, String body, Long taskId) 
            throws GeneralSecurityException, IOException, JoseException, ExecutionException, InterruptedException {

        logger.info("Sending notification with task ID to subscription: {}", subscription.getId());
        logger.info("Task ID: {}", taskId);
        logger.info("Title: {}", title);
        logger.info("Body: {}", body);

        //MODIFIED - Include task ID in the notification data
        //The "data" field is accessible in the service worker via notification.data
        String payload = String.format(
            "{\"title\":\"%s\",\"body\":\"%s\",\"data\":{\"taskId\":\"%s\"}}",
            escapeJson(title),
            escapeJson(body),
            taskId  //ADDED - Include task ID so frontend can identify the specific task
        );

        logger.info("Notification payload: {}", payload);

        Notification notification = new Notification(
            subscription.getEndpoint(),
            subscription.getP256dh(),
            subscription.getAuth(),
            payload
        );

        try {
            // Send notification and get response
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode == 201) {
                // Success! Notification sent
                logger.info("✅ Notification with task ID {} sent successfully to subscription: {}", 
                           taskId, subscription.getId());
                
                // ✅ UPDATE last_used_at timestamp
                updateSubscriptionLastUsed(subscription);
                
            } else if (statusCode == 410) {
                // Subscription expired/invalid - remove it
                logger.warn("⚠️ Subscription {} is expired (410 Gone), removing from database", subscription.getId());
                pushSubscriptionRepository.delete(subscription);
                
            } else {
                // Other error
                logger.error("❌ Failed to send notification to subscription {}. Status: {}", 
                            subscription.getId(), statusCode);
            }
            
        } catch (Exception e) {
            logger.error("❌ Exception sending notification with task ID to subscription {}: {}", 
                        subscription.getId(), e.getMessage());
            throw e;
        }
    }
    /**
     * Send push notification to a specific user (by email)
     */
    public void sendNotificationToUser(String userEmail, String title, String body) {
        if (pushService == null) {
            logger.error("❌ Push service not initialized. Cannot send notification.");
            return;
        }

        try {
            Objects.requireNonNull(userEmail, "userEmail must not be null");

            List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUserEmail(userEmail);
            
            if (subscriptions.isEmpty()) {
                logger.warn("⚠️ No push subscriptions found for user: {}", userEmail);
                return;
            }

            logger.info("📤 Sending notification to {} subscription(s) for user: {}", subscriptions.size(), userEmail);

            for (PushSubscription subscription : subscriptions) {
                try {
                    sendNotification(subscription, title, body);
                } catch (Exception e) {
                    logger.error("❌ Failed to send to subscription {}: {}", subscription.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error sending notification to user {}: {}", userEmail, e.getMessage(), e);
        }
    }

    /**
     * Send push notification to a specific subscription
     */
/*     private void sendNotification(PushSubscription subscription, String title, String body) 
            throws GeneralSecurityException, IOException, JoseException, ExecutionException, InterruptedException {
        
        String payload = String.format(
            "{\"title\":\"%s\",\"body\":\"%s\"}",
            escapeJson(title),
            escapeJson(body)
        );

        Notification notification = new Notification(
            subscription.getEndpoint(),
            subscription.getP256dh(),
            subscription.getAuth(),
            payload
        );

        pushService.send(notification);
        logger.info("✅ Notification sent successfully to subscription: {}", subscription.getId());
    } */

    /**
     * Send push notification to a specific subscription
     * Updates last_used_at timestamp after successful send
     * Handles expired subscriptions (410 Gone)
     */
    private void sendNotification(PushSubscription subscription, String title, String body) 
            throws GeneralSecurityException, IOException, JoseException, ExecutionException, InterruptedException {
        logger.info("Sending notification to subscription: {}", subscription.getId());
        logger.info("Subscription: {}", subscription);
        logger.info("Title: {}", title);
        logger.info("Body: {}", body);
        logger.info("Endpoint: {}", subscription.getEndpoint());
        logger.info("P256dh: {}", subscription.getP256dh());
        logger.info("Auth: {}", subscription.getAuth());
        logger.info("Device Type: {}", subscription.getDeviceType());
        logger.info("Browser: {}", subscription.getBrowser());
        logger.info("OS: {}", subscription.getOs());
        logger.info("Device Name: {}", subscription.getDeviceName());
        logger.info("Last Used At: {}", subscription.getLastUsedAt());
        logger.info("Created At: {}", subscription.getCreatedAt());
        logger.info("Updated At: {}", subscription.getUpdatedAt());
        logger.info("User Email: {}", subscription.getUserEmail());

        String payload = String.format(
            "{\"title\":\"%s\",\"body\":\"%s\"}",
            escapeJson(title),
            escapeJson(body)
        );

        Notification notification = new Notification(
            subscription.getEndpoint(),
            subscription.getP256dh(),
            subscription.getAuth(),
            payload
        );

        try {
            // Send notification and get response
            HttpResponse response = pushService.send(notification);
            int statusCode = response.getStatusLine().getStatusCode();
            
            if (statusCode == 201) {
                // Success! Notification sent
                logger.info("✅ Notification sent successfully to subscription: {}", subscription.getId());
                
                // ✅ UPDATE last_used_at timestamp
                updateSubscriptionLastUsed(subscription);
                
            } else if (statusCode == 410) {
                // Subscription expired/invalid - remove it
                logger.warn("⚠️ Subscription {} is expired (410 Gone), removing from database", subscription.getId());
                pushSubscriptionRepository.delete(subscription);
                
            } else {
                // Other error
                logger.error("❌ Failed to send notification to subscription {}. Status: {}", 
                            subscription.getId(), statusCode);
            }
            
        } catch (Exception e) {
            logger.error("❌ Exception sending notification to subscription {}: {}", 
                        subscription.getId(), e.getMessage());
            throw e;
        }
    }

    /**
     * Update last_used_at timestamp for a subscription
     * Called after successfully sending a notification
     */
    private void updateSubscriptionLastUsed(PushSubscription subscription) {
        try {
            subscription.setUpdatedAt(LocalDateTime.now());
            pushSubscriptionRepository.save(subscription);
            logger.debug("📝 Updated last_used_at for subscription {}", subscription.getId());
        } catch (Exception e) {
            // Don't let this failure stop the notification process
            logger.warn("⚠️ Failed to update last_used_at for subscription {}: {}", 
                    subscription.getId(), e.getMessage());
        }
    }    
    private String escapeJson(String text) {
        if (text == null) return "";
        return text
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t");
    }

    public void sendNotificationToAll(String title, String body) {
        if (pushService == null) {
            logger.error("❌ Push service not initialized. Cannot send notification.");
            return;
        }

        try {
            List<PushSubscription> allSubscriptions = pushSubscriptionRepository.findAll();
            logger.info("📤 Broadcasting notification to {} subscriptions", allSubscriptions.size());

            for (PushSubscription subscription : allSubscriptions) {
                try {
                    sendNotification(subscription, title, body);
                } catch (Exception e) {
                    logger.error("❌ Failed to send to subscription {}: {}", subscription.getId(), e.getMessage());
                }
            }
        } catch (Exception e) {
            logger.error("❌ Error broadcasting notification: {}", e.getMessage(), e);
        }
    }
}