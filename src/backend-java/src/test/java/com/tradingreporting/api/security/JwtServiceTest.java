package com.tradingreporting.api.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingreporting.api.config.AppProperties;
import com.tradingreporting.api.exception.UnauthorizedException;
import java.time.LocalDate;
import org.junit.jupiter.api.Test;

class JwtServiceTest {

    private final AppProperties properties = new AppProperties(
            "unit-test-secret", "HS256", 60, false, "", "", false, "", "./tmp", LocalDate.of(2026, 1, 1));
    private final JwtService jwtService = new JwtService(properties, new ObjectMapper());

    @Test
    void encodesAndDecodesTheSubjectClaim() {
        String token = jwtService.createAccessToken("user-123");

        assertThat(token.split("\\.")).hasSize(3);
        assertThat(jwtService.decodeSubject(token)).isEqualTo("user-123");
    }

    @Test
    void rejectsATokenSignedWithADifferentSecret() {
        AppProperties otherSecret = new AppProperties(
                "a-completely-different-secret", "HS256", 60, false, "", "", false, "", "./tmp", LocalDate.of(2026, 1, 1));
        JwtService otherService = new JwtService(otherSecret, new ObjectMapper());
        String token = otherService.createAccessToken("user-123");

        assertThatThrownBy(() -> jwtService.decodeSubject(token)).isInstanceOf(UnauthorizedException.class);
    }

    @Test
    void rejectsAMalformedToken() {
        assertThatThrownBy(() -> jwtService.decodeSubject("not-a-jwt")).isInstanceOf(UnauthorizedException.class);
    }
}
