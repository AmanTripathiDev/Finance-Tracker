package com.finance.security;

import com.finance.exception.UnauthorizedException;
import java.util.UUID;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class CurrentUserService {

    public UUID getCurrentUserId() {
        FinanceUserPrincipal principal = getCurrentUserPrincipal();
        if (principal.userId() == null) {
            throw new UnauthorizedException("Authenticated user is missing an id");
        }
        return principal.userId();
    }

    public FinanceUserPrincipal getCurrentUserPrincipal() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !authentication.isAuthenticated() || authentication instanceof AnonymousAuthenticationToken) {
            throw new UnauthorizedException("Authentication required");
        }
        if (!(authentication.getPrincipal() instanceof FinanceUserPrincipal principal)) {
            throw new UnauthorizedException("Invalid authentication token");
        }
        return principal;
    }
}
