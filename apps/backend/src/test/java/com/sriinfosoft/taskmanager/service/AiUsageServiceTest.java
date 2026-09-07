package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.User;
import com.sriinfosoft.taskmanager.repository.UserActivityRepository;
import com.sriinfosoft.taskmanager.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The metering gate. These tests pin the contract the AI controllers rely on:
 * unknown user = no credit; at-limit = refuse; enterprise = unlimited;
 * consume increments exactly once and persists.
 */
@ExtendWith(MockitoExtension.class)
class AiUsageServiceTest {

    @Mock UserRepository userRepository;
    @Mock UserActivityRepository activityRepository;   // guardrail collaborator
    @Mock ActivityService activityService;             // activity-trail collaborator
    @InjectMocks AiUsageService service;

    @BeforeEach
    void config() {
        // @Value fields aren't populated outside a Spring context.
        ReflectionTestUtils.setField(service, "dailyBlockThreshold", 10);
        ReflectionTestUtils.setField(service, "exemptEmails", "");
        ReflectionTestUtils.setField(service, "billingLive", false);
    }

    private User user(User.SubscriptionPlan plan, Integer used, Integer limit) {
        User u = new User();
        u.setSubscriptionPlan(plan);
        u.setAiRequestsUsed(used);
        u.setAiRequestsLimit(limit);
        return u;
    }

    @Test
    void unknownUser_hasNoCredit_andCannotConsume() {
        when(userRepository.findByEmail("ghost@x")).thenReturn(Optional.empty());
        assertThat(service.hasCredit("ghost@x")).isFalse();
        assertThat(service.consume("ghost@x")).isFalse();
        verify(userRepository, never()).save(any());
    }

    @Test
    void underLimit_hasCredit_andConsumeIncrements() {
        User u = user(User.SubscriptionPlan.free, 2, 10);
        when(userRepository.findByEmail("a@x")).thenReturn(Optional.of(u));
        assertThat(service.hasCredit("a@x")).isTrue();
        assertThat(service.consume("a@x")).isTrue();
        assertThat(u.getAiRequestsUsed()).isEqualTo(3);
        verify(userRepository).save(u);
    }

    @Test
    void atLimit_refusesConsume_withoutSaving() {
        User u = user(User.SubscriptionPlan.basic, 10, 10);
        when(userRepository.findByEmail("b@x")).thenReturn(Optional.of(u));
        assertThat(service.hasCredit("b@x")).isFalse();
        assertThat(service.consume("b@x")).isFalse();
        assertThat(u.getAiRequestsUsed()).isEqualTo(10);
        verify(userRepository, never()).save(any());
    }

    @Test
    void enterprise_isUnlimited_evenPastNominalLimit() {
        User u = user(User.SubscriptionPlan.enterprise, 999, 10);
        when(userRepository.findByEmail("e@x")).thenReturn(Optional.of(u));
        assertThat(service.hasCredit("e@x")).isTrue();
        assertThat(service.consume("e@x")).isTrue();
        verify(userRepository).save(u);
    }

    @Test
    void usageSnapshot_handlesNullsAndMissingUser() {
        User u = new User();
        when(userRepository.findByEmail("n@x")).thenReturn(Optional.of(u));
        assertThat(service.usage("n@x")).containsExactly(0, 0);
        when(userRepository.findByEmail("ghost@x")).thenReturn(Optional.empty());
        assertThat(service.usage("ghost@x")).containsExactly(0, 0);
    }
}
