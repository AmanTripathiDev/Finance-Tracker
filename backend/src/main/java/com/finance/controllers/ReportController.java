package com.finance.controllers;

import com.finance.dto.ReportDtos.AccountBalanceTrendItem;
import com.finance.dto.ReportDtos.CategorySpendResponse;
import com.finance.dto.ReportDtos.FutureBalancePredictionResponse;
import com.finance.dto.ReportDtos.IncomeExpenseTrendItem;
import com.finance.dto.ReportDtos.InsightItem;
import com.finance.exception.BadRequestException;
import com.finance.services.ReportService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import java.time.LocalDate;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/reports")
@RequiredArgsConstructor
@Tag(name = "Reports", description = "Dashboard analytics, spending reports, and insights")
public class ReportController {

    private final ReportService reportService;

    @GetMapping("/category-spend")
    @Operation(summary = "Spending by category for a date range")
    public CategorySpendResponse categorySpend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);
        return reportService.categorySpend(startDate, endDate);
    }

    @GetMapping("/income-vs-expense")
    @Operation(summary = "Daily income versus expense trend")
    public List<IncomeExpenseTrendItem> incomeVsExpense(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);
        return reportService.incomeExpenseTrend(startDate, endDate);
    }

    @GetMapping("/account-balance-trend")
    @Operation(summary = "Running balance trend by account")
    public List<AccountBalanceTrendItem> accountBalanceTrend(
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate startDate,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate endDate
    ) {
        validateDateRange(startDate, endDate);
        return reportService.accountBalanceTrend(startDate, endDate);
    }

    @GetMapping("/insights")
    @Operation(summary = "Generated spending and budgeting insights")
    public List<InsightItem> insights() {
        return reportService.insights();
    }

    @GetMapping("/future-balance-prediction")
    @Operation(summary = "Projected balance for the next 30 days")
    public FutureBalancePredictionResponse futureBalancePrediction() {
        return reportService.futureBalancePrediction();
    }

    private void validateDateRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null && startDate.isAfter(endDate)) {
            throw new BadRequestException("startDate must be on or before endDate");
        }
    }
}
