# Hiển thị trạng thái thí sinh — 30/10/2026

Nhánh `feature/online-contests`. Thay đổi chỉ frontend, không API/entity/migration hoặc luật thi. Cập nhật riêng client tại 15223 bằng `--no-deps`; không khởi động lại backend, giữ session thi.

## Hành vi

- Cột **Trạng thái thi** không chỉ hiện APPROVED/Đã duyệt. Dùng metadata hiện có và giờ server để chiếu: Chờ duyệt, Không được duyệt, Chưa vào phòng, Đang chờ thi, Đang làm bài, Đã nộp/chốt bài, Chờ chốt bài, Vắng thi, Đã thu hồi quyền thi. Có nhãn cuộc thi hủy/lưu trữ khi phù hợp.
- Quyền dự thi và kết nối hiển thị riêng. Đang kết nối phòng thi không đồng nghĩa đang làm bài; người đã nộp hoặc bị thu hồi không hiện đang làm. Kết nối dùng online/lastSeenAt với cửa sổ 45 giây hiện có, không suy đoán tab đích.
- Trên điện thoại bảng chuyển thành thẻ có nhãn từng mục, nên trạng thái không bị khuất ở cột phải. Bỏ dòng online dưới tên dễ nhầm thành trạng thái dự thi.
- Đây là nhãn hiển thị, không tạo trạng thái DB mới và không thay bộ lọc nghiệp vụ của API. Server vẫn quyết định quyền, deadline, chấm/nộp và thu hồi.

## Kiểm tra

- TypeScript/Vite/Docker build PASS; client health PASS. Chỉ build lại frontend, không chạy lại backend test vì không thay backend.
- **2/2 browser PASS** trong một lượt, **32.0s**:
  - Luồng thật đăng ký/duyệt/check-in: admin thấy Đang chờ thi → Đang làm bài; ba lần rời màn, phạt, cảnh báo, thu hồi và audit vẫn chạy.
  - Render các trạng thái đăng ký, tiến trình, kết nối bằng metadata GET có kiểm soát; đồng thời phân biệt đang kết nối và không có kết nối. Ở 390px còn đọc được trạng thái, không tràn ngang.
- Ảnh kiểm tra `.test-state/contest-monitor-states-mobile.png`, không đưa trace/credentials vào Git.
- `git diff --check` đạt (cảnh báo chuẩn hóa CRLF/LF không phải lỗi whitespace).

Tái hiện trên môi trường test riêng:

```powershell
# source/client
npm run test:e2e -- e2e/online-contests.spec.ts --grep 'tab departures|monitor separates'
```

Các số kiểm thử backend/search trước đó nằm trong biên bản riêng, không dùng làm bằng chứng mới cho thay đổi giao diện này.
