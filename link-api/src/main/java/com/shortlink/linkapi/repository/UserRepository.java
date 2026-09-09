package com.shortlink.linkapi.repository;

import com.shortlink.linkapi.entity.UserEntity;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserRepository extends JpaRepository<UserEntity, Long> {

  Optional<UserEntity> findByApiKeyHash(String apiKeyHash);
}
