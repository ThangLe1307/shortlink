package com.shortlink.redirect.infra.db;

import java.time.Instant;
import java.util.Optional;
import org.springframework.jdbc.core.simple.JdbcClient;
import org.springframework.stereotype.Repository;

@Repository
public class LinkReadRepository {

    public record LinkView(String code, String targetUrl, Instant expiresAt, boolean active) {}

    private final JdbcClient jdbc;

    LinkReadRepository(JdbcClient jdbc) {
        this.jdbc = jdbc;
    }

    public Optional<LinkView> findByCode(String code) {
        return jdbc.sql("""
                    SELECT code, target_url, expires_at, is_active AS active
                    FROM links
                    WHERE code = :code
                    """)
                .param("code", code)
                .query(LinkView.class)
                .optional();
    }
}
