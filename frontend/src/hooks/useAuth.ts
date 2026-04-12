import { useMemo } from "react";
import { useAuthStore } from "../store/authStore";

export const useAuth = () => {
  const accessToken = useAuthStore((state) => state.accessToken);
  const refreshToken = useAuthStore((state) => state.refreshToken);
  const user = useAuthStore((state) => state.user);
  const isGuest = useAuthStore((state) => state.isGuest);
  const clearSession = useAuthStore((state) => state.clearSession);
  const setSession = useAuthStore((state) => state.setSession);

  return useMemo(
    () => ({
      accessToken,
      refreshToken,
      user,
      isGuest,
      isAuthenticated: Boolean(accessToken && user),
      clearSession,
      setSession,
    }),
    [accessToken, clearSession, isGuest, refreshToken, setSession, user],
  );
};
