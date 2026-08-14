package com.sriinfosoft.taskmanager.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.time.format.TextStyle;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Talks to an LLM over the OpenAI-compatible chat-completions format,
 * which lets the provider be swapped by configuration alone:
 *
 *   Gemini (free tier):  AI_BASE_URL=https://generativelanguage.googleapis.com/v1beta/openai
 *                        AI_MODEL=gemini-2.5-flash
 *   Groq (free tier):    AI_BASE_URL=https://api.groq.com/openai/v1
 *                        AI_MODEL=llama-3.3-70b-versatile
 *   Anthropic (paid):    AI_BASE_URL=https://api.anthropic.com/v1
 *                        AI_MODEL=claude-haiku-4-5
 *
 * Default configuration targets the Gemini free tier: with no billing
 * account attached to the key, quota exhaustion returns an error instead
 * of a charge — overspend is structurally impossible. When the quota is
 * out, callers receive AiUnavailableException and the app degrades to
 * its manual/manual-form fallback paths.
 *
 * Tier 1a: parseTask() — turn a natural-language task description into
 * structured fields. The model NEVER writes to the database: it fills a
 * form, the user confirms, and the existing POST /api/tasks path does the
 * insert. Design rule: propose → confirm → execute.
 */
@Service
public class AiService {

    @Value("${ai.api.key:}")
    private String apiKey;

    @Value("${ai.base.url:https://generativelanguage.googleapis.com/v1beta/openai}")
    private String baseUrl;

    @Value("${ai.model:gemini-2.5-flash}")
    private String model;

    private final ObjectMapper mapper = new ObjectMapper();

    /** Thrown when the model call or JSON extraction fails (incl. quota exhausted). */
    public static class AiUnavailableException extends RuntimeException {
        public AiUnavailableException(String msg, Throwable cause) { super(msg, cause); }
        public AiUnavailableException(String msg) { super(msg); }
    }

    public boolean isConfigured() {
        return apiKey != null && !apiKey.isBlank();
    }

    /**
     * Parse a natural-language task description into structured fields.
     *
     * @param text     what the user typed
     * @param timezone IANA zone from the browser (e.g. America/Los_Angeles);
     *                 falls back to UTC when absent/invalid
     * @return map with keys: title, priority, scheduledDate, notify{email,push,sms}, confidence
     */
    public Map<String, Object> parseTask(String text, String timezone) {
        ZoneId zone;
        try {
            zone = ZoneId.of(timezone == null ? "UTC" : timezone);
        } catch (Exception e) {
            zone = ZoneId.of("UTC");
        }
        LocalDate today = LocalDate.now(zone);
        String dayOfWeek = today.getDayOfWeek().getDisplayName(TextStyle.FULL, Locale.ENGLISH);

        String system = String.join("\n",
            "You convert task descriptions into JSON. Today is " + today + " (" + dayOfWeek + "),",
            "timezone " + zone + ". Respond with ONLY a JSON object - no markdown, no prose:",
            "{",
            "  \"title\": string,            // short imperative, e.g. \"Renew car insurance\"",
            "  \"priority\": \"LOW\"|\"MEDIUM\"|\"HIGH\",   // default MEDIUM if unstated",
            "  \"scheduledDate\": \"YYYY-MM-DDTHH:mm\", // resolve relative dates; null if none",
            "  \"notify\": {\"email\": bool, \"push\": bool, \"sms\": bool},  // false if unstated",
            "  \"confidence\": number        // 0-1; below 0.6 means the text was unclear",
            "}");

        String rawText = chatCompletion(system, text, 500);
        JsonNode node = extractJson(rawText);
        return validateParsedTask(node);
    }

    // ============ OpenAI-compatible chat completions call ============

    private String chatCompletion(String system, String userText, int maxTokens) {
        if (!isConfigured()) {
            throw new AiUnavailableException("AI_API_KEY is not configured");
        }
        try {
            List<Map<String, String>> messages = new ArrayList<>();
            messages.add(Map.of("role", "system", "content", system));
            messages.add(Map.of("role", "user", "content", userText));

            Map<String, Object> body = new HashMap<>();
            body.put("model", model);
            body.put("max_tokens", maxTokens);
            body.put("messages", messages);

            String url = baseUrl.endsWith("/") ? baseUrl + "chat/completions"
                                               : baseUrl + "/chat/completions";
            String response = RestClient.create()
                    .post()
                    .uri(url)
                    .header("Authorization", "Bearer " + apiKey)
                    .header("content-type", "application/json")
                    .body(body)
                    .retrieve()
                    .body(String.class);

            JsonNode root = mapper.readTree(response);
            JsonNode choices = root.path("choices");
            if (!choices.isArray() || choices.isEmpty()) {
                throw new AiUnavailableException("Unexpected API response shape");
            }
            return choices.get(0).path("message").path("content").asText();
        } catch (AiUnavailableException e) {
            throw e;
        } catch (Exception e) {
            // 429 (free-tier quota exhausted) lands here too — degrade, never charge
            System.out.println("❌ [AiService] model call failed: " + e.getMessage());
            throw new AiUnavailableException("AI service call failed", e);
        }
    }

    /** Strip optional ```json fences and parse. */
    private JsonNode extractJson(String text) {
        try {
            String cleaned = text.trim()
                    .replaceAll("^```(json)?\\s*", "")
                    .replaceAll("\\s*```$", "");
            return mapper.readTree(cleaned);
        } catch (Exception e) {
            throw new AiUnavailableException("Model did not return valid JSON", e);
        }
    }

    // ============ Validation (never trust model output blindly) ============

    private Map<String, Object> validateParsedTask(JsonNode node) {
        Map<String, Object> out = new HashMap<>();

        String title = node.path("title").asText("").trim();
        if (title.isEmpty()) {
            throw new AiUnavailableException("Model returned no task title");
        }
        out.put("title", title.length() > 200 ? title.substring(0, 200) : title);

        String priority = node.path("priority").asText("MEDIUM").toUpperCase(Locale.ENGLISH);
        if (!priority.equals("LOW") && !priority.equals("MEDIUM") && !priority.equals("HIGH")) {
            priority = "MEDIUM";
        }
        out.put("priority", priority);

        String scheduled = node.path("scheduledDate").isNull()
                ? null : node.path("scheduledDate").asText(null);
        if (scheduled != null) {
            try {
                LocalDateTime.parse(scheduled, DateTimeFormatter.ofPattern("yyyy-MM-dd'T'HH:mm"));
            } catch (Exception e) {
                scheduled = null; // invalid date from the model -> leave the field for the user
            }
        }
        out.put("scheduledDate", scheduled);

        JsonNode notify = node.path("notify");
        Map<String, Boolean> notifyOut = new HashMap<>();
        notifyOut.put("email", notify.path("email").asBoolean(false));
        notifyOut.put("push",  notify.path("push").asBoolean(false));
        notifyOut.put("sms",   notify.path("sms").asBoolean(false));
        out.put("notify", notifyOut);

        double confidence = node.path("confidence").asDouble(0.5);
        out.put("confidence", Math.max(0.0, Math.min(1.0, confidence)));

        return out;
    }
}
