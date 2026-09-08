INSERT INTO users (email, api_key_hash)
VALUES ('me@test.local', repeat('a', 64));
INSERT INTO links (code, target_url, user_id)
VALUES ('abc1234', 'https://spring.io', 1),
       ('dead999', 'https://example.com', 1);
UPDATE links
SET is_active = false
WHERE code = 'dead999';