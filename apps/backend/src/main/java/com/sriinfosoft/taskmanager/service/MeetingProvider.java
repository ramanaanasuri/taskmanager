package com.sriinfosoft.taskmanager.service;

import java.time.LocalDateTime;

/**
 * Seam for creating the live meeting room. Swapping providers is a config/bean
 * change, not a rewrite. Jitsi is the zero-auth default; a Zoom S2S provider
 * plugs in behind this same interface when credentials are wired.
 */
public interface MeetingProvider {

    MeetingDetails createMeeting(Long offeringId, String title, String description,
                                 LocalDateTime startTime, int durationMin);

    default void cancelMeeting(String meetingId) { /* no-op for providers without cancel */ }

    record MeetingDetails(String provider, String meetingId, String joinUrl, String hostUrl) {}
}
