package com.tradingreporting.api.dto;

import com.tradingreporting.api.domain.Trade;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;

public record TradeResponse(
        String id,
        LocalDate tradeDate,
        String account,
        String instrument,
        String side,
        BigDecimal quantity,
        BigDecimal price,
        String currency,
        String status,
        String notes,
        int version,
        String createdById,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        boolean locked,
        String lockedById,
        String lockedByDisplayName,
        OffsetDateTime lockedAt,
        OffsetDateTime lockExpiresAt) {

    public static TradeResponse from(Trade trade) {
        return new TradeResponse(
                trade.getId(),
                trade.getTradeDate(),
                trade.getAccount(),
                trade.getInstrument(),
                trade.getSide(),
                trade.getQuantity(),
                trade.getPrice(),
                trade.getCurrency(),
                trade.getStatus(),
                trade.getNotes(),
                trade.getVersion(),
                trade.getCreatedById(),
                trade.getCreatedAt(),
                trade.getUpdatedAt(),
                trade.isLocked(),
                trade.getLockedById(),
                trade.getLockedByDisplayName(),
                trade.getLockedAt(),
                trade.getLockExpiresAt());
    }
}
