package com.tradingreporting.api.service;

import com.tradingreporting.api.domain.Lockable;
import com.tradingreporting.api.domain.User;
import com.tradingreporting.api.exception.ApiException;
import java.time.OffsetDateTime;
import java.util.Objects;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;

/**
 * Shared lock lifecycle for {@link com.tradingreporting.api.domain.Trade} and
 * {@link com.tradingreporting.api.domain.DailyTradeEntry}, mirroring the Python backend's
 * {@code ensure_unlocked}/manual lock-apply logic in {@code routes_trades.py}. Locks expire after
 * eight hours and are opportunistically cleared once an expired lock is encountered.
 */
@Component
public class LockSupport {

    private static final long LOCK_DURATION_HOURS = 8;

    /** Rejects the operation if the record is actively locked by someone else; otherwise clears
     * an expired lock so the caller can proceed. */
    public void ensureUnlocked(Lockable record, User currentUser) {
        OffsetDateTime now = OffsetDateTime.now();
        OffsetDateTime expiresAt = record.getLockExpiresAt();
        boolean stillActive = expiresAt == null || expiresAt.isAfter(now);
        if (record.isLocked() && stillActive && !Objects.equals(record.getLockedById(), currentUser.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Trade is locked by another user. Refresh and try again.");
        }
        if (record.isLocked() && expiresAt != null && !expiresAt.isAfter(now)) {
            clear(record);
        }
    }

    public void requireExpectedVersion(Lockable record, int expectedVersion) {
        if (record.getVersion() != expectedVersion) {
            throw new ApiException(HttpStatus.CONFLICT, "Trade version conflict: expected " + expectedVersion
                    + ", current version is " + record.getVersion() + ".");
        }
    }

    public void applyLock(Lockable record, User currentUser) {
        OffsetDateTime now = OffsetDateTime.now();
        record.setLocked(true);
        record.setLockedById(currentUser.getId());
        record.setLockedByDisplayName(currentUser.getUsername());
        record.setLockedAt(now);
        record.setLockExpiresAt(now.plusHours(LOCK_DURATION_HOURS));
    }

    /** Applies the owner/administrator unlock authorization rule, then clears the lock. */
    public void applyUnlock(Lockable record, User currentUser, String forbiddenMessage) {
        if (record.isLocked() && !Objects.equals(record.getLockedById(), currentUser.getId()) && !currentUser.isAdmin()) {
            throw new ApiException(HttpStatus.FORBIDDEN, forbiddenMessage);
        }
        clear(record);
    }

    private void clear(Lockable record) {
        record.setLocked(false);
        record.setLockedById(null);
        record.setLockedByDisplayName(null);
        record.setLockedAt(null);
        record.setLockExpiresAt(null);
    }
}
