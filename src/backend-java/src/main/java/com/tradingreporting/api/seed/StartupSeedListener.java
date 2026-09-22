package com.tradingreporting.api.seed;

import com.tradingreporting.api.config.AppProperties;
import com.tradingreporting.api.service.SeedService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/**
 * Mirrors the Python backend's FastAPI lifespan auto-seed: when {@code app.seed-dev-users=true}
 * the normal web application seeds development data once it is fully started. Disabled on the
 * {@code seed} profile, where {@link SeedCommandRunner} performs the equivalent one-shot seed.
 */
@Component
@Profile("!seed")
public class StartupSeedListener {

    private final SeedService seedService;
    private final AppProperties properties;

    public StartupSeedListener(SeedService seedService, AppProperties properties) {
        this.seedService = seedService;
        this.properties = properties;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void onApplicationReady() {
        if (properties.seedDevUsers()) {
            seedService.seedAll();
        }
    }
}
