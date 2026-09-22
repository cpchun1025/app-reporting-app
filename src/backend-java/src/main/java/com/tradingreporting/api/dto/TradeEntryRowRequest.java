package com.tradingreporting.api.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.util.List;

public record TradeEntryRowRequest(
        String id,
        @NotBlank @Size(min = 1, max = 100) String name,
        @NotBlank @Size(min = 1, max = 30) String code,
        @NotNull @Size(min = 1, max = 50) List<BigDecimal> values,
        boolean locked,
        int version) {

    public TradeEntryRowRequest {
        if (version == 0) {
            version = 1;
        }
    }
}
