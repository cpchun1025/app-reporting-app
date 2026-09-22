package com.tradingreporting.api.security;

import com.tradingreporting.api.exception.UnauthorizedException;
import com.tradingreporting.api.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.lang.NonNull;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Resolves the {@code Authorization: Bearer <token>} header into a {@link JwtAuthenticationToken},
 * mirroring the Python backend's {@code get_current_user} dependency. Requests without a bearer
 * token are left unauthenticated so Spring Security's entry point renders the same
 * "Bearer token is required." response as the Python backend.
 */
public class JwtAuthenticationFilter extends OncePerRequestFilter {

    private static final String BEARER_PREFIX = "Bearer ";

    private final JwtService jwtService;
    private final UserRepository userRepository;
    private final ApiErrorResponseWriter errorResponseWriter;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository,
                                    ApiErrorResponseWriter errorResponseWriter) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
        this.errorResponseWriter = errorResponseWriter;
    }

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                     @NonNull FilterChain filterChain) throws ServletException, IOException {
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith(BEARER_PREFIX)) {
            filterChain.doFilter(request, response);
            return;
        }
        String token = header.substring(BEARER_PREFIX.length()).trim();
        try {
            String userId = jwtService.decodeSubject(token);
            var user = userRepository.findById(userId).orElse(null);
            if (user == null || !user.isActive()) {
                throw new UnauthorizedException("Access token user is unavailable.");
            }
            SecurityContextHolder.getContext().setAuthentication(new JwtAuthenticationToken(user));
        } catch (UnauthorizedException error) {
            errorResponseWriter.write(response, 401, error.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }
}
