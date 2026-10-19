# Agent guide

Read this file as the project map. Read other files only for the task at hand; do not reload the entire repository or repeat unchanged reads within one session. Communicate with the user in Vietnamese.

## Start with a small state check

```powershell
git status --short
git branch --show-current
git log -3 --oneline
```

Preserve existing work. Check the relevant diff before editing a modified file. Do not create branches, commit or push unless requested. Do not assume a previous branch, test result or deployment is still current.

## Project and checkpoint

English learning application for a PTIT Network Programming assignment. React/TypeScript/Vite client; Java 17/Spring Boot server; Spring Security; JPA/Hibernate; MySQL 8.4; Flyway; Docker Compose. Network reliability and concurrency are part of the assignment, alongside learning features.

Checkpoint verified **19/10/2026**:

- M01: accounts, roles, administration, sessions and basic WebSocket connection/revocation implemented.
- M02: content authoring/versioning/publication, assets and learner catalog implemented. Seed: 3 Units, 6 lessons, 90 questions, 13 materials. Bulk audio updates preserve version history.
- M03 steps 0–1 implemented: transactional receipt status, V4, entities/repositories, strict DTOs and three question graders. Commits: `bd913be` (step 0), `d37ba0b` (step 1).
- M03 steps 2–3: create/resume/list, pinned snapshots/media grants and full draft save/restore with revision/receipt checks; commit `759ccb3`. Historical backend run: 119 tests. See [step 2–3 validation](statics/results/module-3-step23-validation.md).
- M03 step 4: hint/check, atomic submit/result, correction and learning progress APIs implemented, not committed. Backend passed 136/136 tests on 19/10. See [step 4 validation](statics/results/module-3-step4-validation.md) and [API smoke guide](docs/M03_STEP4_API_SMOKE.md).
- M03 steps 5–6 pending: learning runner UI and E2E integration. Next work normally starts at step 5; follow the user's actual request.
- M04 review scheduling, M05 XP/weekly leagues, M06 complete offline/recovery, M07 advanced statistics are future modules. M08 solo challenges is deferred. `docs/archive/` contains historical IELTS designs, not current requirements.

This checkpoint is a navigation aid. If code/Git has advanced, inspect the affected implementation and update progress; do not assume uncommitted/committed status persists across sessions.

## Read only the relevant sources

| Task | First reads |
|---|---|
| Continue M03 | [progress](docs/M03_PROGRESS.md), then the requested step in [implementation plan](docs/M03_IMPLEMENTATION_PLAN.md) |
| M03 behavior/integration | [handoff](docs/M02_M03_HANDOFF.md), sections 2, 4, 6–7; [API rules](source/common/M02_M03_API.md), relevant sections |
| Exact payloads | Selected schemas/paths in [OpenAPI](source/common/openapi-m02-m03.json), [examples](source/common/examples-m02-m03.json), [test fixtures](source/server/src/test/resources/contracts/m03-fixtures.json) |
| Schema/model | Relevant runtime migration in `source/server/src/main/resources/db/migration/`; [database overview](DATABASE_DESIGN.md) |
| M02 change | [M02 progress](docs/M02_PROGRESS.md), relevant controller/service/test |
| Run/test setup | Relevant section of [README](README.md), Dockerfiles/Compose or test runner |
| Architecture | [backend structure](docs/BACKEND_STRUCTURE.md); relevant implementation |
| Assignment/report | [course instructions](INSTRUCTION.md), [submission](SUBMISSION.md), [overall plan](PLAN_ENGLISH_LEARNING_SYSTEM.md) |

OpenAPI defines exact fields/types/required-null/enum; API rules and handoff define behavior/transactions. Examples are independent samples. Resolve contradictions explicitly rather than silently changing contracts. Use `rg` to locate symbols; avoid printing the whole OpenAPI, all docs or all tests.

## Code map and invariants

