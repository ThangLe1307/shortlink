package com.shortlink.linkapi.dto;

import lombok.Getter;
import lombok.Setter;

import java.time.OffsetDateTime;

@Getter
@Setter
public class LinkResponse {

    private String code;

    private String shortUrl;

    private String targetUrl;

    private Boolean isActive;

    private OffsetDateTime createdAt;

    private OffsetDateTime expiresAt;

}
