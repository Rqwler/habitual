# Architecture

This document describes how Habitual is put together, why the main decisions were made, and where to extend it.

Habitual is deliberately a **monolith**: one Spring Boot process serves both the REST API and the static web client, backed by one PostgreSQL database. That keeps the project deployable as a single image while leaving clean seams for a richer client, new schedule types, and asynchronous side effects.

---

## 1. System context

```text
┌──────────┐   HTTPS    ┌───────────────┐   HTTP    ┌──────────────────┐
│ Browser  │ ─────────▶ │ Caddy (prod)  │ ────────▶ │ Spring Boot app  │
│ HTML/JS  │            │ TLS + proxy   │           │ /api + static    │
└──────────┘            └───────────────┘           └────────┬─────────┘
                                                             │ JDBC
                                                             ▼
                                                   ┌──────────────────┐
                                                   │ PostgreSQL 16    │
                                                   │ Docker volume    │
                                                   └──────────────────┘
```

| Environment | How it runs |
|---|---|
| Local dev | `docker compose -f docker-compose.dev.yml up -d db`, then `mvn spring-boot:run` |
| Local full stack | `docker compose -f docker-compose.dev.yml --profile app up --build` |
| Production | GitHub Actions builds a GHCR image; EC2 runs `db` + `app` + `caddy` via `docker-compose.yml` |

In production only Caddy publishes ports (`80`, `443`). The app is reachable only on the private Compose network, and PostgreSQL publishes no host port at all.

---

## 2. Code layout

```text
com.habittracker
├─ auth/          Register/login endpoints, token issue, auth DTOs
├─ common/        ApiError, exception types, global handler
├─ config/        SecurityFilterChain, Clock, OpenAPI metadata
├─ event/         HabitCompletedEvent + listener (extension point)
├─ habit/         Habit aggregate, CRUD, check-ins, calendar, metrics
│  ├─ dto/        Request/response records
│  └─ frequency/  Frequency strategy interface + implementations
├─ log/           HabitLog entity + repository
├─ security/      JWT service/filter, UserDetails adapter
├─ stats/         Dashboard and heatmap aggregation
└─ user/          User entity + repository
```

The dependency direction is controller → service → repository. DTOs are Java records, so entities never leave the service layer. `common` contains no business logic.

### Responsibilities

| Component | Responsibility |
|---|---|
| `AuthController` / `AuthService` | Normalize email, enforce uniqueness, BCrypt, issue JWT |
| `JwtAuthenticationFilter` | Parse `Authorization: Bearer`, validate, populate `SecurityContext` |
| `HabitController` / `HabitService` | CRUD, reorder, archive, check-in/undo, calendar |
| `FrequencyStrategyRegistry` | Resolve the strategy for a `FrequencyType` |
| `HabitMetricsService` | Current/longest streak, 30-day completion, week progress |
| `StatsService` | Today/week/month summaries, best streak, heatmap |
| `GlobalExceptionHandler` | Map domain exceptions and validation errors to `ApiError` |
| `HabitCompletionListener` | React to a check-in without coupling it to `HabitService` |

---

## 3. Request lifecycle

### Authentication

```text
POST /api/auth/login
  → AuthService normalizes email
  → AuthenticationManager → DaoAuthenticationProvider → BCrypt compare
  → JwtService.generateToken(email)     (HS256, 24 h default)
  → { token, user }
```

On later requests:

```text
Authorization: Bearer <jwt>
  → JwtAuthenticationFilter
      → JwtService.extractSubject
      → AppUserDetailsService.loadUserByUsername
      → UsernamePasswordAuthenticationToken(UserPrincipal)
  → Controller method receives @AuthenticationPrincipal UserPrincipal
  → Service receives principal.id()
```

If the token is missing or invalid, the filter leaves the context empty and the security chain returns a JSON `401`. There are no server-side sessions; every request is authenticated from the token.

### Check-in

