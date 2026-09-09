package com.shortlink.linkapi.service;

import com.shortlink.linkapi.security.AuthenticatedUser;

import java.util.Optional;

public interface ApiKeyService {

    Optional<AuthenticatedUser> getAuthenticatedUser(String apiKey);

}
