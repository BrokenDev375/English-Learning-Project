# M02 — quyền sở hữu Unit, đọc file cũ và cập nhật audio đồng loạt

Ngày kiểm tra: 19/10/2026. Nhánh đích: `M02`.

## Thay đổi

1. Khi ADMIN tạo bài trong Unit của giảng viên, Lesson nhận `ownerId` của Unit. Chủ Unit tiếp tục được sửa bài và sắp xếp bài trong Unit.
2. Giảng viên được đọc file của phiên bản Material PUBLISHED cũ trên tài liệu dùng chung còn active published. Công bố audio/ảnh mới không làm mất quyền đọc file cũ của các câu/bài đang tái sử dụng. Nháp riêng và tài liệu đã ngừng cấp vẫn bị giới hạn quyền.
3. Tài liệu AUDIO đang công bố có bảng chọn câu/bài để chuyển sang phiên bản audio mới nhất. Hệ thống tạo/xuất bản QuestionVersion và LessonVersion mới, giữ nguyên phiên bản cũ. Có xác nhận nội dung/đáp án/transcript, kiểm tra quyền, chặn bản nháp và yêu cầu tải lại nếu preview cũ.

Hợp đồng API bổ sung ở [M02_M03_API.md, mục 9](../../source/common/M02_M03_API.md) và [OpenAPI](../../source/common/openapi-m02-m03.json). Không thêm bảng hoặc thay migration.

## Kết quả đã chạy

| Nhóm | Kết quả | Phạm vi |
| --- | --- | --- |
| AuthoringApiTest | 17/17 PASS | Gồm hồi quy owner Unit, quyền file PUBLISHED cũ, bảo vệ nháp/ngừng cấp và các ca biên soạn sẵn có |
| CatalogApiTest | 8/8 PASS | Danh mục, quyền và revision |
| AudioUpdateApiTest | 5/5 PASS | Cập nhật có chọn lọc, giữ lịch sử/owner/origin, phân quyền, chặn nháp, token cũ, rollback và cạnh tranh cập nhật |
| Playwright audio-update.spec.ts | 2/2 PASS | Chọn bài kéo theo câu, xác nhận nội dung, màn hình 390×844, kiểm tra API sau cập nhật; mất ACK rồi retry cùng payload/requestId không tạo version trùng |
| Frontend build | PASS | TypeScript và Vite build trong Docker |
| Backend package | PASS | Java/Maven đóng gói ứng dụng |

Tổng backend ở lượt này: **30 test, 0 thất bại, 0 lỗi, 0 bỏ qua**. Đây là các bộ liên quan đến thay đổi; không phải kết quả chạy lại toàn bộ backend/E2E của dự án.

Backend chạy HTTP thật với MySQL 8.4 Testcontainers, file lưu tại thư mục thử riêng, service/repository/transaction thật. `AudioUpdateApiTest` thay riêng `MediaInspector` bằng mock để không yêu cầu ffprobe trên Windows; không mock repository hoặc transaction. Playwright chạy app Docker có ffmpeg/ffprobe thật và upload WAV thật, đối chiếu phiên bản cũ/mới qua API.

Ca rollback cố ý làm lỗi DB ở giai đoạn công bố Lesson sau khi đã ghi QuestionVersion mới: mọi thay đổi và receipt đều rollback. Bỏ lỗi DB rồi gửi lại đúng command thành công. Ca cạnh tranh hai tác giả được cấp quyền (owner/ADMIN) cho đúng một kết quả 200 và một 409, không tạo thêm bộ version thứ hai.

Ca mất ACK cho máy chủ commit thành công rồi ngắt phản hồi ở trình duyệt. UI giữ lựa chọn, requestId và payload, khóa thao tác sửa Material trong lúc chưa biết kết quả, thử lại nhận kết quả đã lưu. Các câu/bài chỉ tăng một version.

## Tái hiện

Docker Desktop đang chạy; tại `source/server`:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=AudioUpdateApiTest,AuthoringApiTest,CatalogApiTest' test
```

Tại `source/client`, có dependency và Chromium/Chrome:

```powershell
npm run test:e2e -- e2e/audio-update.spec.ts
```

Lệnh UI mặc định dùng stack Docker E2E riêng theo cấu hình dự án. Nếu đặt `E2E_BASE_URL`, phải trỏ tới môi trường kiểm thử có tài khoản TEACHER demo: test tạo học liệu qua API, rồi ngừng cấp các bài/câu/tài liệu vừa tạo trong teardown. Không chạy test lên dữ liệu thật.

Lượt kiểm tra này dùng stack riêng `english-m02-audio-check-20261019` tại `127.0.0.1:5175`; database và asset volume riêng. Không thay `.env`, dữ liệu hay cấu hình cổng của app đang dùng.

## Phạm vi cần biết

- Không tự chỉnh lại đáp án/nội dung khi file nghe đổi ý nghĩa. Giảng viên cần kiểm tra và xác nhận trước khi áp dụng.
- Không tự sửa bài của giảng viên khác hoặc ghi đè câu/bài đang có nháp. Chủ nội dung có thể mở tài liệu dùng chung để áp dụng cho phần của mình.
- M03 chưa được triển khai trong nhánh M02. Việc giữ lịch sử đã được kiểm tra bằng các snapshot version; chưa có lượt làm thực tế để chạy kiểm thử Attempt end-to-end. M03 cần tiếp tục pin version đã cấp theo hợp đồng.
- Retry UI đang giữ command trong bộ nhớ của màn hình. Nếu tải lại trang/đổi màn, cần đọc lại trạng thái máy chủ trước khi thực hiện thao tác mới; không có hàng đợi offline bền vững cho biên soạn.
