# Kiểm tra số thứ tự vòng lưu cố định — V12

Ngày 27/10/2026, nhánh feature/m07-profile-statistics, HEAD nền cb31f80. Giữ các thay đổi giao diện giáo viên chưa commit từ phiên trước; lượt này chưa commit/push.

## Kết quả và phạm vi

- units.round_number INT dương, duy nhất toàn hệ thống kể cả vòng chưa công bố. Giáo viên sửa vòng sở hữu; ADMIN theo quyền cũ. Số nhỏ học trước, cho phép khoảng trống; số trùng trả 409 ROUND_NUMBER_CONFLICT và không ghi receipt thành công.
- Form Số thứ tự vòng: tạo mới để trống tự cấp max+1; sửa lưu số cụ thể. stage/sortOrder giữ tương thích dữ liệu/payload, không còn quyết định thứ tự vòng. Chủ đề vẫn dùng topicId hiện có.
- Catalog, picker, preview, thứ tự bài, thống kê và học viên dùng roundNumber từ API. Không tải toàn bộ public catalog để tính số. RoundProgress.number và prerequisite dùng cùng số DB trước lọc/phân trang.
- V12 giữ số của các vòng đang công bố theo lộ trình cũ rồi cấp số cho các vòng còn lại. Không sửa migration cũ, ID, bài, câu hỏi, kết quả, XP hoặc tiến độ. Vòng hoàn thành vẫn mở sau khi đổi số. Seed không ghi đè Unit đã có.
- Payload thiếu/null roundNumber giữ tương thích hash receipt cũ. Response replay trước V12 có thể thiếu số và giữ nguyên body; hợp đồng LegacyUnitReceipt mô tả trường hợp đó. GET và ghi mới trả số hiện hành.

## Backend — PASS 102/102 trong lượt hoàn chỉnh cuối

Bind source hiện tại và Maven cache vào english-learning-m07-test:latest; MySQL Testcontainers riêng. Maven chạy offline: -Dtest=RoundNumberMigrationTest,CatalogApiTest,AttemptApiTest,AttemptPersistenceTest,AuthoringApiTest,StatisticsApiTest,StatisticsRewardsIntegrationTest,OrderingMigrationTest,EngagementMigrationTest package.

| Nhóm | Ca qua |
|---|---:|
| AttemptApiTest | 47 |
| AttemptPersistenceTest | 7 |
| AuthoringApiTest | 17 |
| CatalogApiTest | 10 |
| RoundNumberMigrationTest | 1 |
| OrderingMigrationTest | 1 |
| EngagementMigrationTest | 1 |
| StatisticsApiTest | 15 |
| StatisticsRewardsIntegrationTest | 3 |

Không failure/error/skip; BUILD SUCCESS, 09:15 phút, container exited 0. Lượt đầu có lỗi fixture dùng cùng số 1 qua nhiều ca; sửa fixture lấy số từ max DB rồi chạy lại cả phạm vi trên. Không lấy báo cáo lỗi ban đầu làm kết quả cuối.

Ca mới kiểm tra backfill published/draft/retired và revision, migrate lại không chạy, CHECK/UNIQUE; số âm/0/thập phân/tràn INT; hai tác giả tạo cùng số chỉ một thành công; quyền/revision/replay; old payload; đổi số làm đổi prerequisite nhưng giữ kết quả/tiến độ và vòng hoàn thành; retire vòng trước không đánh lại số vòng sau.

## Frontend và hợp đồng — PASS

TypeScript/Vite production build thành công cho image cuối. JSON Schema M02/M03/M07 và 50 example/fixture hợp lệ/không hợp lệ qua, gồm LegacyUnitReceipt. Kiểm tra local references và liên kết Markdown qua; kiểm tra whitespace riêng cho file mới.

Browser Chrome, Playwright 1 worker, chỉ môi trường riêng:

- english-learning-round-order-e2e, 15203/18113: 114/116 qua trong 6.0 phút. Hai lỗi cùng do hướng dẫn nằm trong accessible name của ô số, làm locator theo nhãn chính xác không tìm thấy.
- Sửa aria-label và aria-describedby để tách nhãn/hướng dẫn. Build image mới; english-learning-round-order-final-e2e, 15205/18115, database mới: learning-journey.spec.ts và module-two-catalog.spec.ts **9/9 qua**, 34.7 giây, gồm cả hai ca trước đó lỗi.
- Tổng 116 ca khác nhau đã qua qua các lượt; không mô tả thành một lượt 116/116. Các nhóm còn lại không chạy lại sau thay đổi duy nhất về nhãn ô nhập: game, hoàn thành bài, rewards/League/leaderboard, thống kê, phục hồi, M03 và authoring/navigation đã qua ở lượt đầy đủ.

Kiểm tra giao diện mới: số giáo viên khớp số học viên, draft có số riêng nhưng chưa xuất hiện trên lộ trình, lọc Của tôi/tìm kiếm/picker/thống kê giữ số, báo trùng vẫn sửa được form, đổi số lưu và reload vẫn giữ, tạo mới tự cấp, giao diện mobile không tràn.

## Giới hạn nghiệp vụ

Không tự hoán đổi số hoặc đánh lại vòng của tác giả khác. Đổi chỗ hai số đã dùng cần số tạm còn trống. Không thêm bảng Round hoặc hệ thống lộ trình theo lớp/năm học. Concurrent create tự cấp có thể nhận 409 khi tranh số và được phép thử lại; unique index bảo đảm không có hai vòng cùng số.

## Rollout localhost:15183 — PASS

Đã sao lưu SQL riêng, nâng database đang dùng từ V11 lên V12 bằng image backend đã kiểm thử; frontend khớp image 15205 sau sửa nhãn. HTTP 200, JavaScript asset/hash giống bản kiểm thử, backend /api/health UP. Không chạy E2E lên 15183 và không xóa/reset volume.

Đối chiếu từng hàng cũ qua hash: Unit (mọi trường trước V12), Lesson, lesson_progress, submissions.result_json, request_receipts (hash/payload/response), XP ledger, profiles, daily stats, policies, season rules và frozen League results đều giữ nguyên. round_number khớp chính xác lộ trình công bố cũ; vòng chưa công bố được đánh số phía sau. Backup/log/audit JSON được giữ riêng trong staging .test-state, không đưa vào Git.

Dừng hai môi trường kiểm thử 15203/15205 sau kiểm tra; giữ volume. Bản 15183 tiếp tục chạy. Source đã áp dụng vào dự án hiện tại; chưa commit/push.
