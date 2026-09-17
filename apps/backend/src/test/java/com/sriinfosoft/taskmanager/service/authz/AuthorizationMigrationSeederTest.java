package com.sriinfosoft.taskmanager.service.authz;

import com.sriinfosoft.taskmanager.model.InsightHubMember;
import com.sriinfosoft.taskmanager.model.Question;
import com.sriinfosoft.taskmanager.model.Skill;
import com.sriinfosoft.taskmanager.repository.InsightHubMemberRepository;
import com.sriinfosoft.taskmanager.repository.QuestionRepository;
import com.sriinfosoft.taskmanager.repository.SkillRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class AuthorizationMigrationSeederTest {

    @Mock AuthorizationService authz;
    @Mock InsightHubMemberRepository memberRepo;
    @Mock SkillRepository skillRepo;
    @Mock QuestionRepository questionRepo;
    @InjectMocks AuthorizationMigrationSeeder seeder;

    private Skill skill(long id, String name, String slug) {
        Skill s = new Skill(name, slug); s.setId(id); s.setActive(true); return s;
    }

    @Test
    void grantsMentorsAcrossActiveTopics_backfillsNullQuestionTopics() {
        Skill inv = skill(1, "Investing", "investing");
        Skill aws = skill(2, "AWS", "aws");
        when(skillRepo.findByActiveTrueOrderByName()).thenReturn(List.of(inv, aws));
        when(skillRepo.findBySlug("investing")).thenReturn(Optional.of(inv));

        InsightHubMember mentor = new InsightHubMember(10L, "ranasuri@gmail.com", InsightHubMember.Role.MENTOR);
        InsightHubMember member = new InsightHubMember(10L, "bob@example.com", InsightHubMember.Role.MEMBER);
        when(memberRepo.findAll()).thenReturn(List.of(mentor, member));

        Question q1 = new Question(10L, "bob@example.com", "no topic yet");   // skillId null -> backfill
        Question q2 = new Question(10L, "bob@example.com", "already topical"); q2.setSkillId(2L);
        when(questionRepo.findAll()).thenReturn(List.of(q1, q2));

        seeder.run(null);

        // mentor granted hubadmin on BOTH active topics
        verify(authz).grant("ranasuri@gmail.com", "hubadmin", "investing");
        verify(authz).grant("ranasuri@gmail.com", "hubadmin", "aws");
        // a plain member is NOT granted
        verify(authz, never()).grant(eq("bob@example.com"), anyString(), anyString());
        // only the null-topic question is backfilled to the default (investing = id 1)
        assertThat(q1.getSkillId()).isEqualTo(1L);
        verify(questionRepo).save(q1);
        verify(questionRepo, never()).save(q2);
    }

    @Test
    void noActiveSkills_doesNothing() {
        when(skillRepo.findByActiveTrueOrderByName()).thenReturn(List.of());
        seeder.run(null);
        verify(authz, never()).grant(anyString(), anyString(), anyString());
        verify(questionRepo, never()).save(any());
    }
}
