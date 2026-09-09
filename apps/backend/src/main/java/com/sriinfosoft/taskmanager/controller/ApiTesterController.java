package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import java.net.URI;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * API Tester Authentication Controller
 * 
 * This controller handles authentication for the API Tester console.
 * Credentials are configured in application.properties or environment variables.
 * 
 * Configuration (add to application.properties):
 *   api.tester.username=admin
 *   api.tester.password=your-secure-password
 *   api.tester.enabled=true
 * 
 * Or use environment variables:
 *   API_TESTER_USERNAME=admin
 *   API_TESTER_PASSWORD=your-secure-password
 *   API_TESTER_ENABLED=true
 */
@RestController
@RequestMapping("/api/tester")
@CrossOrigin(origins = "*")
public class ApiTesterController {

    @Value("${api.tester.username:admin}")
    private String testerUsername;

    @Value("${api.tester.password:}")
    private String testerPassword;

    @Value("${api.tester.enabled:true}")
    private boolean testerEnabled;

    /** Pseudo-user owning tester/automation data; never a real account. */
    public static final String TESTER_SUBJECT = "api-tester@sriinfosoft.local";

    @Autowired
    private JwtTokenProvider jwtTokenProvider;

    @Value("${api.base.url:}")
    private String apiBaseUrl;

    /**
     * Authenticate API Tester user
     * POST /api/tester/auth
     */
    @PostMapping("/auth")
    public ResponseEntity<?> authenticate(@RequestBody TesterAuthRequest request) {
        
        // Check if tester is enabled
        if (!testerEnabled) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "API Tester is disabled"));
        }
        // No password configured -> the tester door is disabled, never open.
        if (testerPassword == null || testerPassword.isBlank()) {
            return ResponseEntity.status(403)
                .body(Map.of("error", "API Tester is not configured"));
        }
        // Validate credentials
        if (testerUsername.equals(request.getUsername()) && 
            testerPassword.equals(request.getPassword())) {
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("username", request.getUsername());
            response.put("apiUrl", apiBaseUrl.isEmpty() ? null : apiBaseUrl);
            // Real, scoped JWT: same signing/validation as user tokens, but a
            // pseudo-user subject so tester/automation data stays separate.
            response.put("token", jwtTokenProvider.generateTokenForSubject(TESTER_SUBJECT));
            response.put("message", "Authentication successful");
            
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(401)
            .body(Map.of("error", "Invalid username or password"));
    }

    /**
     * Gate for Caddy forward_auth in front of /reports/* (coverage, Karate).
     * Accepts the tester JWT from a cookie (browser flow via
     * reports-login.html) or a Bearer header (curl). On failure, redirects
     * to the login page with the originally requested path so the browser
     * lands back where it was heading.
     */
    @GetMapping("/verify")
    public ResponseEntity<?> verify(HttpServletRequest request,
            @CookieValue(value = "tester_token", required = false) String cookieToken,
            @RequestHeader(value = "Authorization", required = false) String authHeader,
            @RequestHeader(value = "X-Forwarded-Uri", required = false) String forwardedUri) {

        String token = cookieToken;
        if (token == null && authHeader != null && authHeader.startsWith("Bearer ")) {
            token = authHeader.substring(7);
        }
        boolean ok = testerEnabled
                && token != null
                && jwtTokenProvider.validateToken(token)
                && TESTER_SUBJECT.equals(jwtTokenProvider.getEmailFromToken(token));
        if (ok) {
            return ResponseEntity.ok().build();
        }
        String next = forwardedUri == null ? "/reports/" : forwardedUri;
        return ResponseEntity.status(302)
                .location(URI.create("/reports-login.html?next=" + next))
                .build();
    }

    /**
     * Check if API Tester is enabled
     * GET /api/tester/status
     */
    @GetMapping("/status")
    public ResponseEntity<?> status() {
        Map<String, Object> response = new HashMap<>();
        response.put("enabled", testerEnabled);
        response.put("version", "1.0.0");
        return ResponseEntity.ok(response);
    }

    // Request DTO
    public static class TesterAuthRequest {
        private String username;
        private String password;

        public String getUsername() { return username; }
        public void setUsername(String username) { this.username = username; }
        public String getPassword() { return password; }
        public void setPassword(String password) { this.password = password; }
    }
}