package com.sriinfosoft.taskmanager.controller;

import com.sriinfosoft.taskmanager.security.JwtTokenProvider;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @GetMapping("/me")
    public ResponseEntity<?> getCurrentUser(@RequestHeader("Authorization") String bearerToken) {
        try {
            String token = bearerToken.substring(7); // Remove "Bearer " prefix
            Claims claims = tokenProvider.getClaimsFromToken(token);

            Map<String, Object> userInfo = new HashMap<>();
            userInfo.put("email", claims.get("email"));
            userInfo.put("name", claims.get("name"));
            userInfo.put("picture", claims.get("picture"));

            return ResponseEntity.ok(userInfo);
        } catch (Exception e) {
            return ResponseEntity.badRequest().body("Invalid token");
        }
    }

    @PostMapping("/validate")
    public ResponseEntity<?> validateToken(@RequestHeader("Authorization") String bearerToken) {
        try {
            String token = bearerToken.substring(7);
            boolean isValid = tokenProvider.validateToken(token);

            Map<String, Object> response = new HashMap<>();
            response.put("valid", isValid);

            if (isValid) {
                Claims claims = tokenProvider.getClaimsFromToken(token);
                response.put("email", claims.get("email"));
            }

            return ResponseEntity.ok(response);
        } catch (Exception e) {
            return ResponseEntity.ok(Map.of("valid", false));
        }
    }
}