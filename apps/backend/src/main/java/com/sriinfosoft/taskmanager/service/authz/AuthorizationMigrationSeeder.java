package com.sriinfosoft.taskmanager.service.authz;

import com.sriinfosoft.taskmanager.model.InsightHubMember;
import com.sriinfosoft.taskmanager.model.Question;
import com.sriinfosoft.taskmanager.model.Skill;
import com.sriinfosoft.taskmanager.repository.InsightHubMemberRepository;
import com.sriinfosoft.taskmanager.repository.QuestionRepository;
import com.sriinfosoft.taskmanager.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * One-time, idempotent migration so per-topic authorization is backward-compatible:
 *   1. every existing hub MENTOR becomes a hubadmin grant on every ACTIVE topic
 *      (preserves today's "mentor reviews everything" behaviour), and
 *   2. legacy questions with no topic are backfilled to the default topic (investing).
 * Runs each startup; grants dedupe and backfill only touches null rows, so re-runs are safe.
 */
@Component
@Order(20)   // after the enforcer bean + base-policy seed
public class AuthorizationMigrationSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AuthorizationMigrationSeeder.class);

    private final AuthorizationService authz;
    private final InsightHubMemberRepository memberRepo;
    private final SkillRepository skillRepo;
    private final QuestionRepository questionRepo;

    public AuthorizationMigrationSeeder(AuthorizationService authz,
                                        InsightHubMemberRepository memberRepo,
                                        SkillRepository skillRepo,
                                        QuestionRepository questionRepo) {
        this.authz = authz;
        this.memberRepo = memberRepo;
        this.skillRepo = skillRepo;
        this.questionRepo = questionRepo;
    }

    @Override
    public void run(ApplicationArguments args) {
        List<Skill> activeSkills = skillRepo.findByActiveTrueOrderByName();
        if (activeSkills.isEmpty()) { log.info("authz migration: no active skills; skipping"); return; }

        // ensure the default InsightHub topic (investing) is askable
        skillRepo.findBySlug("investing").ifPresent(s -> {
            if (!s.isAskable()) { s.setAskable(true); skillRepo.save(s); }
        });

        // 1) existing mentors -> hubadmin on every active topic
        int grants = 0;
        for (InsightHubMember m : memberRepo.findAll()) {
            if (m.getRole() != InsightHubMember.Role.MENTOR) continue;
            for (Skill s : activeSkills) { authz.grant(m.getMemberEmail(), "hubadmin", s.getSlug()); grants++; }
        }

        // 2) backfill legacy questions to the default topic
        Long defaultSkillId = skillRepo.findBySlug("investing").map(Skill::getId)
                .orElse(activeSkills.get(0).getId());
        int backfilled = 0;
        for (Question q : questionRepo.findAll()) {
            if (q.getSkillId() == null) { q.setSkillId(defaultSkillId); questionRepo.save(q); backfilled++; }
        }
        log.info("authz migration: ensured {} mentor grant(s) across {} topic(s); backfilled {} question topic(s)",
                grants, activeSkills.size(), backfilled);
    }
}
