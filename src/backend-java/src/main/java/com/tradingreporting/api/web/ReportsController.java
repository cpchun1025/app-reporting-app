package com.tradingreporting.api.web;

import com.tradingreporting.api.dto.ConsolidatedReport;
import com.tradingreporting.api.dto.ReportRow;
import com.tradingreporting.api.exception.UnprocessableEntityException;
import com.tradingreporting.api.service.ReportService;
import java.time.LocalDate;
import java.util.List;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Mirrors {@code app.routes_reports}. Query parameters use the same snake_case names as the
 * Python backend (start_date, end_date, group_by) so both APIs share the same client contract. */
@RestController
@RequestMapping("/reports")
public class ReportsController {

    private final ReportService reportService;

    public ReportsController(ReportService reportService) {
        this.reportService = reportService;
    }

    @GetMapping("/daily")
    public List<ReportRow> daily(@RequestParam("start_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                  @RequestParam("end_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.dailyReport(startDate, endDate);
    }

    @GetMapping("/monthly")
    public List<ReportRow> monthly(@RequestParam("start_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                    @RequestParam("end_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.monthlyReport(startDate, endDate);
    }

    @GetMapping("/annual")
    public List<ReportRow> annual(@RequestParam("start_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                   @RequestParam("end_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate) {
        return reportService.annualReport(startDate, endDate);
    }

    @GetMapping("/consolidated")
    public ConsolidatedReport consolidated(@RequestParam("start_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
                                            @RequestParam("end_date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate,
                                            @RequestParam(value = "group_by", defaultValue = "account") String groupBy) {
        if (!"account".equals(groupBy)) {
            throw new UnprocessableEntityException("Input should be 'account'");
        }
        return reportService.consolidatedReport(startDate, endDate);
    }
}
