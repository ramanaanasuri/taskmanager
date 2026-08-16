package com.sriinfosoft.taskmanager.agent;

import com.fasterxml.jackson.databind.JsonNode;

/**
 * REUSABLE CORE (app-agnostic — ships unchanged to future applications).
 *
 * A tool the agent can call. Each application registers its own toolbox as
 * Spring components implementing this interface; the agent loop discovers
 * them by injection and never needs to know the domain.
 *
 * Security model: execute() always receives the authenticated user's email
 * and MUST scope every data access to it — the agent inherits exactly the
 * REST API's isolation, by construction rather than by prompt.
 */
public interface AgentTool {

    /** Tool name the model calls, e.g. "list_tasks". */
    String name();

    /** One-sentence description shown to the model. */
    String description();

    /** JSON Schema (as a JSON string) of the tool's parameters object. */
    String parametersSchema();

    /**
     * True for tools that CHANGE existing data (update/complete/delete-like).
     * The loop allows one such call per turn unless the user confirmed;
     * additive tools (create) and reads are unrestricted.
     */
    boolean mutatesExistingData();

    /**
     * Execute for the given user. Return a plain-text/JSON result string for
     * the model. Never throw for expected conditions (not found, not owned) —
     * return an explanatory string so the model can react conversationally.
     */
    String execute(String userEmail, JsonNode args) throws Exception;
}
