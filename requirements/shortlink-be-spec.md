# ShortLink — Đặc tả Backend

Bản đặc tả để tự code. Mọi quyết định kiến trúc đã chốt ở session trước được giữ nguyên.

- **Stack**: Java 21, Spring Boot 3.5.x, Maven multi-module, Postgres 16, Redis 7, Kafka/Redpanda, Flyway (chạy tách), Testcontainers.
- **Ports**: `redirect-svc` 8080, `link-api` 8081, `analytics-worker` không expose HTTP. Management port của mọi service: 9090.
- **Mục tiêu dự án**: học DevOps. Nên ưu tiên "có thể vận hành, đo được, degrade được" hơn là đầy đủ tính năng.

---

## 1. Phạm vi

### Trong phạm vi

| Nhóm | Nội dung |
|---|---|
| Quản lý link | Tạo (random code hoặc custom), liệt kê, xem chi tiết, sửa, soft-delete |
| Redirect | `GET /{code}` → 302, xử lý expired/inactive/không tồn tại |
| Analytics | Ghi nhận click qua Kafka, tổng hợp theo ngày, API xem thống kê |
| Auth | API key tĩnh, mỗi user một key |
| Chống lạm dụng | Rate limit trên write path, negative caching trên read path |
| Vận hành | Health probes, Prometheus metrics, structured log, graceful shutdown |

### Ngoài phạm vi (cố ý)

Đăng ký/đăng nhập bằng UI, OAuth2/OIDC, multi-tenant, custom domain, QR code, link preview, phân quyền theo role, xoá cứng dữ liệu, GDPR export, phân tích geo/device chi tiết, A/B testing link.

Ba thứ **cấm** vì phá kiến trúc:

1. `redirect-svc` gọi HTTP sang `link-api` (bất kể lý do).
2. `redirect-svc` ghi vào bất kỳ bảng nào.
3. Nhét logic dùng chung vào module `contracts`.

---

## 2. Actor và service

| Actor | Truy cập gì |
|---|---|
| **Người tạo link** (có API key) | `link-api` qua `/api/**` |
| **Người click link** (ẩn danh) | `redirect-svc` qua `/{code}` |
| **Bot/scanner** (không mời) | `redirect-svc` — đây là lý do có negative caching |

| Service | Trách nhiệm | Sở hữu bảng |
|---|---|---|
| `link-api` | CRUD link, auth, rate limit, đọc thống kê | `users`, `links` (ghi) |
| `redirect-svc` | Phân giải code → URL đích, phát event click | không sở hữu gì (chỉ `SELECT links`) |
| `analytics-worker` | Consume click event, dedupe, tổng hợp ngày | `click_events`, `click_daily` (ghi) |

Hai chỗ cắt ranh giới, đã ghi nhận là exception có ý thức: `redirect-svc` đọc `links`, và `link-api` đọc `click_daily`.

---

## 3. Data model

Đây là `V1__init.sql` đã có, cộng thêm những gì cần cho các mốc sau.

```sql
CREATE TABLE users (
  id           BIGSERIAL PRIMARY KEY,
  email        TEXT        NOT NULL UNIQUE,
  api_key_hash CHAR(64)    NOT NULL UNIQUE,   -- sha256 hex, lowercase
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
  event_id    UUID PRIMARY KEY,               -- do redirect-svc sinh
  code        VARCHAR(12) NOT NULL,
  occurred_at TIMESTAMPTZ NOT NULL,
  ip_hash     CHAR(64),                       -- KHÔNG lưu IP thô
  user_agent  TEXT,
  referer     TEXT
);

CREATE TABLE click_daily (
  code  VARCHAR(12) NOT NULL,
  day   DATE        NOT NULL,
  count BIGINT      NOT NULL DEFAULT 0,
  PRIMARY KEY (code, day)
);

GRANT SELECT ON links TO redirect_ro;
```

Bổ sung khi tới mốc tương ứng (mỗi cái một file `V2__`, `V3__`... riêng, không sửa V1):

```sql
-- Cần cho GET /api/links/{code}/stats
CREATE INDEX ix_click_daily_code_day ON click_daily(code, day);

-- Cần cho analytics-worker khi query lại theo thời gian
CREATE INDEX ix_click_events_occurred ON click_events(occurred_at);
```

### Quy tắc trên dữ liệu

- `code` là **immutable**. Không có API nào đổi code của một link đã tạo.
- Xoá link là **soft delete**: `is_active = false`. Không `DELETE FROM links`, vì click_events và click_daily tham chiếu `code` (không phải FK — cố ý, để analytics không chặn được việc xoá link).
- `code` không được tái sử dụng sau khi soft-delete. Unique index đảm bảo điều này tự động.
- `ip_hash = sha256(ip + salt_của_ngày_hôm_nay)`. Salt đổi mỗi ngày nghĩa là không thể correlate một IP xuyên nhiều ngày — vừa đủ để đếm unique/ngày, không đủ để theo dõi người dùng.

