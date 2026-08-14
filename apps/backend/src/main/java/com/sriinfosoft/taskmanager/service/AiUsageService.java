package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.User;
import com.sriinfosoft.taskmanager.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
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

    /** Read-only check used before calling the model. */
    public boolean hasCredit(String email) {
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
        if (!user.canMakeAiRequest()) {
            return false;
        }
        user.useAiCredit();
        userRepository.save(user);
        System.out.println("🤖 [AiUsage] " + email + " used AI credit: "
                + user.getAiRequestsUsed() + "/" + user.getAiRequestsLimit());
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
}
