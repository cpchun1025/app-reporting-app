package com.tradingreporting.api.service;

import com.tradingreporting.api.domain.DailyTradeEntry;
import com.tradingreporting.api.domain.TradeEntrySnapshot;
import com.tradingreporting.api.domain.TradingBusiness;
import com.tradingreporting.api.domain.User;
import com.tradingreporting.api.dto.DailyTradeEntryResponse;
import com.tradingreporting.api.dto.DailyTradeEntryRowRequest;
import com.tradingreporting.api.dto.DailyTradeEntrySaveRequest;
import com.tradingreporting.api.dto.DailyTradeEntrySaveResponse;
import com.tradingreporting.api.dto.LockResponse;
import com.tradingreporting.api.exception.ApiException;
import com.tradingreporting.api.exception.NotFoundException;
import com.tradingreporting.api.repository.DailyTradeEntryRepository;
import com.tradingreporting.api.repository.TradeEntrySnapshotRepository;
import com.tradingreporting.api.repository.TradingBusinessRepository;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors the daily-trade-entry portions of {@code app.routes_trades}: per-business-date rows are
 * lazily initialized for every active {@link TradingBusiness}, then locked/unlocked/edited with
 * the same optimistic-version and ownership rules as legacy trades.
 */
@Service
public class DailyTradeEntryService {

    private final DailyTradeEntryRepository dailyTradeEntryRepository;
    private final TradingBusinessRepository tradingBusinessRepository;
    private final TradeEntrySnapshotRepository snapshotRepository;
    private final LockSupport lockSupport;
    private final BackupCopyWriter backupCopyWriter;
    private final SnapshotJsonWriter snapshotJsonWriter;

    public DailyTradeEntryService(DailyTradeEntryRepository dailyTradeEntryRepository,
                                   TradingBusinessRepository tradingBusinessRepository,
                                   TradeEntrySnapshotRepository snapshotRepository,
                                   LockSupport lockSupport,
                                   BackupCopyWriter backupCopyWriter,
                                   SnapshotJsonWriter snapshotJsonWriter) {
        this.dailyTradeEntryRepository = dailyTradeEntryRepository;
        this.tradingBusinessRepository = tradingBusinessRepository;
        this.snapshotRepository = snapshotRepository;
        this.lockSupport = lockSupport;
        this.backupCopyWriter = backupCopyWriter;
        this.snapshotJsonWriter = snapshotJsonWriter;
    }

    @Transactional
    public List<DailyTradeEntry> listForDate(LocalDate businessDate, User currentUser) {
        List<TradingBusiness> businesses = tradingBusinessRepository.findAllByActiveTrueOrderByCodeAsc();
        Set<String> existingIds = new HashSet<>();
        for (DailyTradeEntry entry : dailyTradeEntryRepository.findAllByBusinessDate(businessDate)) {
            existingIds.add(entry.getBusiness().getId());
        }
        boolean missing = businesses.stream().anyMatch(business -> !existingIds.contains(business.getId()));
        if (!businesses.isEmpty() && missing) {
            for (TradingBusiness business : businesses) {
                if (!existingIds.contains(business.getId())) {
                    dailyTradeEntryRepository.save(
                            new DailyTradeEntry(business, businessDate, currentUser.getId()));
                }
            }
            try {
                dailyTradeEntryRepository.flush();
            } catch (DataIntegrityViolationException ignored) {
                // Another request initialized the same date first; fall through and reload it.
            }
        }
        return dailyTradeEntryRepository.findAllByBusinessDateOrderByBusinessCode(businessDate);
    }

    @Transactional
    public DailyTradeEntry lock(String id, User currentUser) {
        DailyTradeEntry entry = dailyTradeEntryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Daily trade entry was not found."));
        lockSupport.ensureUnlocked(entry, currentUser);
        lockSupport.applyLock(entry, currentUser);
        return entry;
    }

    @Transactional
    public DailyTradeEntry unlock(String id, User currentUser) {
        DailyTradeEntry entry = dailyTradeEntryRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Daily trade entry was not found."));
        lockSupport.applyUnlock(entry, currentUser,
                "Only the lock owner or an administrator can unlock this daily trade entry.");
        return entry;
    }

    @Transactional
    public DailyTradeEntrySaveResponse saveDailyEntries(DailyTradeEntrySaveRequest request, User currentUser) {
        List<String> ids = request.rows().stream().map(DailyTradeEntryRowRequest::id).toList();
        if (new LinkedHashSet<>(ids).size() != ids.size()) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "Each daily trade entry may be supplied only once.");
        }

        List<DailyTradeEntry> entries = new ArrayList<>();
        for (DailyTradeEntryRowRequest row : request.rows()) {
            DailyTradeEntry entry = dailyTradeEntryRepository.findByIdForUpdate(row.id())
                    .orElseThrow(() -> new NotFoundException("Daily trade entry was not found."));
            if (!entry.getBusinessDate().equals(request.businessDate())) {
                throw new ApiException(HttpStatus.CONFLICT,
                        "Daily trade entry does not belong to the requested business date.");
            }
            lockSupport.ensureUnlocked(entry, currentUser);
            lockSupport.requireExpectedVersion(entry, row.expectedVersion());
            entry.setDelta(row.delta());
            entry.setGamma(row.gamma());
            entry.setTheta(row.theta());
            entry.setVega(row.vega());
            entry.setPnl(row.pnl());
            entry.setUpdatedById(currentUser.getId());
            entries.add(entry);
        }

        snapshotRepository.save(new TradeEntrySnapshot(request.businessDate(),
                snapshotJsonWriter.write(request), currentUser.getId()));
        dailyTradeEntryRepository.flush();
        String ignoredFilename = backupCopyWriter.writeOrFail(request.businessDate(), request);

        List<DailyTradeEntryResponse> rows = entries.stream().map(DailyTradeEntryResponse::from).toList();
        return new DailyTradeEntrySaveResponse(OffsetDateTime.now(), request.businessDate(), rows);
    }

    public static LockResponse lockResponse(DailyTradeEntry entry) {
        return new LockResponse(entry.getId(), entry.isLocked(), entry.getLockedById(),
                entry.getLockedByDisplayName(), entry.getLockedAt(), entry.getLockExpiresAt(), entry.getVersion());
    }
}