---

## 4. Auth

```
Authorization: Bearer sk_live_<43 ký tự base64url>
```

Sinh key: 32 byte từ `SecureRandom` → `Base64.getUrlEncoder().withoutPadding()` → prefix `sk_live_`.

Lưu trong DB: `sha256(toàn bộ chuỗi kể cả prefix)`, hex lowercase. **Không lưu key thô, không log key, không trả lại key sau lần tạo đầu.**

Luồng xác thực (một `OncePerRequestFilter`):

1. Không có header `Authorization` hoặc không bắt đầu bằng `Bearer ` → **401**.
2. Hash phần token → tra `users.api_key_hash`.
3. Không tìm thấy → **401**. Tìm thấy → set `userId` vào request context.
4. Kết quả tra (cả hit và miss) cache trong Caffeine 60 giây, key là hash. Tránh mỗi request một query.

Áp dụng cho toàn bộ `/api/**`. **Không** áp dụng cho `/actuator/**` (port 9090, không expose ra ngoài) và không áp dụng cho `redirect-svc` (public hoàn toàn).

### Tạo user

Không có signup công khai. Hai cách, chọn một:

**Cách A (đơn giản, dùng cho Phase 0–3)**: seed bằng SQL. Sinh key bằng một test hoặc `jshell`, insert hash bằng psql.

**Cách B (khi cần tự động hoá)**: `POST /api/admin/users`, bảo vệ bằng header `X-Bootstrap-Token` so với env `BOOTSTRAP_TOKEN`. Trả về key thô đúng một lần.

```
POST /api/admin/users
X-Bootstrap-Token: <env BOOTSTRAP_TOKEN>
{ "email": "me@test.local" }

201 → { "id": 1, "email": "me@test.local", "apiKey": "sk_live_xxx" }
409 → email đã tồn tại
403 → token sai
```

Đây là auth cố tình thô sơ. Mục tiêu của bạn là DevOps; dựng Keycloak ở đây tốn hai tuần mà không học được gì mới. Khi tới Phase 4 và muốn học secret management thật thì thay bằng OIDC.

---

## 5. `link-api` — API spec

Base: `http://localhost:8081`. Mọi endpoint yêu cầu auth. Mọi lỗi trả `application/problem+json`.

### 5.1 Tạo link

```
POST /api/links
Content-Type: application/json
```

```json
{
  "targetUrl": "https://spring.io/guides",
  "customCode": "spring",
  "expiresAt": "2026-12-31T23:59:59Z"
}
```

**Validation** — vi phạm bất kỳ dòng nào là `400`:

| Field | Bắt buộc | Quy tắc |
|---|---|---|
| `targetUrl` | có | Absolute URL, scheme chỉ `http`/`https`, độ dài ≤ 2048, host phải có dấu `.` hoặc là `localhost`, **không** trùng host của chính ShortLink (chống loop) |
| `customCode` | không | Regex `^[A-Za-z0-9_-]{4,12}$`, không nằm trong danh sách reserved |
| `expiresAt` | không | ISO-8601 có timezone, phải ở tương lai |

Reserved code (không cho custom, vì sẽ đụng path thật hoặc gây nhầm lẫn): `api`, `admin`, `health`, `livez`, `readyz`, `actuator`, `metrics`, `static`, `assets`, `favicon`, `robots`, `sitemap`, `login`, `logout`, `null`, `undefined`.

Chặn scheme khác `http`/`https` là yêu cầu bảo mật, không phải chuyện gọn gàng: `javascript:alert(1)` trong header `Location` là stored XSS ở một số client cũ, còn `data:` và `file:` mở đường cho phishing.

**Xử lý**:

1. Rate limit check (mục 7). Vượt → `429`.
2. Nếu có `customCode`: dùng luôn. Nếu không: sinh 7 ký tự base62 từ `SecureRandom`.
3. `INSERT INTO links`. Bắt `DataIntegrityViolationException` trên `ux_links_code`:
   - Code random → retry, tối đa **3 lần**. Hết 3 lần vẫn conflict → `500` (thực tế không bao giờ xảy ra ở 3.5 nghìn tỷ khả năng; nếu xảy ra thì có bug).
   - Custom code → `409` ngay, không retry.
4. Prewarm cache: `SET link:{code}` với TTL 24h (mục 8).

**Không bao giờ `SELECT` để check code tồn tại trước khi `INSERT`.** Đó là race condition kinh điển; để unique index làm trọng tài.

**Response 201**:

```
Location: /api/links/spring
```
```json
{
  "code": "spring",
  "shortUrl": "http://localhost:8080/spring",
  "targetUrl": "https://spring.io/guides",
  "expiresAt": "2026-12-31T23:59:59Z",
  "isActive": true,
  "createdAt": "2026-09-08T10:30:00Z"
}
```

