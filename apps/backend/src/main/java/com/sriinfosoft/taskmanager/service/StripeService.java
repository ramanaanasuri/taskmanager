package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.User;
import com.sriinfosoft.taskmanager.model.User.SubscriptionPlan;
import com.sriinfosoft.taskmanager.model.User.SubscriptionStatus;
import com.sriinfosoft.taskmanager.repository.UserRepository;
import com.stripe.Stripe;
import com.stripe.exception.StripeException;
import com.stripe.model.*;
import com.stripe.model.checkout.Session;
import com.stripe.param.*;
import com.stripe.param.checkout.SessionCreateParams;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

/**
 * Service for handling Stripe payment operations.
 * Manages subscriptions, checkout sessions, and webhook events.
 */
@Service
public class StripeService {

    @Value("${stripe.secret.key}")
    private String stripeSecretKey;

    @Value("${stripe.webhook.secret}")
    private String webhookSecret;

    @Value("${frontend.url}")
    private String frontendUrl;

    // Stripe Price IDs for each plan (set these in Stripe Dashboard)
    @Value("${stripe.price.basic:}")
    private String basicPriceId;

    @Value("${stripe.price.pro:}")
    private String proPriceId;

    @Value("${stripe.price.enterprise:}")
    private String enterprisePriceId;

    private final UserRepository userRepository;

    public StripeService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @PostConstruct
    public void init() {
        Stripe.apiKey = stripeSecretKey;
        System.out.println("✅ [StripeService] Initialized with API key: " + 
            (stripeSecretKey != null && stripeSecretKey.length() > 10 ? 
             stripeSecretKey.substring(0, 10) + "..." : "NOT SET"));
    }

    // ============ Plan Limits Configuration ============
    
    private static final Map<SubscriptionPlan, Integer> SMS_LIMITS = Map.of(
        SubscriptionPlan.free, 0,
        SubscriptionPlan.basic, 10,
        SubscriptionPlan.pro, 50,
        SubscriptionPlan.enterprise, Integer.MAX_VALUE
    );

    private static final Map<SubscriptionPlan, Integer> AI_LIMITS = Map.of(
        SubscriptionPlan.free, 0,
        SubscriptionPlan.basic, 50,
        SubscriptionPlan.pro, 200,
        SubscriptionPlan.enterprise, Integer.MAX_VALUE
    );

    // ============ User Management ============

    /**
     * Get or create a User entity for the given email
     */
    @Transactional
    public User getOrCreateUser(String email, String name, String picture) {
        System.out.println("🔍 [StripeService] getOrCreateUser for: " + email);
        
        return userRepository.findByEmail(email)
            .orElseGet(() -> {
                System.out.println("👤 [StripeService] Creating new user: " + email);
                User newUser = new User(email, name, picture);
                return userRepository.save(newUser);
            });
    }

    /**
     * Get user subscription info
     */
    public Map<String, Object> getSubscriptionInfo(String email) {
        System.out.println("📊 [StripeService] Getting subscription info for: " + email);
        
        User user = userRepository.findByEmail(email).orElse(null);
        
        Map<String, Object> info = new HashMap<>();
        
        if (user == null) {
            info.put("subscriptionStatus", "free");
            info.put("subscriptionPlan", "free");
            info.put("isPremium", false);
            info.put("smsCreditsUsed", 0);
            info.put("smsCreditsLimit", 0);
            info.put("aiRequestsUsed", 0);
            info.put("aiRequestsLimit", 0);
        } else {
            info.put("subscriptionStatus", user.getSubscriptionStatus().name());
            info.put("subscriptionPlan", user.getSubscriptionPlan().name());
            info.put("isPremium", user.isPremium());
            info.put("smsCreditsUsed", user.getSmsCreditsUsed());
            info.put("smsCreditsLimit", user.getSmsCreditsLimit());
            info.put("aiRequestsUsed", user.getAiRequestsUsed());
            info.put("aiRequestsLimit", user.getAiRequestsLimit());
            info.put("canSendSms", user.canSendSms());
            info.put("canMakeAiRequest", user.canMakeAiRequest());
            info.put("subscriptionStartDate", user.getSubscriptionStartDate());
            info.put("subscriptionEndDate", user.getSubscriptionEndDate());
        }
        
        return info;
    }

    // ============ Stripe Customer Management ============

