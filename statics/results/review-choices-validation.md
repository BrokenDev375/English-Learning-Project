# Kiểm tra ba lựa chọn ôn tập cá nhân

Ngày 28/10/2026, nhánh `feature/m07-profile-statistics`, nền `61af437` cộng thay đổi chưa commit. Biên bản này dành cho bộ chọn/giao diện mới; không dùng số test M04 hoặc M07 lịch sử làm bằng chứng.

## Hành vi đã triển khai

| Lựa chọn | Tập câu |
|---|---|
| Ôn tất cả — ALL | Câu sai LEARN chưa ôn đúng + toàn bộ REVIEW_VARIANT trong bài đang công bố |
| Ôn ưu tiên — PRIORITY | Câu sai chưa ôn đúng, xen kẽ biến thể cùng Objective với nguồn sai chưa giải quyết |
| Ôn các câu sai — WRONG | Chỉ nguồn câu sai LEARN đã nộp, gồm GUIDED/INDEPENDENT |

Nguồn sai lấy FIRST=INCORRECT, theo principal và originKey; không lấy câu bỏ trống, vòng chưa nộp hoặc lỗi chỉ ở PRACTICE/REVIEW. REVIEW nộp đúng chính origin đó sau lần LEARN sai gần nhất giải quyết nguồn sai; LEARN sai mới thêm lại. Dùng finalOutcome bất biến trong Submission ôn, không dùng updatedAt hoặc CORRECTION. Làm đúng biến thể khác cùng mục tiêu hoặc lưu nháp đúng không xóa câu gốc. Không xóa/sửa lịch sử bài làm.

Ba luồng không giới hạn currentDifficulty hoặc lịch đến hạn. Chỉ dùng phiên bản đang thuộc bài công bố; nguồn lỗi lịch sử được đối chiếu bằng origin. Tập câu được khử trùng origin, mỗi lượt 1–10 câu, ít hơn nếu kho thiếu. ALL/WRONG ưu tiên ít/lâu gặp; PRIORITY xen kẽ câu sai và biến thể liên quan. Câu dùng chung nhiều Lesson được giữ đúng slot REVIEW_VARIANT khi cấp với tag biến thể. Câu chung material giữ nhóm/thứ tự biên soạn.

Giao diện ba thẻ, số câu mỗi lượt và nút cập nhật; bỏ bộ lọc/danh sách mục tiêu dài. Dashboard thêm `choices{all,priority,wrong}` dùng cùng tập hợp với bộ chọn. Tài khoản mới vẫn ôn được biến thể. DUE/MISTAKES giữ tương thích request cũ và lượt đang dở, cùng snapshot/receipt/journal/asset grant. Lịch/projection M04 và luật thưởng M05 không đổi. Không migration.

## Kiểm tra đã chạy

| Phạm vi | Kết quả |
|---|---|
| TypeScript + Vite, gồm mã E2E | `npm.cmd run build`: PASS |
| Build backend/runtime Java 17 | Docker Compose build từ source mới: PASS |
| Hồi quy backend tập trung, lượt đầu | 131 lượt test, 0 failure/error/skip, BUILD SUCCESS |
| PersonalReviewApiTest trên source cuối sau sửa slot dùng chung | 18/18, 0 failure/error/skip |
| Browser trên image cuối | 9/9, một lượt 48.7 giây |
| Kiểm tra tĩnh hợp đồng | Hai OpenAPI JSON parse được; enum mới/cũ và ba trường đếm khớp; không phải kiểm định JSON Schema toàn phần |
| Kiểm tra hình ảnh | Đã xem ảnh desktop 1280 px và mobile 390 px; ba thẻ/nút đọc được, không tràn ngang |

Backend chạy bằng Maven 3.9.11 trực tiếp từ cache wrapper trên Windows, Java 21.0.9, compile release 17; integration tạo MySQL 8.4 Testcontainers riêng. Lệnh test:

