package com.tradingreporting.api.service;

import com.tradingreporting.api.domain.Lockable;
import com.tradingreporting.api.domain.User;
import com.tradingreporting.api.exception.ApiException;
import java.time.OffsetDateTime;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;

class LockSupportTest {

    private final LockSupport lockSupport = new LockSupport();

    @Test
    void ensureUnlockedRejectsAnActiveLockOwnedByAnotherUser() {
        User owner = user("owner", false);
        User currentUser = user("editor", false);
        TestLockable record = lockedBy(owner, OffsetDateTime.now().plusMinutes(1));

        assertThatThrownBy(() -> lockSupport.ensureUnlocked(record, currentUser))
                .isInstanceOf(ApiException.class)
                .extracting(error -> ((ApiException) error).getStatus())
                .isEqualTo(HttpStatus.CONFLICT);
    }

    @Test
    void ensureUnlockedClearsAnExpiredLock() {
        User owner = user("owner", false);
        User currentUser = user("editor", false);
        TestLockable record = lockedBy(owner, OffsetDateTime.now().minusMinutes(1));

        lockSupport.ensureUnlocked(record, currentUser);

        assertThat(record.locked).isFalse();
        assertThat(record.lockedById).isNull();
        assertThat(record.lockedByDisplayName).isNull();
        assertThat(record.lockedAt).isNull();
        assertThat(record.lockExpiresAt).isNull();
    }

    @Test
    void applyLockThenOwnerUnlockRestoresAnUnlockedRecord() {
        User owner = user("owner", false);
        TestLockable record = new TestLockable();

        lockSupport.applyLock(record, owner);

        assertThat(record.locked).isTrue();
        assertThat(record.lockedById).isEqualTo(owner.getId());
        assertThat(record.lockedByDisplayName).isEqualTo("owner");
        assertThat(record.lockedAt).isNotNull();
        assertThat(record.lockExpiresAt).isAfter(record.lockedAt);

        lockSupport.applyUnlock(record, owner, "not used");

        assertThat(record.locked).isFalse();
        assertThat(record.lockedById).isNull();
    }

    private static User user(String username, boolean admin) {
        return new User(username, "hash", admin);
    }

    private static TestLockable lockedBy(User owner, OffsetDateTime expiresAt) {
        TestLockable record = new TestLockable();
        record.locked = true;
        record.lockedById = owner.getId();
        record.lockedByDisplayName = owner.getUsername();
        record.lockedAt = OffsetDateTime.now().minusMinutes(1);
        record.lockExpiresAt = expiresAt;
        return record;
    }

    private static final class TestLockable implements Lockable {
        private boolean locked;
        private String lockedById;
        private String lockedByDisplayName;
        private OffsetDateTime lockedAt;
        private OffsetDateTime lockExpiresAt;

        @Override
        public boolean isLocked() {
            return locked;
        }

        @Override
        public void setLocked(boolean locked) {
            this.locked = locked;
        }

        @Override
        public String getLockedById() {
            return lockedById;
        }

        @Override
        public void setLockedById(String lockedById) {
            this.lockedById = lockedById;
        }

        @Override
        public String getLockedByDisplayName() {
            return lockedByDisplayName;
        }

        @Override
        public void setLockedByDisplayName(String lockedByDisplayName) {
            this.lockedByDisplayName = lockedByDisplayName;
        }

        @Override
        public OffsetDateTime getLockedAt() {
            return lockedAt;
        }

        @Override
        public void setLockedAt(OffsetDateTime lockedAt) {
            this.lockedAt = lockedAt;
        }

        @Override
        public OffsetDateTime getLockExpiresAt() {
            return lockExpiresAt;
        }

        @Override
        public void setLockExpiresAt(OffsetDateTime lockExpiresAt) {
            this.lockExpiresAt = lockExpiresAt;
        }

        @Override
        public int getVersion() {
            return 1;
        }
    }
}
