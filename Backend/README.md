# AI-Powered Student Result Intelligence Platform

A monolithic Spring Boot 3 application that ingests exam results from CSV,
computes analytics on them, and turns those analytics into written feedback for
three different audiences using the Claude API.

Java 21 · Spring Boot 3.5 · Spring Security + JWT · Spring Batch · Flyway ·
H2 (file-backed) · official Anthropic Java SDK.

---

## Running it

You need a JDK 21 or newer and nothing else. Maven does not have to be
installed — the project ships the Maven Wrapper, which downloads the right
version on first use.

```bash
# Windows
.\mvnw.cmd package
java -jar target\student-result-intelligence-1.0.0.jar

# macOS / Linux
./mvnw package
java -jar target/student-result-intelligence-1.0.0.jar
```

The app listens on <http://localhost:8080>. If that port is already in use, add
`--server.port=8099`. Nothing else needs installing: the database is a file
under `./data`, created and migrated by Flyway on first start.

To enable real AI generation, set the key before starting:

```bash
export ANTHROPIC_API_KEY=sk-ant-...      # macOS/Linux
$env:ANTHROPIC_API_KEY = "sk-ant-..."    # PowerShell
```

**Without a key the application still works end to end.** Every feedback
endpoint returns real, data-grounded feedback produced by a local rule-based
writer, and the response is labelled `"source": "FALLBACK"` so it is never
mistaken for model output. See [Claude integration](#claude-integration).

Run the tests with `.\mvnw.cmd test` (76 tests, including three integration
suites that boot the whole context against an in-memory database and drive the
upload endpoint over HTTP).

### Demo logins

Created on first start. Password for all four: `Passw0rd!`

| Username   | Role    | Notes                                  |
|------------|---------|----------------------------------------|
| `admin`    | ADMIN   | Everything                             |
| `teacher1` | TEACHER | Staff `T-100`, teaches all class-10 subjects |
| `student1` | STUDENT | Linked to admission `STU1001`          |
| `parent1`  | PARENT  | Linked to `STU1001` only               |

Set `app.demo.seed-users=false` to stop creating them.

---

## A five-minute walkthrough

```bash
# Log in as the teacher
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"teacher1","password":"Passw0rd!"}' | jq -r .accessToken)

# Upload a term of results (returns 202 + an upload job id)
curl -s -X POST http://localhost:8080/api/uploads \
  -H "Authorization: Bearer $TOKEN" \
  -F file=@src/main/resources/samples/results-UT1-2025.csv

# Poll the import
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/uploads/1 | jq

# Upload a second exam so trends have something to compare
curl -s -X POST http://localhost:8080/api/uploads \
  -H "Authorization: Bearer $TOKEN" \
  -F file=@src/main/resources/samples/results-MID-2025.csv

# Find the exam ids
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/reference/exams | jq

# Class rankings and the teacher's exam overview
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/analytics/class/10/exams/1/rankings | jq
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/analytics/class/10/exams/1 | jq

# Teacher feedback for the class
curl -s -H "Authorization: Bearer $TOKEN" \
  http://localhost:8080/api/feedback/teacher/class/10/exams/1 | jq

# Now as the student
STOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"student1","password":"Passw0rd!"}' | jq -r .accessToken)

curl -s -H "Authorization: Bearer $STOKEN" \
  http://localhost:8080/api/analytics/me/exams/1/report | jq
curl -s -H "Authorization: Bearer $STOKEN" \
  http://localhost:8080/api/analytics/me/trend | jq
curl -s -H "Authorization: Bearer $STOKEN" \
  http://localhost:8080/api/feedback/me/exams/1 | jq
```

`src/main/resources/samples/results-with-errors.csv` is a deliberately broken
file: upload it to see the per-line rejection report.

The H2 console is at <http://localhost:8080/h2-console> (JDBC URL
`jdbc:h2:file:./data/srip`, user `sa`, no password).

---

## Project structure

