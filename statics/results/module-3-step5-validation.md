# M03 bước 5 — giao diện học và luyện tập

Ngày: 19/10/2026. Nền Git `2f09404`, nhánh `feature/m03-learning-attempts`. Thay đổi bước 5 chưa commit. Không đổi backend/migration/dependencies. Môi trường Windows, Node 24.15.0/npm 11.14.0, Playwright 1.64.0 dùng Chrome cài sẵn; backend/MySQL/nginx thật qua Docker Compose riêng.

## Kết quả

| Kiểm tra | Kết quả |
|---|---|
| `npm ci` theo lockfile hiện có | PASS, không đổi package-lock |
| `npm run build` bản cuối | PASS — TypeScript và Vite production, 52 modules |
| E2E M03 tập trung sau sửa harness | 11/11 PASS, 25.8 giây |
| Toàn bộ E2E với mã cuối và hai ca M03 bổ sung | **46/46 PASS**, 0 failed/skipped, 1.7 phút; gồm **13 M03 + 33 hồi quy M01/M02/điều hướng** |
| `git diff --check`, whitespace file mới và liên kết tài liệu | PASS |

Mỗi lượt E2E tự build image từ mã hiện tại, chờ seed xong, chạy trình duyệt rồi dừng các container. Project/volumes `english-learning-e2e`, UI 5174/API 8081; không build/restart demo hoặc thao tác DB demo. Log ignored: `.test-state/m03-step5-focused-final.log`, `.test-state/m03-step5-all-e2e.log`. Backend 136/136 ở bước 4 là bằng chứng lịch sử; không chạy lại toàn bộ JUnit trong bước giao diện này. E2E hiện tại gọi backend thật đã build từ source.

Lượt tập trung đầu có hai lỗi harness: exact label của select lẫn text option và assertion disabled trên fieldset. Đã dùng role combobox và kiểm tra radio con bị disabled; lượt 11 test và lượt toàn bộ đều qua. Windows sandbox chặn tiến trình con của Vite; build/E2E chạy với quyền thực thi ngoài sandbox. Cài npm cần bỏ chế độ offline của môi trường; không sửa cấu hình hoặc lockfile dự án.

## Phạm vi đã triển khai

- `LearningWorkspace`: catalog LEARN, form PRACTICE, lịch sử lọc mode/state và phân trang. TEACHER/ADMIN vẫn khám phá giới thiệu, không gọi attempt/progress hoặc có nút tạo lượt.
- `AttemptRunner` với câu chọn một đáp án/điền/ghép theo ID, điều hướng câu, answered count, hướng dẫn/mục tiêu và học liệu. Ghép bảo đảm một-một; điền giới hạn 200 ký tự theo API.
- `LearningMaterial` dùng public asset URL: đọc/ảnh/audio, clamp start/end/duration, giữ vị trí tuyệt đối của audio chung và lưu cùng playerState. Có tải lại khi file lỗi.
- `AttemptSession`: manual save, tuần tự hóa ghi, giữ exact pending command và generation. Sau ACK phải GET đối soát; không thay bản nhập mới hơn bằng ACK cũ. 409 giữ hai bản để người dùng xác nhận; 401 dừng ghi và cho đăng nhập lại; 503/timeout không báo đã lưu.
- Cache trong bộ nhớ theo user/Attempt, giữ qua điều hướng SPA, phân biệt các lượt, xóa khi đăng xuất chủ động hoặc đổi user. Route chỉ chứa ID/bộ lọc; reload/đóng tab cảnh báo nếu còn dữ liệu/thao tác chờ. Không thêm localStorage/IndexedDB/outbox.
- Submit full snapshot có xác nhận số câu thiếu/câu hướng dẫn chưa check; cho nộp sớm. Kết quả FIRST/final, điểm, hoàn thành, keys/explanation/transcript; form CORRECTION giữ kết quả gốc. Catalog nối tiến độ và updateAvailable.

## Bằng chứng 13 ca M03

