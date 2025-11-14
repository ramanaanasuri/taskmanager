package com.sriinfosoft.taskmanager.config;

import com.sriinfosoft.taskmanager.security.JwtAuthenticationFilter;
import com.sriinfosoft.taskmanager.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.web.AuthorizationRequestRepository; // <-- correct interface
import org.springframework.security.oauth2.client.web.HttpSessionOAuth2AuthorizationRequestRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest; // <-- type parameter
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;

import org.springframework.security.web.firewall.HttpFirewall;
import org.springframework.security.web.firewall.StrictHttpFirewall;
import com.sriinfosoft.taskmanager.security.OAuth2AuthenticationSuccessHandler;

// ADDED
import org.springframework.security.web.authentication.HttpStatusEntryPoint;           // ADDED
import org.springframework.http.HttpStatus;                                         // ADDED
import org.springframework.web.filter.ForwardedHeaderFilter;                        // ADDED

@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final JwtAuthenticationFilter jwtAuthenticationFilter;
    private final OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler;
    private final JwtTokenProvider jwtTokenProvider;

    @Value("${frontend.url}")
    private String frontendUrl; // e.g. https://taskmanager.sriinfosoft.com

    @Value("${cors.allowed-origins}")
    private String corsAllowedOrigins; // comma separated list

    public SecurityConfig(OAuth2AuthenticationSuccessHandler oAuth2AuthenticationSuccessHandler, JwtAuthenticationFilter jwtAuthenticationFilter,
                          JwtTokenProvider jwtTokenProvider) {
        this.oAuth2AuthenticationSuccessHandler = oAuth2AuthenticationSuccessHandler;
        this.jwtAuthenticationFilter = jwtAuthenticationFilter;
        this.jwtTokenProvider = jwtTokenProvider;
    }

    @Bean
    public HttpFirewall allowSemicolonFirewall() {
        StrictHttpFirewall firewall = new StrictHttpFirewall();
        firewall.setAllowSemicolon(true);
        return firewall;
    }

    // ADDED: honor X-Forwarded-* so redirects stay HTTPS behind CloudFront/Nginx
    @Bean
    public ForwardedHeaderFilter forwardedHeaderFilter() {                           // ADDED
        return new ForwardedHeaderFilter();                                          // ADDED
    }                                                                                // ADDED

    @Bean
    public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
        http
            .cors(Customizer.withDefaults())
            .csrf(csrf -> csrf.disable())
            // ADDED: make API stateless after OAuth – prevents 302 to login for APIs
            .sessionManagement(sm -> sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS)) // ADDED
            // ADDED: return 401 JSON for unauthenticated API calls (not 302)
            .exceptionHandling(e -> e.authenticationEntryPoint(new HttpStatusEntryPoint(HttpStatus.UNAUTHORIZED))) // ADDED
            // Configure authorization rules
            .authorizeHttpRequests(auth -> auth
            // Public endpoints - no authentication required
            .requestMatchers(
                "/",
                "/index.html",
                "/favicon.ico",
                "/static/**",
                "/assets/**",
                "/manifest.json",
                "/logo192.png",
                "/logo512.png",
                "/sw.js",                  // Service worker
                "/actuator/**",            // Health check endpoints
                "/oauth2/**",              // OAuth2 authorization endpoints
                "/login/**",               // OAuth2 callback endpoints
                "/login/oauth2/**",        // Explicit Spring OAuth2 callback pattern
                "/error"
            ).permitAll()
                // ✅ ADDED: Explicitly require authentication for push endpoints
                .requestMatchers("/api/push/**").authenticated()                
                .anyRequest().authenticated()
            )
            .oauth2Login(oauth -> oauth
                .authorizationEndpoint(ep -> ep
                    .baseUri("/oauth2/authorization")
                    .authorizationRequestRepository(authorizationRequestRepository())
                )
                .redirectionEndpoint(redir -> redir.baseUri("/login/oauth2/code/*"))
                .successHandler(oAuth2AuthenticationSuccessHandler)
                .failureHandler(this::oauth2FailureHandler)
            );

        // Your API uses JWT after login: keep the JWT filter in the chain.
        http.addFilterBefore(jwtAuthenticationFilter, UsernamePasswordAuthenticationFilter.class);

        return http.build();
    }

    @Bean
    public AuthorizationRequestRepository<OAuth2AuthorizationRequest> authorizationRequestRepository() {
        return new HttpSessionOAuth2AuthorizationRequestRepository();
    }

    private void oauth2SuccessHandler(HttpServletRequest req, HttpServletResponse res, Authentication auth)
            throws IOException {
        String jwt = jwtTokenProvider.generateToken(auth);
        String encoded = URLEncoder.encode(jwt, StandardCharsets.UTF_8);
        String target = frontendUrl + "/oauth2/redirect?token=" + encoded;
        res.sendRedirect(target);
    }

    private void oauth2FailureHandler(HttpServletRequest req, HttpServletResponse res, Exception ex)
            throws IOException {
        String target = frontendUrl + "/?loginError=1";
        System.err.println("OAuth2 login failed: " + ex.getClass().getSimpleName() + " - " + ex.getMessage());
        res.sendRedirect(target);
    }

    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration cfg = new CorsConfiguration();

        List<String> origins = Arrays.stream(corsAllowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();

        cfg.setAllowedOrigins(origins);
        cfg.setAllowCredentials(true);
        cfg.setAllowedMethods(Arrays.asList("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        cfg.setAllowedHeaders(Arrays.asList(
                "Authorization", "Content-Type", "Accept", "Origin",
                "X-Requested-With", "Access-Control-Request-Method", "Access-Control-Request-Headers"));
        cfg.setExposedHeaders(List.of("Authorization"));
        cfg.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", cfg);
        return source;
    }
}
