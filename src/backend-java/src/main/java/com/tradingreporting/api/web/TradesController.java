package com.tradingreporting.api.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.tradingreporting.api.domain.User;
import com.tradingreporting.api.dto.DailyTradeEntryListResponse;
import com.tradingreporting.api.dto.DailyTradeEntryResponse;
import com.tradingreporting.api.dto.DailyTradeEntrySaveRequest;
import com.tradingreporting.api.dto.LockResponse;
import com.tradingreporting.api.dto.TradeCreateRequest;
import com.tradingreporting.api.dto.TradeEntrySaveRequest;
import com.tradingreporting.api.dto.TradeResponse;
import com.tradingreporting.api.dto.TradeUpdateRequest;
import com.tradingreporting.api.service.DailyTradeEntryService;
import com.tradingreporting.api.service.TradeService;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.ConstraintViolationException;
import jakarta.validation.Valid;
import jakarta.validation.Validator;
import java.time.LocalDate;
import java.util.List;
import java.util.Set;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Mirrors {@code app.routes_trades}: legacy trade CRUD, the dual Trade/DailyTradeEntry lock
 * endpoints, the per-business-date daily entry list, and the union-typed {@code /entry/save}
 * endpoint (dispatched manually here since Jackson has no built-in discriminator for it, exactly
 * as FastAPI/Pydantic tries each union member in turn).
 */
@RestController
@RequestMapping("/trades")
public class TradesController {

    private final TradeService tradeService;
    private final DailyTradeEntryService dailyTradeEntryService;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    public TradesController(TradeService tradeService, DailyTradeEntryService dailyTradeEntryService,
                             ObjectMapper objectMapper, Validator validator) {
        this.tradeService = tradeService;
        this.dailyTradeEntryService = dailyTradeEntryService;
        this.objectMapper = objectMapper;
        this.validator = validator;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public TradeResponse create(@Valid @RequestBody TradeCreateRequest request, @AuthenticationPrincipal User user) {
        return TradeResponse.from(tradeService.create(request, user));
    }

    @GetMapping("/entry")
    public DailyTradeEntryListResponse listEntries(@RequestParam("business_date") LocalDate businessDate,
                                                     @AuthenticationPrincipal User user) {
        List<DailyTradeEntryResponse> rows = dailyTradeEntryService.listForDate(businessDate, user).stream()
                .map(DailyTradeEntryResponse::from).toList();
        return new DailyTradeEntryListResponse(businessDate, rows);
    }

    @PostMapping("/entry/{entryId}/lock")
    public LockResponse lockEntry(@PathVariable String entryId, @AuthenticationPrincipal User user) {
        return DailyTradeEntryService.lockResponse(dailyTradeEntryService.lock(entryId, user));
    }

    @PostMapping("/entry/{entryId}/unlock")
    public LockResponse unlockEntry(@PathVariable String entryId, @AuthenticationPrincipal User user) {
        return DailyTradeEntryService.lockResponse(dailyTradeEntryService.unlock(entryId, user));
    }

    @PostMapping("/entry/save")
    public Object saveEntry(@RequestBody JsonNode body, @AuthenticationPrincipal User user) {
        JsonNode rows = body.path("rows");
        boolean isDailySave = rows.isArray() && !rows.isEmpty()
                && rows.get(0).has("id") && rows.get(0).has("expected_version");
        if (isDailySave) {
            DailyTradeEntrySaveRequest request = objectMapper.convertValue(body, DailyTradeEntrySaveRequest.class);
            validate(request);
            return dailyTradeEntryService.saveDailyEntries(request, user);
        }
        TradeEntrySaveRequest request = objectMapper.convertValue(body, TradeEntrySaveRequest.class);
        validate(request);
        return tradeService.saveLegacyEntry(request, user);
    }

    @GetMapping("/{tradeId}")
    public TradeResponse get(@PathVariable String tradeId) {
        return TradeResponse.from(tradeService.get(tradeId));
    }

    @PutMapping("/{tradeId}")
    public TradeResponse update(@PathVariable String tradeId, @Valid @RequestBody TradeUpdateRequest request,
                                @AuthenticationPrincipal User user) {
        return TradeResponse.from(tradeService.update(tradeId, request, user));
    }

    @GetMapping
    public Object list(@RequestParam(value = "business_date", required = false) LocalDate businessDate,
                        @AuthenticationPrincipal User user) {
        if (businessDate != null) {
            return dailyTradeEntryService.listForDate(businessDate, user).stream()
                    .map(DailyTradeEntryResponse::from).toList();
        }
        return tradeService.listAll().stream().map(TradeResponse::from).toList();
    }

    @PostMapping("/{tradeId}/lock")
    public LockResponse lock(@PathVariable String tradeId, @AuthenticationPrincipal User user) {
        return tradeService.lockAny(tradeId, user);
    }

    @PostMapping("/{tradeId}/unlock")
    public LockResponse unlock(@PathVariable String tradeId, @AuthenticationPrincipal User user) {
        return tradeService.unlockAny(tradeId, user);
    }

    @DeleteMapping("/{tradeId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void delete(@PathVariable String tradeId, @RequestParam("expected_version") int expectedVersion) {
        tradeService.delete(tradeId, expectedVersion);
    }

    private <T> void validate(T target) {
        Set<ConstraintViolation<T>> violations = validator.validate(target);
        if (!violations.isEmpty()) {
            throw new ConstraintViolationException(violations);
        }
    }
}
