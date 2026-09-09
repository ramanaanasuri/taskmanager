package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.User;
import com.sriinfosoft.taskmanager.repository.UserRepository;
import com.sriinfosoft.taskmanager.model.UserActivity;
import com.sriinfosoft.taskmanager.repository.UserActivityRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * Metering gate for AI features.
 *
 * Every AI endpoint goes through this service before (check) and after
 * (consume) calling the model, so usage is always enforced against the
 * plan limits that Stripe webhooks maintain on the User row
 * (ai_requests_used / ai_requests_limit).
 *
 * The actual rules live on the entity: User.canMakeAiRequest() already
 * handles the enterprise-unlimited case, and User.useAiCredit() handles
 * null-safety. This class only adds persistence and transactionality.
 */
@Service
public class AiUsageService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserActivityRepository activityRepository;

    @Autowired
    private ActivityService activityService;

    /** Daily per-user AI-call ceiling for non-exempt users; crossing it sets a sticky block. */
    @Value("${admin.ai.block.threshold:10}")
    private int dailyBlockThreshold;

    /** Emails never throttled (you, family, invited testers). Separate with comma, semicolon, or spaces. */
    @Value("${admin.exempt.emails:}")
    private String exemptEmails;

    /** While Stripe runs test keys, "paid" is demo money: plans exempt nobody. */
    @Value("${billing.live:false}")
    private boolean billingLive;

    /** Read-only check used before calling the model. */
    public boolean hasCredit(String email) {
        if (isExemptEmail(email)) return true;   // whitelist: no monthly cap
        return userRepository.findByEmail(email)
                .map(User::canMakeAiRequest)
                .orElse(false);
    }

    /**
     * Consume one AI credit after a successful model call.
     * Re-checks the limit inside the transaction; returns false if the
     * user raced to the limit between check and consume (harmless — the
     * response they already earned is still returned to them).
     */
    @Transactional
    public boolean consume(String email) {
        Optional<User> userOpt = userRepository.findByEmail(email);
        if (userOpt.isEmpty()) {
            return false;
        }
        User user = userOpt.get();
        if (!isExemptEmail(email) && !user.canMakeAiRequest()) {
            return false;
        }
        user.useAiCredit();
        userRepository.save(user);
        System.out.println("🤖 [AiUsage] " + email + " used AI credit: "
                + user.getAiRequestsUsed() + "/" + user.getAiRequestsLimit());
        activityService.record(email, UserActivity.AI_CALL,
                user.getAiRequestsUsed() + "/" + user.getAiRequestsLimit(), null);
        applyVelocityGuardrail(user);
        return true;
    }

    /** Usage snapshot for including in API responses. */
    public int[] usage(String email) {
        return userRepository.findByEmail(email)
                .map(u -> new int[]{
                        u.getAiRequestsUsed() == null ? 0 : u.getAiRequestsUsed(),
                        u.getAiRequestsLimit() == null ? 0 : u.getAiRequestsLimit()})
                .orElse(new int[]{0, 0});
    }


    /** True when this email is on the configured exempt list (family, friends, admin). */
    boolean isExemptEmail(String email) {
        if (email == null || exemptEmails == null || exemptEmails.isBlank()) return false;
        // Accept comma, semicolon, or whitespace as separators, in any mix.
        for (String e : exemptEmails.split("[,;\\s]+")) {
            if (e.trim().equalsIgnoreCase(email)) return true;
        }
        return false;
    }

    /** True when this account is never throttled by the velocity guardrail. */
    boolean isExempt(User user) {
        if (isExemptEmail(user.getEmail())) return true;
        // Real paying customers are exempt only when billing is live money.
        return billingLive && user.getSubscriptionPlan() != User.SubscriptionPlan.free;
    }

    /**
     * Velocity brake: a non-exempt user crossing the daily AI-call ceiling is
     * blocked (sticky, survives the monthly reset) and the admin is alerted
     * exactly once. Deterministic and cheap: one indexed count per AI call.
     */
    void applyVelocityGuardrail(User user) {
        try {
            if (Boolean.TRUE.equals(user.getAiBlocked()) || isExempt(user)) return;
            java.time.LocalDateTime midnight = java.time.LocalDate.now().atStartOfDay();
            long today = activityRepository.countByEmailAndEventTypeAndCreatedAtAfter(
                    user.getEmail(), UserActivity.AI_CALL, midnight);
            if (today >= dailyBlockThreshold) {
                user.setAiBlocked(true);
                user.setBlockedReason("guardrail: " + today + " AI calls on "
                        + java.time.LocalDate.now() + " (threshold " + dailyBlockThreshold + ")");
                userRepository.save(user);
                System.out.println("🛑 [AiUsage] BLOCKED " + user.getEmail()
                        + " — " + user.getBlockedReason());
                activityService.alertAdmin("AI guardrail blocked " + user.getEmail(),
                        "<h3>🛑 AI guardrail tripped</h3>"
                        + "<p><b>User:</b> " + user.getEmail() + "<br>"
                        + "<b>Reason:</b> " + user.getBlockedReason() + "<br>"
                        + "<b>Plan:</b> " + user.getSubscriptionPlan() + "</p>"
                        + "<p>AI features are now refused for this account; tasks still work. "
                        + "Unblock via dbtools: <code>UPDATE users SET ai_blocked=0, blocked_reason=NULL "
                        + "WHERE email='" + user.getEmail() + "';</code></p>");
            }
        } catch (Exception e) {
            System.out.println("⚠️ [AiUsage] guardrail check failed: " + e.getMessage());
        }
    }

    /**
     * Monthly reset - makes "N requests per month" literally true.
     * Runs on the 1st at 00:05 in the configured zone (defaults match the
     * digest's zone). Cron/zone overridable via AI_RESET_CRON / DIGEST_ZONE.
     */
    @Scheduled(cron = "${ai.reset.cron:0 5 0 1 * *}", zone = "${ai.digest.zone:America/Los_Angeles}")
    @Transactional
    public void resetMonthlyCounters() {
        int users = userRepository.resetMonthlyUsageCounters();
        System.out.println("🔄 [AiUsage] Monthly reset: AI + SMS counters zeroed for " + users + " users");
    }
}
