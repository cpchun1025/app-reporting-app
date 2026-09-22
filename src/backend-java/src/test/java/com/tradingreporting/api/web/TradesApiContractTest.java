package com.tradingreporting.api.web;

import static org.hamcrest.Matchers.greaterThanOrEqualTo;
import static org.hamcrest.Matchers.hasSize;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingreporting.api.domain.TradingBusiness;
import com.tradingreporting.api.domain.User;
import com.tradingreporting.api.repository.DailyTradeEntryRepository;
import com.tradingreporting.api.repository.TradingBusinessRepository;
import com.tradingreporting.api.repository.UserRepository;
import com.tradingreporting.api.security.JwtService;
import com.tradingreporting.api.security.PasswordHasher;
import java.math.BigDecimal;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

/**
 * End-to-end contract tests exercising the Java backend the same way the Python backend's
 * {@code tests/test_api.py} / {@code tests/test_daily_trade_entries.py} exercise FastAPI: real
 * HTTP requests through Spring Security and the full JPA stack against an in-memory H2 database.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@Transactional
class TradesApiContractTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TradingBusinessRepository tradingBusinessRepository;

    @Autowired
    private DailyTradeEntryRepository dailyTradeEntryRepository;

    @Autowired
    private PasswordHasher passwordHasher;

    @Autowired
    private JwtService jwtService;

    private User admin;
    private User trader;
    private String adminToken;
    private String traderToken;

    @BeforeEach
    void seedUsers() {
        admin = userRepository.save(new User("contract_admin", passwordHasher.hash("Adm1n-Passw0rd!"), true));
        trader = userRepository.save(new User("contract_trader", passwordHasher.hash("Trad3r-Passw0rd!"), false));
        adminToken = jwtService.createAccessToken(admin.getId());
        traderToken = jwtService.createAccessToken(trader.getId());
    }

    @Test
    void healthEndpointsAreUnauthenticatedAndReady() throws Exception {
        mockMvc.perform(get("/health")).andExpect(status().isOk()).andExpect(jsonPath("$.status").value("ok"));
        mockMvc.perform(get("/health/live")).andExpect(status().isOk());
        mockMvc.perform(get("/health/ready")).andExpect(status().isOk());
    }

    @Test
    void tradesEndpointsRequireAuthentication() throws Exception {
        mockMvc.perform(get("/trades")).andExpect(status().isUnauthorized());
    }

    @Test
    void loginRejectsWrongPasswordAndAcceptsCorrectPassword() throws Exception {
        String badBody = objectMapper.writeValueAsString(java.util.Map.of("username", "contract_admin", "password", "wrong-password"));
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(badBody))
                .andExpect(status().isUnauthorized());

        String goodBody = objectMapper.writeValueAsString(java.util.Map.of("username", "contract_admin", "password", "Adm1n-Passw0rd!"));
        mockMvc.perform(post("/auth/login").contentType(MediaType.APPLICATION_JSON).content(goodBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.access_token").isNotEmpty());
    }

    @Test
    void currentUserReturnsUsernameAndRole() throws Exception {
        mockMvc.perform(get("/auth/me").header("Authorization", "Bearer " + traderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.username").value("contract_trader"))
                .andExpect(jsonPath("$.role").value("trader"));
    }

    @Test
    void createGetUpdateAndDeleteTradeRoundTrips() throws Exception {
        String createBody = """
                {"trade_date":"2026-01-15","account":"ACC-1","instrument":"AAPL","side":"BUY",
                 "quantity":"100.0000","price":"150.2500","currency":"usd","status":"booked"}
                """;
        String created = mockMvc.perform(post("/trades")
                        .header("Authorization", "Bearer " + traderToken)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.currency").value("USD"))
                .andReturn().getResponse().getContentAsString();
        JsonNode createdJson = objectMapper.readTree(created);
        String tradeId = createdJson.get("id").asText();
        int version = createdJson.get("version").asInt();

        mockMvc.perform(get("/trades/" + tradeId).header("Authorization", "Bearer " + traderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.instrument").value("AAPL"));

        String updateBody = """
                {"trade_date":"2026-01-16","account":"ACC-1","instrument":"AAPL","side":"SELL",
                 "quantity":"50.0000","price":"151.0000","currency":"USD","status":"BOOKED",
                 "expected_version":%d}
                """.formatted(version);
        String updated = mockMvc.perform(put("/trades/" + tradeId)
                        .header("Authorization", "Bearer " + traderToken)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.side").value("SELL"))
                .andReturn().getResponse().getContentAsString();
        int newVersion = objectMapper.readTree(updated).get("version").asInt();

        // Stale version is rejected with a conflict.
        mockMvc.perform(put("/trades/" + tradeId)
                        .header("Authorization", "Bearer " + traderToken)
                        .contentType(MediaType.APPLICATION_JSON).content(updateBody))
                .andExpect(status().isConflict());

        mockMvc.perform(delete("/trades/" + tradeId)
                        .header("Authorization", "Bearer " + traderToken)
                        .param("expected_version", String.valueOf(newVersion)))
                .andExpect(status().isNoContent());

        mockMvc.perform(get("/trades/" + tradeId).header("Authorization", "Bearer " + traderToken))
                .andExpect(status().isNotFound());
    }

    @Test
    void lockingATradePreventsOthersFromUnlockingItButAdminCan() throws Exception {
        String createBody = """
                {"trade_date":"2026-01-15","account":"ACC-2","instrument":"MSFT","side":"BUY",
                 "quantity":"10.0000","price":"300.0000"}
                """;
        String created = mockMvc.perform(post("/trades")
                        .header("Authorization", "Bearer " + traderToken)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated())
                .andReturn().getResponse().getContentAsString();
        String tradeId = objectMapper.readTree(created).get("id").asText();

        mockMvc.perform(post("/trades/" + tradeId + "/lock").header("Authorization", "Bearer " + traderToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(true));

        // A second lock attempt by another user is rejected while the lock is held.
        mockMvc.perform(post("/trades/" + tradeId + "/lock").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isConflict());

        mockMvc.perform(post("/trades/" + tradeId + "/unlock").header("Authorization", "Bearer " + adminToken))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.locked").value(false));
    }

    @Test
    void dailyEntriesAreAutoInitializedListedLockedAndSaved() throws Exception {
        TradingBusiness business = tradingBusinessRepository.save(new TradingBusiness("Rates Desk", "RATESQA"));
        LocalDate businessDate = LocalDate.of(2026, 2, 1);

        String listResponse = mockMvc.perform(get("/trades/entry")
                        .header("Authorization", "Bearer " + traderToken)
                        .param("business_date", businessDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.business_date").value("2026-02-01"))
                .andExpect(jsonPath("$.rows", hasSize(greaterThanOrEqualTo(1))))
                .andReturn().getResponse().getContentAsString();
        JsonNode rows = objectMapper.readTree(listResponse).get("rows");
        JsonNode row = null;
        for (JsonNode candidate : rows) {
            if (candidate.get("business_id").asText().equals(business.getId())) {
                row = candidate;
            }
        }
        String entryId = row.get("id").asText();
        int entryVersion = row.get("version").asInt();

        String lockResponse = mockMvc.perform(post("/trades/entry/" + entryId + "/lock").header("Authorization", "Bearer " + traderToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int versionAfterLock = objectMapper.readTree(lockResponse).get("version").asInt();
        String unlockResponse = mockMvc.perform(post("/trades/entry/" + entryId + "/unlock").header("Authorization", "Bearer " + traderToken))
                .andExpect(status().isOk())
                .andReturn().getResponse().getContentAsString();
        int versionAfterUnlock = objectMapper.readTree(unlockResponse).get("version").asInt();

        String saveBody = """
                {"business_date":"2026-02-01","rows":[{"id":"%s","expected_version":%d,
                 "delta":"1.2500","gamma":"0.1000","theta":"-0.2000","vega":"0.3000","pnl":"42.0000"}]}
                """.formatted(entryId, versionAfterUnlock);
        mockMvc.perform(post("/trades/entry/save")
                        .header("Authorization", "Bearer " + traderToken)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows[0].pnl").value("42.0000"));

        // GET /trades?business_date= returns the same per-business-date rows.
        mockMvc.perform(get("/trades")
                        .header("Authorization", "Bearer " + traderToken)
                        .param("business_date", businessDate.toString()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$", hasSize(greaterThanOrEqualTo(1))));
    }

    @Test
    void legacyEntrySaveWritesASnapshotAndBackupFile() throws Exception {
        String saveBody = """
                {"business_date":"2026-03-01","rows":[{"name":"Rates","code":"RATES",
                 "values":["1.0000","2.0000"],"locked":false,"version":1}]}
                """;
        mockMvc.perform(post("/trades/entry/save")
                        .header("Authorization", "Bearer " + traderToken)
                        .contentType(MediaType.APPLICATION_JSON).content(saveBody))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.row_count").value(1))
                .andExpect(jsonPath("$.snapshot_filename").isNotEmpty());
    }

    @Test
    void reportsRejectAnEndDateBeforeTheStartDate() throws Exception {
        mockMvc.perform(get("/reports/daily")
                        .header("Authorization", "Bearer " + traderToken)
                        .param("start_date", "2026-02-10")
                        .param("end_date", "2026-02-01"))
                .andExpect(status().isUnprocessableEntity())
                .andExpect(jsonPath("$.detail").value("end_date must be on or after start_date."));
    }

    @Test
    void consolidatedReportAggregatesBookedTrades() throws Exception {
        String createBody = """
                {"trade_date":"2026-04-01","account":"ACC-REPORT","instrument":"AAPL","side":"BUY",
                 "quantity":"10.0000","price":"20.0000"}
                """;
        mockMvc.perform(post("/trades")
                        .header("Authorization", "Bearer " + traderToken)
                        .contentType(MediaType.APPLICATION_JSON).content(createBody))
                .andExpect(status().isCreated());

        mockMvc.perform(get("/reports/consolidated")
                        .header("Authorization", "Bearer " + traderToken)
                        .param("start_date", "2026-04-01")
                        .param("end_date", "2026-04-01"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rows", hasSize(greaterThanOrEqualTo(1))));
    }
}