```
src/main/java/com/srip/
├── StudentResultIntelligenceApplication.java
├── config/          # Security, Batch, Cache, typed @ConfigurationProperties, demo seeding
├── security/        # JwtService, JWT filter, UserDetails, JSON 401/403 handlers
├── domain/          # JPA entities
├── repository/      # Spring Data repositories + aggregate projections
├── dto/             # Request/response records, grouped by use case
│   ├── auth/  analytics/  result/  ai/  admin/
├── analytics/       # GradingService, RankingService, WeaknessDetector, TrendAnalyzer
├── ai/              # ClaudeClient, PromptFactory, FallbackFeedbackWriter
├── batch/           # CSV reader/processor/writer, listeners, tasklet
├── service/         # Orchestration: Auth, Analytics, ClassInsight, Feedback, Upload, AccessGuard
├── controller/      # REST controllers
└── exception/       # Typed exceptions + @RestControllerAdvice

src/main/resources/
├── application.yml
├── db/migration/    # V1 schema, V2 reference data, V3 Spring Batch tables
└── samples/         # Example CSVs, including one full of errors
```

It is a single deployable unit on purpose. One database, one transaction
manager, one process: the analytics engine reads the same tables the importer
writes, and the AI layer reads the analytics directly rather than over a
network hop.

---

## Database schema

Flyway owns the schema (`spring.jpa.hibernate.ddl-auto=none`). The SQL is
portable ANSI, so the same migrations run on MySQL or PostgreSQL unchanged.

| Table              | Purpose |
|--------------------|---------|
| `users`            | Logins, one of four roles |
| `refresh_tokens`   | SHA-256 hashes of issued refresh tokens, with revocation |
| `students`         | Student records; `user_id` is nullable so results can load before logins exist |
| `teachers`         | Staff records |
| `parent_students`  | Which children a parent may read — the PARENT authorisation boundary |
| `subjects`, `topics` | Curriculum |
| `teacher_subjects` | Which class/subject combinations a teacher owns |
| `exams`            | The exam calendar; `exam_date` is what orders trends |
| `exam_results`     | One mark per student/exam/subject, unique on that triple |
| `topic_scores`     | Per-topic breakdown inside one paper — what makes weak-topic detection possible |
| `upload_jobs`      | One row per CSV upload, from PENDING to a terminal status |
| `upload_errors`    | Every rejected row with its line number and reason |
| `ai_feedback`      | Generated documents as JSON, with model, source and token counts |

`percentage` and `grade` are stored on `exam_results` rather than derived on
read: rankings and class averages sort and aggregate over them, and recomputing
them per request would mean scanning the whole class every time.

Spring Batch's `BATCH_*` metadata tables are created by migration `V3`, copied
verbatim from `spring-batch-core`'s own `schema-h2.sql`, with
`spring.batch.jdbc.initialize-schema=never`. Letting Boot create them is a trap
worth knowing about: its `embedded` mode only recognises an *in-memory* H2 URL,
so with a file-backed database the tables are silently never created and the
first job launch fails on missing SQL grammar. Owning the schema in Flyway makes
it explicit, version controlled, and identical across H2, MySQL and PostgreSQL.

---

## Authentication

Role-based login issuing a JWT access token and a refresh token.

- **Access token** — HS256 JWT, 30 minutes, carries `uid` and `role`. Verifying
  one is a signature check, no database hit.
- **Refresh token** — an opaque 384-bit random string, 7 days. Only its SHA-256
  hash is stored, so a database dump cannot be replayed against the API.
- **Rotation** — presenting a refresh token revokes it and issues a replacement.
  A token used twice is the signature of a replay, and the second attempt fails.
  Presenting an already-revoked token revokes every session for that account.
- **Logout** is idempotent and revokes all of the account's refresh tokens.

Access control has two layers, because one is not enough:

1. **URL rules** (`SecurityConfig`) — coarse role gates, e.g. only TEACHER and
   ADMIN may upload or read class-wide analytics. A ranking table names every
   child in the class, so it is staff-only.
2. **`AccessGuard`** — row-level checks that a URL cannot express: a student may
   read only their own record; a parent only the children in `parent_students`;
   staff may read any student. Every student-scoped service call goes through
   it, so a new endpoint cannot forget the check — it has to ask for the student
   id, and asking runs the check.

