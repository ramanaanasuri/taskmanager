package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.config.ReachabilityProperties;
import com.sriinfosoft.taskmanager.controller.ApiTesterController;
import com.sriinfosoft.taskmanager.model.ChannelVerification;
import com.sriinfosoft.taskmanager.model.ChannelVerification.Channel;
import com.sriinfosoft.taskmanager.model.ChannelVerification.Status;
import com.sriinfosoft.taskmanager.repository.ChannelVerificationRepository;
import com.sriinfosoft.taskmanager.repository.ReachConsentRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Reachability logic: OTP request/confirm lifecycle, the >=1-guaranteed-channel
 * floor (PUSH never counts), config-driven OTP length/expiry, and the tester
 * echo path (real accounts never get the code; the tester does, and no real
 * send is attempted for it). Pure unit test — repositories and send rails mocked.
 */
@ExtendWith(MockitoExtension.class)
class ReachabilityServiceTest {

    @Mock ChannelVerificationRepository cvRepo;
    @Mock ReachConsentRepository consentRepo;
    @Mock EmailService emailService;
    @Mock SmsService smsService;

    ReachabilityProperties props;
    ReachabilityService service;

    private static final String USER = "real.user@example.com";
    private static final String TESTER = ApiTesterController.TESTER_SUBJECT;

    @BeforeEach
    void setup() {
        props = new ReachabilityProperties();
        props.setOtpLength(6);
        props.setOtpExpiryMin(10);
        props.setGuaranteedChannelsRaw("EMAIL,SMS");
        props.validate();
        service = new ReachabilityService(cvRepo, consentRepo, emailService, smsService, props);
        // No save() stub: the service never uses save's return value, and this
        // project runs strict Mockito stubbing (unnecessary stubs fail the build).
    }

    @Test
    void requestCode_forRealUser_sendsEmail_andDoesNotEchoCode() throws Exception {
        when(cvRepo.findByUserEmailAndChannel(USER, Channel.EMAIL)).thenReturn(Optional.empty());

        ReachabilityService.RequestResult r = service.requestCode(USER, Channel.EMAIL, null);

        assertThat(r.sent()).isTrue();
        assertThat(r.devCode()).isNull();                       // real users never receive the code in-band
        verify(emailService, times(1)).sendDigestEmail(eq(USER), anyString(), anyString());
    }

    @Test
    void requestCode_forTester_echoesCode_andSkipsRealSend() throws Exception {
        when(cvRepo.findByUserEmailAndChannel(TESTER, Channel.EMAIL)).thenReturn(Optional.empty());

        ReachabilityService.RequestResult r = service.requestCode(TESTER, Channel.EMAIL, null);

        assertThat(r.devCode()).isNotNull().hasSize(props.getOtpLength());
        verify(emailService, never()).sendDigestEmail(anyString(), anyString(), anyString());
    }

    @Test
    void requestCode_isConfigDriven_forCodeLength() throws Exception {
        props.setOtpLength(4);                                   // change the knob…
        when(cvRepo.findByUserEmailAndChannel(TESTER, Channel.EMAIL)).thenReturn(Optional.empty());

        ReachabilityService.RequestResult r = service.requestCode(TESTER, Channel.EMAIL, null);

        assertThat(r.devCode()).hasSize(4);                     // …and behaviour tracks it (not hardcoded)
    }

    @Test
    void requestCode_push_isRejected_notOtpVerified() {
        assertThatThrownBy(() -> service.requestCode(USER, Channel.PUSH, null))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void confirmCode_correctUnexpired_marksVerified() {
        ChannelVerification cv = pending(Channel.EMAIL, "123456", LocalDateTime.now().plusMinutes(5));
        when(cvRepo.findByUserEmailAndChannel(USER, Channel.EMAIL)).thenReturn(Optional.of(cv));

        assertThat(service.confirmCode(USER, Channel.EMAIL, "123456")).isTrue();
        assertThat(cv.getStatus()).isEqualTo(Status.VERIFIED);
        assertThat(cv.getCode()).isNull();
    }

    @Test
    void confirmCode_wrongCode_isRejected_andStaysPending() {
        ChannelVerification cv = pending(Channel.EMAIL, "123456", LocalDateTime.now().plusMinutes(5));
        when(cvRepo.findByUserEmailAndChannel(USER, Channel.EMAIL)).thenReturn(Optional.of(cv));

        assertThat(service.confirmCode(USER, Channel.EMAIL, "000000")).isFalse();
        assertThat(cv.getStatus()).isEqualTo(Status.PENDING);
    }

    @Test
    void confirmCode_expiredCode_isRejected() {
        ChannelVerification cv = pending(Channel.EMAIL, "123456", LocalDateTime.now().minusMinutes(1));
        when(cvRepo.findByUserEmailAndChannel(USER, Channel.EMAIL)).thenReturn(Optional.of(cv));

        assertThat(service.confirmCode(USER, Channel.EMAIL, "123456")).isFalse();
    }

    @Test
    void floor_metByOneVerifiedGuaranteedChannel() {
        when(cvRepo.findByUserEmail(USER)).thenReturn(List.of(verified(Channel.EMAIL)));
        assertThat(service.isVerified(USER)).isTrue();
    }

    @Test
    void floor_notMetByVerifiedPushAlone() {
        when(cvRepo.findByUserEmail(USER)).thenReturn(List.of(verified(Channel.PUSH)));
        assertThat(service.isVerified(USER)).isFalse();          // PUSH is best-effort, never counts
    }

    // ---- helpers ----
    private ChannelVerification pending(Channel ch, String code, LocalDateTime expiry) {
        ChannelVerification cv = new ChannelVerification(USER, ch);
        cv.setStatus(Status.PENDING);
        cv.setCode(code);
        cv.setCodeExpiresAt(expiry);
        return cv;
    }
    private ChannelVerification verified(Channel ch) {
        ChannelVerification cv = new ChannelVerification(USER, ch);
        cv.setStatus(Status.VERIFIED);
        return cv;
    }
}
