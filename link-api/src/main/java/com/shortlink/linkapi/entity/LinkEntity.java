package com.shortlink.linkapi.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.validation.constraints.NotNull;
import java.time.OffsetDateTime;

import lombok.*;

@Entity
@Table(name = "links")
@Getter
@Setter
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class LinkEntity {

  @Id
  @GeneratedValue(strategy = GenerationType.AUTO)
  Long id;

  @NotNull
  @Column(name = "code", nullable = false, unique = true, length = 12)
  String code;

  @NotNull
  @Column(name = "target_url", nullable = false)
  private String targetUrl;

  @NotNull
  @Column(name = "is_active", nullable = false)
  private Boolean isActive = true;

  @Column(name = "expires_at")
  private OffsetDateTime expiresAt;

  @Column(name = "created_at", nullable = false, updatable = false)
  @NotNull
  private OffsetDateTime createdAt;

}