1. LEARN thật từ catalog, đủ ba renderer, hint/check chỉ GUIDED, feedback không transcript. Trả lời mọi câu, lưu, F5/back/forward, nộp hoàn thành, chữa rồi GET result giống bản nộp; quay lại Unit thấy COMPLETED. Kiểm tra responsive 390px và chụp kết quả desktop/mobile.
2. PRACTICE chọn Topic/count, không hiện hint/check, nộp trống, đọc score 0 và tải đúng Attempt sau F5.
3. PUT draft đã commit rồi browser mất response: giữ key/payload cũ, vẫn nhập đáp án mới, rời/quay lại SPA, replay cũ không ghi đè bản nhập mới; lần save mới mới lưu đáp án mới lên server.
4. Hai tab cùng revision: tab sau nhận 409, giữ radio đã nhập và bảng so sánh. Chỉ sau xác nhận giữ bản nhập và bấm save mới thay bản server.
5. Submit đã commit nhưng response bị bỏ: khóa câu trả lời/thao tác sau, replay cùng key nhận cùng Submission ID, không nộp lần hai.
6. Fixture 401 rồi đăng nhập lại cùng user giữ đáp án và payload chờ; fixture 503 cho thao tác kế tiếp giữ trạng thái chưa xác nhận; cả hai replay đúng key/payload.
7. File audio seed thật và DB playerState thật; fixture chỉ đổi metadata start=1/end=3 để kiểm tra clamp. Khôi phục vị trí 2 giây sau F5, tua ngoài đoạn về tối đa 3 giây, mobile không tràn ngang.
8. TEACHER khám phá bài mà không có nút học thật hoặc request attempt/progress.
9. POST create đã commit rồi bỏ response; chuyển sidebar rồi trở lại vẫn gửi đúng payload/key, chỉ có một Attempt.
10. Radio thao tác bằng bàn phím; logout có xác nhận bỏ bản chưa lưu. Người học khác mở URL cũ nhận 404 và không thấy form/câu trả lời cũ.
11. ADMIN khám phá giới thiệu mà không có nút tạo lượt hoặc request attempt/progress.
12. Timeout 15 giây thực của fetch: server đã commit, fixture trì hoãn response 17 giây; client khóa ghi kế tiếp và retry nguyên payload/key thành công.
13. Fixture prompt/TEXT dài không có khoảng trắng, mobile 390px không tràn trang; input fill 250 ký tự chỉ nhận 200 theo giới hạn UI/API.

Ba ảnh đã xem để rà soát bố cục: `source/client/test-results/module-3-result-desktop.png`, `module-3-result-mobile.png`, `module-3-runner-mobile.png`. Thư mục test-results bị ignored và được Playwright thay ở lượt sau; bản sao phiên nghiệm thu ở `.test-state/m03-step5-*.png`.

## Tái hiện

Từ `source/client`, sau `npm ci`:

```powershell
npm run build
$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH = 'C:/Program Files/Google/Chrome/Application/chrome.exe'
npm run test:e2e -- module-three-learning.spec.ts
npm run test:e2e
```

Nếu không dùng Chrome có sẵn, cài Chromium cho Playwright và bỏ biến executablePath. Không đặt E2E_BASE_URL vào cổng demo. Hướng dẫn thao tác UI và phục hồi: [M03_UI_SMOKE](../../docs/M03_UI_SMOKE.md).

## Giới hạn và bước tiếp theo

Các ca lỗi HTTP/response loss dùng interception của Playwright; không phải benchmark mạng hoặc thử hỏng DB vật lý. Metadata clip và nội dung dài là fixture ở phía trình duyệt, không phải thao tác xuất bản học liệu mới. Chưa kiểm tra đầy đủ ma trận bước 6, recreate/restart DB giữ tiến độ hoặc seed lại khi đã có dữ liệu M03.

UI dùng lưu chủ động. Bản nhập/payload chờ chưa lưu bền vững trên trình duyệt: bỏ cảnh báo rồi reload/đóng tab có thể mất phần chưa commit. REVIEW provider thật, XP và offline outbox lần lượt thuộc M04/M05/M06. Không đánh dấu bước 6 hoàn tất chỉ vì E2E hồi quy đã qua.

## Chỉnh trang giới thiệu sau phản hồi giao diện — 19/10/2026

- Thêm `LessonIntroduction`: giới hạn chiều rộng 1000px, tóm tắt số câu, nhãn hoàn thành có dấu ✓, hướng dẫn cụ thể và lọc TEXT trùng mô tả. Các nút cùng hàng trên desktop, xếp dọc trên mobile.
- CTA phân biệt Bắt đầu học/Tiếp tục làm/Học lại từ trạng thái lượt thực tế. COMPLETED không đồng nghĩa không có lượt học lại dở. Thêm Xem kết quả gần nhất, tra đúng Lesson root qua các phiên bản và chọn theo submittedAt; chữa lượt cũ không đẩy nó thành lần nộp gần nhất.
- Build TypeScript/Vite đạt. Docker E2E với nguồn mới: **28/28 PASS**, 1.4 phút, gồm **14 M03 + 9 biên soạn + 5 điều hướng**. Đây là nhóm liên quan, không phải lượt chạy lại toàn bộ suite 47 ca sau khi thêm test mới.
- Ca mới kiểm tra mô tả chỉ hiện một lần, số câu, vị trí các nút desktop, mobile 390px không tràn ngang, học lại/tiếp tục trên bài đã hoàn thành và kết quả gần nhất sau khi sửa bài cũ.
- Đã xem ảnh desktop/mobile; bản sao ignored tại `.test-state/m03-intro-desktop.png`, `.test-state/m03-intro-mobile.png`. Log `.test-state/m03-intro-ui.log`.
- Đã build và cập nhật riêng client local bằng `docker compose --file compose.yml build client`, rồi `up -d --no-deps --no-build client`. HTTP 5173 trả bundle chứa giao diện mới; server/MySQL giữ nguyên container ID và vẫn healthy. Không chạy test ghi dữ liệu vào demo.
- Chưa commit các thay đổi. Hướng dẫn UI/README/progress/AGENTS đã cập nhật theo nhãn và thành phần mới.
