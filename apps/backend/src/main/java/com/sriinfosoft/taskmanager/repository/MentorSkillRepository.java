package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.MentorSkill;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface MentorSkillRepository extends JpaRepository<MentorSkill, Long> {
    List<MentorSkill> findByMentorProfileId(Long mentorProfileId);
    boolean existsByMentorProfileIdAndSkillId(Long mentorProfileId, Long skillId);

    /**
     * Immediate bulk delete (not the derived select-then-remove). Executes as
     * direct SQL before any subsequent re-insert, so replacing a mentor's skills
     * with an overlapping set cannot collide with uk_mentor_skill. flush before,
     * clear after, to keep the persistence context consistent.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("delete from MentorSkill m where m.mentorProfileId = :mentorProfileId")
    void deleteByMentorProfileId(@Param("mentorProfileId") Long mentorProfileId);
}
