package com.shortlink.linkapi.utils;

import com.shortlink.linkapi.security.AuthenticatedUser;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.Optional;

public class AuthenticationUtils {

    public static Optional<AuthenticatedUser> getCurrentUser() {

        SecurityContext securityContext = SecurityContextHolder.getContext();

        Authentication authentication = securityContext.getAuthentication();

        if (authentication != null && authentication.getPrincipal() instanceof AuthenticatedUser) {

            return Optional.of((AuthenticatedUser) authentication.getPrincipal());
        }

        return Optional.empty();
    }

    public static Long requiredCurrentUserId() {
        return getCurrentUser()
                .map(AuthenticatedUser::id)
                .orElseThrow(() -> new IllegalStateException("Current user is not authenticated"));

    }
}
