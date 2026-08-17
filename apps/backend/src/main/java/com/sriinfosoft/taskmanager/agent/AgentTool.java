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

    /**
     * Per-turn execution context. userEmail scopes all data access; zone is
     * the caller's IANA timezone so tools can convert between the user's
     * wall-clock (what the model speaks) and UTC (what the database stores).
     * Future applications extend reuse by adding fields here, not by
     * changing tool signatures again.
     */
    record AgentContext(String userEmail, java.time.ZoneId zone) {}

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
     * Execute in the given context. Return a plain-text/JSON result string for
     * the model. Never throw for expected conditions (not found, not owned) —
     * return an explanatory string so the model can react conversationally.
     *
     * TIME CONVENTION: dates in args are USER-LOCAL (ctx.zone) and must be
     * converted to UTC before persisting; dates rendered into results must be
     * converted UTC -> user-local so the model always speaks the user's clock.
     */
    String execute(AgentContext ctx, JsonNode args) throws Exception;
}