`shortUrl` ghép từ env `SHORT_BASE_URL` (default `http://localhost:8080`) — không hardcode, vì Phase 5 sẽ có domain thật.

**Lỗi**: `400` validation, `401` auth, `409` custom code đã dùng, `429` rate limit.

### 5.2 Liệt kê link của mình

```
GET /api/links?page=0&size=20&sort=createdAt,desc
```

| Param | Default | Giới hạn |
|---|---|---|
| `page` | 0 | ≥ 0 |
| `size` | 20 | 1–100, vượt thì kẹp về 100 (không phải 400) |
| `sort` | `createdAt,desc` | chỉ cho `createdAt` và `code` |

Chỉ trả link của user gọi. `WHERE user_id = :me` — không phải filter ở tầng app.

**Response 200**:

```json
{
  "content": [
    { "code": "spring", "targetUrl": "https://spring.io/guides",
      "isActive": true, "expiresAt": null, "createdAt": "2026-09-08T10:30:00Z" }
  ],
  "page": 0,
  "size": 20,
  "totalElements": 1,
  "totalPages": 1
}
```

Kẹp `size` ở 100 là chống DoS bằng chính API của mình — `?size=1000000` không được phép làm heap phình lên. Đây đúng là kịch bản "cách ly hư hại" đã nói khi giải thích vì sao tách `link-api` khỏi `redirect-svc`.

### 5.3 Chi tiết một link

```
GET /api/links/{code}
```

**Response 200**:

```json
{
  "code": "spring",
  "targetUrl": "https://spring.io/guides",
  "isActive": true,
  "expiresAt": null,
  "createdAt": "2026-09-08T10:30:00Z",
  "totalClicks": 1423
}
```

`totalClicks` = `SELECT COALESCE(SUM(count),0) FROM click_daily WHERE code = ?`. Đây là chỗ cắt ranh giới thứ hai (đọc bảng của `analytics-worker`) — chấp nhận, nhưng ghi nhớ.

**Lỗi**: `404` nếu code không tồn tại **hoặc thuộc user khác**. Trả `404` chứ không `403` — nếu trả 403 thì bạn vừa để người ngoài dò được code nào đang tồn tại.

### 5.4 Sửa link

```
PATCH /api/links/{code}
```

```json
{ "targetUrl": "https://spring.io/projects/spring-boot", "expiresAt": null, "isActive": false }
```

Cả ba field optional; chỉ update field có mặt. Validation giống mục 5.1. **Không cho sửa `code`.**

Sau khi commit DB: `DEL link:{code}` trong Redis (mục 8). Response `200` với body giống 5.3. Lỗi: `400`, `401`, `404`.

Phân biệt `expiresAt: null` (xoá hạn) với việc không gửi field (giữ nguyên) là một bài toán thật với Jackson. Cách gọn nhất: dùng `JsonNullable` từ `openapi-jackson-nullable`, hoặc nhận `Map<String, Object>` rồi tự kiểm tra `containsKey`. Chọn cách nào cũng được, nhưng phải xử lý — nếu không thì mọi PATCH thiếu `expiresAt` sẽ âm thầm xoá hạn của link.

### 5.5 Xoá link

```
DELETE /api/links/{code}
```

`UPDATE links SET is_active = false WHERE code = ? AND user_id = ?`, rồi `DEL link:{code}`.

**Response `204`** không body. Lỗi: `401`, `404`.

Idempotent: xoá link đã inactive vẫn trả `204`.

### 5.6 Thống kê

```
GET /api/links/{code}/stats?from=2026-09-01&to=2026-09-08
```

| Param | Default | Quy tắc |
|---|---|---|
| `from` | `to` - 29 ngày | `LocalDate` (`yyyy-MM-dd`) |
| `to` | hôm nay (UTC) | `>= from`, khoảng cách ≤ 366 ngày |

**Response 200**:

```json
{
  "code": "spring",
  "from": "2026-09-01",
  "to": "2026-09-08",
  "totalClicks": 1423,
  "daily": [
    { "day": "2026-09-01", "count": 340 },
    { "day": "2026-09-03", "count": 1083 }
  ]
}
```

Ngày không có click thì **không có trong mảng** (không fill 0). Client tự fill nếu cần vẽ chart. Ghi rõ trong doc để không ai nhầm là mất dữ liệu.

Lỗi: `400` (from > to, hoặc khoảng > 366 ngày), `401`, `404`.

---

## 6. `redirect-svc` — API spec

Base: `http://localhost:8080`. **Không auth.** Đây là hot path — thứ bạn sẽ load test và scale.

```
GET /{code}
```

Path variable phải có regex `{code:[A-Za-z0-9_-]{4,12}}` để `/favicon.ico`, `/robots.txt` và mọi đường rác không xuống DB.

### Logic

