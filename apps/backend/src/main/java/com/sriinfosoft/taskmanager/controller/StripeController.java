package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.security.JwtTokenProvider;
import com.sriinfosoft.taskmanager.service.StripeService;
import com.stripe.exception.SignatureVerificationException;
import com.stripe.exception.StripeException;
import com.stripe.model.Event;
import com.stripe.net.Webhook;
import io.jsonwebtoken.Claims;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * REST Controller for Stripe payment operations.
 * Handles checkout, subscription management, and webhooks.
 * 
 * Endpoints:
 *   GET  /api/stripe/subscription           - Get user subscription info
 *   POST /api/stripe/create-checkout-session - Create Stripe checkout
 *   POST /api/stripe/create-portal-session   - Create Stripe customer portal
 *   POST /api/stripe/cancel-subscription     - Cancel subscription (downgrade to free) //Added
 *   GET  /api/stripe/plans                   - Get available plans
 *   POST /api/stripe/webhook                 - Handle Stripe webhooks
 *   GET  /api/stripe/can-send-sms           - Check SMS credits
 *   GET  /api/stripe/can-use-ai             - Check AI credits
 */
@RestController
@RequestMapping("/api/stripe")
public class StripeController {

    private final StripeService stripeService;
    private final JwtTokenProvider tokenProvider;

    public StripeController(StripeService stripeService, JwtTokenProvider tokenProvider) {
        this.stripeService = stripeService;
        this.tokenProvider = tokenProvider;
    }

    // ============ Helper Method ============

    /**
     * Extract user email from JWT token
     */
    private String getEmailFromToken(String bearerToken) {
        String token = bearerToken.replace("Bearer ", "");
        Claims claims = tokenProvider.getClaimsFromToken(token);
        return (String) claims.get("email");
    }

    // ============ Subscription Info ============