```text
POST /api/habits/{id}/check-ins {"date":"2026-11-14"}
  → findOwned(habitId, userId)              (404 if not owned)
  → reject archived habits
  → reject future dates
  → strategy.isScheduledOn(habit, date)     (400 if not scheduled)
  → insert habit_logs row unless present
  → publish HabitCompletedEvent
  → recalculate metrics
  → { completed, currentStreak, longestStreak, ... }
```

The unique constraint `(habit_id, log_date)` is the final guard against duplicate check-ins; the service also checks first so the normal path returns a friendly “already checked in” response.

---

## 4. Data model

```text
users 1 ──────< habits 1 ──────< habit_logs
  id             id               id
  email (uq)     user_id          habit_id
  display_name   name             log_date
  password_hash  description      note
  created_at     category         created_at
                 frequency_type   uq(habit_id, log_date)
                 times_per_week
                 weekdays
                 priority
                 archived
                 created_at
```

### Tables

| Table | Notes |
|---|---|
| `users` | Email is unique and stored lowercase. `password_hash` holds BCrypt output. |
| `habits` | Owned by a user (`ON DELETE CASCADE`). `weekdays` is a CSV of ISO day numbers (`1` = Monday). `priority` is higher-first. |
| `habit_logs` | One row per completed day. `ON DELETE CASCADE` from habits. Optional note. |

### Constraints and indexes

- `uq_users_email` guarantees account uniqueness.
- `ck_habits_frequency` limits `frequency_type` to `DAILY`, `WEEKLY`, `SPECIFIC_DAYS`.
- `ck_habits_times_per_week` limits the weekly target to 1–7.
- `uq_habit_logs_habit_date` makes double check-ins impossible at the database level.
- `idx_habits_user_priority` supports the default “importance order” listing.
- `idx_habits_user_active` is a partial index for active habits.
- `idx_habit_logs_habit_date` and `idx_habit_logs_date` support history/calendar reads.

Schema changes are **Flyway-only**. Hibernate runs with `ddl-auto: validate`, so a mismatch between an entity and the migration fails startup instead of silently altering production. Add `V2__...sql` for every change.

---

## 5. Frequency and streak model

`HabitFrequencyStrategy` is the main extension point:

```java
public interface HabitFrequencyStrategy {
    FrequencyType type();
    boolean isScheduledOn(Habit habit, LocalDate date);
    List<LocalDate> scheduledDates(Habit habit, LocalDate start, LocalDate end);
    int expectedCompletions(Habit habit, LocalDate start, LocalDate end, LocalDate reference);
    StreakResult calculateStreak(Habit habit, Set<LocalDate> completed, LocalDate reference);
}
```

`FrequencyStrategyRegistry` maps each `FrequencyType` to its Spring bean. Adding “every N days”, “twice a month”, or a Ramadan-style schedule means adding one implementation and one enum value — no changes to controllers, statistics, or the calendar.

### Implementations

| Type | Scheduled on | Streak unit | Rules |
|---|---|---|---|
| `DAILY` | Every day | days | Consecutive checked days. An unchecked *today* does not break the streak until the day ends. |
| `SPECIFIC_DAYS` | Selected ISO weekdays | days | Only scheduled days count. A missed scheduled day in the past breaks the streak; today has end-of-day grace. |
| `WEEKLY` | Any day, target N/week | weeks | Consecutive weeks meeting the target. The current week counts when already met but is not considered failed while in progress. |

### Completion rate

`expectedCompletions` counts what a perfect user would have done in the window. The window starts at the **habit's creation date**, not the beginning of time, so a habit created today is not penalized for last month.

- Daily: number of days in the effective window.
- Specific days: number of matching weekdays in the window.
- Weekly: `min(target, days available in the window)`, so a habit created on Saturday with a 5/week target expects 2, not 5.

The 30-day rate and dashboard periods use the same helper, and the result is capped at 100%.

### Example

A `SPECIFIC_DAYS` habit scheduled Mon/Wed/Fri, checked in Mon and Wed this week and Fri last week:

