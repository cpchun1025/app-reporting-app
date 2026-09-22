package com.tradingreporting.api.dto;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

public record DailyTradeEntrySaveResponse(OffsetDateTime savedAt, LocalDate businessDate, List<DailyTradeEntryResponse> rows) {
}
