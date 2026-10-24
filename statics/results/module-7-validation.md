# Kiểm tra M07 — Hồ sơ và thống kê

Bản nền ngày 21/10/2026, bổ sung thống kê giảng viên ngày 24/10 ở mục cuối. Nhánh feature/m07-profile-statistics, nền 57f2951. Chưa commit/push.

## Kết quả

- Frontend TypeScript + Vite production build: PASS.
- Backend Java 17 Maven package và compile test: PASS.
- Toàn backend Java 17/MySQL Testcontainers: **165/165**, 0 failure/error/skip, 10 phút 12 giây. Riêng M07: **8/8**, gồm 7 API/integration và 1 presence.
- Browser hồi quy M01–M04: **55/55**.
- Browser M07 sau sửa fixture: **5/5**, 12,4 giây.
- Đã xem ảnh hồ sơ/admin mobile 390×844; không tràn ngang trang, bảng cuộn trong khung.
- Liên kết Markdown tương đối và JSON OpenAPI: PASS.
- Build/chạy thực tế từ thư mục dự án tại localhost:5173/8080: PASS. Chrome đăng nhập demo STUDENT, mở Hồ sơ học tập, API thống kê 200, không có page error. Không tạo/nộp bài để smoke test trên demo.

Lượt browser đầy đủ đầu có 58/60 qua. Hai ca M07 lỗi fixture: nộp qua API khi trang đang mở rồi chưa bấm cập nhật; dùng selector nhãn không phù hợp với select lồng trong label. Đã sửa thao tác/selector và chạy lại toàn bộ nhóm M07 đạt 5/5, không sửa production code để bỏ kiểm tra. Tổng cộng 60 ca khác nhau đã qua sau các lượt này; không gọi là một lượt 60/60 duy nhất.

Lượt API tập trung đầu có fixture tạo submitted_revision=0, bị constraint V4 chặn đúng. Đã tăng revision như luồng M03, không sửa migration; lượt tập trung tiếp theo 8/8 và lượt toàn backend 165/165 đều đạt.

## Môi trường và phạm vi

Mã nguồn được thử tại thư mục tạm riêng. Backend dùng MySQL Testcontainers; browser dùng Compose project english-learning-m07-e2e ở 15177/18087 với .env mẫu trong thư mục tạm. Không dùng database demo để chạy test ghi dữ liệu; không xóa volume hoặc sửa V1–V5.

Lệnh backend sử dụng test image đã build và bind src hiện tại read-only, chạy mvn -B -ntp clean test; tránh kết quả từ test source cũ. Frontend build bằng npm run build; Playwright chạy trên E2E_BASE_URL test riêng với Chrome cài sẵn. Log, XML và ảnh trong thư mục tạm của phiên, không commit credentials/artifact build.

Fixture API tạo dữ liệu ORM hợp lệ cho FIRST, hỗ trợ, câu cũ/mới, trial làm lại/chữa, bài chưa nộp, các khoảng thời gian, bài retire/học lại. Mục tiêu là đối chiếu phép đo và quyền dữ liệu, không chứng minh hiệu quả học tập.

Trước/sau chạy M07 local đều có: users=7, lessons=6, questions=90, materials=13, assets=7, attempts=1, submissions=0, review_objective_progress=0, review_processed_submissions=0; Flyway vẫn ở 5. Đây là kiểm tra số lượng và smoke chỉ đọc, không phải kiểm tra sâu mọi byte dữ liệu. Ba container test Compose riêng đã dừng, volume giữ nguyên; app chính tiếp tục chạy M07.

Giới hạn: chưa có provider XP/Giải đấu tuần M05; presence chỉ là kết nối WebSocket còn mở trên một backend, chưa có độ mới heartbeat M06; chưa nghiệm thu toàn bộ checklist thủ công hoặc benchmark tải. Chưa triển khai export P1.

Xem [tiến độ](../../docs/M07_PROGRESS.md), [kế hoạch](../../docs/M07_IMPLEMENTATION_PLAN.md), [API](../../source/common/M07_API.md).

## Bổ sung thống kê giảng viên — 24/10/2026

Theo yêu cầu chỉ xem Unit mình làm chủ. Không thêm migration, dependency hoặc dữ liệu học viên giả. Backend/DTO/repository và UI mới chỉ đọc; quyền Unit theo principal, không theo chủ câu hỏi.

