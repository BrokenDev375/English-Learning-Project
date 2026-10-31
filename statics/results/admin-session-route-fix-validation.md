# Sửa nhãn hoạt động và đồng bộ tab lúc bắt tay

Ngày 29/10/2026, nhánh feature/admin-session-monitoring, working tree sau runtime nền `60fc657` và nghiệm thu bước 5. Giữ nguyên các thay đổi bước 5 chưa commit và file báo cáo người dùng. Không sửa migration/DB, timeout, bộ chấm hoặc `.env`; chưa commit/push.

## Nguyên nhân và thay đổi

- Menu Tài khoản dùng `#/account` nhưng tracker thiếu route này, nên báo OTHER. Hồ sơ học tập dùng `#/profile`, tracker báo PROFILE nhưng bảng admin lại gắn nhãn Tài khoản. Bổ sung ACCOUNT cho `/account`, giữ PROFILE cho `/profile` và đổi nhãn thành Hồ sơ học tập. Parser activity và filter admin dùng chung tập giá trị; OpenAPI có ACCOUNT.
- Điều hướng giữ query từ màn hình trước có thể mang `attempt` sang menu khác. Tracker chỉ gửi Attempt context và áp dụng deniedAttempt khi còn ở `/learning`, để Tài khoản/Hồ sơ không bị server suy lại thành LEARN/REVIEW hoặc báo OTHER do ID cũ.
- Harness tái hiện tabId đổi sau gửi HELLO nhưng trước ACK: socket vẫn gắn ID cũ cho đến heartbeat 25s. Pending HELLO giữ tabId đã gửi; ACK nếu thấy khác ID hiện tại thì gửi HEARTBEAT mới ngay, không gia hạn idle.
- Tab trắng/“Thẻ ẩn danh mới” không mở app, không có tracker/socket, nên không được tính. Không đổi backend để tính những tab ngoài ứng dụng. Cả hai tab cùng mở app vẫn một tài khoản, một phiên cùng cookie và hai tab/socket.

## Kiểm tra

| Nhóm | Kết quả |
|---|---|
| Harness kết nối | **12/12 PASS**; ca ACK với tabId cũ fail trước fix và qua sau fix |
| Harness tracker | **11/11 PASS**; ACCOUNT fail trước fix; thêm chuyển màn hình giữ query/denied Attempt |
| Backend tập trung | **35/35 PASS**, 0 failure/error/skip, BUILD SUCCESS, 39.873s; `.test-state/session-route-fix-backend.log` |
| Browser | **19/19 PASS** trong một lượt, 55.6s; `.test-state/session-route-fix-browser.log` |
| TypeScript/Vite và OpenAPI JSON | PASS; Docker E2E build source cuối, không lỗi kiểu |
| Git diff whitespace | PASS |

Backend gồm SessionActivityApiTest 7, SessionActivityRegistryTest 9, AdminSessionServiceTest 8, AccountSocketHandlerTest 7, LiveConnectionsTest 4. Ca API mới xác minh ACCOUNT/PROFILE được nhận và lọc riêng, GET/context không tăng deadline. Không dùng số full 361/361 trước fix làm lượt backend mới.

Browser gồm 6 presence, 8 giám sát và 5 navigation. Ca mới bấm menu thật và đối chiếu nhãn bảng/chi tiết/filter, kiểm tra query Attempt còn sót, tab trắng không làm tăng count, mở app ở tab đó tăng 1→2 và đóng giảm 2→1. Ca mở bài LEARN thật rồi chuyển Tài khoản/Hồ sơ đảm bảo không gửi Attempt context ngoài runner. Hồi quy stale/error/reconnect, nhiều phiên, khóa/mất phản hồi và lịch sử điều hướng đều qua.

## Tái lập

Trong source/client: `npm run test:connection`, `npm run test:session`, `npm run build`.

```powershell
$env:E2E_PROJECT_NAME='english-learning-e2e-session-route-fix'
$env:E2E_APP_PORT='5182'
$env:E2E_API_PORT='8092'
$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='C:/Program Files/Google/Chrome/Application/chrome.exe'
npm run test:e2e -- admin-presence.spec.ts admin-session-monitoring.spec.ts navigation.spec.ts --workers=1
```

Backend image `english-learning-session-route-fix` target test; Maven `-Dtest=SessionActivityApiTest,SessionActivityRegistryTest,AdminSessionServiceTest,AccountSocketHandlerTest,LiveConnectionsTest`. Stack E2E riêng đã stop, giữ volume. Không rebuild/restart demo trong lượt sửa này.

Áp dụng vào môi trường đang mở: tại root `docker compose up -d --build`, reload và login lại các cửa sổ. Phải cập nhật cả backend/client vì ACCOUNT là activity bổ sung; cookie/session RAM cũ có thể hết sau backend restart. Mỗi tab cần mở cùng địa chỉ English Learning, không phải trang New Tab.

Không chạy lại full backend/browser, default 300s hoặc script restart/recreate trong lượt sửa hẹp này; các bằng chứng đó tại [bước 5](admin-session-step5-validation.md) thuộc source trước fix. Không suy số tab Chrome toàn trình duyệt từ số tab ứng dụng có socket.
