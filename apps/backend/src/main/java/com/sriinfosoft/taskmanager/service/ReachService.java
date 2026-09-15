package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.config.ReachabilityProperties;
import com.sriinfosoft.taskmanager.model.ChannelVerification;
import com.sriinfosoft.taskmanager.model.ChannelVerification.Channel;
import com.sriinfosoft.taskmanager.model.ChannelVerification.Status;
import com.sriinfosoft.taskmanager.model.ReachAttempt;
import com.sriinfosoft.taskmanager.repository.ChannelVerificationRepository;
import com.sriinfosoft.taskmanager.repository.ReachAttemptRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * The reach engine: nudge the other party over their VERIFIED channels only,
 * reusing the existing email/SMS rails (and the SMS cost guard). Rate-limited
 * per target, and every attempt is logged. Push is best-effort and not sent
 * here (its availability is a live browser subscription). Never "reaches" on
 * an unverified channel.
 */
@Service
public class ReachService {

    private static final Logger log = LoggerFactory.getLogger(ReachService.class);

    private final ChannelVerificationRepository cvRepo;
    private final ReachAttemptRepository reachRepo;
    private final EmailService emailService;
    private final SmsService smsService;

    @Value("${reach.nudge.ratelimit.min:5}")
    private int rateLimitMin;

    public ReachService(ChannelVerificationRepository cvRepo,
                        ReachAttemptRepository reachRepo,
                        EmailService emailService,
                        SmsService smsService) {
        this.cvRepo = cvRepo;
        this.reachRepo = reachRepo;
        this.emailService = emailService;
        this.smsService = smsService;
    }

    /** Reach a target over their verified channels for a session. Rate-limited + logged. */
    public ReachResult reach(String fromEmail, String toEmail, Long offeringId,
                             String title, List<String> requestedChannels) {
        LocalDateTime since = LocalDateTime.now().minusMinutes(rateLimitMin);
        if (reachRepo.countByToEmailAndCreatedAtAfter(toEmail, since) > 0) {
            throw new ResponseStatusException(HttpStatus.TOO_MANY_REQUESTS,
                    "Already nudged in the last " + rateLimitMin + " min");
        }

        List<ChannelVerification> verified = cvRepo.findByUserEmail(toEmail).stream()
                .filter(c -> c.getStatus() == Status.VERIFIED)
                .toList();

        List<String> used = new ArrayList<>();
        String msg = "Your session '" + (title == null ? "" : title) + "' — " + fromEmail
                + " is waiting for you.";
        for (ChannelVerification c : verified) {
            if (c.getChannel() == Channel.PUSH) continue;   // best-effort; not sent here
            if (requestedChannels != null && !requestedChannels.isEmpty()
                    && !requestedChannels.contains(c.getChannel().name())) continue;
            try {
                if (c.getChannel() == Channel.EMAIL) {
                    emailService.sendDigestEmail(c.getValue(), "Session reminder", "<p>" + msg + "</p>");
                    used.add("EMAIL");
                } else if (c.getChannel() == Channel.SMS) {
                    smsService.sendFreeformSms(c.getValue(), msg, fromEmail);
                    used.add("SMS");
                }
            } catch (Exception e) {
                log.warn("reach: {} via {} failed: {}", toEmail, c.getChannel(), e.getMessage());
            }
        }

        String outcome = used.isEmpty() ? "NO_VERIFIED_CHANNEL" : "SENT";
        reachRepo.save(new ReachAttempt(offeringId, fromEmail, toEmail, String.join(",", used), outcome));
        return new ReachResult(outcome, used);
    }

    public record ReachResult(String outcome, List<String> channelsUsed) {}
}
