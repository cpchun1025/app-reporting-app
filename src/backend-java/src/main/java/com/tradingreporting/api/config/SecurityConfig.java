package com.tradingreporting.api.config;

import com.tradingreporting.api.repository.UserRepository;
import com.tradingreporting.api.security.ApiErrorResponseWriter;
import com.tradingreporting.api.security.JwtAuthenticationFilter;
import com.tradingreporting.api.security.JwtService;
import java.util.List;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

/**
 * Stateless bearer-token security, mirroring the Python backend: {@code /auth/login},
 * {@code /auth/mock/callback} and the health endpoints are public; everything else requires a
 * valid JWT resolved by {@link JwtAuthenticationFilter}. Missing/invalid credentials are rendered
 * as {@code {"detail": "Bearer token is required."}} with a {@code WWW-Authenticate: Bearer}
 * header, matching FastAPI's {@code HTTPBearer} dependency.
 */
@Configuration
@EnableWebSecurity
public class SecurityConfig {

    private final AppProperties appProperties;

    public SecurityConfig(AppProperties appProperties) {
        this.appProperties = appProperties;
    }

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http, JwtService jwtService,
                                                     UserRepository userRepository,
                                                     ApiErrorResponseWriter errorResponseWriter) throws Exception {
        http.csrf(csrf -> csrf.disable())
                .cors(cors -> cors.configurationSource(corsConfigurationSource()))
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(authorize -> authorize
                        .requestMatchers("/health", "/health/live", "/health/ready").permitAll()
                        .requestMatchers("/auth/login", "/auth/mock/callback").permitAll()
                        .anyRequest().authenticated())
                .exceptionHandling(handler -> handler
                        .authenticationEntryPoint((request, response, authException) ->
                                errorResponseWriter.write(response, 401, "Bearer token is required."))
                        .accessDeniedHandler((request, response, accessDeniedException) ->
                                errorResponseWriter.write(response, 403, "Access is denied.")))
                .addFilterBefore(new JwtAuthenticationFilter(jwtService, userRepository, errorResponseWriter),
                        UsernamePasswordAuthenticationFilter.class);
        return http.build();
    }

    private CorsConfigurationSource corsConfigurationSource() {
        List<String> origins = appProperties.corsOriginList();
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOrigins(origins.isEmpty() ? List.of("*") : origins);
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS"));
        configuration.setAllowedHeaders(List.of("*"));
        configuration.setAllowCredentials(!origins.isEmpty());
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);
        return source;
    }
}