- current streak: 2 scheduled days (Mon, Wed), because today/Thursday is not scheduled.
- if Friday is skipped, the next calculation sees a missed scheduled day and resets to 0.
- a `DAILY` habit with no check-in today still shows yesterday's streak.

---

## 6. Time and timezone handling

- The server stores `TIMESTAMP WITH TIME ZONE` values in UTC; Hibernate is configured with `hibernate.jdbc.time_zone=UTC`.
- `LocalDate` is used for habit days. The **client sends its local date** on reads and writes (`?date=YYYY-MM-DD`, `{"date":...}`), so a user in UTC+13 sees the correct “today”.
- `APP_TIMEZONE` (default `UTC`) is the server-side fallback when the client omits a date, and it controls future-date validation.
- Future check-ins are rejected. Calendar days are informative and the UI disables future dates.
- `Clock` is a bean (`AppConfig`), which lets tests control “now”.

---

## 7. Security

| Concern | Decision |
|---|---|
| Password storage | BCrypt via Spring Security |
| Tokens | JWT HS256, signed with `JWT_SECRET`, default 24 h expiry |
| Sessions | None; `SessionCreationPolicy.STATELESS` |
| CSRF | Disabled because no cookies are used for auth |
| Authorization | Every habit/log query is scoped by `user_id`; ownership failures return `404` to avoid leaking existence |
| Transport | Caddy terminates TLS and adds HSTS and security headers |
| Actuator | Only `health` and `info` are exposed; health details are hidden |
| Secrets | Environment variables only; production config has no fallbacks |

The web client stores the JWT in `localStorage`, which keeps the project simple but means an XSS bug could exfiltrate the token. Hardening options are documented in the README: HttpOnly `SameSite` cookies, refresh-token rotation, and a strict CSP. The API is stateless, so either approach can be adopted without changing business code.

Not implemented: login rate limiting, account lockout, email verification, password reset, and token revocation. These are appropriate next steps before exposing the app to hostile traffic.

---

## 8. API conventions

- Base path: `/api`.
- JSON only; dates use ISO-8601 (`YYYY-MM-DD`).
- Successful creates return `201`; deletes return `204`; everything else returns `200`.
- Errors use a single shape from `ApiError`:

```json
{
  "timestamp": "2026-11-14T09:15:00Z",
  "status": 400,
  "error": "Bad Request",
  "message": "Future check-ins are not allowed",
  "path": "/api/habits/3/check-ins"
}
```

| Exception | Status |
|---|---|
| `BadRequestException` | 400 |
| `UnauthorizedException` | 401 |
| `ResourceNotFoundException` | 404 |
| `ConflictException` | 409 |
| Bean validation | 400 with field messages |

OpenAPI is generated at runtime: Swagger UI at `/swagger-ui.html`, schema at `/v3/api-docs`.

---

## 9. Frontend

The client is served from `src/main/resources/static` and needs no build step.

| File | Role |
|---|---|
| `index.html` | Login/register screen |
| `app.html` | Dashboard shell: tracker, calendar, stats, modal |
| `js/api.js` | `fetch` wrapper, token storage, error handling, 401 redirect |
| `js/auth.js` | Login/register form logic |
| `js/app.js` | State, rendering, check-ins, calendar, drag-and-drop order |
| `css/styles.css` | Responsive layout, themes, components |

Design notes:

- One in-memory state object (`habits`, selected date, calendar month) and render functions; no framework.
- Drag-and-drop plus accessible up/down buttons; order is persisted via `PUT /api/habits/order`.
- The browser's local date is sent explicitly.
- Toasts and inline errors surface API failures.
- The static assets are cacheable; Spring Boot sets a one-hour cache policy and Caddy adds a one-day cache for `/css/*`, `/js/*`, `/favicon.svg`.

---

## 10. Domain events

`HabitService` publishes `HabitCompletedEvent(habitId, userId, date)` after a **new** check-in. `HabitCompletionListener` currently logs it. This is intentionally the seam for:

- reminder/e-mail jobs,
- badges and milestone awards,
- analytics or activity feeds,
- webhooks.

