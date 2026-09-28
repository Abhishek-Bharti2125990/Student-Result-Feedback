# AI-Powered Student Result Intelligence Platform

A monolithic Spring Boot 3 application that ingests exam results from CSV,
computes analytics on them, and turns those analytics into written feedback for
students and teachers using the Claude API.

Java 21 · Spring Boot 3.5 · Spring Security + JWT · Spring Batch · Flyway ·
MySQL 8 · official Anthropic Java SDK.

---

## Running it

You need **JDK 21 or newer** and **MySQL 8**. Maven does not have to be
installed — the project ships the Maven Wrapper.

Create the database once (Flyway owns everything inside it):

```sql
CREATE DATABASE srip CHARACTER SET utf8mb4;
```

Then point the app at it and start:

```bash
# macOS / Linux
export DB_URL='jdbc:mysql://localhost:3306/srip'
export DB_USERNAME=root
export DB_PASSWORD=yourpassword
./mvnw package
java -jar target/student-result-intelligence-1.0.0.jar

# PowerShell
$env:DB_USERNAME = "root"; $env:DB_PASSWORD = "yourpassword"
.\mvnw.cmd package
java -jar target\student-result-intelligence-1.0.0.jar
```

The app listens on <http://localhost:8080>. If that port is taken, add
`--server.port=8099`.

### No MySQL to hand? Use the `h2` profile

```bash
java -jar target/student-result-intelligence-1.0.0.jar --spring.profiles.active=h2
```

That runs the identical schema against a file-backed H2 database under `./data`,
with nothing to install, and exposes the H2 console at
<http://localhost:8080/h2-console> (JDBC URL `jdbc:h2:file:./data/srip`, user
`sa`, no password). It exists so the application can be demonstrated on a
machine without a database server; MySQL is the real target.

### Enabling Claude

```bash
export ANTHROPIC_API_KEY=sk-ant-...      # macOS/Linux
$env:ANTHROPIC_API_KEY = "sk-ant-..."    # PowerShell
```

