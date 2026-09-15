package com.sriinfosoft.taskmanager.config;

import com.sriinfosoft.taskmanager.model.Skill;
import com.sriinfosoft.taskmanager.repository.SkillRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

import java.util.List;

/** Seeds a small starter skill set once, only when the table is empty. Idempotent. */
@Component
public class SkillSeeder implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SkillSeeder.class);
    private final SkillRepository skillRepo;

    public SkillSeeder(SkillRepository skillRepo) { this.skillRepo = skillRepo; }

    @Override
    public void run(ApplicationArguments args) {
        if (skillRepo.count() > 0) return;
        List<Skill> seed = List.of(
            new Skill("AWS Solutions Architect", "aws-solutions-architect"),
            new Skill("AI / ML Engineering", "ai-ml"),
            new Skill("QA Automation", "qa-automation"),
            new Skill("Investing", "investing"),
            new Skill("Music", "music")
        );
        skillRepo.saveAll(seed);
        log.info("SkillSeeder: inserted {} starter skills", seed.size());
    }
}
