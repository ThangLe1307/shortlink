package com.shortlink.linkapi.service.impl;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.shortlink.linkapi.repository.UserRepository;
import com.shortlink.linkapi.security.AuthenticatedUser;
import com.shortlink.linkapi.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final UserRepository userRepository;

    // local user cache
    private final Cache<String, Optional<AuthenticatedUser>> userCache = Caffeine.newBuilder()
            .expireAfterWrite(Duration.ofSeconds(60))
            .maximumSize(10_000)
            .build();

    @Override
    public Optional<AuthenticatedUser> getAuthenticatedUser(String apiKey) {

        return userCache.get(apiKey, s
                -> userRepository.findByApiKeyHash(s).map(userEntity
                -> new AuthenticatedUser(userEntity.getId(), userEntity.getEmail()))
        );
    }
}
