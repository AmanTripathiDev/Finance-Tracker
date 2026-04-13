import { useState } from "react";
import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation, useQuery, useQueryClient } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Panel } from "../components/Panel";
import { financeService } from "../services/financeService";
import { transactionSchema, type TransactionFormValues } from "../features/transactions/schema";
import { formatCurrency } from "../utils/format";
import { transactionTypeOptions } from "../utils/constants";
import { formatLocalDateForInput } from "../utils/date";

const defaultTransactionValues = (): TransactionFormValues => ({
  accountId: "",
  categoryId: "",
  type: "EXPENSE",
  amount: 0,
  transactionDate: formatLocalDateForInput(new Date()),
  merchant: "",
  note: "",
  paymentMethod: "",
});

export const TransactionsPage = () => {
  const queryClient = useQueryClient();
  const [page, setPage] = useState(0);
  const [search, setSearch] = useState("");
  const [typeFilter, setTypeFilter] = useState("");
  const [accountFilter, setAccountFilter] = useState("");
  const [categoryFilter, setCategoryFilter] = useState("");
  const [editingId, setEditingId] = useState<string | null>(null);

  const accountsQuery = useQuery({ queryKey: ["accounts"], queryFn: financeService.getAccounts });
  const categoriesQuery = useQuery({ queryKey: ["categories"], queryFn: financeService.getCategories });
  const transactionsQuery = useQuery({
    queryKey: ["transactions", page, search, typeFilter, accountFilter, categoryFilter],
    queryFn: () =>
      financeService.getTransactions({
        page,
        size: 10,
        search: search || undefined,
        type: typeFilter || undefined,
        accountId: accountFilter || undefined,
        categoryId: categoryFilter || undefined,
      }),
  });

  const {
    register,
    handleSubmit,
    reset,
    setValue,
    formState: { errors },
  } = useForm<TransactionFormValues>({
    resolver: zodResolver(transactionSchema),
    defaultValues: defaultTransactionValues(),
  });

  const saveMutation = useMutation({
    mutationFn: (values: TransactionFormValues) => {
      const payload = {
        ...values,
        categoryId: values.categoryId || null,
      };
      return editingId ? financeService.updateTransaction(editingId, payload) : financeService.createTransaction(payload);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      queryClient.invalidateQueries({ queryKey: ["reports"] });
      reset(defaultTransactionValues());
      setEditingId(null);
    },
  });

  const deleteMutation = useMutation({
    mutationFn: (id: string) => financeService.deleteTransaction(id),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ["transactions"] });
      queryClient.invalidateQueries({ queryKey: ["accounts"] });
      queryClient.invalidateQueries({ queryKey: ["reports"] });
    },
  });

  return (
    <div className="grid gap-6 xl:grid-cols-[1fr_1.4fr]">
      <Panel title={editingId ? "Edit Transaction" : "New Transaction"} description="Income and expense entries update account balances immediately.">
        <form
          className="grid gap-4"
          onSubmit={handleSubmit((values) => {
            saveMutation.mutate(values);
          })}
        >
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-2 block text-sm font-medium">Account</label>
              <select {...register("accountId")}>
                <option value="">Select account</option>
                {(accountsQuery.data ?? []).map((account) => (
                  <option key={account.id} value={account.id}>
                    {account.name}
                  </option>
                ))}
              </select>
              {errors.accountId ? <p className="mt-1 text-sm text-danger">{errors.accountId.message}</p> : null}
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium">Type</label>
              <select {...register("type")}>
                {transactionTypeOptions.map((type) => (
                  <option key={type} value={type}>
                    {type}
                  </option>
                ))}
              </select>
            </div>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-2 block text-sm font-medium">Category</label>
              <select {...register("categoryId")}>
                <option value="">Optional category</option>
                {(categoriesQuery.data ?? []).map((category) => (
                  <option key={category.id} value={category.id}>
                    {category.name}
                  </option>
                ))}
              </select>
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium">Amount</label>
              <input type="number" step="0.01" {...register("amount")} />
              {errors.amount ? <p className="mt-1 text-sm text-danger">{errors.amount.message}</p> : null}
            </div>
          </div>
          <div className="grid gap-4 md:grid-cols-2">
            <div>
              <label className="mb-2 block text-sm font-medium">Date</label>
              <input type="date" {...register("transactionDate")} />
            </div>
            <div>
              <label className="mb-2 block text-sm font-medium">Payment Method</label>
              <input placeholder="Card / Bank / Cash" {...register("paymentMethod")} />
            </div>
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium">Merchant</label>
            <input placeholder="Optional merchant name" {...register("merchant")} />
          </div>
          <div>
            <label className="mb-2 block text-sm font-medium">Note</label>
            <textarea rows={4} placeholder="Optional note" {...register("note")} />
          </div>
          {saveMutation.isError ? <p className="text-sm text-danger">Unable to save transaction.</p> : null}
          <div className="flex gap-3">
            <button type="submit" className="rounded-full bg-accent px-5 py-3 font-semibold text-white">
              {saveMutation.isPending ? "Saving..." : editingId ? "Update transaction" : "Create transaction"}
            </button>
            {editingId ? (
              <button
                type="button"
                className="rounded-full bg-white px-5 py-3 font-semibold text-ink"
                onClick={() => {
                  setEditingId(null);
                  reset();
                }}
              >
                Cancel
              </button>
            ) : null}
          </div>
        </form>
      </Panel>

      <Panel title="Transaction History" description="Search, filter, edit, and delete transactions with pagination.">
        <div className="mb-5 grid gap-3 md:grid-cols-4">
          <input placeholder="Search merchant or note" value={search} onChange={(event) => setSearch(event.target.value)} />
          <select value={typeFilter} onChange={(event) => setTypeFilter(event.target.value)}>
            <option value="">All types</option>
            {transactionTypeOptions.map((type) => (
              <option key={type} value={type}>
                {type}
              </option>
            ))}
          </select>
          <select value={accountFilter} onChange={(event) => setAccountFilter(event.target.value)}>
            <option value="">All accounts</option>
            {(accountsQuery.data ?? []).map((account) => (
              <option key={account.id} value={account.id}>
                {account.name}
              </option>
            ))}
          </select>
          <select value={categoryFilter} onChange={(event) => setCategoryFilter(event.target.value)}>
            <option value="">All categories</option>
            {(categoriesQuery.data ?? []).map((category) => (
              <option key={category.id} value={category.id}>
                {category.name}
              </option>
            ))}
          </select>
        </div>

        <div className="overflow-x-auto">
          <table>
            <thead>
              <tr className="border-b border-line text-left text-sm text-muted">
                <th className="pb-3">Date</th>
                <th className="pb-3">Description</th>
                <th className="pb-3">Account</th>
                <th className="pb-3">Type</th>
                <th className="pb-3">Amount</th>
                <th className="pb-3 text-right">Actions</th>
              </tr>
            </thead>
            <tbody>
              {(transactionsQuery.data?.content ?? []).map((transaction) => (
                <tr key={transaction.id} className="border-b border-line/70 align-top">
                  <td className="py-4 text-sm text-muted">{transaction.transactionDate}</td>
                  <td className="py-4">
                    <p className="font-semibold text-ink">{transaction.merchant || transaction.categoryName || transaction.type}</p>
                    <p className="text-sm text-muted">{transaction.note || "No note"}</p>
                  </td>
                  <td className="py-4 text-sm text-muted">{transaction.accountName}</td>
                  <td className="py-4 text-sm text-muted">{transaction.type}</td>
                  <td className={`py-4 font-semibold ${transaction.type === "INCOME" ? "text-success" : "text-danger"}`}>
                    {transaction.type === "INCOME" ? "+" : "-"}
                    {formatCurrency(transaction.amount)}
                  </td>
                  <td className="py-4">
                    <div className="flex justify-end gap-2">
                      <button
                        type="button"
                        className="rounded-full bg-white px-3 py-2 text-sm font-medium text-ink"
                        onClick={() => {
                          setEditingId(transaction.id);
                          setValue("accountId", transaction.accountId);
                          setValue("categoryId", transaction.categoryId ?? "");
                          setValue("type", transaction.type === "INCOME" ? "INCOME" : "EXPENSE");
                          setValue("amount", transaction.amount);
                          setValue("transactionDate", transaction.transactionDate);
                          setValue("merchant", transaction.merchant ?? "");
                          setValue("note", transaction.note ?? "");
                          setValue("paymentMethod", transaction.paymentMethod ?? "");
                        }}
                      >
                        Edit
                      </button>
                      <button
                        type="button"
                        className="rounded-full bg-danger px-3 py-2 text-sm font-medium text-white"
                        onClick={() => deleteMutation.mutate(transaction.id)}
                      >
                        Delete
                      </button>
                    </div>
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          {!transactionsQuery.data?.content.length ? <p className="pt-4 text-sm text-muted">No transactions found.</p> : null}
        </div>

        <div className="mt-5 flex items-center justify-between">
          <p className="text-sm text-muted">
            Page {(transactionsQuery.data?.number ?? 0) + 1} of {transactionsQuery.data?.totalPages ?? 1}
          </p>
          <div className="flex gap-3">
            <button
              type="button"
              disabled={transactionsQuery.data?.first ?? true}
              className="rounded-full bg-white px-4 py-2 text-sm font-medium disabled:opacity-50"
              onClick={() => setPage((value) => Math.max(0, value - 1))}
            >
              Previous
            </button>
            <button
              type="button"
              disabled={transactionsQuery.data?.last ?? true}
              className="rounded-full bg-ink px-4 py-2 text-sm font-medium text-white disabled:opacity-50"
              onClick={() => setPage((value) => value + 1)}
            >
              Next
            </button>
          </div>
        </div>
      </Panel>
    </div>
  );
};