```
1. Tra Redis: link:{code}
   ├─ HIT (có dữ liệu)     → dùng luôn
   ├─ TOMBSTONE ("!")      → 404, KẾT THÚC (không xuống DB)
   └─ ABSENT (null / lỗi)  → xuống DB
        ├─ không có row    → SET tombstone TTL 60s → 404
        └─ có row          → SET cache TTL 24h → tiếp
2. Kiểm tra trạng thái:
   ├─ !is_active           → 410
   ├─ expires_at đã qua    → 410
   └─ ok                   → tiếp
3. publishAsync(ClickEvent) — fire and forget, lỗi thì bỏ qua
4. 302 + Location: target_url
```

### Response

| Tình huống | Status | Body | Header |
|---|---|---|---|
| Link hợp lệ | `302` | rỗng | `Location`, `Cache-Control: private, max-age=0, no-store` |
| Code không tồn tại | `404` | rỗng | |
| Link inactive hoặc hết hạn | `410` | rỗng | |
| Postgres down và cache miss | `503` | rỗng | `Retry-After: 5` |

Ba chi tiết dễ bỏ qua:

**`Cache-Control: no-store` trên 302 là bắt buộc.** Không có nó, browser và CDN sẽ cache redirect — và khi user sửa `targetUrl` thì click cũ vẫn đi về đích cũ, bạn không có cách nào invalidate. Cũng là lý do dùng `302` (temporary) chứ không `301` (permanent): 301 bị browser cache vĩnh viễn, gần như không xoá được.

**Body rỗng cho mọi lỗi ở service này.** Đây là ngoại lệ so với RFC 7807 dùng ở `link-api` — vì hot path không nên serialize JSON, và người click link là end user chứ không phải developer cần đọc lỗi máy.

**`503` chứ không `500`** khi DB down. `500` nói "tôi có bug", `503` nói "tôi tạm không phục vụ được, thử lại đi" — và `Retry-After` cho client biết chờ bao lâu. Ở Phase 3 khác biệt này quan trọng vì k8s và load balancer xử lý hai status khác nhau.

### Bốn nguyên tắc bất khả xâm phạm

1. **Không bao giờ ghi DB trên hot path.** `UPDATE links SET clicks = clicks + 1` sẽ làm mọi request cùng một link tranh row lock; ở 2000 rps là chết.
2. **Negative caching là bắt buộc**, không optional. Không có tombstone thì bot quét là bạn tự DDoS Postgres của mình.
3. **Kafka chết thì redirect vẫn sống.** `try-catch` quanh publish, tăng counter `shortlink_clicks_dropped_total`, đi tiếp.
4. **Redis chết thì degrade, không chết.** Bắt exception → coi như cache miss → xuống Postgres.

---

## 7. Rate limiting

Chỉ áp trên `POST /api/links` (write path). GET không giới hạn ở giai đoạn này.

| Thuộc tính | Giá trị |
|---|---|
| Thuật toán | Token bucket |
| Giới hạn | 100 request / 60 giây / user |
| Key Redis | `rl:{userId}` |
| Cài đặt | Lua script (atomic), `EVALSHA` |

**Response khi vượt — `429`**:

```
Retry-After: 37
X-RateLimit-Limit: 100
X-RateLimit-Remaining: 0
X-RateLimit-Reset: 1757328000
```

Viết Lua thay vì dùng Bucket4j là cố ý: `GET` rồi `SET` từ phía application là race condition, và với 3 replica thì rate limit sẽ lỏng gấp 3 lần. Tự viết một lần để hiểu vì sao phải atomic.

**Khi Redis down**: cho request đi qua (fail-open), log warn, tăng counter `shortlink_ratelimit_bypassed_total`. Lý do: rate limit là tính năng bảo vệ, không phải tính năng cốt lõi — chặn hết user vì Redis hiccup là tự gây outage. Đây là lựa chọn có trade-off (kẻ tấn công biết cách làm Redis chết sẽ vượt được limit), nên phải có metric và alert.

---

## 8. Redis — key, format, invalidation

**Đây là contract giữa `link-api` và `redirect-svc`.** Hai service ghi/đọc cùng một key, nên format phải khớp chính xác. Sai chỗ này là bug âm thầm, không ai báo lỗi.

| Key | Value | TTL | Ai ghi | Ai đọc |
|---|---|---|---|---|
| `link:{code}` | `{active}\|{expiresAtMillis}\|{targetUrl}` | 24h | cả hai | `redirect-svc` |
| `link:{code}` | `!` (tombstone) | 60s | `redirect-svc` | `redirect-svc` |
| `rl:{userId}` | token bucket state | 60s | `link-api` | `link-api` |

Format value: `active` là `1`/`0`, `expiresAtMillis` là epoch milli hoặc `0` nếu không hết hạn. Ví dụ: `1|0|https://spring.io`.

