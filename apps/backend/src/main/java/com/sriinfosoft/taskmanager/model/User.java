package com.sriinfosoft.taskmanager.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

/**
 * User entity for tracking Stripe subscriptions and premium features.
 * Email is the primary identifier, matching OAuth login pattern.
 */
@Entity
@Table(name = "users", indexes = {
    @Index(name = "idx_email", columnList = "email"),
    @Index(name = "idx_stripe_customer_id", columnList = "stripe_customer_id"),
    @Index(name = "idx_subscription_status", columnList = "subscription_status")
})
public class User {
    
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    
    @Column(nullable = false, unique = true)
    private String email;
    
    @Column(name = "name")
    private String name;
    
    @Column(name = "picture", length = 500)
    private String picture;
    
    // ============ Stripe Fields ============
    
    @Column(name = "stripe_customer_id", unique = true)
    private String stripeCustomerId;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_status", nullable = false)
    private SubscriptionStatus subscriptionStatus = SubscriptionStatus.free;
    
    @Enumerated(EnumType.STRING)
    @Column(name = "subscription_plan", nullable = false)
    private SubscriptionPlan subscriptionPlan = SubscriptionPlan.free;
    
    @Column(name = "stripe_subscription_id")
    private String stripeSubscriptionId;
    
    @Column(name = "subscription_start_date")
    private LocalDateTime subscriptionStartDate;
    
    @Column(name = "subscription_end_date")
    private LocalDateTime subscriptionEndDate;
    
    // ============ Premium Feature Limits ============
    
    @Column(name = "sms_credits_used")
    private Integer smsCreditsUsed = 0;
    
    @Column(name = "sms_credits_limit")
    private Integer smsCreditsLimit = 0;
    
    @Column(name = "ai_requests_used")
    private Integer aiRequestsUsed = 0;
    
    @Column(name = "ai_requests_limit")
    private Integer aiRequestsLimit = 0;
    
    // ============ Timestamps ============
    
    @Column(name = "created_at")
    private LocalDateTime createdAt;
    
    @Column(name = "updated_at")
    private LocalDateTime updatedAt;
    
    // ============ Enums ============
    
    public enum SubscriptionStatus {
        free, active, canceled, past_due, trialing
    }
    
    public enum SubscriptionPlan {
        free, basic, pro, enterprise
    }
    
    // ============ Constructors ============
    
    public User() {}
    
    public User(String email) {
        this.email = email;
    }
    
    public User(String email, String name, String picture) {
        this.email = email;
        this.name = name;
        this.picture = picture;
    }
    
    // ============ Lifecycle Callbacks ============
    
    @PrePersist
    public void prePersist() {
        if (createdAt == null) {
            createdAt = LocalDateTime.now();
        }
        if (updatedAt == null) {
            updatedAt = LocalDateTime.now();
        }
        if (subscriptionStatus == null) {
            subscriptionStatus = SubscriptionStatus.free;
        }
        if (subscriptionPlan == null) {
            subscriptionPlan = SubscriptionPlan.free;
        }
        if (smsCreditsUsed == null) {
            smsCreditsUsed = 0;
        }
        if (smsCreditsLimit == null) {
            smsCreditsLimit = 0;
        }
        if (aiRequestsUsed == null) {
            aiRequestsUsed = 0;
        }
        if (aiRequestsLimit == null) {
            aiRequestsLimit = 0;
        }
    }
    
    @PreUpdate
    public void preUpdate() {
        updatedAt = LocalDateTime.now();
    }
    
    // ============ Helper Methods ============
    
    /**
     * Check if user has an active premium subscription
     */
    public boolean isPremium() {
        return subscriptionStatus == SubscriptionStatus.active || 
               subscriptionStatus == SubscriptionStatus.trialing;
    }
    
    /**
     * Check if user can send SMS (has credits remaining)
     */
    public boolean canSendSms() {
        if (subscriptionPlan == SubscriptionPlan.enterprise) {
            return true; // Unlimited for enterprise
        }
        return smsCreditsUsed < smsCreditsLimit;
    }
    