    /**
     * Create or get Stripe Customer for user
     */
    @Transactional
    public String getOrCreateStripeCustomer(String email, String name) throws StripeException {
        System.out.println("🔄 [StripeService] getOrCreateStripeCustomer for: " + email);
        
        User user = getOrCreateUser(email, name, null);
        
        // If already has Stripe customer ID, return it
        if (user.getStripeCustomerId() != null) {
            System.out.println("✅ [StripeService] Existing Stripe customer: " + user.getStripeCustomerId());
            return user.getStripeCustomerId();
        }
        
        // Create new Stripe customer
        CustomerCreateParams params = CustomerCreateParams.builder()
            .setEmail(email)
            .setName(name)
            .putMetadata("source", "taskmanager")
            .build();
        
        Customer customer = Customer.create(params);
        
        // Save to database
        user.setStripeCustomerId(customer.getId());
        userRepository.save(user);
        
        System.out.println("✅ [StripeService] Created Stripe customer: " + customer.getId());
        return customer.getId();
    }

    // ============ Checkout Session ============

    /**
     * Create Stripe Checkout Session for subscription
     */
    @Transactional
    public String createCheckoutSession(String email, String name, String plan) throws StripeException {
        System.out.println("🛒 [StripeService] Creating checkout session for: " + email + ", plan: " + plan);
        
        // Get price ID for the plan
        String priceId = getPriceIdForPlan(plan);
        if (priceId == null || priceId.isEmpty()) {
            throw new IllegalArgumentException("Invalid plan or price not configured: " + plan);
        }
        
        // Get or create Stripe customer
        String customerId = getOrCreateStripeCustomer(email, name);
        
        // Create checkout session
        SessionCreateParams params = SessionCreateParams.builder()
            .setMode(SessionCreateParams.Mode.SUBSCRIPTION)
            .setCustomer(customerId)
            .setSuccessUrl(frontendUrl + "?subscription=success&session_id={CHECKOUT_SESSION_ID}")
            .setCancelUrl(frontendUrl + "?subscription=canceled")
            .addLineItem(
                SessionCreateParams.LineItem.builder()
                    .setPrice(priceId)
                    .setQuantity(1L)
                    .build()
            )
            .putMetadata("email", email)
            .putMetadata("plan", plan)
            .build();
        
        Session session = Session.create(params);
        
        System.out.println("✅ [StripeService] Checkout session created: " + session.getId());
        System.out.println("🔗 [StripeService] Checkout URL: " + session.getUrl());
        
        return session.getUrl();
    }

    /**
     * Get Stripe Price ID for subscription plan
     */
    private String getPriceIdForPlan(String plan) {
        return switch (plan.toLowerCase()) {
            case "basic" -> basicPriceId;
            case "pro" -> proPriceId;
            case "enterprise" -> enterprisePriceId;
            default -> null;
        };
    }

    // ============ Customer Portal ============

    /**
     * Create Stripe Customer Portal session for managing subscription
     */
    public String createCustomerPortalSession(String email) throws StripeException {
        System.out.println("🚪 [StripeService] Creating customer portal for: " + email);
        
        User user = userRepository.findByEmail(email)
            .orElseThrow(() -> new IllegalArgumentException("User not found: " + email));
        
        if (user.getStripeCustomerId() == null) {
            throw new IllegalArgumentException("User has no Stripe customer ID");
        }
        
        com.stripe.param.billingportal.SessionCreateParams params =
            com.stripe.param.billingportal.SessionCreateParams.builder()
                .setCustomer(user.getStripeCustomerId())
                .setReturnUrl(frontendUrl)
                .build();
        
        com.stripe.model.billingportal.Session session = 
            com.stripe.model.billingportal.Session.create(params);
        
        System.out.println("✅ [StripeService] Portal session created: " + session.getUrl());
        return session.getUrl();
    }

    // ============ Webhook Handling ============

    /**
     * Handle Stripe webhook events
     */
    @Transactional
    public void handleWebhookEvent(Event event) {
        System.out.println("📨 [StripeService] Webhook event: " + event.getType());
        
        switch (event.getType()) {
            case "checkout.session.completed" -> handleCheckoutCompleted(event);
            case "customer.subscription.created" -> handleSubscriptionCreated(event);
            case "customer.subscription.updated" -> handleSubscriptionUpdated(event);
            case "customer.subscription.deleted" -> handleSubscriptionDeleted(event);
            case "invoice.payment_succeeded" -> handlePaymentSucceeded(event);
            case "invoice.payment_failed" -> handlePaymentFailed(event);
            default -> System.out.println("⚠️ [StripeService] Unhandled event type: " + event.getType());
        }
    }

