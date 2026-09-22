package com.tradingreporting.api.service;

import com.tradingreporting.api.config.AppProperties;
import com.tradingreporting.api.domain.DailyTradeEntry;
import com.tradingreporting.api.domain.Trade;
import com.tradingreporting.api.domain.TradingBusiness;
import com.tradingreporting.api.domain.User;
import com.tradingreporting.api.repository.DailyTradeEntryRepository;
import com.tradingreporting.api.repository.TradeRepository;
import com.tradingreporting.api.repository.TradingBusinessRepository;
import com.tradingreporting.api.repository.UserRepository;
import com.tradingreporting.api.security.PasswordHasher;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Idempotent development-data seeding, mirroring {@code app.seed}: reruns never duplicate or
 * overwrite existing rows, only filling in whatever is still missing.
 */
@Service
public class SeedService {

    private record DevBusiness(String name, String code) {
    }

    private static final List<DevBusiness> DEV_BUSINESSES = List.of(
            new DevBusiness("Rates", "RATES"), new DevBusiness("Credit", "CREDIT"),
            new DevBusiness("Equities", "EQUITY"), new DevBusiness("Commodities", "CMDTY"),
            new DevBusiness("FX", "FX"), new DevBusiness("Macro", "MACRO"),
            new DevBusiness("Volatility", "VOL"), new DevBusiness("Prime Services", "PRIME"),
            new DevBusiness("Energy", "ENERGY"), new DevBusiness("Metals", "METALS"),
            new DevBusiness("EMEA Credit", "EMEA"), new DevBusiness("APAC Rates", "APAC"));

    private final UserRepository userRepository;
    private final TradeRepository tradeRepository;
    private final TradingBusinessRepository tradingBusinessRepository;
    private final DailyTradeEntryRepository dailyTradeEntryRepository;
    private final PasswordHasher passwordHasher;
    private final AppProperties properties;

    public SeedService(UserRepository userRepository, TradeRepository tradeRepository,
                        TradingBusinessRepository tradingBusinessRepository,
                        DailyTradeEntryRepository dailyTradeEntryRepository,
                        PasswordHasher passwordHasher, AppProperties properties) {
        this.userRepository = userRepository;
        this.tradeRepository = tradeRepository;
        this.tradingBusinessRepository = tradingBusinessRepository;
        this.dailyTradeEntryRepository = dailyTradeEntryRepository;
        this.passwordHasher = passwordHasher;
        this.properties = properties;
    }

    /** Runs the full idempotent seed sequence, mirroring the FastAPI lifespan/seed command. */
    @Transactional
    public void seedAll() {
        seedDevelopmentUsers();
        seedSampleTrades();
        seedDailyTradeEntries(properties.developmentBusinessDate());
    }

    @Transactional
    public void seedDevelopmentUsers() {
        String adminPassword = properties.devAdminPassword();
        String traderPassword = properties.devTraderPassword();
        if (adminPassword == null || adminPassword.isBlank() || traderPassword == null || traderPassword.isBlank()) {
            throw new IllegalStateException(
                    "DEV_ADMIN_PASSWORD and DEV_TRADER_PASSWORD must be set when development seeding is enabled.");
        }
        createUserIfMissing("dev_admin", adminPassword, true);
        createUserIfMissing("dev_trader", traderPassword, false);
    }

    private void createUserIfMissing(String username, String password, boolean admin) {
        if (userRepository.findByUsername(username).isEmpty()) {
            userRepository.save(new User(username, passwordHasher.hash(password), admin));
        }
    }

    @Transactional
    public void seedSampleTrades() {
        if (tradeRepository.count() > 0) {
            return;
        }
        User trader = userRepository.findByUsername("dev_trader").orElseThrow(
                () -> new IllegalStateException("Development trader must exist before sample trades are seeded."));
        for (int index = 0; index < DEV_BUSINESSES.size(); index++) {
            DevBusiness business = DEV_BUSINESSES.get(index);
            boolean locked = index == 0 || index == 4;
            Trade trade = new Trade(
                    LocalDate.of(2026, 9, 22),
                    business.name(),
                    business.code(),
                    "BUY",
                    BigDecimal.valueOf(20 + index * 3.5),
                    BigDecimal.valueOf(100 + index * 2.25),
                    "USD",
                    "BOOKED",
                    null,
                    trader.getId());
            if (locked) {
                trade.setLocked(true);
                trade.setLockedById(trader.getId());
                trade.setLockedByDisplayName("dev_trader");
            }
            tradeRepository.save(trade);
        }
    }

    @Transactional
    public void seedDevelopmentBusinesses() {
        Set<String> existingCodes = new HashSet<>();
        for (TradingBusiness business : tradingBusinessRepository.findAll()) {
            existingCodes.add(business.getCode());
        }
        for (DevBusiness business : DEV_BUSINESSES) {
            if (!existingCodes.contains(business.code())) {
                tradingBusinessRepository.save(new TradingBusiness(business.name(), business.code()));
            }
        }
    }

    @Transactional
    public void seedDailyTradeEntries(LocalDate businessDate) {
        seedDevelopmentBusinesses();
        User trader = userRepository.findByUsername("dev_trader").orElseThrow(
                () -> new IllegalStateException("Development trader must exist before daily entries are seeded."));
        List<TradingBusiness> businesses = tradingBusinessRepository.findAllByActiveTrueOrderByCodeAsc();
        Set<String> existingBusinessIds = new HashSet<>();
        for (DailyTradeEntry entry : dailyTradeEntryRepository.findAllByBusinessDate(businessDate)) {
            existingBusinessIds.add(entry.getBusiness().getId());
        }
        for (int index = 0; index < businesses.size(); index++) {
            TradingBusiness business = businesses.get(index);
            if (existingBusinessIds.contains(business.getId())) {
                continue;
            }
            DailyTradeEntry entry = new DailyTradeEntry(business, businessDate, trader.getId());
            entry.setDelta(BigDecimal.valueOf(20 + index * 3.5));
            entry.setGamma(BigDecimal.valueOf(index * 0.25));
            entry.setTheta(BigDecimal.valueOf(-(index + 1) * 0.2));
            entry.setVega(BigDecimal.valueOf((index + 1) * 0.1));
            entry.setPnl(BigDecimal.valueOf((20 + index * 3.5) * (100 + index * 2.25)));
            dailyTradeEntryRepository.save(entry);
        }
    }
}
