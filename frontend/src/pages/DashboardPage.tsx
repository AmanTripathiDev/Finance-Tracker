import { useMemo } from "react";
import { useQuery } from "@tanstack/react-query";
import { CartesianGrid, Cell, Legend, Line, LineChart, Pie, PieChart, ResponsiveContainer, Tooltip, XAxis, YAxis } from "recharts";
import { Panel } from "../components/Panel";
import { StatsCard } from "../components/StatsCard";
import { financeService } from "../services/financeService";
import { useAuthStore } from "../store/authStore";
import { buildMonthToDateRange } from "../features/reports/dateRange";
import { formatCurrency, formatDate } from "../utils/format";

const chartColors = ["#1f5eff", "#32a0ff", "#12a37d", "#3f67d9", "#5c7bdb", "#92b0ff"];

const insightStyles: Record<string, string> = {
  HIGH: "border-danger/30 bg-danger/10 text-danger",
  MEDIUM: "border-amber-300/50 bg-amber-50 text-amber-700",
  LOW: "border-success/25 bg-success/10 text-success",
  INFO: "border-line/70 bg-white/70 text-muted",
};

const insightAccentStyles: Record<string, string> = {
  HIGH: "border-l-danger",
  MEDIUM: "border-l-amber-500",
  LOW: "border-l-success",
  INFO: "border-l-accent/50",
};

const UpArrowIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-4 w-4">
    <path d="m5 15 7-7 7 7" />
  </svg>
);

const DownArrowIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-4 w-4">
    <path d="m19 9-7 7-7-7" />
  </svg>
);

const WalletIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-4 w-4">
    <path d="M3 7a2 2 0 0 1 2-2h13a3 3 0 0 1 0 6H5a2 2 0 0 1-2-2V7Z" />
    <path d="M3 9v8a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-6" />
  </svg>
);

const BarsIcon = () => (
  <svg viewBox="0 0 24 24" fill="none" stroke="currentColor" strokeWidth="1.8" className="h-4 w-4">
    <path d="M4 19h16M7 15V9m5 6V6m5 9v-4" />
  </svg>
);

const insightTypeIcons: Record<string, string> = {
  SPENDING: "S",
  BUDGET: "B",
  TREND: "T",
  GENERAL: "I",
};

