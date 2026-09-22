package com.tradingreporting.api.config;

import java.time.LocalDate;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Mirrors the Python backend's {@code app.config.Settings}. Values are bound from the same
 * environment variable names (JWT_SECRET, CORS_ORIGINS, ...) via placeholders in application.yml
 * so the two backends remain drop-in compatible with the same docker-compose environment block.
 */
@ConfigurationProperties(prefix = "app")
public record AppProperties(
        String jwtSecret,
        String jwtAlgorithm,
        long accessTokenExpireMinutes,
        boolean seedDevUsers,
        String devAdminPassword,
        String devTraderPassword,
        boolean enableScheduler,
        String corsOrigins,
        String tradeSaveCopyPath,
        LocalDate developmentBusinessDate) {

    public List<String> corsOriginList() {
        return corsOrigins == null || corsOrigins.isBlank()
                ? List.of()
                : List.of(corsOrigins.split(",")).stream().map(String::trim).filter(s -> !s.isEmpty()).toList();
    }
}
