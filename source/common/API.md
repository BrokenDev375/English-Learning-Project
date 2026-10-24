# API M01 — English Learning

HTTP JSON cùng origin; cookie JSESSIONID HttpOnly. GET /api/auth/csrf trả token/headerName; POST/PATCH/DELETE cần header CSRF. Lỗi có code, message và fieldErrors nếu có. Không truyền passwordHash/securityVersion ra response.

## Tài khoản

- POST /api/auth/register: {fullName,email,password}; 201 User; chỉ STUDENT. Trường role/status giả mạo bị 400.
- POST /api/auth/login: {email,password}; 200 User + cookie; sai mật khẩu/tài khoản không hoạt động trả 401.
- GET /api/auth/me: 200 User, 401 nếu không còn phiên hợp lệ.
- POST /api/auth/logout: 204, thu hồi phiên hiện tại và socket gắn với phiên.
- GET /api/health: trạng thái backend.

User: {id,email,fullName,role,status,createdAt}. Role: STUDENT/TEACHER/ADMIN; status: ACTIVE/LOCKED/DELETED. Email chuẩn hóa, duy nhất; mật khẩu tối đa 72 byte UTF-8.

## Quản trị

Chỉ ADMIN còn hoạt động:
- GET /api/admin/users?q=&role=&status=&page=0&size=20
- PATCH /api/admin/users/{id}/role: {role}
- PATCH /api/admin/users/{id}/status: {status:ACTIVE|LOCKED}
- DELETE /api/admin/users/{id}: 204, xóa logic, lặp lại không tạo tác dụng khác.

Danh sách trả {items,totalElements,page,size,totalPages}; size từ 1–50, q tối đa 100 ký tự; sort id giảm dần. Mutation trả User nếu có body. 400 input sai; 401 hết/thu hồi phiên; 403 trái quyền/CSRF; 404 không có user; 409 tự quản lý hoặc thay đổi user đã xóa; 503 DB chưa truy cập được.

Không tự quản lý tài khoản admin đang dùng. Không phục hồi tài khoản DELETED. Mọi thay đổi quyền/trạng thái tăng security_version và thu hồi tất cả phiên sau commit. Cùng giá trị hiện tại không thu hồi thêm; retry DELETE thành công 204.

## WebSocket M01

/ws/events dùng cookie đăng nhập và danh sách origin được phép. Mở kết nối trả {version:1,type:CONNECTED,userId}. Client gửi {version:1,type:HEARTBEAT,requestId:"uuid"} hoặc HELLO; server trả {version:1,type:ACK,requestId} hoặc ERROR kèm code. Message tối đa 4 KiB; mã request tối đa 64 ký tự.

Khóa/xóa/đổi quyền, logout hoặc session hết đóng kết nối (1008 SESSION_REVOKED). Client kiểm tra lại /api/auth/me khi bị đóng vì quyền. M06 đã bổ sung heartbeat deadline, SUBSCRIBE/UNSUBSCRIBE đúng owner, ATTEMPT_CHANGED sau commit và presence riêng; xem [hợp đồng M06](M06_API.md).

## Hợp đồng M02–M03 để triển khai

M01 ở trên là API đang chạy. M02 bước 1 đã có GET/POST `/api/author/topics`, `/api/author/objectives`, `/api/author/units` và PUT `/{id}` tương ứng. Bước 2 thêm POST `/api/assets` (multipart file + requestId), GET `/api/assets/{id}` có quyền/Range và vòng đời `/api/author/materials`. Bước 3 thêm vòng đời tương tự cho `/api/author/questions` và `/api/author/lessons`: danh sách/tạo, chi tiết/version, mở/lưu/bỏ draft, preview, publish và retire; POST `/api/author/questions/{id}/copy`; GET `/api/author/units/{id}`; PUT `/api/author/units/{id}/lesson-order` trả Unit. GET `/api/topics`, `/api/objectives`, `/api/units`, `/api/units/{id}`, `/api/lessons/{id}` trả danh mục/giới thiệu active published, không trả ngân hàng/khóa đáp án. Mutation dùng requestId/revision theo DTO; response riêng tư có Cache-Control private, no-store. Bước 4 đã hoàn thành seed 6 bài/90 câu, không thêm endpoint hoặc migration. M03 chưa triển khai. Xem [tiến độ](../../docs/M02_PROGRESS.md), [bàn giao](../../docs/M02_M03_HANDOFF.md), [quy tắc](M02_M03_API.md), [OpenAPI 1.0.0](openapi-m02-m03.json) và [ví dụ JSON](examples-m02-m03.json).
