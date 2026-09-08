# Flyway Naming Convention

Applies to every SQL file under `deploy/migrations/` and `deploy/seed/`.

Flyway runs as a standalone container (`flyway/flyway:11-alpine`, see `deploy/docker-compose.yml`), not from inside a Spring Boot service. Nothing auto-migrates on application startup — migrations are an explicit deployment step.

---

## 1. Filename grammar

```
V<version>__<description>.sql
```

| Part | Rule |
|---|---|
| `V` | Prefix for a *versioned* migration. Uppercase. `R` for repeatable, `U` for undo — see §3. |
| `<version>` | Plain sequential integer: `1`, `2`, `3`. No padding, no dots, no timestamps. |
| `__` | Exactly **two** underscores. One underscore makes Flyway read the rest as part of the version and fail. |
| `<description>` | `snake_case`, lowercase, ASCII only. |
| `.sql` | Lowercase extension. |

Examples:

```
V1__init.sql
V2__add_click_daily_index.sql
V3__add_link_title.sql
V4__backfill_link_title.sql
```

Wrong, and why:

```
v2__add_index.sql              lowercase prefix — not picked up
V2_add_index.sql               one underscore — version parse error
V2__AddIndex.sql               not snake_case
V2__add index.sql              space in filename
V2.1__add_index.sql            no dotted versions in this project
V2__fix.sql                    description says nothing
V2__add_index_and_seed.sql     two unrelated changes in one file
```

## 2. Writing the description

The description is a **changelog line**, not just a filename. Someone reading `flyway_schema_history` should understand the change without opening the file.

- Start with a verb: `add_`, `drop_`, `rename_`, `backfill_`, `alter_`, `grant_`.
- Name the object: table, column, or index.
- Keep it under roughly 50 characters.

| Instead of | Write |
|---|---|
| `V5__update.sql` | `V5__add_expires_at_index_on_links.sql` |
| `V6__changes.sql` | `V6__drop_unused_referer_column.sql` |
| `V7__fix_bug.sql` | `V7__widen_links_code_to_varchar_16.sql` |

## 3. Prefixes

**`V` — versioned.** The default. Runs exactly once, in version order, recorded in `flyway_schema_history`. All schema changes are `V`.

**`R` — repeatable.** Re-runs whenever its checksum changes, always after all `V` migrations. Reserved for objects that are fully redefined each time — views, functions, triggers. Not currently used in this project.

```
R__view_link_daily_summary.sql
```

Repeatable files have **no version number** and must be idempotent (`CREATE OR REPLACE`).

**`U` — undo.** Not used. Rollback policy is roll-forward: to reverse `V7`, write `V8`. See §5.6.

## 4. Version numbering

Sequential, starting at `1`. The next migration takes the next unused integer.

**Merge collisions.** Two branches will both claim the same next number. Whoever merges second renames their file to the next free number and updates their PR. This is a rebase step, never a reason to reuse or fork a version.

Before opening a PR:

```bash
ls deploy/migrations/
```

The highest number wins; take the next one.

**A version number is permanent once merged to `main`.** Do not renumber a migration that has been applied anywhere — not locally, not on a shared database. Flyway records version and checksum, and both must stay stable.

## 5. Core rules

### 5.1 Never edit an applied migration

Flyway stores a checksum per migration. Changing a file that has already run causes:

```
Migration checksum mismatch for migration version 2
```

Once a migration has run *anywhere* — including one developer's local Postgres — it is frozen. Fix mistakes with a new migration.

The single exception: a migration that has never left your working tree and has never been applied. If in doubt, add a new file.

### 5.2 One logical change per file

A migration is the unit of review and the unit of failure. `V2__add_click_daily_index.sql` fails or succeeds as one comprehensible thing; `V2__add_index_and_rename_column_and_seed.sql` leaves you guessing which statement broke.

Statements that only make sense together belong in the same file — adding a column and its index, creating a table and its `GRANT`. Unrelated changes get separate files.

### 5.3 Do not mix DDL and DML

Schema changes (`CREATE`, `ALTER`, `DROP`) and data changes (`INSERT`, `UPDATE`, `DELETE`) go in separate migrations, even for the same feature.

```
V3__add_link_title.sql        ALTER TABLE links ADD COLUMN title TEXT
V4__backfill_link_title.sql   UPDATE links SET title = ... WHERE title IS NULL
```

DDL is fast and deterministic. A backfill over millions of rows is neither, may need batching, and may need to be re-run. Keeping them apart means a slow backfill never blocks a schema deploy.

### 5.4 Seed data does not belong in `deploy/migrations/`

`deploy/migrations/` is schema only. It runs in every environment, production included.

```
deploy/migrations/   V*__  schema (DDL) — all environments
deploy/seed/               sample data  — local/dev only
```

Seed files are loaded by adding a second location, only in dev:

```
-locations=filesystem:/flyway/sql,filesystem:/flyway/seed
```

Production runs with the default single location and never sees `deploy/seed/`.

Seed files follow the same `V<n>__<description>.sql` grammar, numbered in their own sequence, and should be written idempotently (`ON CONFLICT ... DO NOTHING`) so a re-seed against a dirty local database does not fail.

> `V2__add_sample_links.sql` predates this convention and currently lives in `deploy/migrations/`. It moves to `deploy/seed/` — see §7.

### 5.5 Grants live with the DDL that creates the object

`redirect_ro` is created in `deploy/postgres-init/01-roles.sql` — role creation is cluster setup, not a migration. The privileges that role holds are schema, so they belong in the migration that creates the table:

```sql
CREATE TABLE links (...);
GRANT SELECT ON links TO redirect_ro;
```

A new table that a service must read is not finished until its `GRANT` is in the same migration. Otherwise the schema deploys and the service starts failing with `permission denied`.

### 5.6 Forward-only

No `U__` undo migrations, and no manual `DELETE FROM flyway_schema_history`. To reverse a change, write the next migration:

```
V7__add_links_note_column.sql       shipped, then regretted
V8__drop_links_note_column.sql      the fix
```

For destructive changes, split across releases (expand/contract): stop writing the column, deploy, confirm nothing reads it, then drop it in a later migration.

### 5.7 One transaction per migration

Postgres wraps each migration in a transaction, so a failed migration rolls back and its version is not recorded. Do not defeat this with an explicit `COMMIT` mid-file — a half-applied migration that Flyway believes never ran is the worst state to debug.

## 6. Checklist before merging a migration PR

- [ ] Prefix is uppercase `V`, separator is exactly `__`, extension is `.sql`
- [ ] Version number is the next unused integer in the target directory
- [ ] Description starts with a verb and names the object
- [ ] No existing migration file was modified
- [ ] One logical change; DDL and DML not mixed
- [ ] Schema goes to `deploy/migrations/`, sample data to `deploy/seed/`
- [ ] Any `GRANT` a service needs is included
- [ ] `docker compose up` migrates cleanly from an empty volume

## 7. Outstanding cleanup

`deploy/migrations/V2__add_sample_links.sql` violates §5.3 and §5.4 — it is `INSERT` plus `UPDATE` seed data sitting in the schema directory. It has not been committed yet, so it can still be moved without a checksum problem:

1. Move it to `deploy/seed/V1__sample_links.sql`
2. Add `ON CONFLICT` guards so a re-run against an already-seeded database succeeds
3. Add the `deploy/seed` location to the dev Flyway service in `deploy/docker-compose.yml`
4. `V2` in `deploy/migrations/` is then free for the first real schema change — the two indexes in §3 of `shortlink-be-spec.md`