    private void handleCheckoutCompleted(Event event) {
        Session session = (Session) event.getDataObjectDeserializer()
            .getObject().orElse(null);
        
        if (session == null) return;
        
        String email = session.getCustomerEmail();
        if (email == null && session.getMetadata() != null) {
            email = session.getMetadata().get("email");
        }
        
        System.out.println("✅ [StripeService] Checkout completed for: " + email);
    }

    private void handleSubscriptionCreated(Event event) {
        System.out.println("📬 [StripeService] Processing subscription.created event...");
        
        Subscription subscription = extractSubscriptionFromEvent(event);
        
        if (subscription == null) {
            System.out.println("❌ [StripeService] Failed to deserialize subscription from event");
            return;
        }
        
        System.out.println("📋 [StripeService] Subscription ID: " + subscription.getId());
        System.out.println("📋 [StripeService] Customer ID: " + subscription.getCustomer());
        System.out.println("📋 [StripeService] Status: " + subscription.getStatus());
        
        updateUserSubscription(subscription);
    }

    private void handleSubscriptionUpdated(Event event) {
        System.out.println("📬 [StripeService] Processing subscription.updated event...");
        
        Subscription subscription = extractSubscriptionFromEvent(event);
        
        if (subscription == null) {
            System.out.println("❌ [StripeService] Failed to deserialize subscription from event");
            return;
        }
        
        System.out.println("📋 [StripeService] Subscription ID: " + subscription.getId());
        System.out.println("📋 [StripeService] Customer ID: " + subscription.getCustomer());
        System.out.println("📋 [StripeService] Status: " + subscription.getStatus());
        
        updateUserSubscription(subscription);
    }

    private void handleSubscriptionDeleted(Event event) {
        Subscription subscription = extractSubscriptionFromEvent(event);
        
        if (subscription == null) return;
        
        String customerId = subscription.getCustomer();
        
        userRepository.findByStripeCustomerId(customerId).ifPresent(user -> {
            System.out.println("❌ [StripeService] Subscription canceled for: " + user.getEmail());
            user.setSubscriptionStatus(SubscriptionStatus.canceled);
            user.setSubscriptionPlan(SubscriptionPlan.free);
            user.setSmsCreditsLimit(0);
            user.setAiRequestsLimit(0);
            userRepository.save(user);
        });
    }
    
