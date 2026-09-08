package com.shortlink.linkapi.validation.impl;

import com.shortlink.linkapi.dto.LinkRequest;
import com.shortlink.linkapi.validation.LinkValidator;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;

import static com.shortlink.linkapi.constant.ReservedCode.RESERVED_CODE_LIST;

@Component
public class LinkValidatorImpl implements LinkValidator {
    @Override
    public void validateRequest(LinkRequest linkRequest) {

        if (RESERVED_CODE_LIST.contains(linkRequest.getCustomCode())) {
            throw new IllegalArgumentException(
                    "Can not create link when custom code is reserved");
        }

        if (linkRequest.getExpiresAt().isBefore(OffsetDateTime.now())) {
            throw new IllegalArgumentException("Can not create link when expiresAt is after now");
        }

    }
}
