# Database — English Learning (M01–M04 và XP M05)

M06 không thêm bảng SQL hoặc sửa V1–V5. Presence/subscription ở RAM; dữ liệu phục hồi tại thiết bị trong IndexedDB version 1 (workingDrafts, commands, leases). Đây là bản sao theo user/Attempt/writer, không thay MySQL/file volume. Xem [hợp đồng M06](source/common/M06_API.md), [nghiệm thu](statics/results/module-6-validation.md).

Cập nhật M03 ngày 19/10/2026: V4 đã được dùng cho tạo/tiếp tục lượt, nháp, hint/check, nộp/kết quả/chữa và tiến độ. Xem [tiến độ M03](docs/M03_PROGRESS.md), [nghiệm thu toàn module](statics/results/module-3-validation.md). Backend build mới tự áp dụng migration còn thiếu khi khởi động; giữ volume hiện có.

Nguồn schema chính: `source/server/src/main/resources/db/migration/`. Source hiện có Flyway V1–V9; V9 mở rộng CHECK loại câu cho ORDERING; V6 nền M05, V7 outbox thưởng, V8 Giải đấu tuần. Database demo `english-learning-main` được sao lưu trước khi nâng migration, giữ volume/dữ liệu. Hibernate validate khi khởi động. Không sửa migration đã áp dụng hoặc dùng `ddl-auto=create/update` để chữa lệch schema.

| Bảng | Vai trò |
|---|---|
| users | id, email duy nhất, password_hash BCrypt, full_name, role, status, security_version, created_at |
| classrooms | Dữ liệu lớp học của nền M01, teacher_id FK users, join_code và create_request_id có unique constraint |
| class_members | Khóa ghép classroom_id/student_id, FK giữ lịch sử thành viên |
| account_control | Một hàng id=1 để tuần tự hóa thay đổi quản trị |
| flyway_schema_history | Flyway tự quản lý lịch sử migration |

Role: STUDENT (người học), TEACHER (biên soạn), ADMIN (quản trị). Status: ACTIVE, LOCKED, DELETED. Security_version tăng khi đổi quyền/trạng thái, dùng để từ chối session cũ. Xóa logic không xóa hàng users hoặc FK, không giải phóng email để đăng ký lại.

Ứng dụng dùng database english_learning và volume english-learning-project_mysql_data mặc định. Không dùng volume IELTS/IoT. M02 lưu byte ảnh/audio ở asset_data; bảng assets chỉ giữ metadata, storage_key nội bộ, SHA-256 và quyền sở hữu.

V3 tạo đủ 13 bảng học liệu/receipt đã thiết kế và đã được dùng qua API và seed ở bước 1–4. Seed chuẩn có 3 Topic, 3 Unit, 6 mục tiêu, 6 Lesson root, 90 Question root, 13 Material root và 7 Asset; dữ liệu người dùng có thể làm tổng số hàng lớn hơn. Nâng bài mẫu cũ bằng LessonVersion mới, giữ bản công bố cũ và ID/origin của các câu cũ đủ điều kiện. Material/Question/Lesson root giữ con trỏ nháp/công bố và revision; bản công bố bất biến, bản chỉnh sửa tạo version mới. Question lưu origin_key CHAR(36), sao chép giữ origin. JSON content/answer được parse theo từng dạng trước khi lưu. LessonVersion giữ instructions_json và hai tập tham chiếu lesson_objectives/lesson_questions theo Version ID; nhóm/thứ tự nằm ở slot. Bỏ nháp không xóa lịch sử công bố/file hoặc các câu/tài liệu độc lập. Bài làm dùng V4, lịch ôn dùng V5; XP thuộc M05. Tài liệu IELTS cũ trong docs/archive là tham khảo lịch sử, không phải schema app này.

## Dữ liệu học M03 — V4

| Bảng | Vai trò và ràng buộc chính |
|---|---|
| attempts | Owner, mode/state, LessonVersion đã chốt, selection/playerState JSON và revision nghiệp vụ |
| attempt_questions | Version/origin/slot/permutation, dấu seen trước lượt và thời điểm mở hỗ trợ; origin/vị trí không trùng trong lượt |
| attempt_answers | Một đáp án hiện hành cho mỗi item, lưu nháp hoặc final; nháp không tạo trial |
| answer_trials | FIRST/GUIDED_RETRY/CORRECTION bất biến, thứ tự trial duy nhất theo item |
| submissions | Một hàng duy nhất theo Attempt, result_json chứa factsSchemaVersion=1 và Submission ID thật |
| question_exposures | Khóa ghép user/origin; tăng khi tạo lượt mới, không tăng khi resume/GET/replay |
| lesson_progress | Khóa ghép user/Lesson root, STARTED/COMPLETED; NOT_STARTED được API tổng hợp |

Tạo lượt chốt Version ID, permutation và seen trước khi cập nhật exposure. Lưu nháp chỉ ghi answers/playerState/revision/receipt. Submit ghi final, FIRST còn thiếu, Submission, state/revision, progress và receipt cùng transaction. FIRST/Result đã nộp không đổi khi chữa; CORRECTION chỉ thêm trial và tăng revision. COMPLETED không bị hạ bởi học lại dở.

