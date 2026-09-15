package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.MentorProfile;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface MentorProfileRepository extends JpaRepository<MentorProfile, Long> {
    Optional<MentorProfile> findByMentorEmail(String mentorEmail);
}
