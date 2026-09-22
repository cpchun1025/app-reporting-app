package com.tradingreporting.api.seed;

import com.tradingreporting.api.config.AppProperties;
import com.tradingreporting.api.service.SeedService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Explicit, idempotent seed entry point active on the {@code seed} Spring profile, equivalent to
 * running {@code python -m app.seed_command}. With {@code spring.main.web-application-type: none}
 * (see {@code application-seed.yml}) the process exits automatically once this runner completes.
 */
@Component
@Profile("seed")
public class SeedCommandRunner implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(SeedCommandRunner.class);

    private final SeedService seedService;
    private final AppProperties properties;

    public SeedCommandRunner(SeedService seedService, AppProperties properties) {
        this.seedService = seedService;
        this.properties = properties;
    }

    @Override
    public void run(ApplicationArguments args) {
        if (!properties.seedDevUsers()) {
            throw new IllegalStateException("Development seeding is disabled. Set SEED_DEV_USERS=true to run it.");
        }
        seedService.seedAll();
        log.info("Development seed completed.");
    }
}
