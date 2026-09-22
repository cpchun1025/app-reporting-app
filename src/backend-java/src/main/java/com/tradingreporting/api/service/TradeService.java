package com.tradingreporting.api.service;

import com.tradingreporting.api.domain.DailyTradeEntry;
import com.tradingreporting.api.domain.Lockable;
import com.tradingreporting.api.domain.Trade;
import com.tradingreporting.api.domain.TradeEntrySnapshot;
import com.tradingreporting.api.domain.User;
import com.tradingreporting.api.dto.LockResponse;
import com.tradingreporting.api.dto.TradeCreateRequest;
import com.tradingreporting.api.dto.TradeEntrySaveRequest;
import com.tradingreporting.api.dto.TradeEntrySaveResponse;
import com.tradingreporting.api.dto.TradeUpdateRequest;
import com.tradingreporting.api.exception.ApiException;
import com.tradingreporting.api.exception.NotFoundException;
import com.tradingreporting.api.repository.DailyTradeEntryRepository;
import com.tradingreporting.api.repository.TradeEntrySnapshotRepository;
import com.tradingreporting.api.repository.TradeRepository;
import java.time.OffsetDateTime;
import java.util.List;
import org.springframework.dao.OptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Mirrors the trade portions of {@code app.routes_trades}: CRUD with optimistic-version checks,
 * the dual Trade/DailyTradeEntry lock endpoints, and the legacy free-form entry-save path.
 */
@Service
public class TradeService {

    private final TradeRepository tradeRepository;
    private final DailyTradeEntryRepository dailyTradeEntryRepository;
    private final TradeEntrySnapshotRepository snapshotRepository;
    private final LockSupport lockSupport;
    private final BackupCopyWriter backupCopyWriter;
    private final SnapshotJsonWriter snapshotJsonWriter;

    public TradeService(TradeRepository tradeRepository, DailyTradeEntryRepository dailyTradeEntryRepository,
                         TradeEntrySnapshotRepository snapshotRepository, LockSupport lockSupport,
                         BackupCopyWriter backupCopyWriter, SnapshotJsonWriter snapshotJsonWriter) {
        this.tradeRepository = tradeRepository;
        this.dailyTradeEntryRepository = dailyTradeEntryRepository;
        this.snapshotRepository = snapshotRepository;
        this.lockSupport = lockSupport;
        this.backupCopyWriter = backupCopyWriter;
        this.snapshotJsonWriter = snapshotJsonWriter;
    }

    @Transactional
    public Trade create(TradeCreateRequest request, User currentUser) {
        Trade trade = new Trade(request.tradeDate(), request.account(), request.instrument(), request.side(),
                request.quantity(), request.price(), request.currency(), request.status(), request.notes(),
                currentUser.getId());
        return tradeRepository.save(trade);
    }

    @Transactional(readOnly = true)
    public Trade get(String id) {
        return tradeRepository.findById(id).orElseThrow(() -> new NotFoundException("Trade was not found."));
    }

    @Transactional(readOnly = true)
    public List<Trade> listAll() {
        return tradeRepository.findAllByOrderByTradeDateAscAccountAscIdAsc();
    }

    @Transactional
    public Trade update(String id, TradeUpdateRequest request, User currentUser) {
        Trade trade = tradeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Trade was not found."));
        lockSupport.ensureUnlocked(trade, currentUser);
        lockSupport.requireExpectedVersion(trade, request.expectedVersion());
        trade.setTradeDate(request.tradeDate());
        trade.setAccount(request.account());
        trade.setInstrument(request.instrument());
        trade.setSide(request.side());
        trade.setQuantity(request.quantity());
        trade.setPrice(request.price());
        trade.setCurrency(request.currency());
        trade.setStatus(request.status());
        trade.setNotes(request.notes());
        try {
            tradeRepository.flush();
        } catch (OptimisticLockingFailureException error) {
            throw new ApiException(HttpStatus.CONFLICT, "Trade was updated by another request. Reload and try again.");
        }
        return trade;
    }

    @Transactional
    public void delete(String id, int expectedVersion) {
        Trade trade = tradeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Trade was not found."));
        lockSupport.requireExpectedVersion(trade, expectedVersion);
        try {
            tradeRepository.delete(trade);
            tradeRepository.flush();
        } catch (OptimisticLockingFailureException error) {
            throw new ApiException(HttpStatus.CONFLICT, "Trade was updated by another request. Reload and try again.");
        }
    }

    /** Tries a {@link DailyTradeEntry} first, then falls back to a {@link Trade}, mirroring the
     * Python backend's dual-resolution lock/unlock endpoints. */
    @Transactional
    public LockResponse lockAny(String id, User currentUser) {
        var entry = dailyTradeEntryRepository.findByIdForUpdate(id);
        if (entry.isPresent()) {
            Lockable record = entry.get();
            lockSupport.ensureUnlocked(record, currentUser);
            lockSupport.applyLock(record, currentUser);
            return DailyTradeEntryService.lockResponse(entry.get());
        }
        Trade trade = tradeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Trade was not found."));
        lockSupport.ensureUnlocked(trade, currentUser);
        lockSupport.applyLock(trade, currentUser);
        return lockResponse(trade);
    }

    @Transactional
    public LockResponse unlockAny(String id, User currentUser) {
        var entry = dailyTradeEntryRepository.findByIdForUpdate(id);
        if (entry.isPresent()) {
            Lockable record = entry.get();
            lockSupport.applyUnlock(record, currentUser, "Only the lock owner or an administrator can unlock this trade.");
            return DailyTradeEntryService.lockResponse(entry.get());
        }
        Trade trade = tradeRepository.findByIdForUpdate(id)
                .orElseThrow(() -> new NotFoundException("Trade was not found."));
        lockSupport.applyUnlock(trade, currentUser, "Only the lock owner or an administrator can unlock this trade.");
        return lockResponse(trade);
    }

    @Transactional
    public TradeEntrySaveResponse saveLegacyEntry(TradeEntrySaveRequest request, User currentUser) {
        String json = snapshotJsonWriter.write(request);
        snapshotRepository.save(new TradeEntrySnapshot(request.businessDate(), json, currentUser.getId()));
        snapshotRepository.flush();
        String filename = backupCopyWriter.writeOrFail(request.businessDate(), request);
        return new TradeEntrySaveResponse(OffsetDateTime.now(), filename, request.rows().size());
    }

    public static LockResponse lockResponse(Trade trade) {
        return new LockResponse(trade.getId(), trade.isLocked(), trade.getLockedById(), trade.getLockedByDisplayName(),
                trade.getLockedAt(), trade.getLockExpiresAt(), trade.getVersion());
    }
}

