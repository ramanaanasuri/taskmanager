package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.model.PushSubscription;
import com.sriinfosoft.taskmanager.repository.PushSubscriptionRepository;
import com.sriinfosoft.taskmanager.security.JwtTokenProvider;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.Map;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

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
    private static final Logger logger = LoggerFactory.getLogger(PushController.class);

    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(
            @RequestHeader("Authorization") String authHeader,
            @RequestHeader(value = "User-Agent", required = false) String userAgent,  // ADD THIS
            @RequestBody Map<String, Object> subscriptionData,jakarta.servlet.http.HttpServletRequest request
    ) {
        try {
            // Get user email from authentication context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = authentication.getName();
            
            // Fallback: extract from token if authentication doesn't work
            if (userEmail == null || userEmail.equals("anonymousUser")) {
                String token = authHeader.replace("Bearer ", "");
                userEmail = jwtTokenProvider.getEmailFromToken(token);
            }
            
            // Extract subscription details
            String endpoint = (String) subscriptionData.get("endpoint");
            @SuppressWarnings("unchecked")
            Map<String, String> keys = (Map<String, String>) subscriptionData.get("keys");
            String p256dh = keys.get("p256dh");
            String auth = keys.get("auth");
            // === CAPTURE IP ADDRESS ===
            String clientIp = getClientIp(request);            

            // === ADD DEVICE DETECTION ===
            String deviceType = detectDeviceType(userAgent);
            String browser = detectBrowser(userAgent);
            String os = detectOS(userAgent);
            String deviceName = detectDeviceName(userAgent);
            
            // Log device info
            logger.info("📱 Device Detection:");
            System.out.println("   Type: " + deviceType);
            System.out.println("   Browser: " + browser);
            System.out.println("   OS: " + os);
            System.out.println("   Device: " + deviceName);
            logger.info("   IP Address: " + clientIp);
            System.out.println("   User-Agent: " + userAgent);            
            
            // Check if subscription already exists
            var existingSubscription = subscriptionRepository.findByEndpoint(endpoint);
            
            if (existingSubscription.isPresent()) {
                // Update existing subscription
                PushSubscription subscription = existingSubscription.get();
                subscription.setUserEmail(userEmail);
                subscription.setP256dh(p256dh);
                subscription.setAuth(auth);

                // === UPDATE DEVICE FIELDS ===
                subscription.setDeviceType(deviceType);
                subscription.setBrowser(browser);
                subscription.setOs(os);
                subscription.setDeviceName(deviceName);
                subscription.setCreatedFromIp(clientIp);  // UPDATE IP
                subscription.setLastUsedAt(java.time.LocalDateTime.now());                
                subscriptionRepository.save(subscription);

                System.out.println("✅ Subscription UPDATED for: " + userEmail + " (" + deviceType + "/" + browser + ")");
                
                return ResponseEntity.ok(Map.of(
                    "message", "Subscription updated",
                    "subscriptionId", subscription.getId(),
                    "deviceType", deviceType,
                    "browser", browser,
                    "os", os,
                    "deviceName", deviceName
                ));
            } else {
                // Create new subscription
                PushSubscription subscription = new PushSubscription(
                    userEmail,
                    endpoint,
                    p256dh,
                    auth
                );
                // === SET DEVICE FIELDS ===
                subscription.setDeviceType(deviceType);
                subscription.setBrowser(browser);
                subscription.setOs(os);
                subscription.setDeviceName(deviceName);
                subscription.setCreatedFromIp(clientIp);  // SET IP
                subscription.setCreatedAt(java.time.LocalDateTime.now());
                subscription.setLastUsedAt(java.time.LocalDateTime.now());                
                subscriptionRepository.save(subscription);
                
                return ResponseEntity.ok(Map.of(
                    "message", "Subscription created",
                    "subscriptionId", subscription.getId()
                ));
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to save subscription",
                "details", e.getMessage()
            ));
        }
    }
    
    @DeleteMapping("/unsubscribe")
    public ResponseEntity<?> unsubscribe(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> data
    ) {
        try {
            // Get user email from authentication context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = authentication.getName();
            
            // Fallback to token extraction
            if (userEmail == null || userEmail.equals("anonymousUser")) {
                String token = authHeader.replace("Bearer ", "");
                userEmail = jwtTokenProvider.getEmailFromToken(token);
            }
            
            String endpoint = data.get("endpoint");
            
            var subscription = subscriptionRepository.findByEndpoint(endpoint);
            
            if (subscription.isPresent() && subscription.get().getUserEmail().equals(userEmail)) {
                subscriptionRepository.delete(subscription.get());
                return ResponseEntity.ok(Map.of("message", "Subscription deleted"));
            } else {
                return ResponseEntity.notFound().build();
            }
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to delete subscription",
                "details", e.getMessage()
            ));
        }
    }

    /**
     * Check if a subscription endpoint belongs to the current user
     * Used to handle multiple users logging in on the same device
     * 
     * This is the ONLY new method needed - add it to your existing PushController.java
     */
    @PostMapping("/check-subscription")
    public ResponseEntity<?> checkSubscription(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, String> data
    ) {
        try {
            // Get user email from authentication context
            Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
            String userEmail = authentication.getName();
            
            // Fallback to token extraction (same pattern as your subscribe method)
            if (userEmail == null || userEmail.equals("anonymousUser")) {
                String token = authHeader.replace("Bearer ", "");
                userEmail = jwtTokenProvider.getEmailFromToken(token);
            }
            
            String endpoint = data.get("endpoint");
            
            if (endpoint == null) {
                return ResponseEntity.badRequest().body(Map.of(
                    "error", "endpoint is required"
                ));
            }
            
            // Find subscription by endpoint (your repository already has this method!)
            var subscription = subscriptionRepository.findByEndpoint(endpoint);
            
            if (subscription.isPresent()) {
                // Check if it belongs to the current user
                boolean belongsToUser = subscription.get().getUserEmail().equals(userEmail);
                
                return ResponseEntity.ok(Map.of(
                    "belongsToUser", belongsToUser,
                    "userEmail", subscription.get().getUserEmail()
                ));
            }
            
            // Subscription doesn't exist in database
            return ResponseEntity.ok(Map.of("belongsToUser", false));
            
        } catch (Exception e) {
            e.printStackTrace();
            return ResponseEntity.badRequest().body(Map.of(
                "error", "Failed to check subscription",
                "details", e.getMessage()
            ));
        }
    }  
    
    /**
     * Detect device type from User-Agent
     */
    private String detectDeviceType(String userAgent) {
        if (userAgent == null) return "unknown";
        
        String ua = userAgent.toLowerCase();
        
        // Mobile devices
        if (ua.contains("mobile") || 
            ua.contains("android") || 
            ua.contains("iphone") || 
            ua.contains("ipad") || 
            ua.contains("ipod") || 
            ua.contains("blackberry") || 
            ua.contains("windows phone")) {
            return "mobile";
        }
        
        // Tablets
        if (ua.contains("tablet") || ua.contains("ipad")) {
            return "tablet";
        }
        
        // Desktop
        return "web";
    }

    /**
     * Detect browser from User-Agent
     */
    private String detectBrowser(String userAgent) {
        if (userAgent == null) return "Unknown";
        
        String ua = userAgent.toLowerCase();
        
        if (ua.contains("edg/")) return "Edge";
        if (ua.contains("chrome/") && !ua.contains("edg/")) return "Chrome";
        if (ua.contains("firefox/")) return "Firefox";
        if (ua.contains("safari/") && !ua.contains("chrome")) return "Safari";
        if (ua.contains("opera") || ua.contains("opr/")) return "Opera";
        
        return "Unknown";
    }

    /**
     * Detect OS from User-Agent
     */
    private String detectOS(String userAgent) {
        if (userAgent == null) return "Unknown";
        
        String ua = userAgent.toLowerCase();
        
        if (ua.contains("android")) return "Android";
        if (ua.contains("iphone") || ua.contains("ipad") || ua.contains("ipod")) return "iOS";
        if (ua.contains("windows")) return "Windows";
        if (ua.contains("mac os")) return "macOS";
        if (ua.contains("linux")) return "Linux";
        
        return "Unknown";
    }

    /**
     * Get device name (simplified)
     */
    private String detectDeviceName(String userAgent) {
        if (userAgent == null) return null;
        
        String ua = userAgent.toLowerCase();
        
        // Extract device model for Android
        if (ua.contains("android")) {
            // Try to find device model (e.g., "SM-G960F", "Pixel 5")
            // This is simplified - you can enhance it
            if (ua.contains("pixel")) return "Google Pixel";
            if (ua.contains("galaxy")) return "Samsung Galaxy";
            return "Android Device";
        }
        
        // iOS devices
        if (ua.contains("iphone")) return "iPhone";
        if (ua.contains("ipad")) return "iPad";
        
        // Desktop
        if (ua.contains("windows")) return "Windows PC";
        if (ua.contains("mac os")) return "Mac";
        if (ua.contains("linux")) return "Linux PC";
        
        return null;
    }  
    
    /**
     * Get client IP address, checking for proxies and load balancers
     */
    private String getClientIp(jakarta.servlet.http.HttpServletRequest request) {
        // Check common proxy headers first
        String ip = request.getHeader("X-Forwarded-For");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // X-Forwarded-For can contain multiple IPs, take the first one
            return ip.split(",")[0].trim();
        }
        
        ip = request.getHeader("X-Real-IP");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            return ip;
        }
        
        // CloudFront specific
        ip = request.getHeader("CloudFront-Viewer-Address");
        if (ip != null && !ip.isEmpty() && !"unknown".equalsIgnoreCase(ip)) {
            // Format is "ip:port", extract just the IP
            return ip.split(":")[0];
        }
        
        // Fallback to remote address
        return request.getRemoteAddr();
    }    
}
