package com.tradingreporting.api.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingreporting.api.config.AppProperties;
import com.tradingreporting.api.exception.UnauthorizedException;
import java.nio.charset.StandardCharsets;
import java.security.InvalidKeyException;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Component;

/**
 * Minimal HS256 JWT encoder/decoder implemented directly against {@link Mac} so tokens are
 * byte-for-byte compatible with PyJWT, regardless of the configured secret's length (PyJWT does
 * not enforce a minimum HMAC key size, unlike some JVM JWT libraries).
 */
@Component
public class JwtService {

    private static final Base64.Encoder URL_ENCODER = Base64.getUrlEncoder().withoutPadding();
    private static final Base64.Decoder URL_DECODER = Base64.getUrlDecoder();
    private static final String HEADER_JSON = "{\"alg\":\"HS256\",\"typ\":\"JWT\"}";

    private final AppProperties properties;
    private final ObjectMapper objectMapper;

    public JwtService(AppProperties properties, ObjectMapper objectMapper) {
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    public String createAccessToken(String userId) {
        if (!"HS256".equalsIgnoreCase(properties.jwtAlgorithm())) {
            throw new IllegalStateException("Only HS256 is supported: " + properties.jwtAlgorithm());
        }
        long expiresAt = Instant.now().plusSeconds(properties.accessTokenExpireMinutes() * 60).getEpochSecond();
        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("sub", userId);
        payload.put("exp", expiresAt);
        String headerSegment = URL_ENCODER.encodeToString(HEADER_JSON.getBytes(StandardCharsets.UTF_8));
        String payloadSegment = encodePayload(payload);
        String signingInput = headerSegment + "." + payloadSegment;
        String signature = URL_ENCODER.encodeToString(hmacSha256(signingInput));
        return signingInput + "." + signature;
    }

    /** Returns the subject ("sub") claim, or throws {@link UnauthorizedException} if invalid/expired. */
    public String decodeSubject(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw invalidToken();
        }
        String signingInput = parts[0] + "." + parts[1];
        byte[] expectedSignature = hmacSha256(signingInput);
        byte[] actualSignature;
        try {
            actualSignature = URL_DECODER.decode(parts[2]);
        } catch (IllegalArgumentException error) {
            throw invalidToken();
        }
        if (!constantTimeEquals(expectedSignature, actualSignature)) {
            throw invalidToken();
        }
        Map<?, ?> payload;
        try {
            payload = objectMapper.readValue(URL_DECODER.decode(parts[1]), Map.class);
        } catch (Exception error) {
            throw invalidToken();
        }
        Object exp = payload.get("exp");
        if (exp instanceof Number number && Instant.now().getEpochSecond() > number.longValue()) {
            throw new UnauthorizedException("Invalid or expired access token.");
        }
        Object sub = payload.get("sub");
        if (!(sub instanceof String subject) || subject.isEmpty()) {
            throw new UnauthorizedException("Access token is missing a subject.");
        }
        return subject;
    }

    private String encodePayload(Map<String, Object> payload) {
        try {
            byte[] json = objectMapper.writeValueAsBytes(payload);
            return URL_ENCODER.encodeToString(json);
        } catch (Exception error) {
            throw new IllegalStateException("Unable to serialize JWT payload.", error);
        }
    }

    private byte[] hmacSha256(String data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(properties.jwtSecret().getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            return mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
        } catch (NoSuchAlgorithmException | InvalidKeyException error) {
            throw new IllegalStateException("HmacSHA256 is unavailable.", error);
        }
    }

    private static boolean constantTimeEquals(byte[] a, byte[] b) {
        if (a.length != b.length) {
            return false;
        }
        int result = 0;
        for (int i = 0; i < a.length; i++) {
            result |= a[i] ^ b[i];
        }
        return result == 0;
    }

    private static UnauthorizedException invalidToken() {
        return new UnauthorizedException("Invalid or expired access token.");
    }
}