`revision` là số nghiệp vụ dưới khóa, không dùng JPA `@Version`. Repository INSERT Submission với Result nội bộ submissionId=0, gắn ID thật vào JSON rồi flush trước commit. submittedAt trong Result lưu chuỗi ISO để giữ độ chính xác. Receipt theo actor/requestId và payload hash, replay trước kiểm tra state/revision; không ghi receipt tách transaction.

Nháp và kết quả đã commit nằm trong MySQL; bytes ảnh/audio nằm trong asset_data. Session ở RAM của một backend có thể mất sau restart. [Script nghiệm thu bền vững](source/client/scripts/verify-m03-persistence.mjs) kiểm tra đăng nhập lại, restart/recreate giữ volume, seed và replay trong project riêng; xem kết quả thực tế trong biên bản M03.

## Schema M02–M03

- [V3 — M02, 13 bảng](docs/database/planned/V3__learning_content.sql): học liệu có phiên bản, ngân hàng câu và file metadata; request_receipts dùng chung M03.
- [V4 — M03, 7 bảng](docs/database/planned/V4__learning_attempts.sql): lượt làm, đáp án nháp, trial, bài nộp, lịch sử đã gặp và tiến độ bài.
- [Bàn giao và quan hệ dữ liệu](docs/M02_M03_HANDOFF.md), [hợp đồng API](source/common/M02_M03_API.md).

V3 đã được sao chép vào Flyway và áp dụng; file trong planned giữ hợp đồng gốc để đối chiếu. V4 đã có trong Flyway ở bước 1 M03; file planned giữ nguyên để đối chiếu. Giữ JPA validate và không sửa migration đã chạy. M04 thêm V5; XP/league thuộc M05. Xem [tiến độ M02](docs/M02_PROGRESS.md), [M03](docs/M03_PROGRESS.md) và [M04](docs/M04_PROGRESS.md).

## Ôn tập cá nhân M04 — V5

Schema thực thi: [V5__personal_review.sql](source/server/src/main/resources/db/migration/V5__personal_review.sql).

| Bảng | Khóa và trách nhiệm |
|---|---|
| review_objective_progress | PK user_id + objective_id; số liệu FIRST, cờ lỗi/cần luyện, độ khó ưu tiên, stage, ngày thành công gần nhất và next_review_at |
| review_difficulty_evidence | PK user_id + objective_id + difficulty; số bộ/câu mới, số đúng và chuỗi bộ tốt đủ câu |
| review_processed_submissions | PK submission_id, kèm user_id/processed_at; ledger chống xử lý cùng Submission hai lần |

Kết quả gốc vẫn ở submissions.result_json của V4. POST sync đọc tối đa 100 kết quả chưa có ledger theo user, tổng hợp mỗi mục tiêu một lần, ghi cả ba bảng và request_receipts cùng transaction dưới actor lock. Học viên cũ được backfill khi mở Ôn tập cá nhân; GET chỉ đọc. Restart không mất ledger/lịch; gửi lại không tăng số lượt.

Mốc dựa vào submittedAt của kết quả gốc, không lấy thời điểm sync để làm mới lịch. Stage tăng tối đa một lần theo ngày Việt Nam; mốc lưu UTC. Gợi ý mức không lấy câu từng gặp/GUIDED/CORRECTION làm bộ câu mới. Xem [quy tắc/API](source/common/M04_API.md).

## XP M05 — V6/V7

V6/V7 chỉ thêm bảng mới, giữ nguyên V1–V5. Đã có ORM/repository, worker và API thưởng cho XP/streak/badge. Giải đấu tuần thêm ở V8 bên dưới. Tình trạng kiểm tra theo [tiến độ M05](docs/M05_PROGRESS.md).

| Bảng | Vai trò |
|---|---|
| gamification_policies | Snapshot m05-p0-v1 và activatedAt/effectiveAt được lưu một lần; không đổi khi restart |
| review_reward_context | PK attempt/objective, owner, dueAt/selectedAt/stage/cycle và eligibility; ORM/repository được nối create REVIEW |
| gamification_processed_submissions | Terminal decision JSON mỗi Submission; không có trạng thái thành công giả cho nguồn lỗi |
| gamification_reward_claims | Unique user/type/source và bonus user/type/objective/day; NEW_QUESTION để objective_id NULL |
| xp_ledger | Một dòng mỗi claim, requested/awarded/leagueCounted có CHECK; league season FK chờ migration giải đấu |
| gamification_profiles | Tổng XP BIGINT và revision, miền không âm/số nguyên JavaScript an toàn |
| gamification_daily_stats | Khóa user/day, awarded <= cap, qualified count không âm |
| user_badges | Unique user/badge, thời điểm trao và nguồn chứng minh |
| gamification_processing_failures | Retry metadata cho nguồn lỗi; không thay terminal processed ledger |
| gamification_outbox (V7) | UUID event, owner/revision/type, created/delivered; commit cùng projection rồi dispatch invalidation |

