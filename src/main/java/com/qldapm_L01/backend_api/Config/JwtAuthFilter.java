package com.qldapm_L01.backend_api.Config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.qldapm_L01.backend_api.Entity.User;
import com.qldapm_L01.backend_api.Repository.UserRepository;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.security.SignatureException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.web.authentication.WebAuthenticationDetailsSource;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Map;

@Component
@RequiredArgsConstructor
public class JwtAuthFilter extends OncePerRequestFilter {

    private final JwtUtil jwtUtil;
    private final UserDetailsService userDetailsService;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Override
    protected void doFilterInternal(
            HttpServletRequest request,
            HttpServletResponse response,
            FilterChain filterChain
    ) throws ServletException, IOException {

        final String authHeader = request.getHeader("Authorization");

        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            filterChain.doFilter(request, response);
            return;
        }

        final String jwt = authHeader.substring(7).trim();
        final String username;

        try {
            username = jwtUtil.extractUsername(jwt);
        } catch (ExpiredJwtException e) {
            sendError(response, 401, "Token expired");
            return;
        } catch (SignatureException e) {
            sendError(response, 401, "Invalid token signature");
            return;
        } catch (Exception e) {
            sendError(response, 401, "Invalid token");
            return;
        }

        if (username != null && SecurityContextHolder.getContext().getAuthentication() == null) {
            UserDetails userDetails = this.userDetailsService.loadUserByUsername(username);

            User user = userRepository.findByUsername(username)
                    .orElse(null);
            if (user == null) {
                sendError(response, 401, "Invalid token");
                return;
            }

            if (!"ACTIVE".equalsIgnoreCase(user.getStatus())) {
                sendError(response, 403, "ACCOUNT_BANNED");
                return;
            }

            Integer tokenVersion = jwtUtil.extractTokenVersion(jwt);
            if (tokenVersion == null || tokenVersion.intValue() != user.getTokenVersion()) {
                sendError(response, 401, "TOKEN_REVOKED");
                return;
            }

            if (!jwtUtil.isTokenValid(jwt, userDetails)) {
                sendError(response, 401, "Invalid token");
                return;
            }

            UsernamePasswordAuthenticationToken authToken = new UsernamePasswordAuthenticationToken(
                    userDetails,
                    null,
                    userDetails.getAuthorities()
            );
            authToken.setDetails(new WebAuthenticationDetailsSource().buildDetails(request));
            SecurityContextHolder.getContext().setAuthentication(authToken);
        }
        filterChain.doFilter(request, response);
    }

    private void sendError(HttpServletResponse response, int status, String message) throws IOException {
        response.setStatus(status);
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        objectMapper.writeValue(response.getOutputStream(),
                Map.of("statusCode", status, "message", message));
    }
}

