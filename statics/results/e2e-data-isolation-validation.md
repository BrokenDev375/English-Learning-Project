# Dữ liệu E2E xuất hiện trong danh mục học viên

Ngày 18/10/2026. Tên `Unit e2e-authoring-1792225374106` và các tên tương tự là title do test tự tạo bằng `Unit ` + prefix `e2e-authoring-` + `Date.now()`. Phần số là dấu thời gian để các lần chạy không trùng nhau.

## Xác minh nguyên nhân

Đối chiếu SQL trực tiếp nối `units → users/topics → lessons → lesson_versions` và code fixture cho thấy:

- Title trong DB đúng là chuỗi tên test; API/UI hiển thị chính giá trị đó. Tên tiếng Việt của bộ chuẩn vẫn đúng Unicode. Không phải lỗi charset, thay tên bằng ID hoặc lỗi ánh xạ cột.
- Có 11 bài test active published trong 6 Unit test. Chủ sở hữu khớp email `e2e-authoring-<13 chữ số>@english.local`, họ tên fixture, code Topic, tên Unit và tên bài của cùng lượt chạy; Unit/Lesson không có seedKey.
- Test trước đây chạy vào app demo 5173 và không ngừng cấp bài sau khi công bố. Learner catalog đúng quy tắc hiện bài active published, vì vậy nội dung test cũng lọt vào danh mục.

## Xử lý dữ liệu hiện tại

Đã dùng API ADMIN để ngừng cấp đúng 11 bài đã xác minh. Không đổi tên bài test thành bài thật; không xóa root/version, tài khoản, file hoặc volume. Đã lưu snapshot cục bộ trước thao tác để đối chiếu.

Kết quả kiểm tra thực tế:

| Kiểm tra | Kết quả |
|---|---|
| Danh mục học viên sau xử lý | 3 Unit chuẩn, tổng 6 bài; không có Unit E2E |
| Material/Question/Lesson và Topic/Objective/Unit của giáo viên demo | Response JSON trước/sau bằng nhau |
| 11 bài test sau retire | RETIRED; published version và draft không đổi |
| Đọc lại Version ID cũ của 11 bài | Nội dung bằng trước xử lý |

Các root test vẫn có thể được ADMIN xem trong lịch sử/kho tác giả, nhưng không còn là bài active để cấp cho học viên. Không thêm điều kiện giấu title có chữ `e2e` trong code nghiệp vụ.

## Ngăn tái diễn

- `npm run test:e2e` mặc định gọi [runner](../../source/client/scripts/run-e2e.mjs), khởi động Docker project **english-learning-e2e** ở frontend 5174/backend 8081. MySQL và asset volume có tên project riêng, tách khỏi **english-learning-project** đang dùng ở 5173/8080.
- Runner đợi seed hoàn tất rồi mới chạy Playwright; khi xong dừng riêng stack E2E và giữ volume test cho việc chẩn đoán. Không dừng/xóa volume demo.
- Chỉ khi chủ động đặt `E2E_BASE_URL` mới dùng URL có sẵn; không tự đưa mặc định về 5173. Playwright config trực tiếp cũng mặc định 5174.
- Fixture authoring có `afterAll`: đăng nhập đúng tác giả của lượt chạy, kiểm tra owner/Unit/tên bài, ngừng cấp bài và câu đã công bố; xác nhận Unit test không còn trong API học viên.
- Kiểm tra seed đọc toàn bộ các trang Unit và xác nhận không có Unit mang mẫu tên fixture. Không dùng bộ lọc giao diện để che dữ liệu test khỏi assertion.

## Kiểm tra sau thay đổi

| Kiểm tra | Kết quả |
|---|---|
| Docker stack mới với DB/file volume riêng, build và seed | PASS |
| Playwright đầy đủ trên stack riêng | 31/31 PASS, 2 phút |
| afterAll của authoring và assertion không có Unit fixture trong learner catalog | PASS |
| Dừng stack riêng sau test | Cả client/server/MySQL E2E đều stopped |
| App demo sau khi chạy E2E | Vẫn running, MySQL/server healthy; 3 Unit/6 bài, không có title E2E |
| Snapshot M02 của giáo viên demo trước/sau cả lần chạy E2E | Bằng nhau |
| git diff --check | PASS |

Bộ backend không thay đổi trong lần sửa này. Kiểm tra giữ nguyên DB demo sau test được chạy qua API 5173, đối chiếu snapshot với trước xử lý.

Log/snapshot cục bộ ignored: `.test-state/test-curriculum-audit-before.json`, `.test-state/e2e-cleanup-backup.json`, `.test-state/e2e-cleanup-after.json`, `.test-state/isolated-e2e.log`. Script kiểm tra sau chạy: `.test-state/verify-shared-demo.py`.

Tái hiện tại `source/client`, Docker Desktop và Chromium có sẵn:

```powershell
npm run test:e2e
```

Mở app đang sử dụng tại localhost:5173 và F5 để nhận danh mục mới.
