package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.SessionOffering;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface SessionOfferingRepository extends JpaRepository<SessionOffering, Long> {

    List<SessionOffering> findByMentorEmailOrderByStartTimeDesc(String mentorEmail);

    List<SessionOffering> findByStatusOrderByStartTimeAsc(SessionOffering.Status status);

    List<SessionOffering> findByStatusAndSkillIdOrderByStartTimeAsc(SessionOffering.Status status, Long skillId);

    /** Row-locks the offering so the seat-claim (count + insert) is atomic under concurrency. */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select o from SessionOffering o where o.id = :id")
    Optional<SessionOffering> findByIdForUpdate(@Param("id") Long id);
}
