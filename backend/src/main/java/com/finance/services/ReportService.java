package com.finance.services;

import com.finance.dto.ReportDtos.AccountBalanceTrendItem;
import com.finance.dto.ReportDtos.CategorySpendResponse;
import com.finance.dto.ReportDtos.FutureBalancePredictionResponse;
import com.finance.dto.ReportDtos.IncomeExpenseTrendItem;
import com.finance.dto.ReportDtos.InsightItem;
import com.finance.dto.ReportDtos.Point;
import com.finance.entities.Budget;
import com.finance.entities.RecurringFrequency;
import com.finance.entities.RecurringTransaction;
import com.finance.entities.Transaction;
import com.finance.entities.TransactionType;
import com.finance.exception.BadRequestException;
import com.finance.repositories.AccountRepository;
import com.finance.repositories.BudgetRepository;
import com.finance.repositories.RecurringTransactionRepository;
import com.finance.repositories.TransactionRepository;
import com.finance.security.CurrentUserService;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class ReportService {

    private final TransactionRepository transactionRepository;
    private final AccountRepository accountRepository;
    private final BudgetRepository budgetRepository;
    private final RecurringTransactionRepository recurringTransactionRepository;
    private final CurrentUserService currentUserService;

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reports", key = "'category-spend:' + @currentUserService.getCurrentUserId() + ':' + T(java.util.Objects).hash(#startDate, #endDate)")
    public CategorySpendResponse categorySpend(LocalDate startDate, LocalDate endDate) {
        UUID userId = currentUserService.getCurrentUserId();
        LocalDate[] range = normalizeRange(startDate, endDate);
        Map<String, BigDecimal> grouped = new LinkedHashMap<>();

        findTransactionsInRange(userId, range[0], range[1]).stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .forEach(tx -> grouped.merge(categoryName(tx), amountOrZero(tx.getAmount()), BigDecimal::add));

        List<Map<String, Object>> items = grouped.entrySet().stream()
                .sorted(Map.Entry.<String, BigDecimal>comparingByValue().reversed())
                .map(entry -> Map.<String, Object>of("category", entry.getKey(), "amount", entry.getValue()))
                .toList();
        BigDecimal total = grouped.values().stream().reduce(BigDecimal.ZERO, BigDecimal::add);
        return new CategorySpendResponse(items, total);
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reports", key = "'income-vs-expense:' + @currentUserService.getCurrentUserId() + ':' + T(java.util.Objects).hash(#startDate, #endDate)")
    public List<IncomeExpenseTrendItem> incomeExpenseTrend(LocalDate startDate, LocalDate endDate) {
        UUID userId = currentUserService.getCurrentUserId();
        LocalDate[] range = normalizeRange(startDate, endDate);
        Map<LocalDate, IncomeExpenseTrendAccumulator> grouped = new LinkedHashMap<>();

        findTransactionsInRange(userId, range[0], range[1]).stream()
                .filter(tx -> tx.getTransactionDate() != null)
                .sorted(Comparator.comparing(Transaction::getTransactionDate))
                .forEach(tx -> {
                    if (tx.getType() == null) {
                        return;
                    }
                    IncomeExpenseTrendAccumulator accumulator = grouped.computeIfAbsent(
                            tx.getTransactionDate(),
                            date -> new IncomeExpenseTrendAccumulator()
                    );
                    BigDecimal amount = amountOrZero(tx.getAmount());
                    if (tx.getType() == TransactionType.INCOME) {
                        accumulator.income = accumulator.income.add(amount);
                    } else if (tx.getType() == TransactionType.EXPENSE) {
                        accumulator.expense = accumulator.expense.add(amount);
                    }
                });

        return grouped.entrySet().stream()
                .map(entry -> new IncomeExpenseTrendItem(entry.getKey(), entry.getValue().income, entry.getValue().expense))
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reports", key = "'account-balance-trend:' + @currentUserService.getCurrentUserId() + ':' + T(java.util.Objects).hash(#startDate, #endDate)")
    public List<AccountBalanceTrendItem> accountBalanceTrend(LocalDate startDate, LocalDate endDate) {
        UUID userId = currentUserService.getCurrentUserId();
        LocalDate[] range = normalizeRange(startDate, endDate);
        return accountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(Objects::nonNull)
                .map(account -> {
                    BigDecimal running = amountOrZero(account.getOpeningBalance());
                    List<Point> points = new ArrayList<>();
                    List<Transaction> transactions = transactionRepository.findByUserIdAndAccountIdOrderByTransactionDateAsc(userId, account.getId());
                    for (Transaction tx : transactions) {
                        if (tx == null || tx.getTransactionDate() == null || tx.getType() == null) {
                            continue;
                        }
                        BigDecimal amount = amountOrZero(tx.getAmount());
                        if (tx.getType() == TransactionType.INCOME || tx.getType() == TransactionType.TRANSFER_IN) {
                            running = running.add(amount);
                        } else {
                            running = running.subtract(amount);
                        }
                        if (!tx.getTransactionDate().isBefore(range[0]) && !tx.getTransactionDate().isAfter(range[1])) {
                            points.add(new Point(tx.getTransactionDate(), running));
                        }
                    }
                    if (points.isEmpty()) {
                        points.add(new Point(range[0], running));
                    }
                    return new AccountBalanceTrendItem(account.getName(), points);
                })
                .toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reports", key = "'insights:' + @currentUserService.getCurrentUserId()")
    public List<InsightItem> insights() {
        UUID userId = currentUserService.getCurrentUserId();
        YearMonth currentMonth = YearMonth.now();
        YearMonth previousMonth = currentMonth.minusMonths(1);
        LocalDate currentStart = currentMonth.atDay(1);
        LocalDate currentEnd = currentMonth.atEndOfMonth();
        LocalDate previousStart = previousMonth.atDay(1);
        LocalDate previousEnd = previousMonth.atEndOfMonth();

        List<Transaction> currentTransactions = findTransactionsInRange(userId, currentStart, currentEnd);

        List<InsightItem> insights = new ArrayList<>();
        addOverspendingInsight(insights, currentTransactions);
        addBudgetInsights(insights, userId, currentMonth, currentStart, currentEnd);
        addMonthOverMonthInsight(insights, userId, currentStart, currentEnd, previousStart, previousEnd);
        ensureMinimumInsights(insights);

        return insights.stream().limit(5).toList();
    }

    @Transactional(readOnly = true)
    @Cacheable(cacheNames = "reports", key = "'future-balance-prediction:' + @currentUserService.getCurrentUserId()")
    public FutureBalancePredictionResponse futureBalancePrediction() {
        UUID userId = currentUserService.getCurrentUserId();
        int horizonDays = 30;
        LocalDate today = LocalDate.now();
        LocalDate horizonEnd = today.plusDays(horizonDays - 1L);

        BigDecimal currentBalance = accountRepository.findByUserIdOrderByCreatedAtDesc(userId).stream()
                .filter(Objects::nonNull)
                .map(account -> amountOrZero(account.getCurrentBalance()))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal projectedRecurringNet = recurringTransactionRepository.findByUserIdOrderByNextRunDateAsc(userId).stream()
                .filter(Objects::nonNull)
                .map(recurring -> projectedRecurringContribution(recurring, today, horizonEnd))
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        LocalDate spendStart = today.minusDays(horizonDays - 1L);
        BigDecimal totalRecentExpense = findTransactionsInRange(userId, spendStart, today).stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        BigDecimal averageDailySpending = totalRecentExpense.divide(BigDecimal.valueOf(horizonDays), 2, RoundingMode.HALF_UP);
        BigDecimal projectedVariableExpense = averageDailySpending.multiply(BigDecimal.valueOf(horizonDays));
        BigDecimal predictedBalance = currentBalance
                .add(projectedRecurringNet)
                .subtract(projectedVariableExpense)
                .setScale(2, RoundingMode.HALF_UP);

        return new FutureBalancePredictionResponse(
                currentBalance.setScale(2, RoundingMode.HALF_UP),
                projectedRecurringNet.setScale(2, RoundingMode.HALF_UP),
                averageDailySpending,
                predictedBalance,
                horizonDays
        );
    }

    private BigDecimal projectedRecurringContribution(RecurringTransaction recurring, LocalDate rangeStart, LocalDate rangeEnd) {
        if (recurring.getNextRunDate() == null || recurring.getType() == null || recurring.getAmount() == null) {
            return BigDecimal.ZERO;
        }
        if (recurring.getNextRunDate().isAfter(rangeEnd)) {
            return BigDecimal.ZERO;
        }
        LocalDate effectiveEnd = recurring.getEndDate() == null || recurring.getEndDate().isAfter(rangeEnd)
                ? rangeEnd
                : recurring.getEndDate();
        if (effectiveEnd.isBefore(rangeStart)) {
            return BigDecimal.ZERO;
        }

        LocalDate runDate = recurring.getNextRunDate();
        while (runDate.isBefore(rangeStart)) {
            runDate = nextRunDate(runDate, recurring.getFrequency());
            if (runDate == null || runDate.isAfter(effectiveEnd)) {
                return BigDecimal.ZERO;
            }
        }

        int occurrences = 0;
        while (!runDate.isAfter(effectiveEnd)) {
            occurrences++;
            runDate = nextRunDate(runDate, recurring.getFrequency());
            if (runDate == null) {
                break;
            }
        }

        BigDecimal signedAmount = switch (recurring.getType()) {
            case INCOME, TRANSFER_IN -> recurring.getAmount();
            case EXPENSE, TRANSFER_OUT -> recurring.getAmount().negate();
        };

        return signedAmount.multiply(BigDecimal.valueOf(occurrences));
    }

    private LocalDate nextRunDate(LocalDate date, RecurringFrequency frequency) {
        if (date == null || frequency == null) {
            return null;
        }
        return switch (frequency) {
            case DAILY -> date.plusDays(1);
            case WEEKLY -> date.plusWeeks(1);
            case MONTHLY -> date.plusMonths(1);
            case YEARLY -> date.plusYears(1);
        };
    }

    private void ensureMinimumInsights(List<InsightItem> insights) {
        List<InsightItem> defaults = List.of(
                new InsightItem(
                        "GENERAL",
                        "No insights yet",
                        "Add more transactions this month to generate personalized insights.",
                        "INFO"
                ),
                new InsightItem(
                        "GENERAL",
                        "Track consistently",
                        "Keep logging expenses weekly for more accurate month-over-month insights.",
                        "INFO"
                ),
                new InsightItem(
                        "GENERAL",
                        "Set category budgets",
                        "Budgets make overspending alerts and insights more useful.",
                        "INFO"
                )
        );

        for (InsightItem fallback : defaults) {
            if (insights.size() >= 3) {
                break;
            }
            boolean exists = insights.stream().anyMatch(item -> item.title().equals(fallback.title()));
            if (!exists) {
                insights.add(fallback);
            }
        }
    }

    private void addOverspendingInsight(List<InsightItem> insights, List<Transaction> transactions) {
        Map<String, BigDecimal> expenseByCategory = new LinkedHashMap<>();
        for (Transaction tx : transactions) {
            if (tx == null || tx.getType() != TransactionType.EXPENSE) {
                continue;
            }
            expenseByCategory.merge(categoryName(tx), amountOrZero(tx.getAmount()), BigDecimal::add);
        }

        expenseByCategory.entrySet().stream()
                .max(Map.Entry.comparingByValue())
                .ifPresent(entry -> insights.add(new InsightItem(
                        "SPENDING",
                        "Top spending category",
                        String.format("%s has the highest spend this month at %s.", entry.getKey(), formatCurrency(entry.getValue())),
                        "MEDIUM"
                )));
    }

    private void addBudgetInsights(
            List<InsightItem> insights,
            UUID userId,
            YearMonth currentMonth,
            LocalDate currentStart,
            LocalDate currentEnd
    ) {
        List<Budget> budgets = budgetRepository.findByUserIdAndMonthAndYear(
                userId,
                currentMonth.getMonthValue(),
                currentMonth.getYear()
        );
        boolean hasRisk = false;
        int riskInsightsAdded = 0;

        for (Budget budget : budgets) {
            if (budget == null || budget.getCategory() == null || budget.getCategory().getId() == null) {
                continue;
            }
            BigDecimal spent = amountOrZero(transactionRepository.sumAmountByUserIdAndCategoryIdAndTypeAndTransactionDateBetween(
                    userId,
                    budget.getCategory().getId(),
                    TransactionType.EXPENSE,
                    currentStart,
                    currentEnd
            ));
            BigDecimal budgetAmount = amountOrZero(budget.getAmount());
            if (budgetAmount.compareTo(BigDecimal.ZERO) <= 0) {
                continue;
            }

            BigDecimal utilization = spent
                    .multiply(BigDecimal.valueOf(100))
                    .divide(budgetAmount, 1, RoundingMode.HALF_UP);

            if (utilization.compareTo(BigDecimal.valueOf(100)) >= 0) {
                hasRisk = true;
                if (riskInsightsAdded < 2) {
                    insights.add(new InsightItem(
                            "BUDGET",
                            "Budget exceeded",
                            String.format(
                                    "%s budget exceeded at %s%% (%s spent).",
                                    budget.getCategory().getName(),
                                    utilization.toPlainString(),
                                    formatCurrency(spent)
                            ),
                            "HIGH"
                    ));
                    riskInsightsAdded++;
                }
            } else if (utilization.compareTo(BigDecimal.valueOf(80)) >= 0) {
                hasRisk = true;
                if (riskInsightsAdded < 2) {
                    insights.add(new InsightItem(
                            "BUDGET",
                            "Budget nearing limit",
                            String.format(
                                    "%s budget is at %s%% (%s of %s).",
                                    budget.getCategory().getName(),
                                    utilization.toPlainString(),
                                    formatCurrency(spent),
                                    formatCurrency(budgetAmount)
                            ),
                            "MEDIUM"
                    ));
                    riskInsightsAdded++;
                }
            }
        }

        if (!budgets.isEmpty() && !hasRisk) {
            insights.add(new InsightItem(
                    "BUDGET",
                    "Budgets are on track",
                    "No category has crossed 80% of its monthly budget yet.",
                    "LOW"
            ));
        }
    }

    private void addMonthOverMonthInsight(
            List<InsightItem> insights,
            UUID userId,
            LocalDate currentStart,
            LocalDate currentEnd,
            LocalDate previousStart,
            LocalDate previousEnd
    ) {
        BigDecimal currentExpense = totalExpenseForRange(userId, currentStart, currentEnd);
        BigDecimal previousExpense = totalExpenseForRange(userId, previousStart, previousEnd);

        if (currentExpense.compareTo(BigDecimal.ZERO) == 0 && previousExpense.compareTo(BigDecimal.ZERO) == 0) {
            return;
        }

        if (previousExpense.compareTo(BigDecimal.ZERO) == 0) {
            insights.add(new InsightItem(
                    "TREND",
                    "Monthly comparison unavailable",
                    "Previous month has no expense data yet.",
                    "INFO"
            ));
            return;
        }

        BigDecimal delta = currentExpense.subtract(previousExpense);
        BigDecimal percentChange = delta
                .multiply(BigDecimal.valueOf(100))
                .divide(previousExpense, 1, RoundingMode.HALF_UP);

        if (delta.compareTo(BigDecimal.ZERO) > 0) {
            insights.add(new InsightItem(
                    "TREND",
                    "Spending increased",
                    String.format("Expenses are up %s%% vs last month.", percentChange.toPlainString()),
                    "MEDIUM"
            ));
        } else if (delta.compareTo(BigDecimal.ZERO) < 0) {
            insights.add(new InsightItem(
                    "TREND",
                    "Spending decreased",
                    String.format("Expenses are down %s%% vs last month.", percentChange.abs().toPlainString()),
                    "LOW"
            ));
        } else {
            insights.add(new InsightItem(
                    "TREND",
                    "Spending is stable",
                    "Expenses are unchanged compared to last month.",
                    "LOW"
            ));
        }
    }

    private BigDecimal totalExpenseForRange(UUID userId, LocalDate startDate, LocalDate endDate) {
        return findTransactionsInRange(userId, startDate, endDate).stream()
                .filter(tx -> tx.getType() == TransactionType.EXPENSE)
                .map(Transaction::getAmount)
                .filter(Objects::nonNull)
                .reduce(BigDecimal.ZERO, BigDecimal::add);
    }

    private List<Transaction> findTransactionsInRange(UUID userId, LocalDate startDate, LocalDate endDate) {
        return transactionRepository.findByUserIdAndTransactionDateBetween(userId, startDate, endDate).stream()
                .filter(Objects::nonNull)
                .toList();
    }

    private String formatCurrency(BigDecimal amount) {
        return "$" + amountOrZero(amount).setScale(2, RoundingMode.HALF_UP).toPlainString();
    }

    private LocalDate[] normalizeRange(LocalDate startDate, LocalDate endDate) {
        if (startDate != null && endDate != null) {
            if (startDate.isAfter(endDate)) {
                throw new BadRequestException("startDate must be on or before endDate");
            }
            return new LocalDate[]{startDate, endDate};
        }
        if (startDate != null) {
            return new LocalDate[]{startDate, startDate};
        }
        if (endDate != null) {
            return new LocalDate[]{endDate, endDate};
        }
        YearMonth current = YearMonth.now();
        return new LocalDate[]{current.atDay(1), current.atEndOfMonth()};
    }

    private BigDecimal amountOrZero(BigDecimal amount) {
        return amount == null ? BigDecimal.ZERO : amount;
    }

    private String categoryName(Transaction tx) {
        if (tx.getCategory() == null || tx.getCategory().getName() == null || tx.getCategory().getName().isBlank()) {
            return "Uncategorized";
        }
        return tx.getCategory().getName();
    }

    private static class IncomeExpenseTrendAccumulator {
        private BigDecimal income = BigDecimal.ZERO;
        private BigDecimal expense = BigDecimal.ZERO;
    }
}
