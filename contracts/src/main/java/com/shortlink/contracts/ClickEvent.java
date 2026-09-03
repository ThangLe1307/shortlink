package com.shortlink.contracts;

import java.time.Instant;
import java.util.UUID;

public record ClickEvent(
        UUID eventId,
        String code,
        Instant occurredAt,
        String ipHash,
        String userAgent,
        String referer
) {
    public static final String TOPIC = "link.clicks";
}
