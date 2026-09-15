package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.config.ReachabilityProperties;
import com.sriinfosoft.taskmanager.controller.ApiTesterController;
import com.sriinfosoft.taskmanager.model.ChannelVerification;
import com.sriinfosoft.taskmanager.model.ChannelVerification.Channel;
import com.sriinfosoft.taskmanager.model.ChannelVerification.Status;
import com.sriinfosoft.taskmanager.model.ReachConsent;
import com.sriinfosoft.taskmanager.repository.ChannelVerificationRepository;
import com.sriinfosoft.taskmanager.repository.ReachConsentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * Reachability verification: opt-in by channel, verified-or-it-doesn't-count.
 * Guaranteed channels (EMAIL, SMS) are proven by OTP; a user needs at least
 * one verified guaranteed channel to participate. PUSH is best-effort and is
 * not handled here (its availability is a live browser subscription).
 *
 * Delivery reuses the existing EmailService and SmsService rails (SMS keeps
 * the cost guard). For the tester pseudo-user only, the code is echoed in the
 * response so automation can complete the loop without a real inbox — a real
 * account never receives it (the subject is structurally not a real user).
 */
@Service
public class ReachabilityService {

    private static final Logger log = LoggerFactory.getLogger(ReachabilityService.class);
    private final SecureRandom random = new SecureRandom();

    private final ChannelVerificationRepository cvRepo;
    private final ReachConsentRepository consentRepo;
    private final EmailService emailService;
    private final SmsService smsService;
    private final ReachabilityProperties props;

    public ReachabilityService(ChannelVerificationRepository cvRepo,
                               ReachConsentRepository consentRepo,
                               EmailService emailService,
                               SmsService smsService,
                               ReachabilityProperties props) {
        this.cvRepo = cvRepo;
        this.consentRepo = consentRepo;
        this.emailService = emailService;
        this.smsService = smsService;
        this.props = props;
    }

    /** Save channel selection + terms acceptance; ensure a row per selected channel. */
    public void saveSelection(String email, List<String> channels, String termsVersion) {
        String joined = channels == null ? "" : String.join(",", channels);
        ReachConsent consent = consentRepo.findByUserEmail(email).orElseGet(() -> new ReachConsent());
        consent.setUserEmail(email);
        consent.setChannelsSelected(joined);
        consent.setTermsVersion(termsVersion);
        consent.setUpdatedAt(LocalDateTime.now());
        consentRepo.save(consent);

        if (channels != null) {
            for (String c : channels) {
                Channel ch = Channel.valueOf(c.trim().toUpperCase());
                Optional<ChannelVerification> existing = cvRepo.findByUserEmailAndChannel(email, ch);
                if (existing.isEmpty()) {
                    cvRepo.save(new ChannelVerification(email, ch)); // starts SELECTED
                }
            }
        }
    }

    /** Generate + deliver an OTP for a guaranteed channel. Returns devCode only for the tester. */
    public RequestResult requestCode(String email, Channel channel, String value) {
        if (channel == Channel.PUSH) {
            throw new IllegalArgumentException("PUSH is best-effort and is not verified by OTP");
        }
        String target = (channel == Channel.EMAIL)
                ? (value == null || value.isBlank() ? email : value)
                : value;
        if (target == null || target.isBlank()) {
            throw new IllegalArgumentException("value required for channel " + channel);
        }

        ChannelVerification cv = cvRepo.findByUserEmailAndChannel(email, channel)
                .orElseGet(() -> new ChannelVerification(email, channel));
        String code = generateCode(props.getOtpLength());
        cv.setValue(target);
        cv.setStatus(Status.PENDING);
        cv.setCode(code);
        cv.setCodeExpiresAt(LocalDateTime.now().plusMinutes(props.getOtpExpiryMin()));
        cv.setUpdatedAt(LocalDateTime.now());
        cvRepo.save(cv);

        boolean isTester = ApiTesterController.TESTER_SUBJECT.equals(email);
        if (!isTester) {
            try {
                deliver(channel, target, code, email);
            } catch (Exception e) {
                cv.setLastSendFailedAt(LocalDateTime.now());
                cvRepo.save(cv);
                log.warn("reachability: send failed for {} via {}: {}", email, channel, e.getMessage());
                throw new IllegalStateException("Could not send code via " + channel);
            }
        }
        return new RequestResult(true, isTester ? code : null);
    }

