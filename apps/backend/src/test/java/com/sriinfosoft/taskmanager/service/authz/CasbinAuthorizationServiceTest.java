package com.sriinfosoft.taskmanager.service.authz;

import org.casbin.jcasbin.main.Enforcer;
import org.casbin.jcasbin.model.Model;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the authorization core (RBAC with domains = topics). Runs the
 * real jCasbin engine in-memory (no DB) with a seeded policy, exercised through
 * the AuthorizationService interface. Proves the per-topic requirement (AZ-02).
 */
class CasbinAuthorizationServiceTest {

    private static final String MODEL = String.join("\n",
        "[request_definition]", "r = sub, dom, act",
        "[policy_definition]",  "p = sub, dom, act",
        "[role_definition]",    "g = _, _, _",
        "[policy_effect]",      "e = some(where (p.eft == allow))",
        "[matchers]",           "m = g(r.sub, p.sub, r.dom) && (p.dom == \"*\" || r.dom == p.dom) && r.act == p.act");

    private AuthorizationService authz;

    @BeforeEach
    void setup() {
        Model model = new Model();
        model.loadModelFromText(MODEL);
        Enforcer e = new Enforcer(model);
        e.addPolicy("hubadmin", "*", "review");
        e.addPolicy("hubadmin", "*", "answer");
        e.addPolicy("client",   "*", "ask");
        authz = new CasbinAuthorizationService(e);
        authz.grant("ranasuri@gmail.com", "hubadmin", "investing");
        authz.grant("ranasuri@gmail.com", "hubadmin", "aws");
        authz.grant("alice@example.com",  "hubadmin", "investing");
    }

    @Test void AZ01_reviewer_canReviewGrantedTopic() {
        assertThat(authz.can("ranasuri@gmail.com", "investing", "review")).isTrue();
    }

    @Test void AZ02_reviewer_cannotReviewUngrantedTopic() {   // the crux — R2
        assertThat(authz.can("alice@example.com", "aws", "review")).isFalse();
    }

    @Test void AZ03_reviewer_grantedBothTopics() {
        assertThat(authz.can("ranasuri@gmail.com", "aws", "review")).isTrue();
    }

    @Test void AZ04_nonReviewer_cannotReview() {
        assertThat(authz.can("bob@example.com", "investing", "review")).isFalse();
    }

    @Test void AZ05_anyAuthedUser_canAsk() {
        assertThat(authz.can("bob@example.com", "investing", "ask")).isTrue();
    }

    @Test void AZ06_nullOrUnknown_denied() {
        assertThat(authz.can(null, "investing", "review")).isFalse();
        assertThat(authz.can("ghost@example.com", "investing", "review")).isFalse();
    }

    @Test void AZ07_topicsFor_reviewer_bothTopics() {
        assertThat(authz.topicsFor("ranasuri@gmail.com", "review"))
                .containsExactlyInAnyOrder("investing", "aws");
    }

    @Test void AZ08_topicsFor_reviewer_oneTopic() {
        assertThat(authz.topicsFor("alice@example.com", "review"))
                .containsExactly("investing");
    }

    @Test void AZ09_grant_takesEffect() {
        assertThat(authz.can("bob@example.com", "aws", "review")).isFalse();
        authz.grant("bob@example.com", "hubadmin", "aws");
        assertThat(authz.can("bob@example.com", "aws", "review")).isTrue();
    }

    @Test void AZ10_revoke_takesEffect() {
        authz.revoke("alice@example.com", "hubadmin", "investing");
        assertThat(authz.can("alice@example.com", "investing", "review")).isFalse();
    }

    @Test void AZ11_emailAndTopic_caseInsensitive() {
        assertThat(authz.can("RANASURI@GMAIL.COM", "INVESTING", "review")).isTrue();
    }
}