```text
mvn.cmd -B -ntp -Dtest=AttemptRequestsTest,ReviewPolicyTest,PersonalReviewApiTest,AttemptApiTest,GamificationContextApiTest,GamificationAcceptanceTest test
mvn.cmd -B -ntp -Dtest=PersonalReviewApiTest test
```

Lượt đầu gồm AttemptApiTest 47, AttemptRequestsTest 6, GamificationAcceptanceTest 33, GamificationContextApiTest 20, PersonalReviewApiTest 17, ReviewPolicyTest 8. GamificationContextApiTest có các ca kế thừa M04; 131 là số lượt thực thi, không tuyên bố 131 tình huống nghiệp vụ khác nhau. Sau đó bổ sung ca dùng chung một phiên bản câu ở GUIDED và REVIEW_VARIANT, sửa việc chọn slot và chạy lại toàn bộ PersonalReviewApiTest thành 18/18 trên source cuối. Không cộng 131 + 18 thành số ca khác nhau.

Browser dùng Chrome cài sẵn và Playwright, project/volume riêng `english-learning-review-choices-e2e`, UI `15184`, API `18094`:

```powershell
$env:E2E_BASE_URL='http://127.0.0.1:15184'
$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='C:/Program Files/Google/Chrome/Application/chrome.exe'
node ./node_modules/@playwright/test/cli.js test module-four-review.spec.ts round-map.spec.ts
```

Sáu ca M04 kiểm tra: ba lựa chọn/tài khoản mới; nguồn sai gốc và nháp qua reload; bỏ câu nộp ôn đúng; ưu tiên xen kẽ đúng mục tiêu; mất ACK sync/create; tách tài khoản. Ba ca vòng chơi kiểm tra lối vào, khóa/mở vòng, chơi lại và URL PRACTICE cũ. Lượt browser đầu cũng 9/9; lượt cuối sau rebuild backend là bằng chứng ở bảng trên, không cộng thành 18 ca khác nhau.

Các ca backend mới còn kiểm tra: không lấy lỗi REVIEW/PRACTICE hoặc check chưa nộp; FIRST sai dù final LEARN đúng; final REVIEW đúng dù FIRST ôn sai; giữ nguồn khi ôn sai/bỏ trống/chữa sau nộp; hết nguồn sai thì priority về 0 nhưng biến thể còn trong ALL; LEARN sai lại thêm nguồn; topic/owner/retire; count ít hơn yêu cầu; replay/resume và kho không bị lặp mãi cùng mười câu đầu.

## Bằng chứng và giới hạn

- Log/ảnh bị Git ignore tại `.test-state/review-choices/`: `backend-native.log`, `backend-final.log`, `backend-first-reports/`, `browser-final.log`, `e2e-final-build.log`, `desktop.png`, `mobile.png`.
- Lệnh gắn socket Docker vào container test bị xét duyệt tự động từ chối, không thực thi. Đã dùng Maven native/Testcontainers để hoàn thành kiểm thử, không gắn socket vào container. Wrapper native không khởi động được trong môi trường này; dùng `mvn.cmd` có sẵn. Script Python kiểm định toàn phần chưa chạy do máy không có Python mặc định; phần kiểm tra hợp đồng ở trên là kiểm tra tĩnh Node và test DTO backend.
- Không đo tải với lịch sử rất lớn. Danh sách sai được suy ra khi đọc từ lịch sử đã nộp; chưa có bảng projection riêng cho nguồn sai. Các trường lịch/mục tiêu cũ vẫn theo P0, khác với số nguồn sai của ba lựa chọn mới.
- Không nghiệm thu lại toàn bộ backend/browser của hệ thống, không tuyên bố lịch ôn đã được đo hiệu quả học tập.
- Môi trường E2E riêng được dừng sau kiểm tra, giữ volume test. Không cập nhật/restart demo, không sửa volume thật, không commit/push.
