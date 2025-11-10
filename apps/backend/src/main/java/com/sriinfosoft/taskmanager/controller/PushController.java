package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.model.PushSubscription;
import com.sriinfosoft.taskmanager.repository.PushSubscriptionRepository;
import nl.martijndwars.webpush.Notification;
import nl.martijndwars.webpush.PushService;
import org.bouncycastle.jce.provider.BouncyCastleProvider;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import jakarta.annotation.PostConstruct;
import java.security.Security;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/push")
@CrossOrigin(origins = {
    "https://taskmanager.gcp.sriinfosoft.com",      // GCP HTTPS
    "https://taskmanager.sriinfosoft.com",          // Main domain HTTPS
    "https://dmx58qtgmk8t9.cloudfront.net",         // CloudFront HTTPS (replace with actual)
    "http://taskmanager.gcp.sriinfosoft.com",       // GCP HTTP (local testing)
    "http://taskmanager.sriinfosoft.com"            // Main domain HTTP (local testing)
})
public class PushController {

    @Autowired
    private PushSubscriptionRepository subscriptionRepository;

    @Value("${push.vapid.public}")
    private String publicKey;

    @Value("${push.vapid.private}")
    private String privateKey;

    @Value("${push.vapid.subject}")
    private String subject;

    @PostConstruct
    public void init() {
        if (Security.getProvider("BC") == null) {
            Security.addProvider(new BouncyCastleProvider());
        }
    }

    /**
     * Save push subscription from frontend
     * Stores in push_subscriptions table for multi-device support
     */
    @PostMapping("/subscribe")
    public ResponseEntity<Map<String, Object>> subscribe(
            @AuthenticationPrincipal Jwt jwt,
            @RequestBody Map<String, Object> subscription) {
        
        try {
            String userEmail = jwt.getClaim("email");
            String endpoint = (String) subscription.get("endpoint");
            
            @SuppressWarnings("unchecked")
            Map<String, String> keys = (Map<String, String>) subscription.get("keys");
            String p256dh = keys.get("p256dh");
            String auth = keys.get("auth");
            
            // Check if subscription already exists
            var existing = subscriptionRepository.findByEndpoint(endpoint);
            if (existing.isPresent()) {
                System.out.println("✅ Subscription already exists for: " + userEmail);
                return ResponseEntity.ok(Map.of(
                    "success", true,
                    "message", "Subscription already registered"
                ));
            }
            
            // Save new subscription
            PushSubscription pushSub = new PushSubscription(userEmail, endpoint, p256dh, auth);
            subscriptionRepository.save(pushSub);
            
            System.out.println("✅ Push subscription saved for: " + userEmail);
            System.out.println("📍 Endpoint: " + endpoint);
            
            return ResponseEntity.ok(Map.of(
                "success", true,
                "message", "Subscription saved successfully"
            ));
            
        } catch (Exception e) {
            System.err.println("❌ Error saving subscription: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.internalServerError().body(Map.of(
                "success", false,
                "error", "Failed to save subscription"
            ));
        }
    }

    /**
     * Test endpoint - send notification to all subscribed users
     * Sends to ALL devices per user (multi-device support)
     */
    @PostMapping("/test")
    public ResponseEntity<Map<String, Object>> test(
            @RequestParam(defaultValue = "⏰ Task Reminder") String title,
            @RequestParam(defaultValue = "You have a task due soon") String body) {
        
        List<PushSubscription> allSubscriptions = subscriptionRepository.findAll();
        int sent = 0;
        int failed = 0;

        for (PushSubscription sub : allSubscriptions) {
            try {
                sendPushNotification(sub, title, body, "/");
                sent++;
                System.out.println("✅ Sent to: " + sub.getUserEmail());
            } catch (Exception e) {
                failed++;
                System.err.println("❌ Failed to send to: " + sub.getUserEmail());
                System.err.println("Error: " + e.getMessage());
                
                // If 410 Gone or 404, delete the subscription (expired/invalid)
                if (e.getMessage() != null && 
                    (e.getMessage().contains("410") || e.getMessage().contains("404"))) {
                    subscriptionRepository.deleteByEndpoint(sub.getEndpoint());
                    System.out.println("🗑️ Deleted expired subscription");
                }
            }
        }

        return ResponseEntity.ok(Map.of(
            "success", true,
            "sent", sent,
            "failed", failed,
            "total", allSubscriptions.size(),
            "message", "Sent to " + sent + " of " + allSubscriptions.size() + " subscribers"
        ));
    }

    /**
     * Send push notification to a specific subscription
     * Payload format: { "title": "...", "body": "...", "url": "/" }
     */
    private void sendPushNotification(PushSubscription sub, String title, String body, String url) 
            throws Exception {
        
        // Create JSON payload (keep it short - under 4KB)
        String payload = String.format(
            "{\"title\":%s,\"body\":%s,\"url\":%s}",
            jsonEscape(title),
            jsonEscape(body),
            jsonEscape(url)
        );
        
        // Create web-push notification
        nl.martijndwars.webpush.Subscription webPushSub = 
            new nl.martijndwars.webpush.Subscription(
                sub.getEndpoint(),
                new nl.martijndwars.webpush.Subscription.Keys(
                    sub.getP256dh(),
                    sub.getAuth()
                )
            );
        
        Notification notification = new Notification(webPushSub, payload);
        
        // Create push service and send
        PushService pushService = new PushService();
        pushService.setSubject(subject);
        pushService.setPublicKey(publicKey);
        pushService.setPrivateKey(privateKey);
        
        pushService.send(notification);
    }
    
    /**
     * Simple JSON string escaper
     */
    private String jsonEscape(String str) {
        if (str == null) return "null";
        return "\"" + str
            .replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            + "\"";
    }
    
    /**
     * Get subscription count for authenticated user (debugging)
     */
    @GetMapping("/subscriptions")
    public ResponseEntity<Map<String, Object>> getSubscriptions(
            @AuthenticationPrincipal Jwt jwt) {
        
        String userEmail = jwt.getClaim("email");
        List<PushSubscription> subs = subscriptionRepository.findByUserEmail(userEmail);
        
        return ResponseEntity.ok(Map.of(
            "count", subs.size(),
            "userEmail", userEmail
        ));
    }
}
