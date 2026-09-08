package com.shortlink.linkapi.validation;


import com.shortlink.linkapi.dto.LinkRequest;

public interface LinkValidator {
    void validateRequest(LinkRequest linkRequest);
}
