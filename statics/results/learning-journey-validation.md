# Kiểm tra vòng học, trang chủ và bài tập tương tác

Ngày 25/10/2026, nền main `69fb0d1`, nhánh làm việc `feature/m07-profile-statistics`. Thay đổi chưa commit/push.

## Phạm vi

Unit được trình bày thành vòng học cho STUDENT, tiến độ chỉ tính Lesson đang công bố; trang chủ ưu tiên tiếp tục/ôn/bài tiếp theo; MATCHING có thẻ bấm/chạm/bàn phím/kéo thả; ORDERING có đủ biên soạn/xem trước/xuất bản/nháp/chấm/nộp/chữa và khôi phục M06. Không thêm lịch thi, khóa vòng, điểm solo hoặc luật XP mới.

## Kết quả thực tế

| Kiểm tra | Kết quả |
|---|---|
| Frontend TypeScript/Vite production build | PASS, 73 modules; gồm kiểm tra kiểu các E2E mới |
| Backend tập trung OrderingQuestionTest/AnswerGraderTest/AttemptApiTest | 57/57 PASS sau sửa bộ đếm đáp án ORDERING |
| Backend toàn bộ | 270/273 PASS, 3 failure, 0 error/skip; 34:31 phút. Cả ba failure là kỳ vọng số/version migration cũ, liệt kê bên dưới |
| Migration sau cập nhật phạm vi M05 + ca V8→V9 mới | 2/2 PASS, BUILD SUCCESS |
| AttemptPersistenceTest sau cập nhật schema hiện tại V9 | 7/7 PASS, BUILD SUCCESS; thêm đọc/ghi JSON đáp án ORDERING vào ca polymorphic hiện có |
| Phạm vi backend đã qua | **274 ca khác nhau đã qua qua các lượt**, không phải một lượt 274/274 duy nhất |
| Playwright toàn bộ | **95/95 PASS trong một lượt**, 14,7 phút |
| Bổ sung ORDERING mobile/kéo thả | **1/1 PASS**, 7,8 giây; là ca đã có trong nhóm 95 được thêm kiểm tra, không cộng thành 96 ca khác nhau |
| Connection manager | 9/9 PASS |
| IndexedDB/journal browser | 6/6 PASS |
| Fixture OpenAPI M02/M03 | 25/25 PASS (18 valid, 7 invalid), gồm ORDERING; mọi schema hợp lệ, 636 local ref và 190 liên kết file Markdown hợp lệ |
| Xem ảnh và kiểm tra chiều rộng | Trang chủ desktop 1440×1000; trang chủ, MATCHING và ORDERING ở 390×844: PASS, không tràn ngang |
| Migration cũ | V1–V8 đối chiếu nguyên byte với Git archive của main; không sửa migration đã có |

Lượt backend đầy đủ có ba assertion cũ: GamificationMigrationTest mong 3 migration khi gọi latest từ V5 nhưng V9 làm thành 4; ca này đã pin target V8 đúng phạm vi thưởng/League gốc. Hai ca AttemptPersistenceTest mong schema hiện tại V8 nhưng thực tế đã V9; cập nhật kỳ vọng sang V9. Nhóm 2 migration và 7 persistence đã chạy lại qua toàn bộ, không sửa production để bỏ assertion. XML đối chiếu class/method xác nhận mọi ca thất bại của lượt đầy đủ đều có bằng chứng chạy lại qua.

Lượt focused đầu có lỗi production ở AttemptDraftValidator.answered chưa xử lý OrderingAnswer; đã bổ sung nhánh đếm câu và chạy lại 57/57. Lượt browser focused đầu 12/13: một ca cũ tìm h3 trong preview trong khi prompt đã chuyển sang legend của fieldset; sửa selector, lượt toàn bộ 95/95 qua. Không gọi các lượt đầu là PASS toàn bộ.

## Bằng chứng chức năng

- API thật HTTP/session/CSRF/MySQL: vòng riêng theo principal, role 401/403, query sai, hoàn thành/học lại/thêm bài/retire; ORDERING không lộ acceptedOrders trong snapshot, giữ hoán vị, lưu partial, replay cùng requestId, chấm/nộp đúng và replay nộp bài giữ cùng kết quả. Chống thưởng lặp được hồi quy qua các ca M05 chung.
- Bộ chấm: thứ tự đúng thay thế, câu thiếu/sai/bỏ trống, ID lạ/trùng, từ giống chữ có ID khác, JSON Jackson roundtrip và kiểm tra điều kiện xuất bản.
- Upgrade V8→V9 thật bằng Flyway/MySQL: giữ prompt/content/answer/source của câu cũ, trước V9 từ chối ORDERING, sau V9 nhận ORDERING, vẫn chặn UNKNOWN; migrate lại không chạy thêm.
- Browser: home thử lại khi lỗi mạng; partial ORDERING giữ qua reload và tiếp tục từ home; nút đổi vị trí, kéo thả, nộp và hoàn thành vòng; MATCHING dùng Enter, chuyển cặp đã có, kéo thả, bỏ ghép, lưu/tải lại; giáo viên tạo/xuất bản và xem trước ORDERING.
- Hồi quy 95 ca bao gồm các luồng M01–M07, điều hướng, phần thưởng/League và phục hồi trước đó.

## Môi trường và artifact

Frontend React/TypeScript hiện có, Java 17 và MySQL 8.4 Testcontainers; Chrome cài sẵn. Browser dùng Compose project **english-learning-rounds-e2e**, UI **15183**, API **18093**, volume riêng, cấu hình mẫu. Fixture ORDERING được tạo trong môi trường test rồi retire; không ghi dữ liệu giả hoặc nâng schema DB demo. Seed giữ 3 Unit/6 Lesson/90 câu và file gốc.

Build Compose ban đầu thành công. Lần rebuild sau bị Maven Central lỗi TLS/timeout; đóng gói source mới bằng test image Java 17 với POM/src hiện hành bind read-only và Maven cache, rồi COPY JAR mới vào runtime thử. Không đổi Dockerfile/dependency của dự án để né lỗi mạng. Lượt full offline đầu thiếu Surefire provider nên chưa chạy test; dùng cache đầy đủ từ lượt focused để chạy lại lượt đầy đủ nêu trên.

Artifact ngoài Git: `D:\Study\thuc tap mobile vga\.tmp-english-rounds-20261025\.test-state`. Có backend-focused-retry.log/focused-reports, backend-full-cached.log/backend-full-reports, backend-migration.log/backend-migration-reports, backend-persistence.log/backend-persistence-reports, browser-full.log, browser-ordering-mobile.log, recovery-storage.log và screenshots/journey-*.png. Các log PowerShell dùng UTF-16 khi có BOM. Không commit node_modules, target, dist, cache, credential hoặc ảnh kiểm thử.

## Giới hạn

Không chạy lại bộ restart/recreate toàn stack hoặc benchmark tải trong lượt này. Kiểm tra mobile là viewport Chrome, chưa thử thiết bị vật lý; mất mạng UI dùng interception. Demo đang chạy chưa rebuild/migrate V9. ORDERING có trong công cụ biên soạn; không tự đổi câu hỏi seed cũ sang dạng mới. Thống kê ôn/thưởng chỉ đọc dữ liệu đã xử lý, có thể cần cập nhật qua luồng M04/M05 hiện có.

Xem [tiến độ và cách thử](../../docs/LEARNING_JOURNEY_PROGRESS.md), [hợp đồng](../../source/common/M02_M03_API.md).