    private void deliver(Channel channel, String target, String code, String email) throws Exception {
        // Transient SMTP/socket drops (e.g. "Broken pipe" during cold STARTTLS) are
        // retried once — the send is synchronous in the request thread, so a single
        // quick retry self-heals the intermittent failure without the user noticing.
        Exception last = null;
        for (int attempt = 1; attempt <= 2; attempt++) {
            try {
                if (channel == Channel.EMAIL) {
                    String body = "<p>Your verification code is <b>" + code + "</b>.</p>"
                            + "<p>It expires in " + props.getOtpExpiryMin() + " minutes.</p>";
                    emailService.sendDigestEmail(target, "Your verification code", body);
                } else if (channel == Channel.SMS) {
                    smsService.sendOtpSms(target, code, email);
                }
                return;
            } catch (Exception e) {
                last = e;
                log.warn("reachability: send attempt {} via {} failed: {}", attempt, channel, e.getMessage());
                if (attempt < 2) {
                    try { Thread.sleep(800); } catch (InterruptedException ie) { Thread.currentThread().interrupt(); }
                }
            }
        }
        throw last;
    }

    /** Confirm an OTP. Verified only when PENDING, unexpired, and the code matches. */
    public boolean confirmCode(String email, Channel channel, String code) {
        Optional<ChannelVerification> opt = cvRepo.findByUserEmailAndChannel(email, channel);
        if (opt.isEmpty()) return false;
        ChannelVerification cv = opt.get();
        boolean ok = cv.getStatus() == Status.PENDING
                && cv.getCode() != null && cv.getCode().equals(code)
                && cv.getCodeExpiresAt() != null
                && cv.getCodeExpiresAt().isAfter(LocalDateTime.now());
        if (!ok) return false;
        cv.setStatus(Status.VERIFIED);
        cv.setVerifiedAt(LocalDateTime.now());
        cv.setCode(null);
        cv.setCodeExpiresAt(null);
        cv.setUpdatedAt(LocalDateTime.now());
        cvRepo.save(cv);
        return true;
    }

    /** Reachability snapshot for the user. floorMet = >=1 guaranteed channel VERIFIED. */
    public SelfStatus self(String email) {
        List<ChannelVerification> rows = cvRepo.findByUserEmail(email);
        List<ChannelStatus> channels = new ArrayList<>();
        boolean floorMet = false;
        List<String> needs = new ArrayList<>();
        for (ChannelVerification cv : rows) {
            channels.add(new ChannelStatus(cv.getChannel().name(), cv.getStatus().name()));
            if (props.isGuaranteed(cv.getChannel()) && cv.getStatus() == Status.VERIFIED) {
                floorMet = true;
            }
        }
        for (Channel g : props.guaranteedChannels()) {
            boolean verified = rows.stream().anyMatch(r ->
                    r.getChannel() == g && r.getStatus() == Status.VERIFIED);
            if (!verified) needs.add(g.name());
        }
        return new SelfStatus(floorMet, floorMet, channels, needs);
    }

    /** Participation gate helper (used by later slices' participation endpoints). */
    public boolean isVerified(String email) {
        return self(email).floorMet();
    }

    private String generateCode(int len) {
        StringBuilder sb = new StringBuilder(len);
        for (int i = 0; i < len; i++) sb.append(random.nextInt(10));
        return sb.toString();
    }

    // ---- response records ----
    public record RequestResult(boolean sent, String devCode) {}
    public record ChannelStatus(String channel, String status) {}
    public record SelfStatus(boolean verified, boolean floorMet,
                             List<ChannelStatus> channels, List<String> needs) {}
}