- Backend root: `source/server/src/main/java/vn/edu/ptit/english/`. Keep `controller → service interface → service.impl → repository interface/impl → Spring Data JPA`. No JDBC rewrite or direct entity serialization to clients.
- Frontend: `source/client/src/`. `App.tsx` selects views; `navigation.ts` handles hash/history; `api.ts` handles cookie/CSRF/timeout. Existing features: `auth`, `admin`, `content`. Put new learning UI in its own feature and reuse existing conventions.
- Use existing session/CSRF and `UserPrincipal`; never trust client `userId`/owner or add JWT. STUDENT performs real attempts; TEACHER/ADMIN author/preview. Private attempts/assets belonging to others return 404.
- Flyway owns schema; Hibernate stays `ddl-auto=validate`. Runtime migrations V1–V4 exist. Never edit an applied migration or reset a volume to upgrade. Planned SQL under `docs/database/planned/` is a contract reference.
- Writes run in a transaction, locking/validating actor through `AccountGuard` before business locks. Keep a consistent lock order; re-read state after waiting on locks. Account revocation closes sessions/sockets after commit.
- Reuse `RequestReceiptService`: replay before revision/state checks; same key with different operation/payload is a conflict. Keep mutation, receipt and chosen status/body in one transaction. The `Supplier<Reply>` overload supports create/resume 201/200. Retry keeps the exact requestId/payload. ACK only after commit.
- `PublishedContentReader` is server-only and contains answers/transcripts. Build safe learner DTOs explicitly. Never expose keys/explanation/transcript in snapshots or logs; transcript is available only after submission. `AssetReadGrant` is the media permission extension point.
- Pin content Version IDs, origin and display permutation when creating an attempt. Existing attempts survive publication/retirement/audio updates. Copy/version changes do not create a new origin. Exposures increase only on new attempt creation.
- M03 uses `AttemptRequests`, `AttemptViews`, `AttemptData`, `AnswerValidator`, `AnswerGrader`, `AttemptRepository`. Three P0 types: SINGLE_CHOICE/FILL_BLANK/MATCHING. Answer by stable IDs, not display indices. Fill grading follows NFC/ASCII-whitespace flags/Locale.ROOT; no fuzzy matching.
- `AttemptEntity` has explicit business revision, not JPA `@Version`; advance once per real mutation under lock. Draft save creates no trial. FIRST is immutable; final answers and CORRECTION remain distinct. One Submission per Attempt.
- Create Submission via `AttemptRepository` in an existing transaction: internal Result uses submissionId=0; repository inserts, binds the generated ID and flushes. Never return the temporary Result or bypass this path with direct JPA save.
- `AttemptCompletionService` handles hint/check/submit/retry/result. Submit validates full input through `AttemptDraftValidator`, pins grading and persists FIRST/final/result/progress/receipt atomically. Feedback omits transcripts; GET result returns saved facts. Retry only appends CORRECTION and advances revision. Result submittedAt uses explicit JSON STRING format so MySQL JSON does not round fractional epoch seconds.
- Persist only STARTED/COMPLETED progress; synthesize NOT_STARTED on reads. An incomplete replay must not downgrade COMPLETED. Review provider is unavailable until M04; return REVIEW_NOT_READY rather than inventing random review. Full IndexedDB/outbox recovery belongs to M06.
- `LearningProgressService` pages active published lessons plus the actor's historical rows; drafts stay private. Retired lessons with history have no currentPublishedVersionId. Compare current publication with last completed version (or last attempt if never completed) for updateAvailable.

## Verification and local data

Run targeted tests while iterating. Broaden to backend regression when changing migrations/shared services or completing integration. Documentation-only edits need link/diff checks, not the application suite.

From `source/server`, with Java and Docker available:

```powershell
.\mvnw.cmd '-Dtest=AnswerGraderTest,AttemptRequestsTest,AttemptPersistenceTest' test
.\mvnw.cmd test
```

Backend integration tests use separate MySQL Testcontainers. Local execution needs ffprobe/ffmpeg; the Docker test target includes them. Alternative from repository root:

```powershell
docker build --target test -t english-learning-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-test mvn -B -ntp test
```

Rebuild the image after code changes, or deliberately mount current source and recompile; do not report stale image tests as current. Spring integration tests need RANDOM_PORT because WebSocket configuration requires a real servlet container.

From `source/client`: `npm ci`, `npm run build`, `npm run test:e2e` (install Chromium if needed). The E2E runner creates project `english-learning-e2e` on ports 5174/8081 with separate volumes. Do not point E2E_BASE_URL at demo port 5173 for write tests.

Local app: `start.cmd`; UI 5173, backend 8080. Starting a newly built backend applies pending migrations to its configured DB. Do not restart/rebuild the demo merely to verify a documentation edit. Never remove demo volumes (`down -v`) or truncate demo data for tests. Do not print or commit `.env`/credentials.

Historical evidence: [step 0](statics/results/module-3-step0-validation.md), [step 1](statics/results/module-3-step1-validation.md). The step 1 backend run passed 100 tests; this is not evidence for later edits. Record actual commands/results and limitations; never claim unrun tests or network benchmarks. `.test-state/` is ignored scratch/log storage.

## Maintain continuity

After substantive changes, update the relevant progress/plan and validation record with implemented scope, remaining work and actual checks. Keep this guide short: update milestones, entry points and durable invariants, not session logs. Before finishing, check `git diff --check` and Git status; clearly distinguish committed work, uncommitted work and tests actually run.
