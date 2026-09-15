package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.MentorProfile;
import com.sriinfosoft.taskmanager.model.SessionOffering;
import com.sriinfosoft.taskmanager.model.Skill;
import com.sriinfosoft.taskmanager.repository.*;
import com.sriinfosoft.taskmanager.service.LiveSessionService.CreateOfferingRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Mentor-side logic + the participation gate. Verifies: unverified -> 403,
 * non-mentor -> 403, capacity<1 invariant -> 400, skill-not-taught -> 400,
 * happy create -> DRAFT, schedule DRAFT -> SCHEDULED with meeting fields,
 * non-owner -> 403, already-scheduled -> 409. Pure unit test (mocks).
 */
@ExtendWith(MockitoExtension.class)
class LiveSessionServiceTest {

    @Mock SkillRepository skillRepo;
    @Mock MentorProfileRepository mentorRepo;
    @Mock MentorSkillRepository mentorSkillRepo;
    @Mock SessionOfferingRepository offeringRepo;
    @Mock MeetingProvider meetingProvider;
    @Mock ReachabilityService reachability;

    LiveSessionService service;

    private static final String MENTOR = "mentor@example.com";

    @BeforeEach
    void setup() {
        service = new LiveSessionService(skillRepo, mentorRepo, mentorSkillRepo,
                offeringRepo, meetingProvider, reachability);
    }

    private CreateOfferingRequest validReq() {
        CreateOfferingRequest r = new CreateOfferingRequest();
        r.skillId = 1L; r.title = "AWS prep"; r.description = "d";
        r.startTime = LocalDateTime.now().plusDays(1); r.durationMin = 60; r.capacity = 1;
        return r;
    }
    private MentorProfile mentor() { MentorProfile mp = new MentorProfile(MENTOR); mp.setId(7L); return mp; }
    private Skill activeSkill() { Skill s = new Skill("AWS", "aws"); s.setId(1L); s.setActive(true); return s; }

    @Test
    void createOffering_unverified_isForbidden() {
        when(reachability.isVerified(MENTOR)).thenReturn(false);
        assertThatThrownBy(() -> service.createOffering(MENTOR, validReq()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createOffering_notMentor_isForbidden() {
        when(reachability.isVerified(MENTOR)).thenReturn(true);
        when(mentorRepo.findByMentorEmail(MENTOR)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.createOffering(MENTOR, validReq()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void createOffering_capacityBelowOne_isBadRequest() {
        when(reachability.isVerified(MENTOR)).thenReturn(true);
        when(mentorRepo.findByMentorEmail(MENTOR)).thenReturn(Optional.of(mentor()));
        CreateOfferingRequest r = validReq(); r.capacity = 0;      // invariant, not config
        assertThatThrownBy(() -> service.createOffering(MENTOR, r))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createOffering_skillNotTaught_isBadRequest() {
        when(reachability.isVerified(MENTOR)).thenReturn(true);
        when(mentorRepo.findByMentorEmail(MENTOR)).thenReturn(Optional.of(mentor()));
        when(skillRepo.findById(1L)).thenReturn(Optional.of(activeSkill()));
        when(mentorSkillRepo.existsByMentorProfileIdAndSkillId(7L, 1L)).thenReturn(false);
        assertThatThrownBy(() -> service.createOffering(MENTOR, validReq()))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
    }

    @Test
    void createOffering_happy_isDraft() {
        when(reachability.isVerified(MENTOR)).thenReturn(true);
        when(mentorRepo.findByMentorEmail(MENTOR)).thenReturn(Optional.of(mentor()));
        when(skillRepo.findById(1L)).thenReturn(Optional.of(activeSkill()));
        when(mentorSkillRepo.existsByMentorProfileIdAndSkillId(7L, 1L)).thenReturn(true);
        when(offeringRepo.save(any(SessionOffering.class))).thenAnswer(i -> i.getArgument(0));

        SessionOffering o = service.createOffering(MENTOR, validReq());

        assertThat(o.getStatus()).isEqualTo(SessionOffering.Status.DRAFT);
        assertThat(o.getMentorEmail()).isEqualTo(MENTOR);
        assertThat(o.getCapacity()).isEqualTo(1);
    }

    @Test
    void schedule_happy_setsScheduledAndMeeting() {
        SessionOffering o = new SessionOffering();
        o.setId(50L); o.setMentorEmail(MENTOR); o.setStatus(SessionOffering.Status.DRAFT);
        o.setStartTime(LocalDateTime.now().plusDays(1)); o.setTitle("t"); o.setDurationMin(60);
        when(reachability.isVerified(MENTOR)).thenReturn(true);
        when(offeringRepo.findById(50L)).thenReturn(Optional.of(o));
        when(meetingProvider.createMeeting(any(), any(), any(), any(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(new MeetingProvider.MeetingDetails("jitsi", "room1", "https://meet.jit.si/room1", "https://meet.jit.si/room1"));
        when(offeringRepo.save(any(SessionOffering.class))).thenAnswer(i -> i.getArgument(0));

        SessionOffering out = service.scheduleOffering(MENTOR, 50L);

        assertThat(out.getStatus()).isEqualTo(SessionOffering.Status.SCHEDULED);
        assertThat(out.getZoomJoinUrl()).contains("meet.jit.si");
        assertThat(out.getMeetingProvider()).isEqualTo("jitsi");
    }

    @Test
    void schedule_notOwner_isForbidden() {
        SessionOffering o = new SessionOffering();
        o.setId(50L); o.setMentorEmail("someone.else@example.com");
        o.setStatus(SessionOffering.Status.DRAFT); o.setStartTime(LocalDateTime.now().plusDays(1));
        when(reachability.isVerified(MENTOR)).thenReturn(true);
        when(offeringRepo.findById(50L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.scheduleOffering(MENTOR, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN));
    }

    @Test
    void schedule_alreadyScheduled_isConflict() {
        SessionOffering o = new SessionOffering();
        o.setId(50L); o.setMentorEmail(MENTOR); o.setStatus(SessionOffering.Status.SCHEDULED);
        o.setStartTime(LocalDateTime.now().plusDays(1));
        when(reachability.isVerified(MENTOR)).thenReturn(true);
        when(offeringRepo.findById(50L)).thenReturn(Optional.of(o));
        assertThatThrownBy(() -> service.scheduleOffering(MENTOR, 50L))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(e -> assertThat(((ResponseStatusException) e).getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
    }
}