| Kiểm tra trong lượt này | Kết quả |
|---|---|
| Build TypeScript/Vite và image client/server | PASS |
| Backend tập trung StatisticsApiTest + StatisticsPresenceTest trước ca hai phiên bản | **15/15 PASS**, 0 failure/error/skip, 1 phút 14 giây |
| Backend toàn bộ sau thêm ca lịch sử hai phiên bản | **173/173 PASS**, 0 failure/error/skip, BUILD SUCCESS, **18 phút 21 giây**; riêng M07 16/16 (15 API + 1 presence) |
| Browser toàn bộ | **62/63** qua trong 5,1 phút; ca teacher mới lỗi fixture gửi PRACTICE thiếu topicId do đọc nhầm response UnitDetail |
| Browser teacher sau sửa fixture | **3/3 PASS**, 18,5 giây; không thay production code để bỏ kiểm tra |
| Tổng phạm vi browser đã qua | **63 ca khác nhau**: 55 hồi quy M01–M04/navigation + 5 profile/admin + 3 teacher, qua các lượt nêu trên; không gọi là một lượt 63/63 |
| Mobile teacher 390×844 | PASS kiểm tra chiều rộng và xem ảnh, bảng cuộn trong khung |
| Runtime từ thư mục dự án | PASS tại localhost 5173/8080: backend health 200, JS mới có TeacherStatistics, Chrome đăng nhập giảng viên mở UI, API teacher 200, không page error |

Backend mới kiểm tra quyền sai vai trò/khóa phiên, foreign/missing Unit, giáo viên rỗng, dùng chung câu giữa hai Unit, chủ Lesson khác chủ Unit, bỏ PRACTICE/REVIEW và FIRST chưa nộp, completion không nhân đôi do học lại, period/retire, hint/retry/correction, breakdown/paging, query sai và các phiên bản câu khác nhau trong lịch sử bài. Teacher API không trả email/tên/userId/Attempt ID/đáp án của học viên. HTTP kiểm thử dùng cookie/CSRF thật với MySQL Testcontainers.

Browser dùng server/MySQL thật ở Compose project **english-learning-m07-teacher-e2e**, cổng **15179/18089**, volume riêng. Tạo học viên và bài nộp thật trong môi trường test; không ghi test vào demo 5173/8080. Ca mất mạng dùng interception/abort, không phải đo mất gói TCP vật lý.

Lệnh: `npm run build`; backend chạy image test Java 17 hiện có với source mới bind read-only và `mvn -B -ntp clean test`; browser dùng `E2E_BASE_URL=http://127.0.0.1:15179`, Chrome sẵn có và Playwright. Docker Desktop ban đầu tắt, đã khởi động nền. Source/runtime M05/M06 không ghép vào nhánh M07 trong lượt này.

Artifact ngoài Git: `C:\Users\Admin\AppData\Local\Temp\english-m07-teacher-20261024\` gồm focused-tests.log, backend-full.log, e2e-full.log/json, e2e-teacher.log/json và ảnh ở project/.test-state/m07-teacher-mobile.png. Backend chỉ rõ phạm vi đã chạy, không dùng số 165 ngày 21/10 thay kết quả mới.

Cuối log full suite có chẩn đoán Surefire phải dừng fork JVM sau 30 giây chờ shutdown, sau khi toàn bộ test đã hoàn thành. Maven vẫn BUILD SUCCESS, Docker exit 0 và XML ghi 173 test không failure/error/skip. Chưa điều tra riêng thời gian cleanup JVM; không coi dòng chẩn đoán này là một test thất bại hoặc che nó bằng thay timeout. Các lỗi kết nối Hikari tới Testcontainers đã đóng trong log không phải lỗi backend demo.

Môi trường browser test riêng đã dừng sau kiểm tra, volume giữ nguyên.

Áp dụng có kiểm tra hash trước/sau đủ 30 file M07 (gồm bản nền và hai file teacher mới), không ghi đè thay đổi ngoài phạm vi. Demo chỉ chạy smoke đọc thống kê: trước/sau users=7, lessons=6, questions=90, materials=13, assets=7, attempts=1, submissions=0, review_objective_progress=0, review_processed_submissions=0, flyway_schema_history=5. Đây là kiểm tra số lượng/luồng đọc, không khẳng định mọi byte DB không đổi. runtime-before.json, runtime-after.json và runtime-teacher.png giữ trong thư mục artifact ngoài Git. Stash M07 gốc vẫn được giữ; toàn bộ M07 hiện chưa commit/push.
