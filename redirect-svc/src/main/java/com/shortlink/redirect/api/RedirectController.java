package com.shortlink.redirect.api;

import com.shortlink.redirect.infra.db.LinkReadRepository;
import java.net.URI;
import java.time.Instant;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class RedirectController {

    private final LinkReadRepository repo;

    RedirectController(LinkReadRepository repo) {
        this.repo = repo;
    }

    @GetMapping("/{code:[A-Za-z0-9]{4,12}}")
    public ResponseEntity<Void> redirect(@PathVariable String code) {
        var found = repo.findByCode(code);
        if (found.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        var link = found.get();
        boolean gone = !link.active()
                || (link.expiresAt() != null && link.expiresAt().isBefore(Instant.now()));
        if (gone) {
            return ResponseEntity.status(HttpStatus.GONE).build();
        }
        return ResponseEntity.status(HttpStatus.FOUND)
                .location(URI.create(link.targetUrl()))
                .build();
    }
}
