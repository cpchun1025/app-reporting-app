package com.tradingreporting.api.dto;

import java.time.LocalDate;
import java.util.List;

public record DailyTradeEntryListResponse(LocalDate businessDate, List<DailyTradeEntryResponse> rows) {
}
