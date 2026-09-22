package com.tradingreporting.api.service;

import com.tradingreporting.api.domain.Trade;
import com.tradingreporting.api.dto.ConsolidatedReport;
import com.tradingreporting.api.dto.ReportRow;
import com.tradingreporting.api.exception.ApiException;
import com.tradingreporting.api.repository.TradeRepository;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Mirrors {@code app.routes_reports}: daily/monthly/annual/consolidated aggregation over trades. */
@Service
public class ReportService {

    public enum Period { DAY, MONTH, YEAR, ACCOUNT }

    private final TradeRepository tradeRepository;

    public ReportService(TradeRepository tradeRepository) {
        this.tradeRepository = tradeRepository;
    }

    @Transactional(readOnly = true)
    public List<ReportRow> dailyReport(LocalDate startDate, LocalDate endDate) {
        return buildReport(startDate, endDate, Period.DAY);
    }

    @Transactional(readOnly = true)
    public List<ReportRow> monthlyReport(LocalDate startDate, LocalDate endDate) {
        return buildReport(startDate, endDate, Period.MONTH);
    }

    @Transactional(readOnly = true)
    public List<ReportRow> annualReport(LocalDate startDate, LocalDate endDate) {
        return buildReport(startDate, endDate, Period.YEAR);
    }

    @Transactional(readOnly = true)
    public ConsolidatedReport consolidatedReport(LocalDate startDate, LocalDate endDate) {
        return new ConsolidatedReport(startDate, endDate, buildReport(startDate, endDate, Period.ACCOUNT));
    }

    private List<ReportRow> buildReport(LocalDate startDate, LocalDate endDate, Period period) {
        validateDateRange(startDate, endDate);
        List<Trade> trades = tradeRepository.findAllByTradeDateBetween(startDate, endDate);

        record Totals(int[] tradeCount, BigDecimal[] buyQuantity, BigDecimal[] sellQuantity, BigDecimal[] grossNotional) {
        }
        Map<String, Totals> totals = new TreeMap<>();
        for (Trade trade : trades) {
            String key = switch (period) {
                case DAY -> trade.getTradeDate().toString();
                case MONTH -> trade.getTradeDate().toString().substring(0, 7);
                case YEAR -> String.valueOf(trade.getTradeDate().getYear());
                case ACCOUNT -> trade.getAccount();
            };
            Totals total = totals.computeIfAbsent(key, k -> new Totals(
                    new int[]{0}, new BigDecimal[]{BigDecimal.ZERO}, new BigDecimal[]{BigDecimal.ZERO},
                    new BigDecimal[]{BigDecimal.ZERO}));
            total.tradeCount()[0]++;
            total.grossNotional()[0] = total.grossNotional()[0].add(trade.getQuantity().multiply(trade.getPrice()));
            if ("BUY".equals(trade.getSide())) {
                total.buyQuantity()[0] = total.buyQuantity()[0].add(trade.getQuantity());
            } else {
                total.sellQuantity()[0] = total.sellQuantity()[0].add(trade.getQuantity());
            }
        }
        List<ReportRow> rows = new java.util.ArrayList<>();
        for (Map.Entry<String, Totals> entry : totals.entrySet()) {
            Totals total = entry.getValue();
            rows.add(new ReportRow(
                    entry.getKey(),
                    total.tradeCount()[0],
                    total.buyQuantity()[0],
                    total.sellQuantity()[0],
                    total.buyQuantity()[0].subtract(total.sellQuantity()[0]),
                    total.grossNotional()[0]));
        }
        return rows;
    }

    private static void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (endDate.isBefore(startDate)) {
            throw new ApiException(HttpStatus.UNPROCESSABLE_ENTITY, "end_date must be on or after start_date.");
        }
    }
}