Chọn string thủ công thay vì JSON vì hot path — Jackson tốn CPU và tạo garbage, payload lớn hơn 3–4 lần. Đổi lại phải cẩn thận khi đổi format: code decode **phải** coi giá trị không parse được là cache miss (rồi đi DB), không được throw. Nhờ vậy bạn deploy được version mới với format mới mà không cần flush Redis.

### Bảng invalidation

| Sự kiện ở `link-api` | Việc phải làm với Redis |
|---|---|
| Tạo link mới | `SET link:{code}` TTL 24h (prewarm) |
| Sửa `targetUrl` / `expiresAt` / `isActive` | `DEL link:{code}` |
| Soft delete | `DEL link:{code}` |

Prewarm lúc tạo không phải để tối ưu — nó **sửa một bug thật**. Kịch bản: ai đó gọi `/spring` trước khi bạn tạo link đó → `redirect-svc` ghi tombstone TTL 60s. Bạn tạo link `spring` ngay sau đó. Nếu chỉ `INSERT` mà không ghi Redis, tombstone vẫn còn → link mới trả 404 trong tối đa một phút. Với URL shortener thì đây là tính năng chính bị vỡ.

Đổi lại, `DEL` khi sửa (thay vì `SET` giá trị mới) là chọn đơn giản và an toàn: request kế tiếp sẽ miss rồi tự nạp lại từ DB. `SET` giá trị mới sẽ nhanh hơn một nhịp nhưng mở ra race giữa `link-api` và `redirect-svc` cùng ghi một key.

**Lỗi Redis ở `link-api` không được làm fail request.** Link đã vào DB rồi; log warn, tăng counter `shortlink_cache_evict_failed_total`, trả `201` bình thường. TTL 24h là cận trên của mức lệch dữ liệu.

---

## 9. Kafka contract

**Topic**: `link.clicks` — 3 partition (local), replication 1 (local) / 3 (prod). Retention 7 ngày.
**DLT**: `link.clicks.DLT`.
**Key**: `code` (string). Cùng một code vào cùng partition → thứ tự đảm bảo trong phạm vi một link, và batch rollup hiệu quả hơn.
**Value**: JSON của record trong module `contracts`:

```java
public record ClickEvent(
        UUID eventId,        // do producer sinh — khoá dedupe
        String code,
        Instant occurredAt,
        String ipHash,       // sha256(ip + daily salt), nullable
        String userAgent,    // cắt còn 512 ký tự
        String referer       // cắt còn 512 ký tự
) {
    public static final String TOPIC = "link.clicks";
}
```

`contracts` chỉ chứa đúng những record như thế này. Sửa nó là trigger build cả 3 service (path filter ở Phase 1 CI), nên đừng nhét entity, util, hay base class vào.

**Producer config** (`redirect-svc`):

```yaml
acks: 1
linger.ms: 20
compression.type: lz4
max.block.ms: 100        # QUAN TRỌNG: default 60s sẽ block thread khi broker down
delivery.timeout.ms: 5000
enable.idempotence: false # bắt buộc false khi acks=1
```

`max.block.ms: 100` là dòng đáng nhớ nhất trong file này. Default 60 giây nghĩa là khi Kafka down, thread xử lý request sẽ đứng một phút — và bạn vừa để analytics (tính năng phụ) làm sập redirect (tính năng core).

`acks=1` là chọn có ý thức: chấp nhận mất event nếu leader chết đúng lúc, để đổi lấy latency thấp trên hot path. Đếm click sai 0.1% không ai chết.

---

## 10. `analytics-worker`

Không có HTTP endpoint (trừ actuator ở 9090). Chỉ consume Kafka.

**Consumer config**:

```yaml
group-id: analytics-worker
enable-auto-commit: false
ack-mode: MANUAL
max-poll-records: 500
auto-offset-reset: earliest
```

**Xử lý mỗi batch**, trong một transaction:

```java
@KafkaListener(topics = ClickEvent.TOPIC, batch = "true")
@Transactional
void onBatch(List<ClickEvent> events, Acknowledgment ack) {
    // 1. Dedupe trong batch (cùng eventId có thể xuất hiện 2 lần trong một poll)
    // 2. INSERT INTO click_events ... ON CONFLICT (event_id) DO NOTHING RETURNING event_id
    // 3. Rollup CHỈ những event_id được RETURNING → group by (code, day)
    // 4. INSERT INTO click_daily ... ON CONFLICT (code, day) DO UPDATE SET count = count + EXCLUDED.count
    ack.acknowledge();   // commit offset SAU KHI DB commit
}
```

Bốn bước, và bước 3 là chỗ tinh tế nhất của cả dự án:

`ON CONFLICT DO NOTHING` làm việc insert raw event idempotent. Nhưng `count = count + EXCLUDED.count` thì **không** idempotent — replay một batch sẽ đếm trùng. Kafka là at-least-once và rebalance chắc chắn sẽ xảy ra, nên đây không phải rủi ro lý thuyết.

