package com.sriinfosoft.taskmanager.service.authz;

import java.util.List;
import java.util.Set;

/**
 * Application-owned authorization contract. Call sites depend on THIS interface,
 * never on the underlying engine (jCasbin today) or its storage. Swapping the
 * engine or the policy store (MariaDB -> other DB / NoSQL) changes only the
 * implementation/adapter, not callers.
 *
 * Model: RBAC with domains. "domain" == a topic (a skill slug, e.g. "investing").
 * Roles: client, mentor, hubadmin. Actions: ask, review, answer.
 * Asking is open to any authenticated user; review/answer are per-topic grants.
 */
public interface AuthorizationService {

    /** Can {@code userEmail} perform {@code action} within {@code topic}? */
    boolean can(String userEmail, String topic, String action);

    /** Topics in which the user may perform {@code action} (e.g. "review") — for UI + queues. */
    Set<String> topicsFor(String userEmail, String action);

    /** Grant a role to a user within a topic (admin op; console later). Persisted. */
    void grant(String userEmail, String role, String topic);

    /** Revoke a role from a user within a topic. Persisted. */
    void revoke(String userEmail, String role, String topic);

    /** All grants (for the admin console / audit): each row is [email, role, topic]. */
    List<List<String>> grants();
}
