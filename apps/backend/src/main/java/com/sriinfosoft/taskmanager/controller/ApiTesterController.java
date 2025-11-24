package com.sriinfosoft.taskmanager.controller;

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

    @Value("${api.tester.password:taskmanager2025}")
    private String testerPassword;

    @Value("${api.tester.enabled:true}")
    private boolean testerEnabled;

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
        // DEBUG - remove after testing!
        System.out.println("Expected username: " + testerUsername);
        System.out.println("Expected password length: " + testerPassword.length());
        System.out.println("Received username: " + request.getUsername());
        System.out.println("Received password length: " + request.getPassword().length());
        // Validate credentials
        if (testerUsername.equals(request.getUsername()) && 
            testerPassword.equals(request.getPassword())) {
            
            Map<String, Object> response = new HashMap<>();
            response.put("success", true);
            response.put("username", request.getUsername());
            response.put("apiUrl", apiBaseUrl.isEmpty() ? null : apiBaseUrl);
            response.put("message", "Authentication successful");
            
            return ResponseEntity.ok(response);
        }

        return ResponseEntity.status(401)
            .body(Map.of("error", "Invalid username or password"));
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