Cách xử lý đúng: `RETURNING event_id` cho biết row nào **thực sự** được insert (row trùng không được return), rồi chỉ rollup từ tập đó. Duplicate tự động không được tính.

Nếu thấy phức tạp thì giai đoạn đầu cứ rollup toàn bộ batch và **ghi chú lại** là số liệu xấp xỉ. Điều quan trọng không phải là làm đúng ngay, mà là *biết* mình đang trade cái gì.

**Error handling**: `DefaultErrorHandler` với `FixedBackOff(1000ms, 3)` + `DeadLetterPublishingRecoverer` → `link.clicks.DLT`.

Phân biệt hai loại lỗi: lỗi deserialize (message hỏng, retry vô nghĩa) nên vào DLT ngay — thêm vào `addNotRetryableExceptions`. Lỗi DB tạm thời thì retry có ích. Nếu không phân biệt, một message hỏng sẽ retry mãi và chặn cả partition.

**Metrics phải có**: `shortlink_clicks_processed_total`, `shortlink_clicks_duplicate_total`, `shortlink_clicks_dlt_total`, và consumer lag (Micrometer Kafka binder tự expose `kafka_consumer_fetch_manager_records_lag_max`). Lag là metric để KEDA scale ở Phase 3 và là một trong ba alert của Phase 2.

---

## 11. Format lỗi (`link-api`)

RFC 7807 qua `ProblemDetail` (có sẵn trong Boot 3), một `@RestControllerAdvice` duy nhất.

```json
{
  "type": "https://shortlink.dev/errors/validation-failed",
  "title": "Validation failed",
  "status": 400,
  "detail": "targetUrl must be an absolute http or https URL",
  "instance": "/api/links",
  "errors": [
    { "field": "targetUrl", "message": "must be an absolute http or https URL" }
  ]
}
```

### Catalog

| Status | `type` suffix | Khi nào |
|---|---|---|
| 400 | `validation-failed` | Bean validation fail, param sai |
| 401 | `unauthorized` | Thiếu/sai API key |
| 403 | `forbidden` | Bootstrap token sai |
| 404 | `link-not-found` | Code không tồn tại hoặc của user khác |
| 409 | `code-taken` | Custom code đã dùng |
| 429 | `rate-limit-exceeded` | Vượt token bucket |
| 500 | `internal-error` | Ngoài dự kiến — **detail luôn generic** |
| 503 | `dependency-unavailable` | Postgres không phục vụ được |

Với `500`, `detail` phải là một câu cố định kèm `traceId`, tuyệt đối không chứa message của exception. Stack trace và message của DB (tên bảng, tên cột, giá trị) đi vào log, không đi ra response.

---

## 12. Yêu cầu phi chức năng

### Latency budget

| Đường | p99 mục tiêu | Ghi chú |
|---|---|---|
| `GET /{code}` cache hit | < 5ms | Redis GET + tạo response |
| `GET /{code}` cache miss | < 20ms | + một index lookup Postgres |
| `POST /api/links` | < 100ms | + rate limit + insert + prewarm |
| `GET /api/links/{code}/stats` | < 300ms | Aggregate query |

Baseline hiện tại của bạn (chỉ Postgres, không Redis): **~6300 rps, median 6ms, p99 23ms, bottleneck là Hikari pool 5 connection.** Dùng làm mốc so sánh.

### Degradation matrix

Năm câu này là bài kiểm tra xem bạn đã hiểu kiến trúc của mình chưa. Sau khi code xong, tự verify bằng cách `docker compose stop` từng thứ:

| Thành phần down | `GET /{code}` | `POST /api/links` |
|---|---|---|
| Redis | ✅ 302 (chậm hơn, xuống DB) | ✅ 201 (rate limit fail-open) |
| Kafka | ✅ 302 (mất analytics) | ✅ 201 |
| Postgres | ⚠️ chỉ link đang trong cache | ❌ 503 |
| `link-api` | ✅ 302 (hoàn toàn độc lập) | ❌ |
| `analytics-worker` | ✅ 302 (event dồn trong Kafka) | ✅ 201 |

### Health probes

Cấu hình giống nhau ở cả 3 service, nhưng **nội dung readiness khác nhau**:

| Service | Liveness | Readiness |
|---|---|---|
| `redirect-svc` | process sống | `db` |
| `link-api` | process sống | `db` |
| `analytics-worker` | process sống | `db` + `kafka` |

```yaml
management:
  server.port: 9090
  endpoints.web.exposure.include: health,info,metrics,prometheus
  endpoint.health:
    probes.enabled: true
    group.readiness.include: db      # redirect-svc & link-api
```

