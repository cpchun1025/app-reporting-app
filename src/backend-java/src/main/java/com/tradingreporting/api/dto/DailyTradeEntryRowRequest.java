package com.tradingreporting.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;

public record DailyTradeEntryRowRequest(
        @NotBlank @Size(min = 1, max = 36) String id,
        @NotNull @Min(1) Integer expectedVersion,
        @NotNull @Digits(integer = 16, fraction = 4) BigDecimal delta,
        @NotNull @Digits(integer = 16, fraction = 4) BigDecimal gamma,
        @NotNull @Digits(integer = 16, fraction = 4) BigDecimal theta,
        @NotNull @Digits(integer = 16, fraction = 4) BigDecimal vega,
        @NotNull @Digits(integer = 16, fraction = 4) BigDecimal pnl) {
}
