package com.sriinfosoft.taskmanager.security;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Pure-logic tests for token issue/parse/validate. No Spring context:
 * the two @Value fields are set by reflection exactly as Spring would.
 */
class JwtTokenProviderTest {

    private static final String SECRET = "unit-test-secret-key-0123456789abcdef";
    private JwtTokenProvider provider;

    @BeforeEach
    void setUp() {
        provider = new JwtTokenProvider();
        ReflectionTestUtils.setField(provider, "jwtSecret", SECRET);
        ReflectionTestUtils.setField(provider, "jwtExpiration", 3_600_000L);
    }

    private Authentication authFor(Map<String, Object> attrs) {
        OAuth2User principal = mock(OAuth2User.class);
        attrs.forEach((k, v) -> when(principal.getAttribute(k)).thenReturn(v));
        Authentication auth = mock(Authentication.class);
        when(auth.getPrincipal()).thenReturn(principal);
        return auth;
    }

    @Test
    void roundTrip_subjectIsEmail_andValidates() {
        String token = provider.generateToken(authFor(Map.of(
                "email", "user@example.com", "name", "Unit User")));
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getEmailFromToken(token)).isEqualTo("user@example.com");
    }

    @Test
    void missingEmail_fallsBackToName_asSubject() {
        String token = provider.generateToken(authFor(Map.of("name", "No Email")));
        assertThat(provider.getEmailFromToken(token)).isEqualTo("No Email");
    }

    @Test
    void facebookNestedPicture_isUnwrapped_withoutError() {
        String token = provider.generateToken(authFor(Map.of(
                "email", "fb@example.com",
                "picture", Map.of("data", Map.of("url", "https://img.example/p.jpg")))));
        assertThat(provider.validateToken(token)).isTrue();
    }

    @Test
    void expiredToken_isInvalid() {
        ReflectionTestUtils.setField(provider, "jwtExpiration", -1000L);
        String token = provider.generateToken(authFor(Map.of("email", "old@example.com")));
        assertThat(provider.validateToken(token)).isFalse();
    }

    @Test
    void tamperedToken_isInvalid() {
        String token = provider.generateToken(authFor(Map.of("email", "user@example.com")));
        String tampered = token.substring(0, token.length() - 3) + "abc";
        assertThat(provider.validateToken(tampered)).isFalse();
    }

    @Test
    void subjectToken_forTesterPrincipal_roundTrips() {
        String token = provider.generateTokenForSubject("api-tester@sriinfosoft.local");
        assertThat(provider.validateToken(token)).isTrue();
        assertThat(provider.getEmailFromToken(token)).isEqualTo("api-tester@sriinfosoft.local");
    }

    @Test
    void garbage_isInvalid_notException() {
        assertThat(provider.validateToken("not.a.jwt")).isFalse();
    }
}