**Redis tuyệt đối không nằm trong readiness của `redirect-svc`.** Nếu Redis down mà readiness đỏ, k8s rút toàn bộ pod khỏi service → không ai phục vụ được, dù service vẫn chạy tốt bằng Postgres. Readiness chỉ chứa dependency mà thiếu nó thì pod *thực sự* vô dụng. Đây là một trong những lỗi cấu hình phổ biến nhất và bạn sẽ gặp lại ở Phase 3.

Tương tự, readiness ≠ liveness. Gộp lại thì Redis hiccup 2 giây sẽ làm k8s **restart** pod thay vì chỉ tạm ngừng đưa traffic vào.

### Config — mọi giá trị qua env var

Một image chạy được mọi môi trường. Không có `application-prod.yaml` đóng gói trong jar.

```yaml
# pattern chung
spring.datasource.url: ${DB_URL:jdbc:postgresql://localhost:5432/shortlink}
```

| Env var | Service | Default |
|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | cả 3 | localhost, user tương ứng |
| `REDIS_HOST`, `REDIS_PORT` | redirect-svc, link-api | localhost, 6379 |
| `KAFKA_BOOTSTRAP` | redirect-svc, analytics-worker | localhost:9092 |
| `SHORT_BASE_URL` | link-api | http://localhost:8080 |
| `IP_HASH_SALT` | redirect-svc | (dev default, prod phải set) |
| `BOOTSTRAP_TOKEN` | link-api | (không có default) |

DB user theo service: `redirect_ro` cho `redirect-svc` (pool 5, `read-only: true`), `shortlink` cho hai service còn lại (pool 20).

### Khác

- **Log JSON ra stdout** (`logstash-logback-encoder`), có `traceId`. Không ghi file, không rotate — đó là việc của platform.
- **Graceful shutdown**: `server.shutdown: graceful`, `spring.lifecycle.timeout-per-shutdown-phase: 25s`. Kiểm chứng ở Phase 3 bằng `kubectl delete pod` khi đang load test — không được mất request nào.
- **Virtual threads**: `spring.threads.virtual.enabled: true`. Cả 3 service đều IO-bound thuần.
- **Image**: multi-stage, base `eclipse-temurin:21-jre-alpine`, non-root user, layered jar.

---

## 13. Danh sách metric bắt buộc

Chuẩn bị trước cho Phase 2 — thiếu những cái này thì dashboard không vẽ được gì hay.

| Metric | Type | Tag | Service |
|---|---|---|---|
| `http.server.requests` | timer | tự động (uri, status, method) | tất cả |
| `shortlink.cache` | counter | `result` = hit / miss / tombstone | redirect-svc |
| `shortlink.clicks.published` | counter | | redirect-svc |
| `shortlink.clicks.dropped` | counter | `reason` | redirect-svc |
| `shortlink.links.created` | counter | `type` = random / custom | link-api |
| `shortlink.code.collisions` | counter | | link-api |
| `shortlink.ratelimit.rejected` | counter | | link-api |
| `shortlink.ratelimit.bypassed` | counter | | link-api |
| `shortlink.clicks.processed` | counter | | analytics-worker |
| `shortlink.clicks.duplicate` | counter | | analytics-worker |
| `hikaricp.connections.*` | gauge | tự động | tất cả |

Dùng **một** counter với tag thay vì nhiều counter riêng — đó là cách làm đúng với Prometheus, vì bạn sẽ query `sum(rate(shortlink_cache_total[1m])) by (result)` để ra hit rate trên một panel.

Bật histogram cho HTTP để tính p99 trong Grafana:

```yaml
management.metrics.distribution.percentiles-histogram.http.server.requests: true
```

---

## 14. Thứ tự code và acceptance criteria

Làm theo thứ tự này. Mỗi mốc có tiêu chí "xong" kiểm chứng được — đừng đi tiếp khi chưa đạt.

### M0 — Hạ tầng ✅ (đã xong)
`docker compose up` ra Postgres + Flyway migrate thành công.

### M1 — `redirect-svc` chỉ với Postgres ✅ (đã xong)
`curl` ra `302` / `404` / `410` đúng ba trường hợp. Baseline đã đo: ~6300 rps, p99 23ms.

### M2 — Redis + negative caching
- `GET link:{code}` sau một request thấy đúng format `1|0|https://...`, TTL ~86400.
- Request code không tồn tại → key có value `!`, TTL ~60.
- 5000 request cùng một code → metric `shortlink_cache{result="miss"}` đúng **1**.
- `docker compose stop redis` → vẫn `302`, log có warn, readiness vẫn xanh.
- Đo lại `ab`: rps tăng rõ rệt, `hikaricp_connections_pending` về 0.