Because the listener runs inside the transaction by default, a future listener that performs slow I/O should use `@TransactionalEventListener(phase = AFTER_COMMIT)` and/or an outbox table.

---

## 11. Configuration and profiles

| Profile | Use | Datasource | JWT |
|---|---|---|---|
| `dev` (default) | local Maven run | `localhost:5432/habittracker`, `habit/habit` | dev fallback secret |
| `test` | automated tests | H2 in PostgreSQL mode | test secret |
| `prod` | Docker/EC2 | `SPRING_DATASOURCE_*` required | `JWT_SECRET` required |

`application.yml` holds shared settings: Flyway, JPA validation, Jackson, compression, actuator, springdoc. Profile files override only what differs. Production fails fast if a required secret is missing.

---

## 12. Observability

- `/actuator/health` (and liveness/readiness probes) for Docker, Compose, and the deploy workflow.
- `/actuator/info` for build metadata.
- Structured-enough logs to stdout; Docker rotates them (`10 MB × 3` on EC2).
- JaCoCo report at `target/site/jacoco/index.html` after `mvn verify`.

No metrics backend or tracing is wired in; Micrometer is on the classpath through Actuator, so Prometheus can be added later.

---

## 13. Testing strategy

| Layer | Tool | Purpose |
|---|---|---|
| Frequency/streak/metrics units | JUnit 5 + Mockito | Edge cases: grace days, missed days, weekly targets, creation date, rate caps |
| API integration | `@SpringBootTest` + MockMvc + H2 | Register/login, auth failures, CRUD, check-ins, isolation, calendar, reorder |
| Database integration | Testcontainers PostgreSQL + Flyway | Real dialect, constraints, migration validity |
| Style | Spotless (google-java-format) | Consistent formatting, bound to `verify` |
| Coverage | JaCoCo | Line coverage gate of 60% for this mini project |

H2 is used for speed in normal runs. The `-Pit` Maven profile runs `*IT.java` through Failsafe; `HabitApiIT` uses a real PostgreSQL container and is skipped automatically when Docker is unavailable.

---

## 14. Key trade-offs

| Decision | Benefit | Cost |
|---|---|---|
| Monolith + static frontend | One image, simple deploys, no CORS | No independent frontend scaling |
| JWT in `localStorage` | Trivial stateless client | XSS token exposure; no revocation |
| Flyway + `validate` | Predictable schema, safe upgrades | Every entity change needs a migration |
| CSV weekdays column | Simple, readable schema | Not queryable per weekday in SQL |
| Streaks computed on read | No stale counters, easy backfills | O(history) work per habit; fine at this scale |
| H2 for most tests | Fast feedback | Some PostgreSQL behavior only covered by IT |
| Priority as integer | Cheap ordering and drag/drop | Reorder rewrites all active habits |

At a larger scale, streak/statistics would move to cached aggregates or materialized views, and check-ins would be processed asynchronously.

---

## 15. Extension recipes

**New frequency type**

1. Add the enum value in `FrequencyType`.
2. Implement `HabitFrequencyStrategy` and annotate it `@Component`.
3. Update `HabitService.applyFrequency` validation.
4. Add the enum to the database check constraint in a new Flyway migration.
5. Add unit tests for scheduling and streaks; the UI only needs a new form option.

**Reminder e-mails**

Consume `HabitCompletedEvent` or add a scheduled job that queries habits with no log for the local day. Keep delivery outside the request transaction.

**CSV export**

Add `GET /api/stats/export` that streams `habit_logs` joined with `habits` for the current user; the data model already has everything needed.

**Refresh tokens**

Add a `refresh_tokens` table, issue short-lived access tokens, rotate refresh tokens, and move the client to HttpOnly cookies.

---

## Related documents

- [../README.md](../README.md) — features, quick start, API reference
- [DEPLOYMENT.md](DEPLOYMENT.md) — EC2, DNS, TLS, CI/CD, backups, troubleshooting
