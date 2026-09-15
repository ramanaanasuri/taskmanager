package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.ChannelVerification;
import com.sriinfosoft.taskmanager.model.ChannelVerification.Channel;
import com.sriinfosoft.taskmanager.model.ChannelVerification.Status;
import com.sriinfosoft.taskmanager.model.ReachAttempt;
import com.sriinfosoft.taskmanager.repository.ChannelVerificationRepository;
import com.sriinfosoft.taskmanager.repository.ReachAttemptRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/** Reach engine: rate-limit, verified-channels-only (push skipped), logged. */
@ExtendWith(MockitoExtension.class)
class ReachServiceTest {

    @Mock ChannelVerificationRepository cvRepo;
    @Mock ReachAttemptRepository reachRepo;
    @Mock EmailService emailService;
    @Mock SmsService smsService;

    ReachService service;
    private static final String FROM = "mentor@example.com";
    private static final String TO = "learner@example.com";

    @BeforeEach
    void setup() {
        service = new ReachService(cvRepo, reachRepo, emailService, smsService);
        ReflectionTestUtils.setField(service, "rateLimitMin", 5);
    }

    private ChannelVerification verified(Channel ch, String value) {
        ChannelVerification cv = new ChannelVerification(TO, ch);
        cv.setStatus(Status.VERIFIED); cv.setValue(value);
        return cv;
    }

    @Test
    void reach_rateLimited_isTooManyRequests() {
        when(reachRepo.countByToEmailAndCreatedAtAfter(eq(TO), any(LocalDateTime.class))).thenReturn(1L);
        assertThatThrownBy(() -> service.reach(FROM, TO, 9L, "AWS", null))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.TOO_MANY_REQUESTS));
    }

    @Test
    void reach_sendsOverVerifiedEmail_skipsPush_andLogs() throws Exception {
        when(reachRepo.countByToEmailAndCreatedAtAfter(eq(TO), any(LocalDateTime.class))).thenReturn(0L);
        when(cvRepo.findByUserEmail(TO)).thenReturn(List.of(
                verified(Channel.EMAIL, TO), verified(Channel.PUSH, "sub")));

        ReachService.ReachResult r = service.reach(FROM, TO, 9L, "AWS", null);

        assertThat(r.outcome()).isEqualTo("SENT");
        assertThat(r.channelsUsed()).containsExactly("EMAIL");     // push skipped (best-effort)
        verify(emailService).sendDigestEmail(eq(TO), anyString(), anyString());
        verify(reachRepo).save(any(ReachAttempt.class));           // logged
    }

    @Test
    void reach_noVerifiedChannel_logsNoChannel() {
        when(reachRepo.countByToEmailAndCreatedAtAfter(eq(TO), any(LocalDateTime.class))).thenReturn(0L);
        when(cvRepo.findByUserEmail(TO)).thenReturn(List.of());

        ReachService.ReachResult r = service.reach(FROM, TO, 9L, "AWS", null);
        assertThat(r.outcome()).isEqualTo("NO_VERIFIED_CHANNEL");
        verify(reachRepo).save(any(ReachAttempt.class));
    }
}
