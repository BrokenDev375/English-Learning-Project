# Sửa điều hướng Back/Forward

Cập nhật 18/10/2026. Lỗi được báo: chuyển các màn/tab trong app nhưng bấm Back của Brave lại về trang trình duyệt trước khi mở localhost.

## Nguyên nhân và thay đổi

App dùng state React để đổi màn nhưng không thêm lịch sử URL. Trình duyệt chỉ biết lần mở localhost, nên Back không có màn nội bộ để trở về.

Đã bổ sung điều hướng theo hash URL và History API, đồng bộ React khi Back/Forward hoặc hash thay đổi:

- Menu Trang chủ/Tài khoản/Quản trị/Quản lý học liệu/Khám phá bài học.
- Bốn khu vực học liệu và ba tab Chủ đề/Mục tiêu/Unit.
- Form danh mục, form mới/chi tiết tài liệu/câu/bài, màn sắp xếp bài trong Unit.
- Danh sách Unit → bài trong Unit → giới thiệu bài cho học viên.
- Nút quay lại trong app dùng lịch sử trước đó khi phù hợp; mở link trực tiếp vẫn có đường về danh sách.
- Sau khi tạo hoặc sao chép nội dung, URL được thay bằng ID đã lưu; không tạo thêm bước lịch sử cho mỗi lần lưu.
- Tải lại hoặc mở link trên tab khác đọc lại nội dung qua API có xác thực. History chỉ giữ địa chỉ màn/ID, không giữ snapshot đáp án, transcript hoặc root riêng tư.
- Đăng xuất/hết phiên vẫn yêu cầu đăng nhập; Back không khôi phục phiên đã kết thúc.

Không chặn người dùng rời ứng dụng: khi đã quay hết các màn nội bộ, Back tiếp tục theo lịch sử thật của trình duyệt. Những thao tác ở bản cũ chưa được ghi lịch sử không thể được dựng lại sau khi cập nhật. F5 đọc dữ liệu đã lưu trên server; nội dung đang nhập chưa lưu không trở thành bản nháp tự động.

## Kiểm tra đã chạy

| Kiểm tra | Kết quả |
|---|---|
| TypeScript/Vite build | PASS |
| Docker client build và recreate | PASS; local localhost:5173 đã dùng bản mới |
| Toàn bộ Playwright | 31/31 PASS, không failure, 1,4 phút |
| M01 hồi quy | 5/5 PASS |
| M02 hồi quy catalog/material/authoring/seed | 21/21 PASS |
| Điều hướng mới | 5/5 PASS |
| git diff --check | PASS |

Năm ca mới ở [navigation.spec.ts](../../source/client/e2e/navigation.spec.ts):

1. Sidebar và các tab danh mục: Back/Forward đúng màn, reload giữ tab; bấm lại tab đang mở không tạo bước lịch sử thừa.
2. Form danh mục/tài liệu: Back/Forward, reload, mở chi tiết bằng link trong tab mới, về danh sách; history không chứa dữ liệu học liệu.
3. Form câu/bài và sắp xếp bài theo Unit: Back/Forward và reload khôi phục đúng màn.
4. Học viên mở Unit/giới thiệu: Back từng cấp, Forward, reload và nút quay lại trong app.
5. Mở link rồi đăng nhập giữ màn yêu cầu; đăng xuất rồi Back vẫn hiện đăng nhập.

Luồng lưu nháp, mất ACK/thử lại, công bố, sao chép và giữ phiên bản cũ đều nằm trong 26 ca hồi quy chạy cùng lần. Không thay backend/migration trong bản sửa này; không chạy lại bộ backend vì thay đổi chỉ ở frontend. Kết quả backend trước đó được ghi trong [nghiệm thu seed M02](module-2-seed-validation.md).

Log cục bộ ignored: `.test-state/navigation-full-e2e.log`, `.test-state/navigation-deploy.log`; báo cáo Playwright ở `source/client/playwright-report`.

## Tự thử nhanh

1. Mở localhost:5173, nhấn Ctrl+F5 để nhận frontend mới rồi đăng nhập.
2. Trang chủ → Quản lý học liệu → Chủ đề → Mục tiêu học → Unit.
3. Bấm Back: Mục tiêu học → Chủ đề → danh mục Unit ban đầu → Trang chủ. Bấm Forward để đi lại.
4. Mở Tài liệu → một tài liệu; Back về kho, Forward mở lại; F5 vẫn ở chi tiết tài liệu đã lưu.
5. Khám phá bài học → một Unit → giới thiệu bài; Back từng cấp trong app.

Tái chạy tại `source/client`, app demo đang chạy và có Chromium/`PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`:

```powershell
npm run build
npm run test:e2e
```
