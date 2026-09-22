package com.tradingreporting.api.dto;

import com.tradingreporting.api.domain.DailyTradeEntry;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record DailyTradeEntryResponse(
        String id,
        String businessId,
        LocalDate businessDate,
        String name,
        String code,
        BigDecimal delta,
        BigDecimal gamma,
        BigDecimal theta,
        BigDecimal vega,
        BigDecimal pnl,
        int version,
        boolean locked,
        String lockedById,
        String lockedByDisplayName,
        OffsetDateTime lockedAt,
        OffsetDateTime lockExpiresAt,
        OffsetDateTime updatedAt) {

    public static DailyTradeEntryResponse from(DailyTradeEntry entry) {
        return new DailyTradeEntryResponse(
                entry.getId(),
                entry.getBusiness().getId(),
                entry.getBusinessDate(),
                entry.getBusiness().getName(),
                entry.getBusiness().getCode(),
                entry.getDelta(),
                entry.getGamma(),
                entry.getTheta(),
                entry.getVega(),
                entry.getPnl(),
                entry.getVersion(),
                entry.isLocked(),
                entry.getLockedById(),
                entry.getLockedByDisplayName(),
                entry.getLockedAt(),
                entry.getLockExpiresAt(),
                entry.getUpdatedAt());
    }
}
