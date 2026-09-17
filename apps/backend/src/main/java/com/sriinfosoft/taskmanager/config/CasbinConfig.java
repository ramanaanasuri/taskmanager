package com.sriinfosoft.taskmanager.config;

import org.casbin.adapter.JDBCAdapter;
import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.springframework.core.io.ClassPathResource;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;
import java.nio.charset.StandardCharsets;

/**
 * Builds the jCasbin {@link Enforcer} from the RBAC-with-domains model, backed by
 * the JDBC adapter over the app's existing MariaDB {@link DataSource} (policy lives
 * in the {@code casbin_rule} table, auto-created by the adapter). The base role
 * policy (p rows) is seeded once if absent; user grants (g rows) are managed at
 * runtime via {@code AuthorizationService.grant/revoke}.
 */
@Configuration
public class CasbinConfig {

    @Bean
    public Enforcer casbinEnforcer(DataSource dataSource) throws Exception {
        String modelText = new String(
                new ClassPathResource("rbac_model.conf").getInputStream().readAllBytes(),
                StandardCharsets.UTF_8);
        Model model = new Model();
        model.loadModelFromText(modelText);

        JDBCAdapter adapter = new JDBCAdapter(dataSource);   // uses casbin_rule; auto-creates it
        Enforcer enforcer = new Enforcer(model, adapter);
        enforcer.loadPolicy();

        seedBasePolicy(enforcer);
        return enforcer;
    }

    /** Idempotent: ensure the role capabilities exist (what each role may do in any topic). */
    private void seedBasePolicy(Enforcer e) {
        addIfAbsent(e, "hubadmin", "*", "review");
        addIfAbsent(e, "hubadmin", "*", "answer");
        addIfAbsent(e, "mentor",   "*", "review");
        addIfAbsent(e, "mentor",   "*", "answer");
        addIfAbsent(e, "client",   "*", "ask");
        e.savePolicy();
    }

    private void addIfAbsent(Enforcer e, String sub, String dom, String act) {
        if (!e.hasPolicy(sub, dom, act)) e.addPolicy(sub, dom, act);
    }
}
