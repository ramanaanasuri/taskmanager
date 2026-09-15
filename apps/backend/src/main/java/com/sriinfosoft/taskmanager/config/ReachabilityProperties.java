package com.sriinfosoft.taskmanager.config;

import com.sriinfosoft.taskmanager.model.ChannelVerification.Channel;
import jakarta.annotation.PostConstruct;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Typed, validated configuration for reachability. No policy value is hardcoded:
 * OTP length/expiry and the guaranteed-channel set are all externalized with
 * documented defaults and validated at startup (fail loud, not blank).
 */
@Component
public class ReachabilityProperties {

    @Value("${reachability.otp.length:6}")
    private int otpLength;

    @Value("${reachability.otp.expiry.min:10}")
    private int otpExpiryMin;

    @Value("${reachability.guaranteed.channels:EMAIL,SMS}")
    private String guaranteedChannelsRaw;

    private Set<Channel> guaranteed;

    @PostConstruct
    public void validate() {
        if (otpLength < 4 || otpLength > 10) {
            throw new IllegalStateException("reachability.otp.length must be 4..10, was " + otpLength);
        }
        if (otpExpiryMin < 1 || otpExpiryMin > 60) {
            throw new IllegalStateException("reachability.otp.expiry.min must be 1..60, was " + otpExpiryMin);
        }
        try {
            guaranteed = Arrays.stream(guaranteedChannelsRaw.split(","))
                    .map(String::trim).filter(s -> !s.isEmpty())
                    .map(s -> Channel.valueOf(s.toUpperCase()))
                    .collect(Collectors.toCollection(() -> EnumSet.noneOf(Channel.class)));
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException("reachability.guaranteed.channels invalid: " + guaranteedChannelsRaw, e);
        }
        if (guaranteed.isEmpty()) {
            throw new IllegalStateException("reachability.guaranteed.channels must list at least one channel");
        }
        if (guaranteed.contains(Channel.PUSH)) {
            throw new IllegalStateException("PUSH is best-effort and cannot be a guaranteed channel");
        }
    }

    public int getOtpLength() { return otpLength; }
    public void setOtpLength(int otpLength) { this.otpLength = otpLength; }

    public int getOtpExpiryMin() { return otpExpiryMin; }
    public void setOtpExpiryMin(int otpExpiryMin) { this.otpExpiryMin = otpExpiryMin; }

    public String getGuaranteedChannelsRaw() { return guaranteedChannelsRaw; }
    public void setGuaranteedChannelsRaw(String raw) { this.guaranteedChannelsRaw = raw; }

    public Set<Channel> guaranteedChannels() { return guaranteed; }
    public boolean isGuaranteed(Channel c) { return guaranteed != null && guaranteed.contains(c); }
}
