package com.tradingreporting.api.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.LocalDate;
import java.util.List;

public record TradeEntrySaveRequest(
        @NotNull LocalDate businessDate,
        @NotNull @Size(min = 1, max = 500) List<@Valid TradeEntryRowRequest> rows) {
}
