import { zodResolver } from "@hookform/resolvers/zod";
import { useMutation } from "@tanstack/react-query";
import { useForm } from "react-hook-form";
import { Link, useNavigate } from "react-router-dom";
import { financeService } from "../services/financeService";
import { useAuthStore } from "../store/authStore";
import type { AuthResponse } from "../types";
import { signupSchema, type SignupFormValues } from "../features/auth/schema";
import { extractApiError } from "../utils/apiError";

export const SignupPage = () => {
  const navigate = useNavigate();
  const setSession = useAuthStore((state) => state.setSession);
  const {
    register,
    handleSubmit,
    formState: { errors },
  } = useForm<SignupFormValues>({
    resolver: zodResolver(signupSchema),
    defaultValues: { displayName: "", email: "", password: "", confirmPassword: "" },
  });

  const mutation = useMutation({
    mutationFn: async (values: SignupFormValues) =>
      (
        await financeService.register({
          displayName: values.displayName,
          email: values.email,
          password: values.password,
        })
      ).data as AuthResponse,
    onSuccess: (data) => {
      setSession({ accessToken: data.accessToken, refreshToken: data.refreshToken, user: data.user });
      navigate("/");
    },
  });

  const apiError = mutation.isError
    ? extractApiError(mutation.error, "Signup failed. Please try again.")
    : null;

  return (
    <div className="auth-shell flex min-h-screen items-center justify-center px-4 py-8">
      <div className="auth-card w-full max-w-2xl rounded-[34px] border border-line bg-panel p-7 shadow-panel sm:p-10">
        <div className="auth-badge">Start Fresh</div>
        <h1 className="mt-5 font-display text-4xl leading-tight text-ink sm:text-5xl">Create your account</h1>
        <p className="mt-4 max-w-lg text-base leading-relaxed text-muted">
          Registration seeds your default income and expense categories automatically.
        </p>

        <form className="mt-8 grid gap-5 sm:grid-cols-2" onSubmit={handleSubmit((values) => mutation.mutate(values))}>
          <div className="sm:col-span-2">
            <label className="mb-2 block text-sm font-semibold text-ink">Display name</label>
            <input {...register("displayName")} />
            {errors.displayName ? <p className="mt-1 text-sm text-danger">{errors.displayName.message}</p> : null}
          </div>
          <div className="sm:col-span-2">
            <label className="mb-2 block text-sm font-semibold text-ink">Email</label>
            <input type="email" {...register("email")} />
            {errors.email ? <p className="mt-1 text-sm text-danger">{errors.email.message}</p> : null}
          </div>
          <div>
            <label className="mb-2 block text-sm font-semibold text-ink">Password</label>
            <input type="password" {...register("password")} />
            {errors.password ? <p className="mt-1 text-sm text-danger">{errors.password.message}</p> : null}
          </div>
          <div>
            <label className="mb-2 block text-sm font-semibold text-ink">Confirm password</label>
            <input type="password" {...register("confirmPassword")} />
            {errors.confirmPassword ? <p className="mt-1 text-sm text-danger">{errors.confirmPassword.message}</p> : null}
          </div>
          <div className="sm:col-span-2">
            {apiError ? <p className="mb-3 text-sm text-danger">{apiError.message}</p> : null}
            <button
              type="submit"
              disabled={mutation.isPending}
              className="auth-cta w-full rounded-full bg-accent px-5 py-3 font-semibold text-white transition hover:brightness-110 disabled:opacity-60"
            >
              {mutation.isPending ? "Creating account..." : "Sign up"}
            </button>
          </div>
        </form>

        <p className="mt-7 text-sm text-muted">
          Already registered?{" "}
          <Link to="/login" className="font-semibold text-accent transition hover:text-accent2">
            Login
          </Link>
        </p>
      </div>
    </div>
  );
};