    /**
     * Extract Subscription from Event using multiple methods
     * Handles API version mismatches between webhook and SDK
     */
    private Subscription extractSubscriptionFromEvent(Event event) {
        // Method 1: Try standard deserialization
        try {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            if (deserializer.getObject().isPresent()) {
                System.out.println("✅ [StripeService] Deserialized using standard method");
                return (Subscription) deserializer.getObject().get();
            }
        } catch (Exception e) {
            System.out.println("⚠️ [StripeService] Standard deserialization failed: " + e.getMessage());
        }
        
        // Method 2: Try unsafe deserialization (ignores API version mismatch)
        try {
            EventDataObjectDeserializer deserializer = event.getDataObjectDeserializer();
            StripeObject obj = deserializer.deserializeUnsafe();
            if (obj instanceof Subscription) {
                System.out.println("✅ [StripeService] Deserialized using unsafe method");
                return (Subscription) obj;
            }
        } catch (Exception e) {
            System.out.println("⚠️ [StripeService] Unsafe deserialization failed: " + e.getMessage());
        }
        
        // Method 3: Extract subscription ID and fetch directly from Stripe API
        try {
            String rawJson = event.getData().getObject().toJson();
            System.out.println("📄 [StripeService] Raw event data length: " + rawJson.length());
            
            // Extract subscription ID from raw JSON using simple string parsing
            String subId = extractIdFromJson(rawJson, "sub_");
            if (subId != null) {
                System.out.println("🔄 [StripeService] Fetching subscription directly: " + subId);
                Subscription subscription = Subscription.retrieve(subId);
                System.out.println("✅ [StripeService] Retrieved subscription via API");
                return subscription;
            }
        } catch (Exception e) {
            System.out.println("⚠️ [StripeService] Direct API fetch failed: " + e.getMessage());
        }
        
        // Method 4: Try to get customer ID and find recent subscription
        try {
            String rawJson = event.getData().getObject().toJson();
            String customerId = extractIdFromJson(rawJson, "cus_");
            if (customerId != null) {
                System.out.println("🔄 [StripeService] Finding subscription for customer: " + customerId);
                Map<String, Object> params = new HashMap<>();
                params.put("customer", customerId);
                params.put("limit", 1);
                SubscriptionCollection subscriptions = Subscription.list(params);
                if (!subscriptions.getData().isEmpty()) {
                    Subscription subscription = subscriptions.getData().get(0);
                    System.out.println("✅ [StripeService] Found subscription via customer lookup: " + subscription.getId());
                    return subscription;
                }
            }
        } catch (Exception e) {
            System.out.println("⚠️ [StripeService] Customer subscription lookup failed: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Extract an ID from JSON string (e.g., sub_xxx or cus_xxx)
     */
    private String extractIdFromJson(String json, String prefix) {
        int start = json.indexOf(prefix);
        if (start == -1) return null;
        
        int end = start;
        while (end < json.length() && (Character.isLetterOrDigit(json.charAt(end)) || json.charAt(end) == '_')) {
            end++;
        }
        
        String id = json.substring(start, end);
        System.out.println("🔍 [StripeService] Extracted ID: " + id);
        return id;
    }

    private void handlePaymentSucceeded(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
            .getObject().orElse(null);
        
        if (invoice == null) return;
        
        String customerId = invoice.getCustomer();
        System.out.println("💰 [StripeService] Payment succeeded for customer: " + customerId);
        
        // Reset monthly usage on successful payment
        userRepository.findByStripeCustomerId(customerId).ifPresent(user -> {
            user.resetMonthlyUsage();
            userRepository.save(user);
            System.out.println("🔄 [StripeService] Reset monthly usage for: " + user.getEmail());
        });
    }

    private void handlePaymentFailed(Event event) {
        Invoice invoice = (Invoice) event.getDataObjectDeserializer()
            .getObject().orElse(null);
        
        if (invoice == null) return;
        
        String customerId = invoice.getCustomer();
        System.out.println("⚠️ [StripeService] Payment failed for customer: " + customerId);
        
        userRepository.findByStripeCustomerId(customerId).ifPresent(user -> {
            user.setSubscriptionStatus(SubscriptionStatus.past_due);
            userRepository.save(user);
        });
    }

    /**
     * Update user subscription based on Stripe subscription object
     */
    private void updateUserSubscription(Subscription subscription) {
        String customerId = subscription.getCustomer();
        System.out.println("🔍 [StripeService] Looking for user with stripe_customer_id: " + customerId);
        
        Optional<User> userOpt = userRepository.findByStripeCustomerId(customerId);
        
        if (userOpt.isEmpty()) {
            System.out.println("❌ [StripeService] User NOT FOUND with stripe_customer_id: " + customerId);
            System.out.println("🔍 [StripeService] Checking all users in database...");
            
            // Debug: List all users with their customer IDs
            userRepository.findAll().forEach(u -> {
                System.out.println("   - User: " + u.getEmail() + " | customer_id: " + u.getStripeCustomerId());
            });
            return;
        }
        
        User user = userOpt.get();
        System.out.println("✅ [StripeService] Found user: " + user.getEmail());
        System.out.println("📝 [StripeService] Updating subscription for: " + user.getEmail());
        
        try {
            // Set subscription ID
            user.setStripeSubscriptionId(subscription.getId());
            System.out.println("   - Set stripe_subscription_id: " + subscription.getId());
            
            // Set status
            SubscriptionStatus newStatus = mapStripeStatus(subscription.getStatus());
            user.setSubscriptionStatus(newStatus);
            System.out.println("   - Set subscription_status: " + newStatus);
            
            // Determine plan from price
            if (subscription.getItems() != null && !subscription.getItems().getData().isEmpty()) {
                String priceId = subscription.getItems().getData().get(0).getPrice().getId();
                System.out.println("   - Price ID from Stripe: " + priceId);
                System.out.println("   - Configured basicPriceId: " + basicPriceId);
                System.out.println("   - Configured proPriceId: " + proPriceId);
                System.out.println("   - Configured enterprisePriceId: " + enterprisePriceId);
                
                SubscriptionPlan plan = determinePlanFromPriceId(priceId);
                user.setSubscriptionPlan(plan);
                System.out.println("   - Set subscription_plan: " + plan);
                
                // Set limits based on plan
                Integer smsLimit = SMS_LIMITS.get(plan);
                Integer aiLimit = AI_LIMITS.get(plan);
                user.setSmsCreditsLimit(smsLimit != null ? smsLimit : 0);
                user.setAiRequestsLimit(aiLimit != null ? aiLimit : 0);
                System.out.println("   - Set sms_credits_limit: " + smsLimit);
                System.out.println("   - Set ai_requests_limit: " + aiLimit);
            } else {
                System.out.println("⚠️ [StripeService] No subscription items found!");
            }
            
            // Set dates
            if (subscription.getCurrentPeriodStart() != null) {
                LocalDateTime startDate = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(subscription.getCurrentPeriodStart()),
                    ZoneId.systemDefault()
                );
                user.setSubscriptionStartDate(startDate);
                System.out.println("   - Set subscription_start_date: " + startDate);
            }
            
            if (subscription.getCurrentPeriodEnd() != null) {
                LocalDateTime endDate = LocalDateTime.ofInstant(
                    Instant.ofEpochSecond(subscription.getCurrentPeriodEnd()),
                    ZoneId.systemDefault()
                );
                user.setSubscriptionEndDate(endDate);
                System.out.println("   - Set subscription_end_date: " + endDate);
            }
            
            User savedUser = userRepository.save(user);
            System.out.println("✅ [StripeService] User subscription SAVED to database!");
            System.out.println("   - Final status: " + savedUser.getSubscriptionStatus());
            System.out.println("   - Final plan: " + savedUser.getSubscriptionPlan());
            System.out.println("   - Final SMS limit: " + savedUser.getSmsCreditsLimit());
            System.out.println("   - Final AI limit: " + savedUser.getAiRequestsLimit());
            
        } catch (Exception e) {
            System.out.println("❌ [StripeService] ERROR updating user subscription: " + e.getMessage());
            e.printStackTrace();
        }
    }

    private SubscriptionStatus mapStripeStatus(String stripeStatus) {
        return switch (stripeStatus) {
            case "active" -> SubscriptionStatus.active;
            case "canceled" -> SubscriptionStatus.canceled;
            case "past_due" -> SubscriptionStatus.past_due;
            case "trialing" -> SubscriptionStatus.trialing;
            default -> SubscriptionStatus.free;
        };
    }

    private SubscriptionPlan determinePlanFromPriceId(String priceId) {
        if (priceId.equals(basicPriceId)) return SubscriptionPlan.basic;
        if (priceId.equals(proPriceId)) return SubscriptionPlan.pro;
        if (priceId.equals(enterprisePriceId)) return SubscriptionPlan.enterprise;
        return SubscriptionPlan.free;
    }

    // ============ Usage Tracking ============

    /**
     * Check and decrement SMS credit for user
     * @return true if SMS can be sent, false if limit reached
     */
    @Transactional
    public boolean useSmsCredit(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        
        if (user == null || !user.canSendSms()) {
            System.out.println("⚠️ [StripeService] SMS limit reached for: " + email);
            return false;
        }
        
        user.useSmsCredit();
        userRepository.save(user);
        System.out.println("📱 [StripeService] SMS credit used for: " + email + 
            " (" + user.getSmsCreditsUsed() + "/" + user.getSmsCreditsLimit() + ")");
        return true;
    }

    /**
     * Check and decrement AI credit for user
     * @return true if AI request can be made, false if limit reached
     */
    @Transactional
    public boolean useAiCredit(String email) {
        User user = userRepository.findByEmail(email).orElse(null);
        
        if (user == null || !user.canMakeAiRequest()) {
            System.out.println("⚠️ [StripeService] AI limit reached for: " + email);
            return false;
        }
        
        user.useAiCredit();
        userRepository.save(user);
        System.out.println("🤖 [StripeService] AI credit used for: " + email + 
            " (" + user.getAiRequestsUsed() + "/" + user.getAiRequestsLimit() + ")");
        return true;
    }

    // ============ Getters for Webhook Secret ============
    
    public String getWebhookSecret() {
        return webhookSecret;
    }
}