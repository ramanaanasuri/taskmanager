package com.sriinfosoft.taskmanager.service.authz;

import org.casbin.jcasbin.main.Enforcer;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

/**
 * jCasbin implementation of {@link AuthorizationService}. Enforcement + grant
 * management delegate to the injected {@link Enforcer}, whose policy is loaded
 * from (and persisted to) whatever adapter the enforcer was built with — the
 * storage seam. Nothing here knows about MariaDB.
 *
 * "ask" is universal for any authenticated user (asking is not a privilege);
 * "review"/"answer" are enforced per topic via the RBAC-with-domains policy.
 */
@Service
public class CasbinAuthorizationService implements AuthorizationService {

    private final Enforcer enforcer;

    public CasbinAuthorizationService(Enforcer enforcer) {
        this.enforcer = enforcer;
    }

    @Override
    public boolean can(String userEmail, String topic, String action) {
        if (userEmail == null || userEmail.isBlank() || topic == null || action == null) return false;
        String u = userEmail.trim().toLowerCase();
        String d = topic.trim().toLowerCase();
        if ("ask".equals(action)) return true;                 // asking is open to any authed user
        return enforcer.enforce(u, d, action);                 // review/answer: per-topic grant
    }

    @Override
    public Set<String> topicsFor(String userEmail, String action) {
        Set<String> topics = new TreeSet<>();
        if (userEmail == null) return topics;
        String u = userEmail.trim().toLowerCase();
        // grouping rows: [user, role, domain] — collect domains the user is granted a role in
        for (List<String> g : enforcer.getFilteredNamedGroupingPolicy("g", 0, u)) {
            if (g.size() >= 3) {
                String role = g.get(1), domain = g.get(2);
                if (enforcer.enforce(u, domain, action)
                        || roleCanDoAnywhere(role, action)) {
                    topics.add(domain);
                }
            }
        }
        return topics;
    }

    private boolean roleCanDoAnywhere(String role, String action) {
        for (List<String> p : enforcer.getPolicy()) {
            if (p.size() >= 3 && p.get(0).equals(role)
                    && ("*".equals(p.get(1))) && p.get(2).equals(action)) return true;
        }
        return false;
    }

    @Override
    public synchronized void grant(String userEmail, String role, String topic) {
        enforcer.addNamedGroupingPolicy("g",
                userEmail.trim().toLowerCase(), role.trim().toLowerCase(), topic.trim().toLowerCase());
    }

    @Override
    public synchronized void revoke(String userEmail, String role, String topic) {
        enforcer.removeNamedGroupingPolicy("g",
                userEmail.trim().toLowerCase(), role.trim().toLowerCase(), topic.trim().toLowerCase());
    }

    @Override
    public List<List<String>> grants() {
        return new ArrayList<>(enforcer.getNamedGroupingPolicy("g"));
    }
}
