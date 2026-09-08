package com.shortlink.linkapi.repository;

import com.shortlink.linkapi.entity.LinkEntity;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LinkRepository extends JpaRepository<LinkEntity, Long> {
}
