package com.tradingreporting.api.dto;

import java.math.BigDecimal;

public record ReportRow(
        String period,
        int tradeCount,
        BigDecimal buyQuantity,
        BigDecimal sellQuantity,
        BigDecimal netQuantity,
        BigDecimal grossNotional) {
}