**Without a key the application still works end to end.** Every feedback
endpoint returns real, data-grounded feedback produced by a local rule-based
writer, and the response is labelled `"source": "FALLBACK"` so it is never
mistaken for model output. See [Claude integration](#claude-integration).

### Tests

```bash
./mvnw test
```

The suite runs against an in-memory H2 database (`application-test.yml`), so it
needs no MySQL and leaves nothing behind. It still runs the real Flyway
migrations rather than a Hibernate-generated approximation of them, and the
integration suites boot the whole context and drive the upload endpoint over
HTTP.

### Demo logins

Created on first start. Password for all three: `Passw0rd!`

| Username   | Role    | Notes                                        |
|------------|---------|----------------------------------------------|
| `admin`    | ADMIN   | Uploads results, manages accounts            |
| `teacher1` | TEACHER | Staff `T-100`, teaches all class-10 subjects |
| `student1` | STUDENT | Linked to `student_id` `1001`                |

Set `app.demo.seed-users=false` to stop creating them.

There are exactly three roles. A `PARENT` role is not supported — `users.role`
is a foreign key onto the `roles` table, so a request naming a fourth role is
rejected rather than half-created.

---

## A five-minute walkthrough

```bash
# Log in as the admin
TOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"admin","password":"Passw0rd!"}' | jq -r .accessToken)

# Upload a term of results (returns 202 + an upload job id)
curl -s -X POST http://localhost:8080/api/admin/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F file=@src/main/resources/samples/results-unit-test-1.csv

# Poll the import: status, counts, and any per-line rejections
curl -s -H "Authorization: Bearer $TOKEN" http://localhost:8080/api/admin/upload/1 | jq

# Upload a second exam so trends have something to compare
curl -s -X POST http://localhost:8080/api/admin/upload \
  -H "Authorization: Bearer $TOKEN" \
  -F file=@src/main/resources/samples/results-midterm.csv
```

By the time the job reports `COMPLETED`, the analytics and both feedback
documents already exist — the import job writes them as its last two steps, so
no dashboard is ever blank on first open.

```bash
# --- Teacher view ---
TTOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"teacher1","password":"Passw0rd!"}' | jq -r .accessToken)

# The whole dashboard: class totals, four score buckets, topics to re-teach, AI advice
curl -s -H "Authorization: Bearer $TTOKEN" \
  http://localhost:8080/api/teacher/dashboard | jq

# One bucket at a time
curl -s -H "Authorization: Bearer $TTOKEN" \
  http://localhost:8080/api/teacher/students/critical | jq

# --- Student view ---
STOKEN=$(curl -s -X POST http://localhost:8080/api/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"username":"student1","password":"Passw0rd!"}' | jq -r .accessToken)

curl -s -H "Authorization: Bearer $STOKEN" http://localhost:8080/api/student/dashboard | jq
curl -s -H "Authorization: Bearer $STOKEN" http://localhost:8080/api/student/feedback  | jq
curl -s -H "Authorization: Bearer $STOKEN" http://localhost:8080/api/student/resources | jq
```

Neither dashboard needs a class name or an exam id. Omitted, they mean "the
latest" — which is what someone asking "how did we do?" almost always means.

`src/main/resources/samples/results-with-errors.csv` is a deliberately broken
file: upload it to see the per-line rejection report.

---

## API endpoints

### Auth

| Method | Path | Notes |
|---|---|---|
| POST | `/api/auth/login` | Returns an access token, a refresh token and the caller's profile |
| POST | `/api/auth/refresh` | Rotates the refresh token |
| POST | `/api/auth/logout` | Revokes every refresh token for the account |
| POST | `/api/auth/register` | ADMIN only |
| GET  | `/api/auth/me` | The caller's own profile |

### Student

| Method | Path | Notes |
|---|---|---|
| GET | `/api/student/dashboard` | Percentage, grade, rank, category, strong/weak topics, resources |
| GET | `/api/student/feedback` | The AI feedback document; `?refresh=true` regenerates |
| GET | `/api/student/resources` | Book and video recommendations for the weak topics |

All three take an optional `?examId=` and, for staff, an optional
`?studentId=`. A student passing someone else's `studentId` gets 403.

### Teacher

| Method | Path | Notes |
|---|---|---|
| GET | `/api/teacher/dashboard` | Class totals, four score buckets, weakest topics, AI guidance |
| GET | `/api/teacher/students/critical` | Below 50% |
| GET | `/api/teacher/students/average` | 50 to 70 |
| GET | `/api/teacher/students/good` | 70 to 85 |
| GET | `/api/teacher/students/excellent` | Above 85 |

All take optional `?className=` and `?examId=`.

### Admin

| Method | Path | Notes |
|---|---|---|
| POST | `/api/admin/upload` | Multipart CSV upload; returns 202 + job id |
| GET  | `/api/admin/upload/{id}` | Import progress and the per-line error report |
| GET  | `/api/admin/upload` | Upload history, newest first |
| GET  | `/api/admin/students` | Full roster |
| POST | `/api/admin/teacher-subjects` | Assign a class/subject to a teacher |

### Detail views behind the dashboards

`/api/analytics/**` (one student's exam report, results, trend, weak subjects,
weak topics, strong topics; class rankings, class report, class weak topics),
`/api/feedback/**` (a named exam's documents) and `/api/reference/**` (exams,
subjects, topics, the study-resource library, class rosters). A client that only
needs to draw a dashboard should use the two dashboard endpoints instead.

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
│   ├── auth/  analytics/  dashboard/  result/  ai/  admin/
├── analytics/       # GradingService, RankingService, WeaknessDetector, TrendAnalyzer, ScoreCategory
├── ai/              # ClaudeClient, ClaudeService, PromptFactory, FallbackFeedbackWriter
├── batch/           # CSV reader/processor/writer, the four tasklets, skip listeners
├── service/         # Orchestration: Auth, Analytics, ClassInsight, the two dashboards,
│                    #   Feedback, StudyResource, Upload, AccessGuard
├── controller/      # REST controllers
└── exception/       # Typed exceptions + @RestControllerAdvice

src/main/resources/
├── application.yml          # MySQL by default
├── application-h2.yml       # The zero-install profile
├── db/migration/
│   ├── common/   # V2 reference data — portable, shared by both vendors
│   ├── mysql/    # V1 schema, V3 Spring Batch tables
│   └── h2/       # The same two, in H2's dialect
└── samples/                 # Example CSVs, including one full of errors
```

It is a single deployable unit on purpose. One database, one transaction
manager, one process: the analytics engine reads the same tables the importer
writes, and the AI layer reads the analytics directly rather than over a
network hop.

---

## Database schema

Flyway owns the schema (`spring.jpa.hibernate.ddl-auto=none`).

| Table | Purpose |
|---|---|
| `roles` | The three roles, as data rather than a bare enum; `users.role` is a real FK onto it |
| `users` | Logins |
| `refresh_tokens` | SHA-256 hashes of issued refresh tokens, with revocation |
| `students` | Student records; `admission_no` is the CSV's `student_id`, and `user_id` is nullable so results can load before logins exist |
| `teachers`, `teacher_subjects` | Staff, and which class/subject combinations they own |
| `subjects`, `topics` | Curriculum; `topics.chapter_name` is the textbook unit |
| `exams` | The exam calendar; `exam_date` is what orders trends |
| `results` (`exam_results`) | One mark per student/exam/subject, unique on that triple |
| `topic_scores` | One row per CSV line — what makes weak-topic detection possible |
| `analytics` | Each student's computed standing per exam: percentage, grade, rank, category, strengths, weaknesses |
| `study_resources` | The curated book/video library |
| `upload_jobs`, `upload_errors` | One row per upload, and every rejected row with its line number |
| `feedback` | Generated documents as JSON, with model, source and token counts |

`percentage` and `grade` are stored on `exam_results` rather than derived on
read: rankings and class averages sort and aggregate over them, and recomputing
them per request would mean scanning the whole class every time. The `analytics`
table exists for the same reason one level up — the teacher dashboard's whole job
is to bucket a class by category, and deriving that on read would mean
re-ranking the class on every page load.

### Why two sets of DDL

`spring.flyway.locations` is `classpath:db/migration/common,classpath:db/migration/{vendor}`.
The reference data in `common` is portable and shared. The DDL is not, and
pretending otherwise would be worse than duplicating it: MySQL has no sequences
(Spring Batch needs three `*_SEQ` tables there and real sequences on H2) and
rejects `TIMESTAMP(9)`. Each vendor therefore gets its own `V1` and `V3`. The two
`V1` files differ only in identity-column syntax and **must be kept in step**.

Spring Batch's `BATCH_*` tables are created by migration `V3` with
`spring.batch.jdbc.initialize-schema=never`. Letting Boot create them is a trap
worth knowing about: its `embedded` mode only recognises an *in-memory* H2 URL,
so with any real database the tables are silently never created and the first job
launch fails on missing SQL grammar, while `always` re-runs plain
`CREATE TABLE`s on the second boot. Owning the schema in Flyway makes it
explicit and version controlled.

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

1. **URL rules** (`SecurityConfig`) — coarse role gates. The teacher dashboard
   names every child in the class and their weaknesses, so it is staff-only;
   uploads are staff-only; `/api/admin/**` is ADMIN-only.
2. **`AccessGuard`** — row-level checks a URL cannot express: a student may read
   only their own record, staff may read any. Every student-scoped service call
   goes through it, so a new endpoint cannot forget the check — it has to ask for
   the student id, and asking runs the check.

| Endpoint | STUDENT | TEACHER | ADMIN |
|---|:-:|:-:|:-:|
| `POST /api/auth/login`, `/refresh`, `/logout` | ✅ | ✅ | ✅ |
| `POST /api/auth/register` | — | — | ✅ |
| `POST /api/admin/upload`, `/api/admin/**` | — | — | ✅ |
| `POST /api/uploads` | — | ✅ | ✅ |
| `GET /api/student/**` | own only | ✅ | ✅ |
| `GET /api/teacher/**` | — | ✅ | ✅ |
| `GET /api/analytics/me/**` | ✅ | — | — |
| `GET /api/analytics/students/{id}/**` | own only | ✅ | ✅ |
| `GET /api/analytics/class/**` | — | ✅ | ✅ |
| `GET /api/feedback/teacher/**` | — | ✅ | ✅ |
| `GET /api/reference/exams`, `/subjects`, `/study-resources` | ✅ | ✅ | ✅ |
| `GET /api/reference/students` | — | ✅ | ✅ |

---

## CSV upload and the batch job

### Format

One row per **topic**, not per subject:

```
student_id,student_name,class_name,section,exam_name,subject,chapter_name,topic_name,marks_obtained,maximum_marks
1001,Ayushman,10,A,Midterm,Mathematics,Algebra,Quadratic Equations,10,25
```

All ten columns must be present; `section` is the only one that may be empty.
Several rows describe the same subject paper, and **the subject total is the sum
of them** — the file never states a subject total, so nothing can disagree with
its own breakdown.

The file is self-describing: it names the student, the class, the exam, the
subject and the topic. A row may therefore legitimately introduce people and
curriculum the database has never seen, which is how a new class is onboarded.
Subjects and exams are keyed by the slug of their name (`Social Science` →
`SOCIAL-SCIENCE`), so the same name always resolves to the same row.

### Flow

`POST /api/admin/upload` stores the file, creates an `upload_jobs` row, launches
the job asynchronously, and returns **202** with the job id. The client polls
`GET /api/admin/upload/{id}`.

The file is written to disk rather than streamed from the request body. That
decouples the two: the HTTP call returns as soon as the bytes are safe, and a
failed job can be re-run against the same file without a re-upload.

### The job — `resultImportJob`

1. **`validateCsvHeaderStep`** — checks the header and fails the whole job once
   if the columns are wrong. Getting 900 identical errors because two columns
   were swapped tells the uploader nothing.
2. **`prepareReferenceDataStep`** — creates every student, exam, subject and
   topic the file names, and commits them before any row is processed. The
   obvious place to do this is the item processor, and that is the wrong place: a
   chunk-oriented step rolls the whole chunk back when one row fails, so a
   subject created while processing row 12 disappears when row 15 is rejected,
   and every later row fails on a dangling foreign key. This step also clears the
   breakdown of each subject paper the file touches, so a corrected file that
   drops a topic does not leave the old row behind.
3. **`importResultsStep`** — chunked (100 rows per transaction) read → validate →
   write, fault tolerant. Percentages and grades are calculated here, and each
   subject is re-totalled from all of its topic rows after every chunk.
4. **`generateAnalyticsStep`** — ranks each affected class and stores every
   affected student's standing. Ranking cannot happen during the import: a rank
   is a property of the whole class, so a student ranked in chunk 1 would be
   ranked against a third of it.
5. **`generateAiInsightsStep`** — writes the student and teacher feedback
   documents. A failure here never fails the job; the marks are the deliverable
   and advice about them is secondary.

Steps 4 and 5 are scoped to the `(student, exam)` pairs this upload touched, read
back from the `upload_job_id` stamp on the result rows — a file covering one
class must not trigger a recompute of the whole school.

Validation runs at three levels: column presence, numeric format of the two mark
columns, and arithmetic sanity — marks cannot be negative, cannot exceed the
maximum, and the maximum cannot be zero.

A bad row is **skipped and recorded**, not fatal. School files routinely contain
a handful of blank marks or a mark above the paper total, and importing the other
890 rows while reporting the 10 failures is far more useful than rejecting the
file. Every skip lands in `upload_errors` with its line number, the original
text, and a specific reason, so nothing fails silently. The tasklet steps are
deliberately *not* fault tolerant: each is all-or-nothing by nature, and
analytics for half a class would be worse than a failed job an operator can
re-run.

Re-uploading a corrected file **updates** existing marks rather than duplicating
them. Correcting a file is a normal operation.

---

## Analytics engine

| Component | Responsibility |
|---|---|
| `GradingService` | Percentage, grade band, and score category |
| `RankingService` | Class rankings from database-side aggregates |
| `WeaknessDetector` | Weak and strong subjects and topics |
| `TrendAnalyzer` | Direction of travel, overall and per subject |
| `AnalyticsService` | Per-student result cards and the AI input snapshot |
| `AnalyticsWriteService` | Computes and stores the `analytics` row |
| `ClassInsightService` | Cached class-wide rankings, category counts, class weak topics |

### Score categories

| Category | Range | Dashboard heading |
|---|---|---|
| `CRITICAL` | below 50 | Students Below 50% |
| `AVERAGE` | 50 to 70 | Students Between 50 and 70 |
| `GOOD` | 70 to 85 | Students Between 70 and 85 |
| `EXCELLENT` | above 85 | Students Above 85 |

Bounds are exclusive upper limits, so a percentage sitting exactly on a boundary
lands in the **higher** band: 50 is `AVERAGE`, not `CRITICAL`. That is how "50 to
70" reads to a teacher, and the other way round would put a student who scraped
the threshold on the intervention list. They are configurable via
`app.grading.category-bounds`.

### Other decisions worth calling out

**All arithmetic is `BigDecimal` with an explicit scale.** Marks are money-like.
A score of 49.995 must not round across a boundary differently depending on the
platform, which is what `double` would risk.

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

**Strength detection applies no relative test.** Every subject is somebody's
"best subject" relative to something; only an absolute standard (≥ 75%) makes
"you are strong at this" mean anything.

**Class weak topics are ordered by affected students, not by average.** The
number of students is what turns a private gap into a teaching issue: a topic 25
students failed needs a lesson, one that two students failed needs two
conversations.

**Trends are ordered by `exam_date`, not insertion order.** Results are
routinely backfilled weeks late, which would otherwise invert the trend. A
movement under 5 points is reported as `STABLE` rather than dressed up as a
trend, and fewer than two exams gives `INSUFFICIENT_DATA` instead of a guess.

Thresholds, grade bands, category bounds and the pass mark are all configuration
(`app.grading.*`, `app.analytics.*`) — grading scales differ per board and per
year, and changing one should not need a code change.

---

## Learning resources

`study_resources` is a curated library: one book and one video per topic, keyed
by subject and topic name as plain text.

| id | subject | topic | resource_type | title | url |
|---|---|---|---|---|---|
| 1 | Mathematics | Quadratic Equations | BOOK | RD Sharma Class 10 — Quadratic Equations | |
| 2 | Mathematics | Quadratic Equations | VIDEO | Quadratic Equations in One Shot | `https://…` |

Subject and topic are deliberately **not** foreign keys: the library is
maintained independently of what any upload happens to contain, and a resource
for a topic nobody has been examined on yet is still a valid row. Matching is
case-insensitive, because the library is curated by hand while the topic names
arrive from a spreadsheet.

`StudyResourceService` returns resources in weak-topic order, worst topic first,
and the same list is injected into the AI prompt. That is the point: the model
may recommend a resource, but only one the school has vetted. A model asked for
"a good book on quadratic equations" will happily produce a plausible title that
does not exist, and one invented ISBN is enough to lose a teacher's trust in the
whole feature.

---

## Claude integration

`ClaudeClient` is the only class that talks to the API, using the official
`com.anthropic:anthropic-java` SDK against `claude-opus-5`. `ClaudeService` sits
on top and is the only place that decides between the model and the local
writer — so the degraded path is exercised by the same code every caller uses,
rather than being bolted on at one call site.

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
reproducible: the student opening the portal next week must see the same words
the teacher saw. The import job generates it up front
(`app.claude.generate-on-import`, default on), and reading a dashboard never
triggers a billed call as a side effect. `?refresh=true` is the explicit
override after a re-upload has changed the marks. Responses report
`input_tokens`/`output_tokens` so cache savings are visible.

### The two documents

| Audience | Contains |
|---|---|
| **Student** | Summary, strengths, weaknesses, an ordered study plan (subject, topic, concrete action, timeframe, daily minutes, a vetted resource), a closing note |
| **Teacher** | Class summary, weak students ranked by priority with a specific action each, classroom-level interventions, remedial recommendations, topics to re-teach |

Each prompt is written for its reader. The teacher prompt separates an
individual problem from a class-wide one — if most of the class lost marks on the
same topic, that is a teaching issue, not forty separate student issues, and it
belongs under topics to re-teach.

### Running without a key

`FallbackFeedbackWriter` produces both documents from the same analytics, with
no network call. It exists so the platform is fully functional on a fresh clone,
and it is the degraded path when the API is unreachable — a result portal should
not go dark because an upstream service is down. Its output is deliberately more
mechanical, and always labelled `"source": "FALLBACK"`.

---

## Caching

The original specification called for Redis. Redis is a separate server process
and the brief was to keep everything runnable locally, so `CacheConfig` uses
Spring's in-process cache manager instead. Three caches: `examRankings`,
`classAnalytics` and `aiFeedback` — the first two because a ranking table is
identical for everyone in the class who asks for it, the third because every
miss is a billed model call.

No service code knows the difference; they only use `@Cacheable` against those
names. **To move to Redis:** add `spring-boot-starter-data-redis`, delete
`CacheConfig`, and set `spring.cache.type=redis` with a host and port. Boot then
supplies a `RedisCacheManager` behind the same interface.

Class-level caches are evicted at the start of the analytics step and again when
the import completes. Without that, a freshly uploaded exam would keep serving
the ranking computed from the previous marks — a silent wrong answer, which is
worse than a slow one.

---

## Before deploying anywhere shared

- Replace `app.jwt.secret` with a real secret from the environment (`JWT_SECRET`).
- Set `app.demo.seed-users=false`.
- Use a MySQL account that is not `root`, via `DB_USERNAME` / `DB_PASSWORD`.
- Remove the `/h2-console/**` `permitAll` rule from `SecurityConfig`.
- Narrow the CORS origins in `SecurityConfig`.
