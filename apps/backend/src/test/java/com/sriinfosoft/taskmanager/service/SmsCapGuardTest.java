package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.UserActivity;
import com.sriinfosoft.taskmanager.repository.UserActivityRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * The SMS cost guard: under the cap a send proceeds to the provider (and a
 * failed provider send records nothing); at the cap the send is skipped
 * silently — no exception, so the scheduler never retry-storms — and the
 * admin is alerted exactly once per day; cap 0 is a kill switch.
 * ADDED for SMS Cost Guard.
 */
@ExtendWith(MockitoExtension.class)
class SmsCapGuardTest {

    @Mock UserActivityRepository activityRepository;
    @Mock ActivityService activityService;
    @InjectMocks SmsService service;

    private static final String PHONE = "+15551234567";

    @BeforeEach
    void config() {
        // Twilio selected but deliberately unconfigured: a send that passes the
        // cap check throws before any real HTTP, so tests stay offline.
        ReflectionTestUtils.setField(service, "smsProvider", "twilio");
        ReflectionTestUtils.setField(service, "twilioSid", "");
        ReflectionTestUtils.setField(service, "twilioToken", "");
        ReflectionTestUtils.setField(service, "twilioFrom", "");
        ReflectionTestUtils.setField(service, "smsDailyCap", 10);
    }

    @Test
    void underCap_proceedsToProvider_andRecordsNothingWhenProviderFails() {
        when(activityRepository.countByEventTypeAndCreatedAtAfter(
                eq(UserActivity.SMS_SENT), any(LocalDateTime.class))).thenReturn(5L);

        // Reaching the provider (which throws on blank creds) proves the cap let it through.
        assertThatThrownBy(() -> service.sendTestSms(PHONE))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("incomplete");

        // Recording happens only after a successful send.
        verify(activityService, never()).record(anyString(), anyString(), anyString(), any());
        verify(activityService, never()).alertAdmin(anyString(), anyString());
    }

    @Test
    void atCap_skipsSilently_noExceptionAndNoRecord() {
        when(activityRepository.countByEventTypeAndCreatedAtAfter(
                eq(UserActivity.SMS_SENT), any(LocalDateTime.class))).thenReturn(10L);

        // No throw: the scheduler marks the task notified instead of retrying forever.
        assertThatCode(() -> service.sendTestSms(PHONE)).doesNotThrowAnyException();

        verify(activityService, never()).record(anyString(), anyString(), anyString(), any());
    }

    @Test
    void atCap_alertsAdmin_exactlyOncePerDay() {
        when(activityRepository.countByEventTypeAndCreatedAtAfter(
                eq(UserActivity.SMS_SENT), any(LocalDateTime.class))).thenReturn(10L);

        assertThatCode(() -> service.sendTestSms(PHONE)).doesNotThrowAnyException();
        assertThatCode(() -> service.sendTestSms(PHONE)).doesNotThrowAnyException();
        assertThatCode(() -> service.sendTestSms(PHONE)).doesNotThrowAnyException();

        verify(activityService, times(1)).alertAdmin(contains("cap reached"), anyString());
    }

    @Test
    void overCap_staysSkipped() {
        when(activityRepository.countByEventTypeAndCreatedAtAfter(
                eq(UserActivity.SMS_SENT), any(LocalDateTime.class))).thenReturn(37L);

        assertThatCode(() -> service.sendTestSms(PHONE)).doesNotThrowAnyException();
        verify(activityService, never()).record(anyString(), anyString(), anyString(), any());
    }

    @Test
    void capZero_isAKillSwitch_nothingEverSends() {
        ReflectionTestUtils.setField(service, "smsDailyCap", 0);
        when(activityRepository.countByEventTypeAndCreatedAtAfter(
                eq(UserActivity.SMS_SENT), any(LocalDateTime.class))).thenReturn(0L);

        // With a zero cap even the first send of the day is skipped, not attempted.
        assertThatCode(() -> service.sendTestSms(PHONE)).doesNotThrowAnyException();
        verify(activityService, never()).record(anyString(), anyString(), anyString(), any());
        verify(activityService, times(1)).alertAdmin(contains("cap reached"), anyString());
    }
}
