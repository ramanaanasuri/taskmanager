package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.service.AiService;
import com.sriinfosoft.taskmanager.service.AiUsageService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

/**
 * AI endpoints. All are JWT-protected by SecurityConfig's
 * anyRequest().authenticated() — no security changes were needed.
 *
 * Flow for every endpoint: metering check -> model call -> consume credit.
 * The credit is consumed only on SUCCESS, so a failed model call never
 * charges the user.
 */
@RestController
@RequestMapping("/api/ai")
public class AiController {

    @Autowired
    private AiService aiService;

    @Autowired
    private AiUsageService aiUsageService;

    @Autowired
    private com.sriinfosoft.taskmanager.agent.AgentService agentService;

    /**
     * Tier 1a: natural-language task creation.
     * Body: {"text": "...", "timezone": "America/Los_Angeles"}
     * 200 -> {"task": {...}, "aiRequests": {"used": n, "limit": m}}
     * 402 -> plan limit reached (frontend opens the plans modal)
     * 503 -> AI unavailable (frontend falls back to the manual form)
     */
    @PostMapping("/parse-task")
    public ResponseEntity<?> parseTask(@RequestBody Map<String, String> body) {
        String email = getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }

        String text = body.getOrDefault("text", "").trim();
        if (text.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "text is required"));
        }
        if (text.length() > 1000) {
            text = text.substring(0, 1000);
        }

        System.out.println("🤖 [AiController] parse-task for: " + email);

        // ---- Metering gate (the 402 path is a feature: it sells the upgrade) ----
        if (!aiUsageService.hasCredit(email)) {
            int[] usage = aiUsageService.usage(email);
            Map<String, Object> err = new HashMap<>();
            err.put("error", "You've used all AI requests included in your plan.");
            err.put("code", "AI_LIMIT_REACHED");
            err.put("aiRequests", Map.of("used", usage[0], "limit", usage[1]));
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED).body(err);
        }

        // ---- Model call ----
        Map<String, Object> parsed;
        try {
            parsed = aiService.parseTask(text, body.get("timezone"));
        } catch (AiService.AiUnavailableException e) {
            System.out.println("❌ [AiController] " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI is unavailable right now — please use the form.",
                                 "code", "AI_UNAVAILABLE"));
        }

        // ---- Consume credit only after success ----
        aiUsageService.consume(email);
        int[] usage = aiUsageService.usage(email);

        Map<String, Object> out = new HashMap<>();
        out.put("task", parsed);
        out.put("aiRequests", Map.of("used", usage[0], "limit", usage[1]));
        return ResponseEntity.ok(out);
    }

    /**
     * Tier 2: conversational agent with tools.
     * Body: {"messages":[{"role":"user","content":"..."}...],
     *        "confirmed": false, "timezone":"America/Los_Angeles"}
     * The CLIENT holds conversation history (the API is stateless).
     * One AI credit per user turn (not per loop iteration).
     */
    @PostMapping("/chat")
    public ResponseEntity<?> chat(@RequestBody Map<String, Object> body) {
        String email = getCurrentUserEmail();
        if (email == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                    .body(Map.of("error", "Not authenticated"));
        }
        @SuppressWarnings("unchecked")
        java.util.List<Map<String, Object>> messages =
                (java.util.List<Map<String, Object>>) body.getOrDefault("messages", java.util.List.of());
        if (messages.isEmpty()) {
            return ResponseEntity.badRequest().body(Map.of("error", "messages is required"));
        }
        boolean confirmed = Boolean.TRUE.equals(body.get("confirmed"));
        String timezone = body.get("timezone") instanceof String s ? s : null;

        System.out.println("\ud83e\udd16 [AiController] chat turn for: " + email
                + (confirmed ? " (confirmed)" : ""));

        if (!aiUsageService.hasCredit(email)) {
            int[] usage = aiUsageService.usage(email);
            return ResponseEntity.status(HttpStatus.PAYMENT_REQUIRED)
                    .body(Map.of("error", "You've used all AI requests included in your plan.",
                                 "code", "AI_LIMIT_REACHED",
                                 "aiRequests", Map.of("used", usage[0], "limit", usage[1])));
        }

        com.sriinfosoft.taskmanager.agent.AgentService.AgentResult result;
        try {
            result = agentService.run(email, messages, confirmed, timezone);
        } catch (AiService.AiUnavailableException e) {
            System.out.println("\u274c [AiController] " + e.getMessage());
            return ResponseEntity.status(HttpStatus.SERVICE_UNAVAILABLE)
                    .body(Map.of("error", "AI is unavailable right now \u2014 please try again later.",
                                 "code", "AI_UNAVAILABLE"));
        }

        aiUsageService.consume(email);
        int[] usage = aiUsageService.usage(email);
        Map<String, Object> out = new HashMap<>();
        out.put("reply", result.reply());
        out.put("actions", result.actions());
        out.put("aiRequests", Map.of("used", usage[0], "limit", usage[1]));
        return ResponseEntity.ok(out);
    }

    // Same pattern as TaskController
    private String getCurrentUserEmail() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated()) {
            return null;
        }
        Object principal = authentication.getPrincipal();
        if (principal instanceof String email && email.contains("@")) {
            return email;
        }
        return null;
    }
}