export const DashboardPage = () => {
  const isGuest = useAuthStore((state) => state.isGuest);
  const range = useMemo(() => buildMonthToDateRange(), []);
  const accountsQuery = useQuery({ queryKey: ["accounts"], queryFn: financeService.getAccounts });
  const budgetsQuery = useQuery({ queryKey: ["budgets"], queryFn: financeService.getBudgets });
  const goalsQuery = useQuery({ queryKey: ["goals"], queryFn: financeService.getGoals });
  const recurringQuery = useQuery({ queryKey: ["recurring"], queryFn: financeService.getRecurring });
  const transactionsQuery = useQuery({
    queryKey: ["transactions", "recent"],
    queryFn: () => financeService.getTransactions({ page: 0, size: 5 }),
  });
  const categorySpendQuery = useQuery({
    queryKey: ["reports", "category-spend", range],
    queryFn: () => financeService.getCategorySpend(range),
  });
  const trendQuery = useQuery({
    queryKey: ["reports", "income-expense", range],
    queryFn: () => financeService.getIncomeExpenseTrend(range),
  });
  const insightsQuery = useQuery({
    queryKey: ["reports", "insights"],
    queryFn: financeService.getInsights,
  });
  const predictionQuery = useQuery({
    queryKey: ["reports", "future-balance-prediction"],
    queryFn: financeService.getFutureBalancePrediction,
  });

  const totals = useMemo(() => {
    const items = trendQuery.data ?? [];
    const income = items.reduce((sum, item) => sum + item.income, 0);
    const expense = items.reduce((sum, item) => sum + item.expense, 0);
    return { income, expense, net: income - expense };
  }, [trendQuery.data]);

  const accountBalance = (accountsQuery.data ?? []).reduce((sum, account) => sum + account.currentBalance, 0);
  const topSpending = (categorySpendQuery.data?.items ?? []).slice(0, 5);

  return (
    <div className="space-y-8">
      {isGuest ? (
        <section className="rounded-2xl border border-accent/15 bg-gradient-to-r from-accent/10 via-white to-accent2/10 px-4 py-3 shadow-sm">
          <div className="flex items-center gap-3">
            <span className="inline-flex h-8 w-8 items-center justify-center rounded-full border border-accent/20 bg-white/80 text-sm text-accent">
              🚀
            </span>
            <div>
              <p className="text-sm font-semibold text-ink">You are using a demo account</p>
              <p className="text-xs text-muted">Changes are isolated for exploration and no signup is required.</p>
            </div>
          </div>
        </section>
      ) : null}
      <section className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
        <StatsCard
          label="Month Income"
          value={formatCurrency(totals.income)}
          helper="Total credited in selected month."
          trend="Cash in this month"
          trendTone="positive"
          icon={<UpArrowIcon />}
        />
        <StatsCard
          label="Month Expense"
          value={formatCurrency(totals.expense)}
          helper="Total debited in selected month."
          trend="Outgoing this month"
          trendTone="negative"
          icon={<DownArrowIcon />}
        />
        <StatsCard
          label="Net Balance"
          value={formatCurrency(totals.net)}
          helper="Income minus expense for the current month."
          trend={totals.net >= 0 ? "Positive cashflow" : "Negative cashflow"}
          trendTone={totals.net >= 0 ? "positive" : "negative"}
          icon={<BarsIcon />}
        />
        <StatsCard
          label="All Accounts"
          value={formatCurrency(accountBalance)}
          helper={`${accountsQuery.data?.length ?? 0} tracked accounts`}
          trend="Current portfolio value"
          trendTone="neutral"
          icon={<WalletIcon />}
        />
      </section>

      <Panel title="Future Balance Prediction" description="Estimated balance for the next 30 days using recurring cash flow and average daily spending.">
        <div className="rounded-2xl border border-accent/15 bg-gradient-to-r from-accent/10 via-white/40 to-accent2/10 p-4 dark:border-slate-700/70 dark:from-accent/20 dark:via-slate-900/50 dark:to-accent2/20">
          <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-4">
            <div className="rounded-2xl border border-line/70 bg-white/90 p-4 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700/70 dark:bg-slate-900/75">
              <p className="text-xs font-semibold uppercase tracking-[0.12em] text-muted dark:text-slate-300">Predicted Balance</p>
              <p className="mt-1 text-2xl font-semibold text-ink">{formatCurrency(predictionQuery.data?.predictedBalance ?? accountBalance)}</p>
              <p className="mt-1 text-sm text-muted dark:text-slate-300">In {predictionQuery.data?.horizonDays ?? 30} days</p>
            </div>
            <div className="rounded-2xl border border-line/70 bg-white/90 p-4 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700/70 dark:bg-slate-900/75">
              <p className="text-xs font-semibold uppercase tracking-[0.12em] text-muted dark:text-slate-300">Current Balance</p>
              <p className="mt-1 text-xl font-semibold text-ink">{formatCurrency(predictionQuery.data?.currentBalance ?? accountBalance)}</p>
            </div>
            <div className="rounded-2xl border border-line/70 bg-white/90 p-4 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700/70 dark:bg-slate-900/75">
              <p className="text-xs font-semibold uppercase tracking-[0.12em] text-muted dark:text-slate-300">Recurring Net Impact</p>
              <p className="mt-1 text-xl font-semibold text-ink">{formatCurrency(predictionQuery.data?.projectedRecurringNet ?? 0)}</p>
            </div>
            <div className="rounded-2xl border border-line/70 bg-white/90 p-4 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700/70 dark:bg-slate-900/75">
              <p className="text-xs font-semibold uppercase tracking-[0.12em] text-muted dark:text-slate-300">Avg Daily Spending</p>
              <p className="mt-1 text-xl font-semibold text-ink">{formatCurrency(predictionQuery.data?.averageDailySpending ?? 0)}</p>
            </div>
          </div>
        </div>
      </Panel>

      <Panel title="Smart Insights" description="Actionable highlights based on your budgets and spending trends.">
        <div className="grid gap-4 md:grid-cols-2 xl:grid-cols-3">
          {(insightsQuery.data ?? []).slice(0, 5).map((insight, index) => (
            <article
              key={`${insight.type}-${insight.title}-${index}`}
              className={`rounded-2xl border border-line/70 border-l-4 bg-white/85 p-4 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lift dark:bg-slate-900/70 ${
                insightAccentStyles[insight.severity] ?? insightAccentStyles.INFO
              }`}
            >
              <div className="flex items-center justify-between gap-2">
                <span className="inline-flex items-center gap-2 text-xs font-semibold uppercase tracking-[0.12em] text-muted dark:text-slate-300">
                  <span className="inline-flex h-5 w-5 items-center justify-center rounded-full border border-line/70 bg-white/80 text-[10px] font-bold text-accent dark:border-slate-600 dark:bg-slate-800">
                    {insightTypeIcons[insight.type] ?? "I"}
                  </span>
                  {insight.type}
                </span>
                <span
                  className={`rounded-full border px-2.5 py-1 text-[11px] font-semibold uppercase tracking-[0.06em] ${
                    insightStyles[insight.severity] ?? insightStyles.INFO
                  }`}
                >
                  {insight.severity}
                </span>
              </div>
              <h3 className="mt-2 text-base font-semibold text-ink">{insight.title}</h3>
              <p className="mt-1 text-sm text-muted dark:text-slate-300">{insight.description}</p>
            </article>
          ))}
          {!insightsQuery.data?.length ? <p className="text-sm text-muted dark:text-slate-300">No insights available yet.</p> : null}
        </div>
      </Panel>

      <section className="grid items-start gap-6 xl:grid-cols-[1.4fr_1fr]">
        <Panel title="Spending by Category" description="Current month expense allocation across categories.">
          <div className="grid min-w-0 gap-5 lg:grid-cols-[minmax(0,1fr)_220px]">
            <div className="relative h-80 w-full overflow-hidden rounded-2xl border border-line/70 bg-white/75 p-4 shadow-sm transition-all duration-300 hover:shadow-md dark:border-slate-700/70 dark:bg-slate-900/70">
              <div className="h-full w-full">
                <ResponsiveContainer width="100%" height="100%">
                  <PieChart>
                    <defs>
                      <linearGradient id="donutGlow" x1="0" x2="1" y1="0" y2="1">
                        <stop offset="0%" stopColor="#1f5eff" stopOpacity="0.26" />
                        <stop offset="100%" stopColor="#32a0ff" stopOpacity="0.05" />
                      </linearGradient>
                    </defs>
                    <circle cx="50%" cy="50%" r="56" fill="url(#donutGlow)" />
                    <Pie
                      data={categorySpendQuery.data?.items ?? []}
                      dataKey="amount"
                      nameKey="category"
                      innerRadius="52%"
                      outerRadius="76%"
                      paddingAngle={2}
                      stroke="rgba(255,255,255,0.88)"
                      strokeWidth={3}
                    >
                      {(categorySpendQuery.data?.items ?? []).map((entry, index) => (
                        <Cell key={entry.category} fill={chartColors[index % chartColors.length]} />
                      ))}
                    </Pie>
                    <Tooltip
                      formatter={(value: number) => formatCurrency(value)}
                      contentStyle={{ borderRadius: "12px", borderColor: "rgba(126, 144, 173, 0.28)" }}
                    />
                    <Legend
                      verticalAlign="bottom"
                      iconType="circle"
                      height={26}
                      wrapperStyle={{ fontSize: "12px", color: "#5f6e85", left: 0, right: 0 }}
                    />
                  </PieChart>
                </ResponsiveContainer>
              </div>
              <div className="pointer-events-none absolute inset-0 flex items-center justify-center">
                <div className="rounded-xl bg-white/85 px-3 py-2 text-center shadow-sm dark:bg-slate-900/85">
                  <p className="text-[11px] uppercase tracking-[0.16em] text-muted dark:text-slate-300">Total Spend</p>
                  <p className="mt-0.5 font-display text-xl font-semibold text-ink">{formatCurrency(categorySpendQuery.data?.total ?? 0)}</p>
                </div>
              </div>
            </div>
            <div className="min-w-0 space-y-2.5">
              {topSpending.map((item, index) => (
                <div key={item.category} className="rounded-xl border border-line/60 bg-white/80 px-3 py-2.5 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700/60 dark:bg-slate-900/70">
                  <div className="flex items-center justify-between gap-3">
                    <span className="inline-flex items-center gap-2 text-sm text-ink">
                      <span className="h-2.5 w-2.5 rounded-full" style={{ backgroundColor: chartColors[index % chartColors.length] }} />
                      {item.category}
                    </span>
                    <span className="text-sm font-semibold text-ink">{formatCurrency(item.amount)}</span>
                  </div>
                </div>
              ))}
              {!topSpending.length ? <p className="text-sm text-muted dark:text-slate-300">No spend data yet.</p> : null}
            </div>
          </div>
        </Panel>

        <Panel title="Goal Progress" description="Savings goals and completion pace.">
          <div className="space-y-4">
            {(goalsQuery.data ?? []).slice(0, 4).map((goal) => (
              <div key={goal.id} className="rounded-2xl border border-line/70 bg-white/80 p-4 transition duration-300 hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700/70 dark:bg-slate-900/70">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="font-semibold text-ink">{goal.name}</p>
                    <p className="text-sm text-muted dark:text-slate-300">{goal.targetDate ? `Target ${formatDate(goal.targetDate)}` : "No target date"}</p>
                  </div>
                  <p className="text-sm font-medium text-ink">{goal.progressPercent.toFixed(0)}%</p>
                </div>
                <div className="mt-3 h-2.5 overflow-hidden rounded-full bg-slate-200">
                  <div className="h-full rounded-full bg-gradient-to-r from-accent to-accent2" style={{ width: `${Math.min(goal.progressPercent, 100)}%` }} />
                </div>
              </div>
            ))}
            {!goalsQuery.data?.length ? <p className="text-sm text-muted dark:text-slate-300">No goals yet.</p> : null}
          </div>
        </Panel>
      </section>

      <section className="grid items-start gap-6 xl:grid-cols-[1.4fr_1fr]">
        <Panel title="Income vs Expense Trend" description="Daily totals for the current month.">
          <div className="h-80 w-full overflow-hidden rounded-2xl border border-line/70 bg-white/75 p-4 shadow-sm transition-all duration-300 hover:shadow-md dark:border-slate-700/70 dark:bg-slate-900/70">
            <div className="h-full w-full min-w-0">
              <ResponsiveContainer width="100%" height="100%">
                <LineChart data={trendQuery.data ?? []} margin={{ top: 14, right: 16, left: 4, bottom: 8 }}>
                  <CartesianGrid strokeDasharray="3 3" stroke="rgba(126, 144, 173, 0.18)" />
                  <XAxis dataKey="date" tickLine={false} axisLine={false} />
                  <YAxis tickFormatter={(value) => `$${value}`} tickLine={false} axisLine={false} />
                  <Tooltip
                    formatter={(value: number) => formatCurrency(value)}
                    contentStyle={{ borderRadius: "12px", borderColor: "rgba(126, 144, 173, 0.28)" }}
                  />
                  <Legend verticalAlign="top" iconType="circle" wrapperStyle={{ paddingBottom: "8px", fontSize: "12px" }} />
                  <Line type="monotone" dataKey="income" stroke="#1f5eff" strokeWidth={3} dot={{ r: 2 }} activeDot={{ r: 5 }} />
                  <Line type="monotone" dataKey="expense" stroke="#d55353" strokeWidth={3} dot={{ r: 2 }} activeDot={{ r: 5 }} />
                </LineChart>
              </ResponsiveContainer>
            </div>
          </div>
        </Panel>

        <Panel title="Upcoming Recurring" description="Next scheduled items waiting to post.">
          <div className="space-y-3">
            {(recurringQuery.data ?? []).slice(0, 5).map((item) => (
              <div key={item.id} className="rounded-2xl border border-line/70 bg-white/80 p-4 transition duration-300 hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700/70 dark:bg-slate-900/70">
                <p className="font-semibold text-ink">{item.title}</p>
                <p className="mt-1 text-sm text-muted dark:text-slate-300">
                  {item.frequency} on {formatDate(item.nextRunDate)} from {item.accountName}
                </p>
                <p className="mt-2 text-sm font-medium text-ink">{formatCurrency(item.amount)}</p>
              </div>
            ))}
            {!recurringQuery.data?.length ? <p className="text-sm text-muted dark:text-slate-300">No recurring transactions scheduled.</p> : null}
          </div>
        </Panel>
      </section>

      <section className="grid items-start gap-6 xl:grid-cols-[1.1fr_1fr]">
        <Panel title="Budget Progress" description="Monitor monthly budget utilization and alerts.">
          <div className="space-y-4">
            {(budgetsQuery.data ?? []).slice(0, 5).map((budget) => (
              <div key={budget.id} className="rounded-2xl border border-line/70 bg-white/80 p-4 transition duration-300 hover:-translate-y-0.5 hover:shadow-md dark:border-slate-700/70 dark:bg-slate-900/70">
                <div className="flex items-center justify-between gap-3">
                  <div>
                    <p className="font-semibold text-ink">{budget.categoryName}</p>
                    <p className="text-sm text-muted dark:text-slate-300">
                      {budget.month}/{budget.year} - Alert {budget.alertLevel}
                    </p>
                  </div>
                  <p className="text-sm font-medium text-ink">{formatCurrency(budget.spentAmount)} / {formatCurrency(budget.amount)}</p>
                </div>
                <div className="mt-3 h-2.5 overflow-hidden rounded-full bg-slate-200">
                  <div className="h-full rounded-full bg-gradient-to-r from-accent2 to-accent" style={{ width: `${Math.min(budget.utilizationPercent, 100)}%` }} />
                </div>
              </div>
            ))}
            {!budgetsQuery.data?.length ? <p className="text-sm text-muted dark:text-slate-300">No budgets configured.</p> : null}
          </div>
        </Panel>

        <Panel title="Recent Transactions" description="Latest transaction activity across all accounts.">
          <div className="space-y-3">
            {(transactionsQuery.data?.content ?? []).map((transaction) => (
              <div key={transaction.id} className="rounded-2xl border border-line/70 bg-white/80 p-4 shadow-sm transition-all duration-300 hover:-translate-y-0.5 hover:shadow-lift dark:border-slate-700/70 dark:bg-slate-900/70">
                <div className="flex items-center justify-between gap-4">
                  <div>
                    <p className="font-semibold leading-6 text-ink">{transaction.merchant || transaction.categoryName || transaction.type}</p>
                    <p className="mt-0.5 text-sm text-muted dark:text-slate-300">
                      {transaction.accountName} - {formatDate(transaction.transactionDate)}
                    </p>
                  </div>
                  <p
                    className={`rounded-full border px-3 py-1 text-sm font-semibold ${
                      transaction.type === "INCOME"
                        ? "border-success/25 bg-success/10 text-success"
                        : "border-danger/25 bg-danger/10 text-danger"
                    }`}
                  >
                    {transaction.type === "INCOME" ? "+" : "-"}
                    {formatCurrency(transaction.amount)}
                  </p>
                </div>
              </div>
            ))}
          </div>
        </Panel>
      </section>
    </div>
  );
};