    /**
     * Check if user can make AI requests (has credits remaining)
     */
    public boolean canMakeAiRequest() {
        if (subscriptionPlan == SubscriptionPlan.enterprise) {
            return true; // Unlimited for enterprise
        }
        return aiRequestsUsed < aiRequestsLimit;
    }
    
    /**
     * Increment SMS usage counter
     */
    public void useSmsCredit() {
        if (smsCreditsUsed == null) {
            smsCreditsUsed = 0;
        }
        smsCreditsUsed++;
    }
    
    /**
     * Increment AI usage counter
     */
    public void useAiCredit() {
        if (aiRequestsUsed == null) {
            aiRequestsUsed = 0;
        }
        aiRequestsUsed++;
    }
    
    /**
     * Reset monthly usage counters (called by scheduler on billing cycle)
     */
    public void resetMonthlyUsage() {
        smsCreditsUsed = 0;
        aiRequestsUsed = 0;
    }
    
    // ============ Getters and Setters ============
    
    public Long getId() {
        return id;
    }
    
    public void setId(Long id) {
        this.id = id;
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        this.email = email;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        this.name = name;
    }
    
    public String getPicture() {
        return picture;
    }
    
    public void setPicture(String picture) {
        this.picture = picture;
    }
    
    public String getStripeCustomerId() {
        return stripeCustomerId;
    }
    
    public void setStripeCustomerId(String stripeCustomerId) {
        this.stripeCustomerId = stripeCustomerId;
    }
    
    public SubscriptionStatus getSubscriptionStatus() {
        return subscriptionStatus;
    }
    
    public void setSubscriptionStatus(SubscriptionStatus subscriptionStatus) {
        this.subscriptionStatus = subscriptionStatus;
    }
    
    public SubscriptionPlan getSubscriptionPlan() {
        return subscriptionPlan;
    }
    
    public void setSubscriptionPlan(SubscriptionPlan subscriptionPlan) {
        this.subscriptionPlan = subscriptionPlan;
    }
    
    public String getStripeSubscriptionId() {
        return stripeSubscriptionId;
    }
    
    public void setStripeSubscriptionId(String stripeSubscriptionId) {
        this.stripeSubscriptionId = stripeSubscriptionId;
    }
    
    public LocalDateTime getSubscriptionStartDate() {
        return subscriptionStartDate;
    }
    
    public void setSubscriptionStartDate(LocalDateTime subscriptionStartDate) {
        this.subscriptionStartDate = subscriptionStartDate;
    }
    
    public LocalDateTime getSubscriptionEndDate() {
        return subscriptionEndDate;
    }
    
    public void setSubscriptionEndDate(LocalDateTime subscriptionEndDate) {
        this.subscriptionEndDate = subscriptionEndDate;
    }
    
    public Integer getSmsCreditsUsed() {
        return smsCreditsUsed;
    }
    
    public void setSmsCreditsUsed(Integer smsCreditsUsed) {
        this.smsCreditsUsed = smsCreditsUsed;
    }
    
    public Integer getSmsCreditsLimit() {
        return smsCreditsLimit;
    }
    
    public void setSmsCreditsLimit(Integer smsCreditsLimit) {
        this.smsCreditsLimit = smsCreditsLimit;
    }
    
    public Integer getAiRequestsUsed() {
        return aiRequestsUsed;
    }
    
    public void setAiRequestsUsed(Integer aiRequestsUsed) {
        this.aiRequestsUsed = aiRequestsUsed;
    }
    
    public Integer getAiRequestsLimit() {
        return aiRequestsLimit;
    }
    
    public void setAiRequestsLimit(Integer aiRequestsLimit) {
        this.aiRequestsLimit = aiRequestsLimit;
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public void setCreatedAt(LocalDateTime createdAt) {
        this.createdAt = createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    public void setUpdatedAt(LocalDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }
}