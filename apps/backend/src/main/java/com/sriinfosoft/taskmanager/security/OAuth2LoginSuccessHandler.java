package com.sriinfosoft.taskmanager.security;

import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.beans.factory.annotation.Autowired;
import com.sriinfosoft.taskmanager.repository.UserRepository;
import com.sriinfosoft.taskmanager.service.ActivityService;
import com.sriinfosoft.taskmanager.model.UserActivity;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.Authentication;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

import java.io.IOException;

@Component
public class OAuth2LoginSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {

    @Autowired
    private JwtTokenProvider tokenProvider;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private ActivityService activityService;

    @Value("${frontend.url:https://taskmanager.gcp.sriinfosoft.com}")
    private String frontendUrl;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request,
                                       HttpServletResponse response,
                                       Authentication authentication) throws IOException, ServletException {
        
        if (response.isCommitted()) {
            logger.debug("Response has already been committed. Unable to redirect.");
            return;
        }

        // Activity trail: signup (no user row yet) vs returning login.
        try {
            OAuth2User principal = (OAuth2User) authentication.getPrincipal();
            String email = principal.getAttribute("email");
            if (email != null) {
                String ip = request.getHeader("X-Forwarded-For");
                if (ip != null && ip.contains(",")) ip = ip.split(",")[0].trim();
                if (ip == null) ip = request.getRemoteAddr();
                boolean existing = userRepository.findByEmail(email).isPresent();
                activityService.record(email,
                        existing ? UserActivity.LOGIN : UserActivity.SIGNUP,
                        "oauth2", ip);
            }
        } catch (Exception e) {
            logger.warn("activity record on login failed: " + e.getMessage());
        }

        // Generate JWT token
        String token = tokenProvider.generateToken(authentication);

        // Redirect to frontend with token
        String targetUrl = UriComponentsBuilder.fromUriString(frontendUrl + "/oauth2/redirect")
                .queryParam("token", token)
                .build().toUriString();

        getRedirectStrategy().sendRedirect(request, response, targetUrl);
    }
}
