import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useEffect, useState } from "react";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { loginSchema, type LoginFormValues } from "../features/auth/schema";
import { financeService } from "../services/financeService";
import { useAuthStore } from "../store/authStore";
import type { AuthResponse } from "../types";
import { extractApiError } from "../utils/apiError";

const guestCredentials: LoginFormValues = {
  email: "guest@demo.com",
  password: "guest123",
};

export const LoginPage = () => {
  const navigate = useNavigate();
  const setSession = useAuthStore((state) => state.setSession);
  const [showWarmupMessage, setShowWarmupMessage] = useState(false);
  const {
    register,
    handleSubmit,
    setValue,
    formState: { errors },
  } = useForm<LoginFormValues>({
    resolver: zodResolver(loginSchema),
    defaultValues: { email: "", password: "" },
  });

  const mutation = useMutation({
    mutationFn: async (values: LoginFormValues) => (await financeService.login(values)).data as AuthResponse,
    onSuccess: (data) => {
      const isGuest = data.user.email.toLowerCase() === guestCredentials.email;
      setSession({ accessToken: data.accessToken, refreshToken: data.refreshToken, user: data.user, isGuest });
      navigate("/");
    },
  });

  const apiError = mutation.isError
    ? extractApiError(mutation.error, "Login failed. Check your credentials and API availability.")
    : null;

  useEffect(() => {
    if (!mutation.isPending) {
      setShowWarmupMessage(false);
      return;
    }

    const timeoutId = window.setTimeout(() => {
      setShowWarmupMessage(true);
    }, 2000);

    return () => {
      window.clearTimeout(timeoutId);
    };
  }, [mutation.isPending]);

  const handleGuestLogin = () => {
    setValue("email", guestCredentials.email, { shouldDirty: true, shouldTouch: true });
    setValue("password", guestCredentials.password, { shouldDirty: true, shouldTouch: true });
    mutation.mutate(guestCredentials);
  };

  return (
    <div className="auth-shell flex min-h-screen items-center justify-center px-4 py-8">
      <div className="auth-card w-full max-w-lg rounded-[34px] border border-line bg-panel p-7 shadow-panel sm:p-10">
        <div className="auth-badge">Finance Tracker</div>
        <h1 className="mt-5 font-display text-4xl leading-tight text-ink sm:text-5xl">Welcome back</h1>
        <p className="mt-4 max-w-md text-base leading-relaxed text-muted">
          Sign in to manage your personal finances across accounts, budgets, goals, and reports.
        </p>

        <form className="mt-8 space-y-5" onSubmit={handleSubmit((values) => mutation.mutate(values))}>
          <div>
            <label className="mb-2 block text-sm font-semibold text-ink">Email</label>
            <input type="email" {...register("email")} />
            {errors.email ? <p className="mt-1 text-sm text-danger">{errors.email.message}</p> : null}
          </div>
          <div>
            <label className="mb-2 block text-sm font-semibold text-ink">Password</label>
            <input type="password" {...register("password")} />
            {errors.password ? <p className="mt-1 text-sm text-danger">{errors.password.message}</p> : null}
            <div className="mt-2 text-right">
              <Link to="/forgot-password" className="text-sm font-semibold text-accent transition hover:text-accent2">
                Forgot Password?
              </Link>
            </div>
          </div>
          {mutation.isPending && showWarmupMessage ? (
            <div className="flex items-start gap-3 rounded-2xl border border-accent/15 bg-gradient-to-r from-accent/10 via-white to-accent2/10 px-4 py-3 text-sm text-ink shadow-sm">
              <span className="mt-0.5 inline-flex h-5 w-5 shrink-0 animate-spin rounded-full border-2 border-accent/25 border-t-accent" />
              <div>
                <p className="font-semibold text-ink">🚀 Starting server (first time may take 50-60 seconds)</p>
                <p className="mt-1 text-xs text-muted">This usually happens when the backend is waking up from idle.</p>
              </div>
            </div>
          ) : null}
          {!mutation.isPending && apiError ? <p className="text-sm text-danger">{apiError.message}</p> : null}
          <button
            type="submit"
            disabled={mutation.isPending}
            className="auth-cta w-full rounded-full bg-accent px-5 py-3 font-semibold text-white transition hover:brightness-110 disabled:opacity-60"
          >
            {mutation.isPending ? "Signing in..." : "Login"}
          </button>
          <button
            type="button"
            title="No signup required"
            disabled={mutation.isPending}
            onClick={handleGuestLogin}
            className="w-full rounded-full border border-line bg-white/80 px-5 py-3 font-semibold text-ink transition hover:border-accent/35 hover:bg-white disabled:cursor-not-allowed disabled:opacity-60"
          >
            {mutation.isPending ? "Signing in..." : "🚀 Try as Guest"}
          </button>
          <p className="text-center text-xs uppercase tracking-[0.14em] text-muted">No signup required</p>
        </form>

        <p className="mt-7 text-sm text-muted">
          No account yet?{" "}
          <Link to="/signup" className="font-semibold text-accent transition hover:text-accent2">
            Create one
          </Link>
        </p>
      </div>
    </div>
  );
};
