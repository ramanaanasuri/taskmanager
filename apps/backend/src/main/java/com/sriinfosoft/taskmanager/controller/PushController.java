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

@RestController
@RequestMapping("/api/push")
public class PushController {
    
    @Autowired
    private PushSubscriptionRepository subscriptionRepository;
    
    @Autowired
    private JwtTokenProvider jwtTokenProvider;
    
    @PostMapping("/subscribe")
    public ResponseEntity<?> subscribe(
            @RequestHeader("Authorization") String authHeader,
            @RequestBody Map<String, Object> subscriptionData
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
            
            // Check if subscription already exists
            var existingSubscription = subscriptionRepository.findByEndpoint(endpoint);
            
            if (existingSubscription.isPresent()) {
                // Update existing subscription
                PushSubscription subscription = existingSubscription.get();
                subscription.setUserEmail(userEmail);
                subscription.setP256dh(p256dh);
                subscription.setAuth(auth);
                subscriptionRepository.save(subscription);
                
                return ResponseEntity.ok(Map.of(
                    "message", "Subscription updated",
                    "subscriptionId", subscription.getId()
                ));
            } else {
                // Create new subscription
                PushSubscription subscription = new PushSubscription(
                    userEmail,
                    endpoint,
                    p256dh,
                    auth
                );
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
}
