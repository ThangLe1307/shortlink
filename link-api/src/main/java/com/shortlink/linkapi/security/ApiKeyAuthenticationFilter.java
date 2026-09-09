package com.shortlink.linkapi.security;

import com.shortlink.linkapi.service.ApiKeyService;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.NonNull;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;
import java.util.Optional;

@RequiredArgsConstructor
@Component
public class ApiKeyAuthenticationFilter extends OncePerRequestFilter {

    private final ApiKeyService apiKeyService;

    private static final String BEARER_PREFIX = "Bearer ";

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain filterChain)
            throws ServletException, IOException {

        this.extractApiKey(request)
                .flatMap(apiKeyService::getAuthenticatedUser)
                .ifPresent(this::authenticateUser);


        filterChain.doFilter(request, response);
    }

    Optional<String> extractApiKey(HttpServletRequest request) {

        String apiKey = request.getHeader(HttpHeaders.AUTHORIZATION);

        if (apiKey != null && apiKey.startsWith(BEARER_PREFIX)) {
            return Optional.of(apiKey.substring(BEARER_PREFIX.length()));
        } else {

            return Optional.empty();
        }

    }

    //Authenticate user and save user information to security context
    void authenticateUser(AuthenticatedUser user) {
        UsernamePasswordAuthenticationToken authentication
                = UsernamePasswordAuthenticationToken.authenticated(user, null, List.of());
        SecurityContext context = SecurityContextHolder.createEmptyContext();
        context.setAuthentication(authentication);
        SecurityContextHolder.setContext(context);
    }

}
