package com.sriinfosoft.taskmanager.repository;

import com.sriinfosoft.taskmanager.model.ChannelVerification;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ChannelVerificationRepository extends JpaRepository<ChannelVerification, Long> {
    List<ChannelVerification> findByUserEmail(String userEmail);
    Optional<ChannelVerification> findByUserEmailAndChannel(String userEmail, ChannelVerification.Channel channel);
}
