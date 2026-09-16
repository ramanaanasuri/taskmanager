package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.model.SessionOffering;
import com.sriinfosoft.taskmanager.service.*;
import com.sriinfosoft.taskmanager.service.LiveSessionService.CreateOfferingRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** Live Sessions endpoints (mentor + learner + lobby). All under /api (authenticated). */
@RestController
@RequestMapping("/api")
public class LiveSessionController {

    private final LiveSessionService service;
    private final EnrollmentService enrollment;
    private final LobbyService lobby;

    public LiveSessionController(LiveSessionService service, EnrollmentService enrollment, LobbyService lobby) {
        this.service = service;
        this.enrollment = enrollment;
        this.lobby = lobby;
    }

    // ---- skills & mentor profile (2a) ----
    @GetMapping("/skills")
    public ResponseEntity<?> skills() {
        String email = currentEmail(); if (email == null) return unauth();
        return ResponseEntity.ok(Map.of("skills", service.listSkills()));
    }

    @GetMapping("/mentors/profile")
    public ResponseEntity<?> mentorPublicProfile(@RequestParam("email") String email) {
        if (currentEmail() == null) return unauth();
        return ResponseEntity.ok(service.getPublicMentorProfile(email));
    }

    @GetMapping("/mentor/profile")
    public ResponseEntity<?> getProfile() {
        String email = currentEmail(); if (email == null) return unauth();
        return ResponseEntity.ok(service.getMentorProfile(email));
    }

    @PostMapping("/mentor/profile")
    public ResponseEntity<?> saveProfile(@RequestBody ProfileRequest req) {
        String email = currentEmail(); if (email == null) return unauth();
        return ResponseEntity.ok(service.saveMentorProfile(email, req.bio, req.skillIds));
    }

    // ---- offerings: create / schedule / mine (2a) ----
    @PostMapping("/offerings")
    public ResponseEntity<?> createOffering(@RequestBody CreateOfferingRequest req) {
        String email = currentEmail(); if (email == null) return unauth();
        SessionOffering created = service.createOffering(email, req);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @GetMapping("/my/offerings")
    public ResponseEntity<?> myOfferings() {
        String email = currentEmail(); if (email == null) return unauth();
        return ResponseEntity.ok(Map.of("offerings", service.listMyOfferings(email)));
    }

    @PostMapping("/offerings/{id}/schedule")
    public ResponseEntity<?> schedule(@PathVariable Long id) {
        String email = currentEmail(); if (email == null) return unauth();
        return ResponseEntity.ok(service.scheduleOffering(email, id));
    }

    // ---- browse / detail / join / my sessions (2b) ----
    @GetMapping("/offerings")
    public ResponseEntity<?> browse(@RequestParam(name = "skill", required = false) Long skillId) {
        String email = currentEmail(); if (email == null) return unauth();
        return ResponseEntity.ok(Map.of("offerings", enrollment.browse(skillId)));
    }

    @GetMapping("/offerings/{id}")
    public ResponseEntity<?> detail(@PathVariable Long id) {
        String email = currentEmail(); if (email == null) return unauth();
        return ResponseEntity.ok(enrollment.detail(email, id));
    }

    @PostMapping("/offerings/{id}/join")
    public ResponseEntity<?> join(@PathVariable Long id) {
        String email = currentEmail(); if (email == null) return unauth();
        EnrollmentService.JoinResult r = enrollment.join(email, id);
        HttpStatus status = "ENROLLED".equals(r.outcome()) ? HttpStatus.CREATED : HttpStatus.OK;
        return ResponseEntity.status(status).body(r);
    }

    @GetMapping("/my/sessions")
    public ResponseEntity<?> mySessions() {
        String email = currentEmail(); if (email == null) return unauth();
        return ResponseEntity.ok(Map.of("sessions", enrollment.mySessions(email)));
    }

    // ---- lobby: check-in / lobby / reach (2c) ----
    @PostMapping("/offerings/{id}/checkin")
    public ResponseEntity<?> checkin(@PathVariable Long id) {
        String email = currentEmail(); if (email == null) return unauth();
        lobby.checkIn(email, id);
        return ResponseEntity.ok(Map.of("checkedIn", true));
    }

    @GetMapping("/offerings/{id}/lobby")
    public ResponseEntity<?> lobbyView(@PathVariable Long id) {
        String email = currentEmail(); if (email == null) return unauth();
        return ResponseEntity.ok(lobby.lobby(email, id));
    }

    @PostMapping("/offerings/{id}/reach")
    public ResponseEntity<?> reach(@PathVariable Long id, @RequestBody(required = false) ReachRequest req) {
        String email = currentEmail(); if (email == null) return unauth();
        String target = req == null ? null : req.targetEmail;
        List<String> channels = req == null ? null : req.channels;
        return ResponseEntity.ok(lobby.reach(email, id, target, channels));
    }

    // ---- helpers ----
    private String currentEmail() {
        Authentication a = SecurityContextHolder.getContext().getAuthentication();
        if (a == null || !a.isAuthenticated()) return null;
        String name = a.getName();
        if (name == null || "anonymousUser".equals(name)) return null;
        return name;
    }
    private ResponseEntity<?> unauth() {
        return ResponseEntity.status(401).body(Map.of("error", "Not authenticated"));
    }

    public static class ProfileRequest { public String bio; public List<Long> skillIds; }
    public static class ReachRequest { public String targetEmail; public List<String> channels; }
}
