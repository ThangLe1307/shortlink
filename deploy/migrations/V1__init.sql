CREATE TABLE users (
  id           BIGSERIAL PRIMARY KEY,
  email        TEXT      NOT NULL UNIQUE,
  api_key_hash CHAR(64)  NOT NULL UNIQUE,
  created_at   TIMESTAMPTZ NOT NULL DEFAULT now()
);

CREATE TABLE links (
  id         BIGSERIAL PRIMARY KEY,
  code       VARCHAR(12) NOT NULL,
  target_url TEXT        NOT NULL,
  user_id    BIGINT      NOT NULL REFERENCES users(id),
  expires_at TIMESTAMPTZ,
  is_active  BOOLEAN     NOT NULL DEFAULT true,
  created_at TIMESTAMPTZ NOT NULL DEFAULT now()
);
CREATE UNIQUE INDEX ux_links_code ON links(code);
CREATE INDEX ix_links_user ON links(user_id, created_at DESC);

CREATE TABLE click_events (
  event_id    UUID PRIMARY KEY,
  code        VARCHAR(12) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  ip_hash     CHAR(64),
  user_agent  TEXT,
  referer     TEXT
);

CREATE TABLE click_daily (
  code  VARCHAR(12) NOT NULL,
  day   DATE        NOT NULL,
  count BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (code, day)
);

-- redirect-svc chỉ được đọc đúng một bảng
GRANT SELECT ON links TO redirect_ro;