| Endpoint | STUDENT | PARENT | TEACHER | ADMIN |
|---|:-:|:-:|:-:|:-:|
| `POST /api/auth/login`, `/refresh`, `/logout` | ✅ | ✅ | ✅ | ✅ |
| `POST /api/auth/register` | — | — | — | ✅ |
| `POST /api/uploads` | — | — | ✅ | ✅ |
| `GET /api/analytics/me/**` | ✅ | — | — | — |
| `GET /api/analytics/students/{id}/**` | own only | linked children | ✅ | ✅ |
| `GET /api/analytics/class/**` | — | — | ✅ | ✅ |
| `GET /api/feedback/me/**`, `/student/{id}/**` | own only | linked children | ✅ | ✅ |
| `GET /api/feedback/parent/{id}/**` | own only | linked children | ✅ | ✅ |
| `GET /api/feedback/teacher/**` | — | — | ✅ | ✅ |
| `/api/admin/**` | — | — | — | ✅ |

---

## CSV upload and the batch job

### Format

```
admission_no,exam_code,subject_code,marks_obtained,max_marks,attempted,remarks,topic_breakdown
STU1001,UT1-2025,MATH,78,100,true,,Algebra:12/25|Geometry:22/25|Trigonometry:23/25|Statistics:21/25
STU1001,UT1-2025,ENG,88,100,true,,
```

The first five columns are required; `attempted`, `remarks` and
`topic_breakdown` are optional. `topic_breakdown` is `Topic:got/max` entries
separated by `|`, and topic names may contain spaces and hyphens
(`Physics - Optics:20/25`).

### Flow

`POST /api/uploads` stores the file, creates an `upload_jobs` row, launches the
job asynchronously, and returns **202** with the job id. The client polls
`GET /api/uploads/{id}`.

The file is written to disk rather than streamed from the request body. That
decouples the two: the HTTP call returns as soon as the bytes are safe, and a
failed job can be re-run against the same file without a re-upload.

### The job — `resultImportJob`

1. **`validateCsvHeaderStep`** (tasklet) — checks the header and fails the whole
   job once if the columns are wrong. Getting 900 identical errors because two
   columns were swapped tells the uploader nothing.
2. **`importResultsStep`** (chunked, 50 rows per transaction) — read → validate
   → write, fault tolerant.

Validation runs at three levels: column presence and numeric format; referential
existence of the admission number, exam code, subject code and topic names; and
arithmetic sanity — marks cannot exceed the maximum, the maximum cannot be zero,
and topic maximums cannot exceed the paper total.

A bad row is **skipped and recorded**, not fatal. School files routinely contain
a handful of unknown admission numbers or a blank mark, and importing the other
890 rows while reporting the 10 failures is far more useful than rejecting the
file. Every skip lands in `upload_errors` with its line number, the original
text, and a specific reason, so nothing fails silently.

Re-uploading a corrected file **updates** existing marks rather than duplicating
them (unique on student + exam + subject), and replaces the topic breakdown
rather than appending to it. Correcting a file is a normal operation.

---

## Analytics engine

| Component | Responsibility |
|---|---|
| `GradingService` | Percentage and grade from configurable bands |
| `RankingService` | Class rankings from database-side aggregates |
| `WeaknessDetector` | Weak subjects and weak topics |
| `TrendAnalyzer` | Direction of travel, overall and per subject |
| `AnalyticsService` | Per-student result cards and the AI input snapshot |
| `ClassInsightService` | Cached class-wide rankings and the teacher overview |

A few decisions worth calling out:

**All arithmetic is `BigDecimal` with an explicit scale.** Marks are money-like.
A score of 49.995 must not round across the pass boundary differently depending
on the platform, which is what `double` would risk.

**Rankings aggregate in SQL.** A class of 40 over 5 subjects is 200 rows;
summing them in the database and returning 40 keeps the cost flat as classes
grow. Ties share a rank and the next rank skips (1, 2, 2, 4) — inventing a
tiebreak would be a silent editorial decision about who did better.

**Weakness detection applies two tests, because one misses real cases.** An
absolute threshold (≤ 50%) catches outright failure. A relative test — a subject
more than 15 points below the student's own average — catches the strong student
scoring 68% while averaging 88% elsewhere. That is a genuine gap no absolute
cutoff would ever flag. Weak topics also carry an occurrence count: a topic weak
in three exams is a different problem from one bad day.

**Trends are ordered by `exam_date`, not insertion order.** Results are
routinely backfilled weeks late, which would otherwise invert the trend. A
movement under 5 points is reported as `STABLE` rather than dressed up as a
trend, and fewer than two exams gives `INSUFFICIENT_DATA` instead of a guess.

