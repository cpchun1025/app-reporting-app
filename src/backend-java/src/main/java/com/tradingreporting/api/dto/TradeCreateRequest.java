package com.tradingreporting.api.dto;

import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;

public record TradeCreateRequest(
        @NotNull LocalDate tradeDate,
        @NotBlank @Size(min = 1, max = 100) String account,
        @NotBlank @Size(min = 1, max = 100) String instrument,
        @NotNull @Pattern(regexp = "BUY|SELL") String side,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 4) BigDecimal quantity,
        @NotNull @DecimalMin(value = "0", inclusive = false) @Digits(integer = 14, fraction = 4) BigDecimal price,
        @Size(min = 3, max = 3) String currency,
        @Size(min = 1, max = 20) String status,
        @Size(max = 4000) String notes) {

    public TradeCreateRequest {
        currency = (currency == null || currency.isBlank()) ? "USD" : currency.toUpperCase();
        status = (status == null || status.isBlank()) ? "BOOKED" : status;
    }
}
