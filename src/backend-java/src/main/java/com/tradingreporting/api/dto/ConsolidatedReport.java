package com.tradingreporting.api.dto;

import java.time.LocalDate;
import java.util.List;

public record ConsolidatedReport(LocalDate startDate, LocalDate endDate, List<ReportRow> rows) {
}
