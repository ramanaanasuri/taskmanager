package com.sriinfosoft.taskmanager.service;

import com.sriinfosoft.taskmanager.model.*;
import com.sriinfosoft.taskmanager.repository.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Live Sessions mentor-side logic: skills, mentor profile (bio + taught skills),
 * offering creation and scheduling. Mutating actions are gated on reachability
 * verification (unverified -> 403) — the Slice 1 foundation enforced for real.
 */
@Service
public class LiveSessionService {

    private final SkillRepository skillRepo;
    private final MentorProfileRepository mentorRepo;
    private final MentorSkillRepository mentorSkillRepo;
    private final SessionOfferingRepository offeringRepo;
    private final MeetingProvider meetingProvider;
    private final ReachabilityService reachability;

    public LiveSessionService(SkillRepository skillRepo,
                              MentorProfileRepository mentorRepo,
                              MentorSkillRepository mentorSkillRepo,
                              SessionOfferingRepository offeringRepo,
                              MeetingProvider meetingProvider,
                              ReachabilityService reachability) {
        this.skillRepo = skillRepo;
        this.mentorRepo = mentorRepo;
        this.mentorSkillRepo = mentorSkillRepo;
        this.offeringRepo = offeringRepo;
        this.meetingProvider = meetingProvider;
        this.reachability = reachability;
    }

    public List<Skill> listSkills() {
        return skillRepo.findByActiveTrueOrderByName();
    }

    public MentorProfileView getMentorProfile(String email) {
        MentorProfile mp = mentorRepo.findByMentorEmail(email)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Not a mentor"));
        return toView(mp);
    }

    /** Become a mentor or update bio + taught skills (skills replace the prior set). */
    @Transactional
    public MentorProfileView saveMentorProfile(String email, String bio, List<Long> skillIds) {
        MentorProfile mp = mentorRepo.findByMentorEmail(email).orElseGet(() -> new MentorProfile(email));
        mp.setBio(bio);
        mp.setActive(true);
        mp = mentorRepo.save(mp);

        if (skillIds != null) {
            for (Long sid : skillIds) {
                Skill s = skillRepo.findById(sid).orElseThrow(() ->
                        new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill not found: " + sid));
                if (!s.isActive()) {
                    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill inactive: " + sid);
                }
            }
            mentorSkillRepo.deleteByMentorProfileId(mp.getId());
            for (Long sid : skillIds) {
                mentorSkillRepo.save(new MentorSkill(mp.getId(), sid));
            }
        }
        return toView(mp);
    }

    /** Create an offering. Gated: verified reachability + is a mentor + teaches the skill. */
    public SessionOffering createOffering(String email, CreateOfferingRequest req) {
        requireVerified(email);
        MentorProfile mp = mentorRepo.findByMentorEmail(email).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.FORBIDDEN, "Not a mentor"));

        if (req.capacity < 1) {  // domain invariant, not a config knob
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "capacity must be >= 1");
        }
        if (req.durationMin < 1) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "durationMin must be >= 1");
        }
        if (req.startTime == null || !req.startTime.isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime must be in the future");
        }
        Skill skill = skillRepo.findById(req.skillId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill not found"));
        if (!skill.isActive()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Skill inactive");
        }
        if (!mentorSkillRepo.existsByMentorProfileIdAndSkillId(mp.getId(), req.skillId)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "You don't teach this skill");
        }

        SessionOffering o = new SessionOffering();
        o.setMentorEmail(email);
        o.setSkillId(req.skillId);
        o.setTitle(req.title);
        o.setDescription(req.description);
        o.setStartTime(req.startTime);
        o.setDurationMin(req.durationMin);
        o.setCapacity(req.capacity);
        o.setStatus(SessionOffering.Status.DRAFT);
        o.setRateCurrency(req.rateCurrency);
        o.setRateAmount(req.rateAmount);
        o.setRateDescription(req.rateDescription);
        return offeringRepo.save(o);
    }

    public List<SessionOffering> listMyOfferings(String email) {
        return offeringRepo.findByMentorEmailOrderByStartTimeDesc(email);
    }

    /** Schedule (create the meeting room). Gated: verified + owner + DRAFT + future. */
    public SessionOffering scheduleOffering(String email, Long offeringId) {
        requireVerified(email);
        SessionOffering o = offeringRepo.findById(offeringId).orElseThrow(() ->
                new ResponseStatusException(HttpStatus.NOT_FOUND, "Offering not found"));
        if (!o.getMentorEmail().equals(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Not your offering");
        }
        if (o.getStatus() != SessionOffering.Status.DRAFT) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Only DRAFT offerings can be scheduled");
        }
        if (!o.getStartTime().isAfter(LocalDateTime.now())) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "startTime is in the past");
        }
        MeetingProvider.MeetingDetails md = meetingProvider.createMeeting(
                o.getId(), o.getTitle(), o.getDescription(), o.getStartTime(), o.getDurationMin());
        o.setMeetingProvider(md.provider());
        o.setMeetingProviderId(md.meetingId());
        o.setZoomJoinUrl(md.joinUrl());
        o.setZoomHostUrl(md.hostUrl());
        o.setStatus(SessionOffering.Status.SCHEDULED);
        o.setUpdatedAt(LocalDateTime.now());
        return offeringRepo.save(o);
    }

    private void requireVerified(String email) {
        if (!reachability.isVerified(email)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "VERIFICATION_REQUIRED");
        }
    }

    private MentorProfileView toView(MentorProfile mp) {
        List<Long> skillIds = mentorSkillRepo.findByMentorProfileId(mp.getId())
                .stream().map(MentorSkill::getSkillId).toList();
        return new MentorProfileView(mp.getId(), mp.getMentorEmail(), mp.getBio(), mp.isActive(), skillIds);
    }

    // ---- request / view types ----
    public static class CreateOfferingRequest {
        public Long skillId;
        public String title;
        public String description;
        public LocalDateTime startTime;
        public int durationMin;
        public int capacity;
        public String rateCurrency;
        public java.math.BigDecimal rateAmount;
        public String rateDescription;
    }

    public record MentorProfileView(Long id, String mentorEmail, String bio,
                                    boolean active, List<Long> skillIds) {}
}