Thresholds, grade bands and the pass mark are all configuration
(`app.grading.*`, `app.analytics.*`) — grading scales differ per board and per
year, and changing one should not need a code change.

---

## Claude integration

`ClaudeClient` is the only class that talks to the API. It uses the official
`com.anthropic:anthropic-java` SDK against `claude-opus-5`.

**Structured outputs.** The response format is a JSON schema derived from the
target Java record, so the API cannot return prose that fails to parse. Nothing
scrapes text or repairs half-written JSON. The `@JsonPropertyDescription` on
each field *is* the instruction — the schema and the prompt are the same
artefact, so they cannot drift apart.

**Prompt caching.** The system prompt is sent as a cacheable block. It is
byte-identical for every student, so after the first call in a window the API
serves it from cache at roughly a tenth of the input cost — and writing feedback
for a class of forty is forty calls sharing that prefix. This is also why the
system prompts are constants: a single varying character (a timestamp, a name)
would invalidate the cache for the whole class. All per-student data goes in the
user message.

**The model interprets; it never calculates.** Every number in the prompt comes
from the analytics engine. Arithmetic is the one thing this system already knows
exactly, and letting a model redo it would introduce errors for no benefit. It
also makes the output auditable: any claim can be checked against the
`StudentSnapshot` the model was given.

**Refusals are checked.** A safety decline returns HTTP 200 with
`stop_reason: "refusal"`, so `stop_reason` is inspected before the content is
read.

**Feedback is generated once and stored.** A model call costs money and is not
reproducible: the parent opening the portal next week must see the same words
the teacher saw. `?refresh=true` is the explicit override after a re-upload has
changed the marks. Responses report `input_tokens`/`output_tokens` so cache
savings are visible.

### The three documents

| Audience | Contains |
|---|---|
| **Student** | Summary, strengths, weaknesses, an ordered study plan (subject, topic, concrete action, timeframe, weekly hours), a closing note |
| **Teacher** | Class summary, weak students ranked by priority with a specific action each, classroom-level interventions, remedial recommendations, topics to re-teach |
| **Parent** | Plain-language summary, home-support recommendations, a four-week guidance schedule, positives to acknowledge, encouragement |

Each prompt is written for its reader. The teacher prompt separates an
individual problem from a class-wide one — if most of the class lost marks on
the same topic, that is a teaching issue, not forty separate student issues. The
parent prompt assumes no jargon, assumes a working parent with limited evening
time, opens with what is going well, and never suggests punishment or private
tuition as a default.

### Running without a key

`FallbackFeedbackWriter` produces all three documents from the same analytics,
with no network call. It exists so the platform is fully functional on a fresh
clone, and it is the degraded path when the API is unreachable — a result portal
should not go dark because an upstream service is down. Its output is
deliberately more mechanical, and always labelled `"source": "FALLBACK"`.

---

## Caching

The original specification called for Redis. Redis is a separate server process
and the brief was to keep everything runnable locally with nothing to install,
so `CacheConfig` uses Spring's in-process cache manager instead. Three caches:
`examRankings`, `classAnalytics` and `aiFeedback` — the first two because a
ranking table is identical for everyone in the class who asks for it, the third
because every miss is a billed model call.

No service code knows the difference; they only use `@Cacheable` against those
names. **To move to Redis:** add `spring-boot-starter-data-redis`, delete
`CacheConfig`, and set `spring.cache.type=redis` with a host and port. Boot then
supplies a `RedisCacheManager` behind the same interface.

Class-level caches are evicted when an import completes. Without that, a freshly
uploaded exam would keep serving the ranking computed from the previous
marks — a silent wrong answer, which is worse than a slow one.

---

## Moving to MySQL or PostgreSQL

1. Swap the driver dependency in `pom.xml`.
2. Change `spring.datasource.*` in `application.yml`.
3. Replace `V3__spring_batch_schema.sql` with the matching `schema-mysql.sql` or
   `schema-postgresql.sql` from the `spring-batch-core` jar. Leave
   `spring.batch.jdbc.initialize-schema=never`.

`V1` and `V2` need no changes — they are portable ANSI SQL.

## Before deploying anywhere shared

- Replace `app.jwt.secret` with a real secret from the environment.
- Set `app.demo.seed-users=false`.
- Disable the H2 console and remove its `permitAll` rule.
- Narrow the CORS origins in `SecurityConfig`.
