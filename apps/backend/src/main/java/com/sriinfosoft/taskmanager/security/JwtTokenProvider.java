package com.sriinfosoft.taskmanager.security;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.stereotype.Component;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.Map;

@Component
public class JwtTokenProvider {

    @Value("${jwt.secret}")
    private String jwtSecret;

    @Value("${jwt.expiration}")
    private long jwtExpiration;

    private SecretKey getSigningKey() {
        // HS256 requires >= 256-bit key (32+ ASCII chars)
        byte[] keyBytes = jwtSecret.getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }

    public String generateToken(Authentication authentication) {
    OAuth2User principal = (OAuth2User) authentication.getPrincipal();

    Date now = new Date();
    Date expiryDate = new Date(now.getTime() + jwtExpiration);

    String email = principal.getAttribute("email");
    String name = principal.getAttribute("name");

    // Facebook returns picture as a nested map
    Object pictureObj = principal.getAttribute("picture");
    String pictureUrl = null;
    if (pictureObj instanceof Map<?, ?> map) {
        Object data = map.get("data");
        if (data instanceof Map<?, ?> dataMap) {
            Object url = dataMap.get("url");
            if (url != null) pictureUrl = url.toString();
        }
    }

    return Jwts.builder()
            .subject(email != null ? email : name) // fallback if email missing
            .issuedAt(now)
            .expiration(expiryDate)
            .claim("email", email)
            .claim("name", name)
            .claim("picture", pictureUrl)
            .signWith(getSigningKey(), Jwts.SIG.HS256)
            .compact();
    }


    public String getEmailFromToken(String token) {
        Claims claims = getClaimsFromToken(token);
        return claims.getSubject(); // subject was the email
    }

    public boolean validateToken(String authToken) {
        try {
            // 0.12+ parser: parser().verifyWith(key).build().parseSignedClaims(token)
            Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(authToken);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            // JwtException covers signature, malformed, expired, unsupported, etc.
            System.err.println("Invalid JWT: " + ex.getMessage());
            return false;
        }
    }

    public Claims getClaimsFromToken(String token) {
        return Jwts.parser()
                .verifyWith(getSigningKey())
                .build()
                .parseSignedClaims(token)
                .getPayload();
    }
}
