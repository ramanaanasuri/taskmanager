package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.User;
import com.sriinfosoft.taskmanager.model.UserActivity;
import com.sriinfosoft.taskmanager.repository.UserActivityRepository;
import com.sriinfosoft.taskmanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The velocity guardrail: exempt users are never throttled; a non-exempt user
 * at the daily ceiling is blocked exactly once (sticky, with reason and a
 * single admin alert); a blocked user is refused by the gate itself.
 */
@ExtendWith(MockitoExtension.class)
class AiGuardrailTest {

    @Mock UserRepository userRepository;
    @Mock UserActivityRepository activityRepository;
    @Mock ActivityService activityService;
    @InjectMocks AiUsageService service;

    private User freeUser(String email, int used, int limit) {
        User u = new User();
        u.setSubscriptionPlan(User.SubscriptionPlan.free);
        u.setEmail(email);
        u.setAiRequestsUsed(used);
        u.setAiRequestsLimit(limit);
        return u;
    }

    @BeforeEach
    void config() {
        ReflectionTestUtils.setField(service, "dailyBlockThreshold", 10);
        ReflectionTestUtils.setField(service, "exemptEmails", "ranasuri@gmail.com, kid@example.com");
        ReflectionTestUtils.setField(service, "billingLive", false);
    }

    @Test
    void exemptEmail_isNeverBlocked_regardlessOfVelocity() {
        User u = freeUser("ranasuri@gmail.com", 1, 200);
        service.applyVelocityGuardrail(u);
        assertThat(u.getAiBlocked()).isFalse();
        verifyNoInteractions(activityRepository);
    }

    @Test
    void exemptList_acceptsMixedSeparators() {
        // comma, semicolon, and whitespace must all delimit the whitelist
        ReflectionTestUtils.setField(service, "exemptEmails",
                "me@example.com, family@example.com;friend@example.com  extra@example.com");
        for (String email : new String[]{
                "family@example.com", "friend@example.com", "extra@example.com"}) {
            User u = freeUser(email, 1, 200);
            service.applyVelocityGuardrail(u);
            assertThat(u.getAiBlocked()).as(email + " should be exempt").isFalse();
        }
        verifyNoInteractions(activityRepository);
    }

    @Test
    void exemptEmail_bypassesMonthlyCap() {
        ReflectionTestUtils.setField(service, "exemptEmails", "family@example.com");
        // hasCredit is true for a whitelisted email even with no users row at all
        assertThat(service.hasCredit("family@example.com")).isTrue();
        assertThat(service.hasCredit("Family@Example.COM")).isTrue();
        // and a non-listed email still depends on the plan limit
        assertThat(service.hasCredit("stranger@example.com")).isFalse();
    }

    @Test
    void exemptMatch_isCaseInsensitive() {
        ReflectionTestUtils.setField(service, "exemptEmails", "family@example.com");
        User u = freeUser("Family@Example.COM", 1, 200);
        service.applyVelocityGuardrail(u);
        assertThat(u.getAiBlocked()).isFalse();
        verifyNoInteractions(activityRepository);
    }

    @Test
    void blankExemptList_exemptsNobody() {
        ReflectionTestUtils.setField(service, "exemptEmails", "");
        User u = freeUser("anyone@example.com", 1, 200);
        when(activityRepository.countByEmailAndEventTypeAndCreatedAtAfter(
                eq("anyone@example.com"), eq(UserActivity.AI_CALL), any(LocalDateTime.class)))
                .thenReturn(10L);
        service.applyVelocityGuardrail(u);
        assertThat(u.getAiBlocked()).isTrue();
    }

    @Test
    void testModePaidPlan_isNotExempt_whileBillingIsNotLive() {
        User u = freeUser("stranger@x", 1, 200);
        u.setSubscriptionPlan(User.SubscriptionPlan.pro); // clicked through test checkout
        when(activityRepository.countByEmailAndEventTypeAndCreatedAtAfter(
                eq("stranger@x"), eq(UserActivity.AI_CALL), any(LocalDateTime.class)))
                .thenReturn(10L);
        service.applyVelocityGuardrail(u);
        assertThat(u.getAiBlocked()).isTrue();
        assertThat(u.getBlockedReason()).contains("threshold 10");
        verify(activityService).alertAdmin(contains("blocked stranger@x"), anyString());
    }

    @Test
    void livePaidPlan_isExempt() {
        ReflectionTestUtils.setField(service, "billingLive", true);
        User u = freeUser("customer@x", 1, 200);
        u.setSubscriptionPlan(User.SubscriptionPlan.pro);
        service.applyVelocityGuardrail(u);
        assertThat(u.getAiBlocked()).isFalse();
        verifyNoInteractions(activityRepository);
    }

    @Test
    void underThreshold_staysUnblocked() {
        User u = freeUser("new@x", 3, 25);
        when(activityRepository.countByEmailAndEventTypeAndCreatedAtAfter(
                anyString(), anyString(), any(LocalDateTime.class))).thenReturn(9L);
        service.applyVelocityGuardrail(u);
        assertThat(u.getAiBlocked()).isFalse();
        verify(activityService, never()).alertAdmin(anyString(), anyString());
    }

    @Test
    void alreadyBlocked_shortCircuits_noSecondAlert() {
        User u = freeUser("burner@x", 12, 25);
        u.setAiBlocked(true);
        service.applyVelocityGuardrail(u);
        verifyNoInteractions(activityRepository);
        verify(activityService, never()).alertAdmin(anyString(), anyString());
    }

    @Test
    void blockedUser_isRefusedByTheGate() {
        User u = freeUser("burner@x", 1, 25); // credits remain…
        u.setAiBlocked(true);                 // …but the block wins
        when(userRepository.findByEmail("burner@x")).thenReturn(Optional.of(u));
        assertThat(service.hasCredit("burner@x")).isFalse();
        assertThat(service.consume("burner@x")).isFalse();
        verify(userRepository, never()).save(any());
    }
}
