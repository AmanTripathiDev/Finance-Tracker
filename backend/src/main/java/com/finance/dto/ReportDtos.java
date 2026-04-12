package com.finance.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;

public class ReportDtos {

    public record CategorySpendResponse(
            List<Map<String, Object>> items,
            @Schema(example = "1240.50")
            BigDecimal total
    ) {
    }

    public record IncomeExpenseTrendItem(
            @Schema(example = "2026-04-12")
            LocalDate date,
            @Schema(example = "800.00")
            BigDecimal income,
            @Schema(example = "125.00")
            BigDecimal expense
    ) {
    }

    public record AccountBalanceTrendItem(
            String accountName,
            List<Point> points
    ) {
    }

    public record Point(
            @Schema(example = "2026-04-12")
            LocalDate date,
            @Schema(example = "2450.75")
            BigDecimal balance
    ) {
    }

    public record InsightItem(
            @Schema(example = "SPENDING")
            String type,
            @Schema(example = "Dining is up 18%")
            String title,
            @Schema(example = "You spent more on restaurants this month than last month.")
            String description,
            @Schema(example = "HIGH")
            String severity
    ) {
    }

    public record FutureBalancePredictionResponse(
            @Schema(example = "2450.75")
            BigDecimal currentBalance,
            @Schema(example = "-120.00")
            BigDecimal projectedRecurringNet,
            @Schema(example = "34.25")
            BigDecimal averageDailySpending,
            @Schema(example = "2330.75")
            BigDecimal predictedBalance,
            @Schema(example = "30")
            Integer horizonDays
    ) {
    }
}