Hook ReviewRewardContextRecorder chạy trong cùng transaction actor-locked của AttemptService khi tạo REVIEW mới, sau khi chọn/chốt câu. Resume/replay không capture lại. DueAt chuẩn microsecond; cycle hash không phụ thuộc lastSubmissionId. Provider không có lịch M04 thì không tạo bằng chứng giả. Mọi lỗi lưu context rollback cả Attempt/exposure/receipt.

Các bảng trên đã có ORM và được dùng bởi GamificationService/worker. Claims, ledger, day/profile, badge, processed decision, outbox và receipt sync cùng transaction dưới actor lock. GET không tạo projection. Xem [bộ luật](docs/M05_DECISIONS.md) trước khi viết dữ liệu; migration tiếp theo không được sửa V6/V7 đã chạy.

## Giải đấu tuần — V8

| Bảng | Vai trò/ràng buộc |
|---|---|
| league_seasons | UNIQUE startAt, end/cutoff, OPEN/CLOSING/FINALIZED, rules JSON và policy snapshot/revision |
| league_group_allocators | PK season/tier, khóa xếp nhóm; tạo đủ 5 tier cùng transaction tạo season |
| league_groups | UNIQUE season/tier/groupNo, memberCount CHECK 0–25, revision và finalizedAt marker |
| league_members | UUID public memberKey, UNIQUE season/user, FK group/season và user, joinedAt/weeklyXp; frozen result JSON trên row có sẵn |
| league_outbox | Event riêng tư theo group hoặc user, created/delivered, không FK users để finalizer không khóa actor ngầm |

V8 thêm FK xp_ledger.season_id; không sửa ledger cũ, không đưa XP đã xử lý trước join vào tuần. Member điểm tuần và từng ledger countedXp/reason được ghi cùng transaction XP. Actor → season theo thứ tự → allocator/group → member/claims/projections; finalizer season → group → member có sẵn, không quay lại actor. DynamicUpdate trên membership và outbox chỉ cập nhật cột thay đổi, tránh cập nhật lại FK không đổi.

Tạo tuần hiện tại bằng worker dưới khóa policy, GET không tự tạo. Chốt từng nhóm sau cutoff trong transaction riêng; result/member và group marker cùng commit. Job tiếp tục nhóm còn thiếu rồi mới FINALIZED season. Nhóm đã chốt đủ để chính học viên tham gia tuần mới, không phải đợi nhóm khác. V8 đã áp dụng trên môi trường kiểm tra và demo `english-learning-main`; không chỉnh lại file migration khi tiếp tục.

## M07 — phép chiếu chỉ đọc

Thống kê TEACHER (24/10) không thêm bảng: quyền theo units.owner_id, nguồn LEARN qua attempts.lesson_version_id → lesson_versions.lesson_id → lessons.unit_id. Không nối membership câu hiện tại để suy Unit; không lẫn các lượt dùng chung câu ở Unit khác hoặc PRACTICE/REVIEW không có Unit nguồn. Kết quả dùng answer_trials FIRST đã có Submission; câu/phiên bản cũ vẫn được giữ. Hoàn thành đếm cặp user/lesson trong lesson_progress, hints đếm AttemptQuestion mở gợi ý, mọi dữ liệu trả ra là tổng hợp.

M07 không có bảng hoặc migration riêng, dùng V1–V5 hiện có. StatisticsRepository dùng JPQL aggregate trên Attempt/Submission, AnswerTrial/AttemptQuestion/QuestionVersion; LessonProgress và ReviewProgress. FIRST chỉ vào thống kê sau khi có Submission, lấy đúng metadata phiên bản; GUIDED_RETRY/CORRECTION tách riêng. Không đọc toàn bộ JSON kết quả chứa đáp án để trả cho UI. GET không ghi ledger/lịch ôn. XP/league được đọc qua M05LearnerRewardsReader và GET service M05 trên schema V6–V8 đã có; không thêm bảng thống kê. Ước lượng kết nối lấy từ RAM LiveConnections, không ghi thành dữ liệu online bền vững. Xem [API M07](source/common/M07_API.md).

## Vòng học và ORDERING — 25/10

Vòng học dùng Unit/Lesson hiện có. Tiến độ vòng được tổng hợp chỉ đọc từ các Lesson đang công bố và lesson_progress của principal; không thêm bảng điểm hoặc trạng thái vòng. Thêm một bài công bố có thể tăng mẫu số, retire một bài loại bài đó khỏi mẫu số; không xóa lịch sử riêng của học viên.

V9 chỉ DROP/ADD CHECK ck_question_versions_type để nhận ORDERING, giữ nguyên V1–V8. QuestionVersion.content_json chứa tokens [{id,text}], answer_json chứa acceptedOrders [[id,...]]. AttemptAnswer lưu {type:ORDERING,tokenIds:[...]}; display_json.optionIds giữ hoán vị thẻ. Các bảng Attempt/Trial/Submission/receipt/XP hiện có tiếp tục được dùng, không đổi luật thưởng hoặc nội dung Submission cũ. DB demo chưa được migrate trong lượt triển khai này.
