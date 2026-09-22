package com.tradingreporting.api.dto;

import java.time.OffsetDateTime;

public record LockResponse(
        String id,
        boolean locked,
        String lockedById,
        String lockedByDisplayName,
        OffsetDateTime lockedAt,
        OffsetDateTime lockExpiresAt,
        int version) {
}
