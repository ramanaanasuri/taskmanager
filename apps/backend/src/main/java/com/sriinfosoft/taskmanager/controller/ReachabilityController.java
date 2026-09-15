package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.model.ChannelVerification.Channel;
import com.sriinfosoft.taskmanager.service.ReachabilityService;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * Account-level reachability endpoints. All are under /api and therefore
 * authenticated by SecurityConfig; the authenticated email is the owner.
 */
@RestController
@RequestMapping("/api/reachability")
public class ReachabilityController {

    private final ReachabilityService service;

    public ReachabilityController(ReachabilityService service) {
        this.service = service;
    }

    @GetMapping("/self")
    public ResponseEntity<?> self() {
        String email = currentEmail();
        if (email == null) return unauth();
        return ResponseEntity.ok(service.self(email));
    }

    @PostMapping("/channels")
    public ResponseEntity<?> channels(@RequestBody ChannelsRequest req) {
        String email = currentEmail();
        if (email == null) return unauth();
        service.saveSelection(email, req.channelsSelected, req.termsVersion);
        return ResponseEntity.ok(service.self(email));
    }

    @PostMapping("/verify/request")
    public ResponseEntity<?> requestCode(@RequestBody VerifyRequest req) {
        String email = currentEmail();
        if (email == null) return unauth();
        Channel channel;
        try {
            channel = Channel.valueOf(req.channel.trim().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid channel"));
        }
        try {
            ReachabilityService.RequestResult r = service.requestCode(email, channel, req.value);
            return ResponseEntity.ok(r);
        } catch (IllegalArgumentException e) {
            return ResponseEntity.badRequest().body(Map.of("error", e.getMessage()));
        } catch (IllegalStateException e) {
            return ResponseEntity.status(503).body(Map.of("error", e.getMessage()));
        }
    }

    @PostMapping("/verify/confirm")
    public ResponseEntity<?> confirmCode(@RequestBody ConfirmRequest req) {
        String email = currentEmail();
        if (email == null) return unauth();
        Channel channel;
        try {
            channel = Channel.valueOf(req.channel.trim().toUpperCase());
        } catch (Exception e) {
            return ResponseEntity.badRequest().body(Map.of("error", "invalid channel"));
        }
        boolean verified = service.confirmCode(email, channel, req.code);
        return ResponseEntity.ok(Map.of("verified", verified));
    }

    private String currentEmail() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        String name = a.getName();
        if (name == null || "anonymousUser".equals(name)) return null;
        return name;
    }

    private ResponseEntity<?> unauth() {
        return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
    }

    // ---- request bodies ----
    public static class ChannelsRequest {
        public List<String> channelsSelected;
        public String termsVersion;
    }
    public static class VerifyRequest {
        public String channel;
        public String value;
    }
    public static class ConfirmRequest {
        public String channel;
        public String code;
    }
}
