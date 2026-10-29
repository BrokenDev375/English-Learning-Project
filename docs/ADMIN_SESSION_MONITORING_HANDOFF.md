# Bàn giao giám sát phiên và timeout

Nhánh `feature/admin-session-monitoring`, nền runtime `60fc657`. Hợp đồng tại [API](../source/common/ADMIN_SESSION_MONITORING_API.md), kết quả theo từng lượt tại [nghiệm thu bước 5](../statics/results/admin-session-step5-validation.md).

## Chạy và kiểm tra giao diện

Tại thư mục gốc, `docker compose up -d --build`. Rebuild/restart backend làm mất phiên RAM và cần login lại; dữ liệu đã commit và volume giữ nguyên. Không dùng `down -v`. Không chép cấu hình timeout test vào `.env` demo.

ADMIN mở Quản lý tài khoản. Bộ lọc Giám sát hoạt động và Danh sách tài khoản độc lập; số tổng giám sát tính trên bộ lọc trước phân trang. Xem phiên mở khung chọn phiên/tab; Escape đóng và focus về nút mở, mobile có cuộn trong khung/bảng. Khóa gọi cùng M01, thu hồi mọi phiên của tài khoản sau commit. Mở khóa cần login mới. Khi mất phản hồi, để giao diện GET đối soát hoặc bấm Kiểm tra trạng thái; không suy “thất bại” từ timeout và không resend liên tục.

Một user nhiều tab vẫn tính một tài khoản online. Phiên không có socket có thể vẫn còn hạn. IDLE là từ 60s không tương tác; hết phiên mặc định 300s, cảnh báo 30s. Trạng thái chức năng/visibility là tín hiệu client, không chứng minh đang chú ý học. Không có chức năng thi riêng được suy ra từ LEARN/REVIEW.

Để thử số tab, cả hai tab phải mở địa chỉ English Learning và có kết nối. “Thẻ ẩn danh mới”, about:blank hoặc website khác không được tính. Mở hai tab trong cùng cửa sổ ẩn danh dùng chung phiên; cửa sổ thường và cửa sổ ẩn danh là hai phiên riêng. ACCOUNT hiển thị Tài khoản; PROFILE hiển thị Hồ sơ học tập, kể cả URL còn query attempt từ màn hình học trước đó.

Hết phiên giữ nháp/pending đã commit IndexedDB, dừng sender và fence callback cũ; cùng user login lại mới đối soát/replay. Logout chủ động vẫn theo quy tắc purge hiện có. Không có login offline, tải trước toàn bộ media hoặc đảm bảo giữ bản chưa commit IndexedDB.

## Tái lập kiểm thử

Backend dùng target `test` của Dockerfile và MySQL Testcontainers; có Docker socket và `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. Dùng `mvn -B -ntp test`, lưu XML Surefire vào thư mục riêng. Không dùng tên volume demo để nghiệm thu.

Trong `source/client`, chạy `npm run build`, `npm run test:connection`, `npm run test:session`, `npm run test:recovery-storage`. Với Chrome có sẵn, đặt `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH` theo máy. Các lệnh dưới sử dụng stack/volume test riêng; runner tự stop sau chạy, giữ volume.

Hồi quy browser đầy đủ, không chờ timeout thật trong từng ca:

```powershell
$env:E2E_PROJECT_NAME='english-learning-e2e-session-step5-full'
$env:E2E_APP_PORT='5182'
$env:E2E_API_PORT='8092'
npm run test:e2e -- --grep-invert '@idle-short|@idle-default'
```

Idle/recovery rút ngắn riêng — không chạy chung suite bình thường:

```powershell
$env:E2E_PROJECT_NAME='english-learning-e2e-session-step5-idle'
$env:E2E_APP_PORT='5185'
$env:E2E_API_PORT='8095'
$env:E2E_IDLE_TESTS='1'
$env:SESSION_IDLE_TIMEOUT_SECONDS='20'
$env:SESSION_IDLE_WARNING_SECONDS='5'
$env:SESSION_IDLE_SWEEP_MS='1000'
npm run test:e2e -- session-idle.spec.ts
```

Timeout mặc định 300s thật — chạy trong PowerShell mới hoặc xóa các override timeout 20s ở trên:

```powershell
Remove-Item Env:SESSION_IDLE_TIMEOUT_SECONDS,Env:SESSION_IDLE_WARNING_SECONDS,Env:SESSION_IDLE_SWEEP_MS -ErrorAction SilentlyContinue
$env:E2E_PROJECT_NAME='english-learning-e2e-session-default'
$env:E2E_APP_PORT='5183'
$env:E2E_API_PORT='8093'
$env:E2E_DEFAULT_IDLE_TESTS='1'
npm run test:e2e -- session-default-idle.spec.ts
```

Restart/recreate, receipt/ledger/media và khóa khi đang REVIEW:

```powershell
node scripts/verify-session-acceptance.mjs
```

Script cố định project `english-learning-session-acceptance`, cổng 5184/8094; không dùng stack M06/demo cũ. Script giữ volume, không `down -v`, không log mật khẩu. `.env` chỉ được đọc nội bộ để login bootstrap admin; JSON evidence không ghi credentials. Khi chạy lại trên volume cũ tạo user mới, đối chiếu số liệu trước/sau trong chính lượt đó.

## Giới hạn vận hành

Registry/session/presence thuộc một backend RAM; restart mất auth/presence nhưng không mất dữ liệu đã commit. Invalidation gom tối đa một lần/giây, poll visible 15s bù sự kiện thiếu, heartbeat/tab window 75s. Không có lịch sử giám sát bền vững hoặc tổng hợp nhiều backend. Lỗi giữ snapshot có thời điểm cập nhật, không dựng số 0 giả. Các trường hợp thiết bị ngủ thật, tải lớn và tương tác do bot không được bảo đảm bởi kết quả local; xem giới hạn cụ thể trong biên bản.
