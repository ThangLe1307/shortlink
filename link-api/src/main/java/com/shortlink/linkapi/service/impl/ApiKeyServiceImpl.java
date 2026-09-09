package com.shortlink.linkapi.service.impl;

import com.shortlink.linkapi.repository.UserRepository;
import com.shortlink.linkapi.security.AuthenticatedUser;
import com.shortlink.linkapi.service.ApiKeyService;
import lombok.RequiredArgsConstructor;
import org.apache.catalina.User;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
@RequiredArgsConstructor
public class ApiKeyServiceImpl implements ApiKeyService {

    private final UserRepository userRepository;

    @Override
    public Optional<AuthenticatedUser> getAuthenticatedUser(String apiKey) {

        return userRepository.findByApiKeyHash(apiKey)
                .map(
                        userEntity ->
                                new AuthenticatedUser(userEntity.getId(), userEntity.getEmail())
                );
    }
}