### M3 — `link-api`: CRUD + auth
- Thêm `<module>link-api</module>` vào parent pom.
- Tạo user bằng cách A hoặc B, có được một API key.
- `POST /api/links` không header → `401`. Có header sai → `401`.
- Tạo link random → `201`, `code` 7 ký tự. Tạo với `customCode` trùng → `409`.
- `targetUrl: "javascript:alert(1)"` → `400`.
- `GET /api/links` chỉ thấy link của mình (test bằng 2 user).
- `GET /api/links/{code}` với code của user khác → `404` (không phải 403).
- Tạo link rồi `curl localhost:8080/{code}` ngay → `302` (prewarm hoạt động).
- Sửa `targetUrl` rồi curl lại → thấy URL mới ngay (invalidation hoạt động).

### M4 — Rate limit
- 101 request `POST /api/links` trong một phút → request thứ 101 trả `429` có `Retry-After`.
- `docker compose stop redis` → vẫn `201`, counter `bypassed` tăng.
- Chạy 2 instance `link-api` (port khác nhau) → tổng cộng vẫn chỉ 100 request qua được. Nếu qua được 200 thì Lua script chưa atomic hoặc key sai.

### M5 — Kafka + `analytics-worker`
- Thêm Redpanda vào compose, thêm 2 module vào pom.
- Một click → thấy message trong topic (`rpk topic consume link.clicks`).
- `analytics-worker` chạy → `click_events` có row, `click_daily.count` tăng.
- **Test idempotency**: gửi lại cùng một `eventId` → `click_events` không thêm row, `click_daily.count` **không** tăng.
- `docker compose stop redpanda` → `GET /{code}` vẫn `302` trong dưới 200ms (kiểm chứng `max.block.ms`). Counter `clicks_dropped` tăng.
- Gửi một message JSON hỏng → nó vào `link.clicks.DLT`, không chặn partition.
- `GET /api/links/{code}/stats` trả đúng số đã click.

### M6 — Testcontainers integration test
- Test `redirect-svc`: Postgres + Redis container, verify 302/404/410 và tombstone.
- Test `link-api`: verify auth, validation, rate limit, invalidation.
- Test `analytics-worker`: Kafka container, gửi duplicate, verify count đúng.
- `./mvnw verify` từ root chạy sạch trên máy trắng → **sẵn sàng cho Phase 1 CI**.

---

## 15. Những chỗ sẽ sai nếu không cẩn thận

Danh sách để tự review khi code xong mỗi mốc.

**Format Redis lệch giữa hai service.** `link-api` ghi `true|0|url` mà `redirect-svc` đọc `1|0|url` → parse fail âm thầm, mọi request thành cache miss, và bạn chỉ phát hiện khi thấy hit rate = 0 trong Grafana. Cách phòng: đặt encode/decode thành một class dùng chung... nhưng nó không được vào `contracts`. Giải pháp thực dụng: viết một test ở mỗi service assert đúng một chuỗi literal giống nhau. Test fail là biết ngay.

**Quên `DEL` cache khi sửa link.** Link đã sửa vẫn redirect về đích cũ tối đa 24 giờ. Không có exception, không có log — bug im lặng nhất trong hệ thống này.

**`SELECT` trước `INSERT` khi sinh code.** Race condition; hai request đồng thời cùng ra một code, một cái fail sau khi đã trả 201 cho user.

**Tombstone chặn link mới.** Nếu bỏ prewarm ở `POST /api/links`, custom code từng bị query trước đó sẽ 404 trong một phút.

**`ON CONFLICT DO UPDATE count = count + ...` mà không lọc qua `RETURNING`.** Đếm trùng khi Kafka rebalance.

**Redis trong readiness probe.** Phase 3 sẽ dạy bạn bài này bằng một outage tự gây.

**`max.block.ms` để default.** Kafka down làm hot path treo 60 giây mỗi request.

**Exception message của DB lọt vào response 500.** Lộ tên bảng, tên cột, có khi cả giá trị.

**Component scan nuốt bean của nhau.** Mỗi app phải một base package riêng (`com.shortlink.linkapi`, `...redirect`, `...analytics`) — nếu trùng thì lúc test khi chúng cùng trên classpath sẽ lẫn bean.

**`spring-boot-maven-plugin` trong `contracts`.** Nó repackage thành fat jar, class bị đẩy vào `BOOT-INF/classes/`, và service phụ thuộc sẽ `ClassNotFoundException`. Lỗi số 1 của multi-module Spring Boot, rất khó đoán.

---

## 16. Đọc lại khi bí

Câu hỏi thiết kế nào chưa rõ thì quay về nguyên tắc gốc:

1. **Redirect là core, mọi thứ khác là phụ.** Nghi ngờ thì chọn phương án giữ redirect sống.
2. **Hot path không ghi, không serialize JSON, không gọi network trừ Redis và (async) Kafka.**
3. **Mỗi dependency đều sẽ chết. Bạn phải biết trước lúc đó chuyện gì xảy ra.**
4. **Contract giữa service không chỉ là API** — nó còn là DB permission (`GRANT SELECT`), là Redis key format, là Kafka schema. Cả bốn đều phải được giữ có ý thức.
