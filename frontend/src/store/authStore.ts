import { create } from "zustand";
import { persist } from "zustand/middleware";
import type { User } from "../types";

type AuthState = {
  accessToken: string | null;
  refreshToken: string | null;
  user: User | null;
  isGuest: boolean;
  setSession: (payload: { accessToken: string; refreshToken: string; user: User; isGuest?: boolean }) => void;
  clearSession: () => void;
};

export const useAuthStore = create<AuthState>()(
  persist(
    (set) => ({
      accessToken: null,
      refreshToken: null,
      user: null,
      isGuest: false,
      setSession: ({ accessToken, refreshToken, user, isGuest = false }) => set({ accessToken, refreshToken, user, isGuest }),
      clearSession: () => set({ accessToken: null, refreshToken: null, user: null, isGuest: false }),
    }),
    { name: "finance-tracker-auth" },
  ),
);