    /**
     * GET /api/stripe/subscription
     * Get current user's subscription information
     * 
     * Response includes:
     * - subscriptionStatus: free | active | canceled | past_due | trialing
     * - subscriptionPlan: free | basic | pro | enterprise
     * - isPremium: boolean
     * - canCancel: boolean (true if user can downgrade to free)
     * - smsCreditsUsed/Limit: usage stats
     * - aiRequestsUsed/Limit: usage stats
     */
    @GetMapping("/subscription")
    public ResponseEntity<?> getSubscription(@RequestHeader("Authorization") String bearerToken) {
        try {
            String email = getEmailFromToken(bearerToken);
            System.out.println("📊 [StripeController] Get subscription for: " + email);
            
            Map<String, Object> info = stripeService.getSubscriptionInfo(email);
            return ResponseEntity.ok(info);
        } catch (Exception e) {
            System.err.println("❌ [StripeController] Error getting subscription: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ============ Checkout ============

    /**
     * POST /api/stripe/create-checkout-session
     * Create a Stripe Checkout session for subscription
     * 
     * Request body: { "plan": "basic" | "pro" | "enterprise" }
     */
    @PostMapping("/create-checkout-session")
    public ResponseEntity<?> createCheckoutSession(
            @RequestHeader("Authorization") String bearerToken,
            @RequestBody Map<String, String> request) {
        try {
            String email = getEmailFromToken(bearerToken);
            String plan = request.get("plan");
            
            // Get user name from token
            String token = bearerToken.replace("Bearer ", "");
            Claims claims = tokenProvider.getClaimsFromToken(token);
            String name = (String) claims.get("name");
            
            System.out.println("🛒 [StripeController] Create checkout for: " + email + ", plan: " + plan);
            
            if (plan == null || plan.isEmpty()) {
                return ResponseEntity.badRequest().body(Map.of("error", "Plan is required"));
            }
            
            String checkoutUrl = stripeService.createCheckoutSession(email, name, plan);
            
            return ResponseEntity.ok(Map.of(
                "url", checkoutUrl,
                "plan", plan
            ));
        } catch (StripeException e) {
            System.err.println("❌ [StripeController] Stripe error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("❌ [StripeController] Error creating checkout: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ============ Customer Portal ============

    /**
     * POST /api/stripe/create-portal-session
     * Create a Stripe Customer Portal session for managing subscription
     */
    @PostMapping("/create-portal-session")
    public ResponseEntity<?> createPortalSession(@RequestHeader("Authorization") String bearerToken) {
        try {
            String email = getEmailFromToken(bearerToken);
            System.out.println("🚪 [StripeController] Create portal for: " + email);
            
            String portalUrl = stripeService.createCustomerPortalSession(email);
            
            return ResponseEntity.ok(Map.of("url", portalUrl));
        } catch (StripeException e) {
            System.err.println("❌ [StripeController] Stripe error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("❌ [StripeController] Error creating portal: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    // ============ Cancel Subscription ============ //Added: Complete section

    /**
     * POST /api/stripe/cancel-subscription
     * Cancel user's subscription and downgrade to free plan
     * 
     * This endpoint:
     * 1. Cancels the subscription in Stripe (immediately)
     * 2. Updates the database to free plan
     * 3. Returns updated subscription info
     * 
     * Response on success:
     * {
     *   "success": true,
     *   "message": "Your subscription has been canceled...",
     *   "subscription": { updated subscription info }
     * }
     * 
     * Response on error:
     * { "error": "error message" }
     */
    @PostMapping("/cancel-subscription")
    public ResponseEntity<?> cancelSubscription(@RequestHeader("Authorization") String bearerToken) {
        try {
            String email = getEmailFromToken(bearerToken);
            System.out.println("📛 [StripeController] Cancel subscription for: " + email);
            
            // Call service to cancel subscription
            Map<String, Object> result = stripeService.cancelSubscription(email);
            
            // Get updated subscription info to return to frontend
            Map<String, Object> updatedSubscription = stripeService.getSubscriptionInfo(email);
            result.put("subscription", updatedSubscription);
            
            System.out.println("✅ [StripeController] Subscription canceled successfully for: " + email);
            return ResponseEntity.ok(result);
            
        } catch (StripeException e) {
            System.err.println("❌ [StripeController] Stripe error during cancel: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Stripe error: " + e.getMessage()));
        } catch (IllegalArgumentException e) {
            System.err.println("⚠️ [StripeController] Cancel validation error: " + e.getMessage());
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (Exception e) {
            System.err.println("❌ [StripeController] Error canceling subscription: " + e.getMessage());
            e.printStackTrace();
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", "Failed to cancel subscription. Please try again."));
        }
    }

    // ============ Pricing Info ============

    /**
     * GET /api/stripe/plans
     * Get available subscription plans with pricing
     */
    @GetMapping("/plans")
    public ResponseEntity<?> getPlans() {
        System.out.println("📋 [StripeController] Get pricing plans");
        
        Map<String, Object> plans = new HashMap<>();
        
        // Free plan
        Map<String, Object> freePlan = new HashMap<>();
        freePlan.put("name", "Free");
        freePlan.put("price", 0);
        freePlan.put("interval", "month");
        freePlan.put("features", new String[]{
            "Unlimited tasks",
            "Email notifications",
            "Push notifications",
            "Basic support"
        });
        freePlan.put("smsLimit", 0);
        freePlan.put("aiLimit", 0);
        plans.put("free", freePlan);
        
        // Basic plan
        Map<String, Object> basicPlan = new HashMap<>();
        basicPlan.put("name", "Basic");
        basicPlan.put("price", 4.99);
        basicPlan.put("interval", "month");
        basicPlan.put("features", new String[]{
            "Everything in Free",
            "10 SMS notifications/month",
            "50 AI requests/month",
            "Priority support"
        });
        basicPlan.put("smsLimit", 10);
        basicPlan.put("aiLimit", 50);
        plans.put("basic", basicPlan);
        
        // Pro plan
        Map<String, Object> proPlan = new HashMap<>();
        proPlan.put("name", "Pro");
        proPlan.put("price", 9.99);
        proPlan.put("interval", "month");
        proPlan.put("features", new String[]{
            "Everything in Basic",
            "50 SMS notifications/month",
            "200 AI requests/month",
            "Advanced analytics",
            "Priority support"
        });
        proPlan.put("smsLimit", 50);
        proPlan.put("aiLimit", 200);
        proPlan.put("popular", true);
        plans.put("pro", proPlan);
        
        // Enterprise plan
        Map<String, Object> enterprisePlan = new HashMap<>();
        enterprisePlan.put("name", "Enterprise");
        enterprisePlan.put("price", 29.99);
        enterprisePlan.put("interval", "month");
        enterprisePlan.put("features", new String[]{
            "Everything in Pro",
            "Unlimited SMS notifications",
            "Unlimited AI requests",
            "Custom integrations",
            "Dedicated support",
            "SLA guarantee"
        });
        enterprisePlan.put("smsLimit", -1); // -1 = unlimited
        enterprisePlan.put("aiLimit", -1);
        plans.put("enterprise", enterprisePlan);
        
        return ResponseEntity.ok(plans);
    }

    // ============ Webhook ============

    /**
     * POST /api/stripe/webhook
     * Handle Stripe webhook events
     * This endpoint must be publicly accessible (no auth required)
     */
    @PostMapping("/webhook")
    public ResponseEntity<?> handleWebhook(
            @RequestBody String payload,
            @RequestHeader("Stripe-Signature") String sigHeader) {
        
        System.out.println("📨 [StripeController] Webhook received");
        
        try {
            // Verify webhook signature
            Event event = Webhook.constructEvent(
                payload, 
                sigHeader, 
                stripeService.getWebhookSecret()
            );
            
            System.out.println("✅ [StripeController] Webhook verified: " + event.getType());
            
            // Handle the event
            stripeService.handleWebhookEvent(event);
            
            return ResponseEntity.ok(Map.of("received", true));
        } catch (SignatureVerificationException e) {
            System.err.println("❌ [StripeController] Invalid webhook signature");
            return ResponseEntity.status(HttpStatus.BAD_REQUEST)
                .body(Map.of("error", "Invalid signature"));
        } catch (Exception e) {
            System.err.println("❌ [StripeController] Webhook error: " + e.getMessage());
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(Map.of("error", e.getMessage()));
        }
    }

    // ============ Usage Check (for premium features) ============

    /**
     * GET /api/stripe/can-send-sms
     * Check if user can send SMS (has credits remaining)
     */
    @GetMapping("/can-send-sms")
    public ResponseEntity<?> canSendSms(@RequestHeader("Authorization") String bearerToken) {
        try {
            String email = getEmailFromToken(bearerToken);
            Map<String, Object> info = stripeService.getSubscriptionInfo(email);
            
            boolean canSend = (boolean) info.getOrDefault("canSendSms", false);
            int used = (int) info.getOrDefault("smsCreditsUsed", 0);
            int limit = (int) info.getOrDefault("smsCreditsLimit", 0);
            
            return ResponseEntity.ok(Map.of(
                "canSendSms", canSend,
                "used", used,
                "limit", limit,
                "remaining", Math.max(0, limit - used)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }

    /**
     * GET /api/stripe/can-use-ai
     * Check if user can make AI requests (has credits remaining)
     */
    @GetMapping("/can-use-ai")
    public ResponseEntity<?> canUseAi(@RequestHeader("Authorization") String bearerToken) {
        try {
            String email = getEmailFromToken(bearerToken);
            Map<String, Object> info = stripeService.getSubscriptionInfo(email);
            
            boolean canUse = (boolean) info.getOrDefault("canMakeAiRequest", false);
            int used = (int) info.getOrDefault("aiRequestsUsed", 0);
            int limit = (int) info.getOrDefault("aiRequestsLimit", 0);
            
            return ResponseEntity.ok(Map.of(
                "canUseAi", canUse,
                "used", used,
                "limit", limit,
                "remaining", Math.max(0, limit - used)
            ));
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        }
    }
}