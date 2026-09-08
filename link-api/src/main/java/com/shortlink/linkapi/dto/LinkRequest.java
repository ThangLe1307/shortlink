package com.shortlink.linkapi.dto;

import java.time.OffsetDateTime;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import lombok.Getter;
import lombok.NonNull;
import lombok.Setter;

@Getter
@Setter
public class LinkRequest {

  @NotNull
  private String targetUrl;

  @Pattern(regexp = "^[A-Za-z0-9_-]{4,12}$")
  private String customCode;

  private OffsetDateTime expiresAt;

}
