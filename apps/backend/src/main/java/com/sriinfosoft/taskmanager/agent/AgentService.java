package com.sriinfosoft.taskmanager.agent;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sriinfosoft.taskmanager.service.AiService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * REUSABLE CORE (app-agnostic — ships unchanged to future applications).
 *
 * The agent loop: model -> tool_use -> execute -> tool_result -> model,
 * until the model answers in text or a hard cap is reached.
 *
 * Guardrails written in code, not in the prompt:
 *   1. Only registered AgentTool beans exist — nothing else is callable.
 *   2. At most ONE existing-data mutation per turn unless the user
 *      confirmed (confirmed=true on the request); further mutating calls
 *      return CONFIRMATION_REQUIRED to the model, which then presents the
 *      plan and asks the user to approve.
 *   3. MAX_ITERATIONS caps runaway loops.
 *   4. Every tool executes as the authenticated user (enforced in tools).
 *
 * Domain enters only through: the injected AgentTool beans and the
 * systemPrompt argument. A payments or e-commerce app reuses this class
 * with its own toolbox and prompt.
 */
@Service
public class AgentService {

    private static final int MAX_ITERATIONS = 8;

    @Autowired private AiService aiService;
    @Autowired private List<AgentTool> tools;   // all AgentTool beans, any app

    private final ObjectMapper mapper = new ObjectMapper();

    /** Result of one agent turn. */
    public record AgentResult(String reply, List<String> actions) {}

    public AgentResult run(String userEmail, List<Map<String, Object>> conversation,
                           boolean confirmed, String timezone) {

        List<Object> messages = new ArrayList<>();
        messages.add(Map.of("role", "system", "content", systemPrompt(timezone)));
        messages.addAll(conversation);

        List<Object> toolDefs = tools.stream().map(this::toOpenAiTool).map(o -> (Object) o).toList();
        Map<String, AgentTool> byName = new HashMap<>();
        tools.forEach(t -> byName.put(t.name(), t));

        List<String> actions = new ArrayList<>();
        int mutations = 0;

        for (int i = 0; i < MAX_ITERATIONS; i++) {
            JsonNode msg = aiService.chatWithTools(messages, toolDefs);
            JsonNode toolCalls = msg.path("tool_calls");

            if (!toolCalls.isArray() || toolCalls.isEmpty()) {
                String reply = msg.path("content").asText("");
                return new AgentResult(reply.isBlank()
                        ? "(The assistant returned no text — please try rephrasing.)" : reply, actions);
            }

            // echo the assistant tool-call message back into the transcript
            messages.add(mapper.convertValue(msg, Map.class));

            for (JsonNode call : toolCalls) {
                String callId = call.path("id").asText("");
                String name = call.path("function").path("name").asText("");
                String argsRaw = call.path("function").path("arguments").asText("{}");
                String result;

                AgentTool tool = byName.get(name);
                if (tool == null) {
                    result = "ERROR: unknown tool '" + name + "'.";
                } else {
                    try {
                        JsonNode args = mapper.readTree(argsRaw.isBlank() ? "{}" : argsRaw);
                        if (tool.mutatesExistingData() && mutations >= 1 && !confirmed) {
                            result = "CONFIRMATION_REQUIRED: you already changed one task this turn. "
                                   + "Do NOT make further changes now. Instead, summarize the full plan "
                                   + "of remaining changes for the user and ask them to reply 'yes' to proceed.";
                        } else {
                            result = tool.execute(userEmail, args);
                            if (tool.mutatesExistingData()) mutations++;
                            actions.add(name + " " + argsRaw);
                            System.out.println("🤖 [Agent] " + userEmail + " -> " + name + " " + argsRaw);
                        }
                    } catch (Exception e) {
                        result = "ERROR: tool failed: " + e.getMessage();
                    }
                }
                messages.add(Map.of("role", "tool", "tool_call_id", callId, "content", result));
            }
        }
        return new AgentResult(
            "I reached my step limit for one request — please break this into smaller pieces.", actions);
    }

    private Map<String, Object> toOpenAiTool(AgentTool t) {
        try {
            Map<String, Object> fn = new HashMap<>();
            fn.put("name", t.name());
            fn.put("description", t.description());
            fn.put("parameters", mapper.readTree(t.parametersSchema()));
            return Map.of("type", "function", "function", fn);
        } catch (Exception e) {
            throw new IllegalStateException("Bad schema for tool " + t.name(), e);
        }
    }

    private String systemPrompt(String timezone) {
        ZoneId zone;
        try { zone = ZoneId.of(timezone == null ? "UTC" : timezone); }
        catch (Exception e) { zone = ZoneId.of("UTC"); }
        return String.join("\n",
            "You are the task assistant inside Task Manager Pro. Today is "
                + LocalDate.now(zone) + ", timezone " + zone + ".",
            "You help the user manage THEIR tasks using the provided tools.",
            "Rules:",
            "- Use tools for any facts about tasks; never invent tasks, ids, or dates.",
            "- Dates you pass to tools are user-local, format YYYY-MM-DDTHH:mm.",
            "- Before changing several tasks, list the intended changes and ask for confirmation.",
            "- If a tool returns CONFIRMATION_REQUIRED, present the remaining plan and ask the user to reply yes.",
            "- Call tools ONLY through the function-calling mechanism. NEVER write function-call",
            "  syntax, XML tags, or JSON in your reply text.",
            "- Be brief and concrete; reply in plain sentences without markdown formatting.");
    }
}
