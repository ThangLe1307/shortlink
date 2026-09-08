package com.shortlink.linkapi.dto;

import java.time.OffsetDateTime;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class LinkRequest {

  private String url;

  private String customCode;

  private OffsetDateTime expiresAt;

}
