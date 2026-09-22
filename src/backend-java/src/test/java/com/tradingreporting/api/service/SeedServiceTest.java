package com.tradingreporting.api.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.tradingreporting.api.repository.DailyTradeEntryRepository;
import com.tradingreporting.api.repository.TradeRepository;
import com.tradingreporting.api.repository.TradingBusinessRepository;
import com.tradingreporting.api.repository.UserRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.TestPropertySource;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors {@code app.seed}'s idempotency guarantee: running the seed twice never duplicates rows. */
@SpringBootTest
@ActiveProfiles("test")
@TestPropertySource(properties = "app.seed-dev-users=true")
@Transactional
class SeedServiceTest {

    @Autowired
    private SeedService seedService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TradeRepository tradeRepository;

    @Autowired
    private TradingBusinessRepository tradingBusinessRepository;

    @Autowired
    private DailyTradeEntryRepository dailyTradeEntryRepository;

    @Test
    void seedingTwiceProducesTheSameRowCounts() {
        seedService.seedAll();
        long users1 = userRepository.count();
        long trades1 = tradeRepository.count();
        long businesses1 = tradingBusinessRepository.count();
        long entries1 = dailyTradeEntryRepository.count();

        seedService.seedAll();
        long users2 = userRepository.count();
        long trades2 = tradeRepository.count();
        long businesses2 = tradingBusinessRepository.count();
        long entries2 = dailyTradeEntryRepository.count();

        assertThat(users2).isEqualTo(users1);
        assertThat(trades2).isEqualTo(trades1);
        assertThat(businesses2).isEqualTo(businesses1);
        assertThat(entries2).isEqualTo(entries1);
        assertThat(userRepository.findByUsername("dev_admin")).isPresent();
        assertThat(userRepository.findByUsername("dev_trader")).isPresent();
        assertThat(trades1).isEqualTo(12);
        assertThat(businesses1).isEqualTo(12);
        assertThat(entries1).isEqualTo(12);
    }

    @Test
    void seedingWithoutDevPasswordsFailsFast() {
        SeedService withoutPasswords = new SeedService(userRepository, tradeRepository, tradingBusinessRepository,
                dailyTradeEntryRepository, new com.tradingreporting.api.security.PasswordHasher(),
                new com.tradingreporting.api.config.AppProperties("secret", "HS256", 60, true, "", "", false, "",
                        "./tmp", java.time.LocalDate.of(2026, 1, 1)));

        assertThatThrownBy(withoutPasswords::seedDevelopmentUsers).isInstanceOf(IllegalStateException.class);
    }
}
