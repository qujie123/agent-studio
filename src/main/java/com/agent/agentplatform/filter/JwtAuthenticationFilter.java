package com.agent.agentplatform.filter;

import com.agent.agentplatform.utils.JwtUtil;
import com.agent.agentplatform.repository.UserRepository;
import io.jsonwebtoken.JwtException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    private static final Logger log = LoggerFactory.getLogger(JwtAuthenticationFilter.class);
    private final JwtUtil jwtUtil;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtUtil jwtUtil, UserRepository userRepository) {
        this.jwtUtil = jwtUtil;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        String authHeader = request.getHeader("Authorization");
        if (authHeader != null && authHeader.startsWith("Bearer ")
                && SecurityContextHolder.getContext().getAuthentication() == null) {
            try {
                String token = authHeader.substring(7);
                var claims = jwtUtil.getClaims(token);
                Long userId = claims.get("userId", Long.class);
                if (userId != null) {
                    userRepository.findById(userId)
                            .filter(user -> user.isEnabled() && user.getTokenVersion() ==
                                    (claims.get("tokenVersion", Long.class) == null ? 0L : claims.get("tokenVersion", Long.class)))
                            .filter(user -> user.getUsername().equals(claims.getSubject()))
                            .filter(user -> "USER".equals(user.getRole()) || "ADMIN".equals(user.getRole()))
                            .ifPresent(user -> {
                                var authority = new SimpleGrantedAuthority("ROLE_" + user.getRole());
                                var authentication = new UsernamePasswordAuthenticationToken(
                                        user.getUsername(), null, List.of(authority));
                                SecurityContextHolder.getContext().setAuthentication(authentication);
                            });
                }
            } catch (JwtException | IllegalArgumentException exception) {
                SecurityContextHolder.clearContext();
                log.debug("JWT authentication failed: {}", exception.getClass().getSimpleName());
            }
        }
        filterChain.doFilter(request, response);
    }
}
