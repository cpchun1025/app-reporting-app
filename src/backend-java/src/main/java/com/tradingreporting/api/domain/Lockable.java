package com.tradingreporting.api.domain;

import java.time.OffsetDateTime;

/** Shared lock/version fields implemented by {@link Trade} and {@link DailyTradeEntry}, letting
 * service code share a single lock-lifecycle implementation for both entity kinds. */
public interface Lockable {
    boolean isLocked();

    void setLocked(boolean locked);

    String getLockedById();

    void setLockedById(String lockedById);

    String getLockedByDisplayName();

    void setLockedByDisplayName(String lockedByDisplayName);

    OffsetDateTime getLockedAt();

    void setLockedAt(OffsetDateTime lockedAt);

    OffsetDateTime getLockExpiresAt();

    void setLockExpiresAt(OffsetDateTime lockExpiresAt);

    int getVersion();
}
