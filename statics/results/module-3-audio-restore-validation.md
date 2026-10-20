# M03 — khôi phục vị trí nghe sau xung đột bản nháp

Ngày kiểm tra: 20/10/2026. Nhánh: `feature/m03-learning-attempts`, nền `9b0f1e0`.

## Vấn đề và kết quả sửa

Hai tab đang làm cùng Attempt. Sau khi tab A lưu audio ở giây 7, tab B đang giây 2 nhận 409 và chọn **Dùng bản máy chủ**. Trước sửa, đáp án/playerState đã nhận bản server nhưng audio DOM vẫn giây 2. Thao tác phát/dừng tiếp theo có thể ghi lại vị trí local cũ.

`AttemptSession.playerRestoreVersion` là mốc UI nhận playerState từ server khi tải bản nháp hoặc chấp nhận bản server sau đối chiếu. Runner chuyển mốc này cho LearningMaterial; player cập nhật điểm khôi phục và seek khi metadata đã sẵn sàng. Giữ bản local và ACK lưu thông thường không phát mốc khôi phục, để bảo vệ vị trí nghe mới hơn trong lúc chờ phản hồi. Audio tiếp tục dùng phần tử/file đang tải, không cần nạp lại nguồn chỉ để đổi vị trí.

Phạm vi thay đổi: session/runner/player, test trình duyệt và tài liệu. HTTP DTO, backend, database và migration giữ nguyên.

## Bằng chứng đã chạy

| Kiểm tra | Kết quả |
| --- | --- |
| Ca hai tab dùng server, trước sửa | FAIL đúng lỗi: Expected 7, Received 2 |
| Cùng ca sau sửa, backend và WAV thật | PASS; audio về giây 7, server vẫn giây 7, bản nháp UI sạch |
| Hai tab giữ bản local | PASS; audio giữ giây 2, lưu tiếp server nhận giây 2 |
| Giữ chậm ACK lưu giây 2 sau commit, tua local tới giây 4 | PASS; ACK cũ không thay vị trí giây 4; server chỉ nhận giây 4 ở lần lưu tiếp theo |
| Toàn bộ `module-three-learning.spec.ts` | **17/17 PASS**, gồm 14 ca cũ và 3 ca mới, không fail/skip; thời gian Playwright 59,7 giây |
| `npm run build` | PASS: TypeScript và Vite production build |

Các ca hồi quy mới dùng tài khoản thử riêng, HTTP/API/MySQL và file audio seed thật. Ca ACK giữ phản hồi sau khi máy chủ đã commit; không giả kết quả lưu bằng response tự dựng. Các ca cũ về 401/503 và đoạn audio vẫn có fixture như mô tả ở báo cáo bước 5.

Không chạy lại JUnit hoặc toàn bộ các file E2E trong lần sửa riêng frontend này. Kết quả trên không phải kiểm tra tải hoặc nghiệm thu offline đầy đủ của M06.

## Môi trường và lệnh tái hiện

Windows, Chrome headless, frontend React/TypeScript; Docker Compose project riêng `english-learning-e2e`, MySQL 8.4 và media trong volume E2E. Cổng API 8081 đang có chương trình khác sử dụng nên lượt này dùng UI **15174**, API **18081**. App local vẫn ở **5173/8080**, không đổi `.env` và không xóa volume dữ liệu.

Từ `source/client`, Docker Desktop đang chạy và đã cài dependency:

```powershell
$env:E2E_APP_PORT='15174'
$env:E2E_API_PORT='18081'
$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='C:/Program Files/Google/Chrome/Application/chrome.exe'
Remove-Item Env:E2E_BASE_URL -ErrorAction SilentlyContinue
npm run build
npm run test:e2e -- module-three-learning.spec.ts
```

Runner build source hiện tại, đợi seed hoàn tất, chạy test rồi dừng stack E2E riêng. Không đặt E2E_BASE_URL tới demo 5173 để chạy các test có tạo dữ liệu.

Test tay bổ sung tại [UI-18 và UI-19](../../docs/M03_MANUAL_TEST_CASES.md). Sau khi frontend local được build lại, tải lại trang để nhận bundle mới; bản nháp đã lưu vẫn khôi phục từ server.
