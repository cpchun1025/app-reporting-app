package com.tradingreporting.api.web;

import com.tradingreporting.api.exception.ServiceUnavailableException;
import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import java.util.List;
import java.util.Map;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors the Python backend's {@code /health}, {@code /health/live}, and {@code /health/ready}. */
@RestController
@RequestMapping("/health")
public class HealthController {

    @PersistenceContext
    private EntityManager entityManager;

    @GetMapping
    public Map<String, String> health() {
        return Map.of("status", "ok");
    }

    @GetMapping("/live")
    public Map<String, String> live() {
        return Map.of("status", "ok");
    }

    @GetMapping("/ready")
    @Transactional(readOnly = true)
    public Map<String, String> ready() {
        Object revision;
        try {
            entityManager.createNativeQuery("SELECT 1").getSingleResult();
                List<?> revisions = entityManager.createNativeQuery("SELECT version_num FROM alembic_version")
                    .getResultList();
                revision = revisions.stream().findFirst().orElse(null);
        } catch (RuntimeException error) {
            throw new ServiceUnavailableException("Database is not ready.");
        }
        if (revision == null) {
            throw new ServiceUnavailableException("Database migrations are not ready.");
        }
        return Map.of("status", "ok");
    }
}
