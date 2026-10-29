# Kiểm tra dữ liệu lớp demo — 27/10/2026

Nền source `fac44f4`, nhánh `main`; bổ sung `scripts/demo-classroom-data.mjs` và `scripts/seed-demo-classroom.mjs`. Môi trường local đang chạy `english-learning-main`, giao diện 5176/API 8083. Chỉ dùng tài khoản/học liệu giả lập để kiểm tra, giữ dữ liệu có trước.

## Kết quả

| Kiểm tra | Kết quả |
|---|---|
| `node --check scripts/seed-demo-classroom.mjs` | PASS |
| Chạy không có `--apply` | In kế hoạch, không nạp dữ liệu |
| Tạo/xuất bản 2 Unit, 4 Lesson, 36 Question, 4 READING Material qua API tác giả | PASS; preview không có lỗi; mỗi Lesson đã công bố đủ 9 slot |
| Đăng ký/đăng nhập 6 STUDENT giả lập | PASS |
| LEARN theo thứ tự vòng, nộp đầy đủ, xác nhận hoàn thành | PASS; 39 lượt nộp/39 bài hoàn thành tính theo từng học sinh |
| Đọc lại Result đã lưu | PASS; submissionId và scorePercent khớp phản hồi nộp |
| Nháp server cho 5 học sinh | PASS; đọc lại IN_PROGRESS và đúng 2 câu đã nhập/lượt |
| Đồng bộ M04/M05 | PASS; backlog thưởng hết; pendingSubmissions M04 bằng 0 |
| Hồ sơ thống kê | PASS; XP khớp M05, số lượt đã nộp khớp danh sách lượt |
| Bảng xếp hạng toàn hệ thống | PASS; có đủ 6 tên và XP tuần đúng; sắp xếp XP giảm dần |
| Nhóm League | PASS; 6 học sinh trong nhóm, có XP tuần |
| Chạy lại script cùng checkpoint | PASS; XP giữ nguyên 200/170/135/100/50/25 |
| Replay HTTP một request submit đã commit | PASS; cùng submissionId; không thêm lượt hoặc XP |

Các kết quả XP tương ứng Minh Anh, Hoàng Nam, Thu Hà, Gia Bảo, Lan Anh, Đức Minh. Học sinh có số bài hoàn thành lần lượt 10/9/8/6/4/2. Lượt nạp và các lần chạy lại đều thành công; report chi tiết có ID lượt và điểm từng bài ở `.test-state/demo-classroom/report.json` (Git ignore).

## Phạm vi và giới hạn

Đây là kiểm tra tích hợp HTTP trên ứng dụng local, không phải toàn bộ backend/browser regression. Không sửa mã nghiệp vụ hoặc migration; không chạy lại bộ test toàn hệ thống. Chưa kiểm tra trực quan bằng trình duyệt trong lượt bổ sung dữ liệu này. Không tạo audio/ảnh mới, streak nhiều ngày hoặc kết quả League tuần cũ. Ngày học và XP dùng thời gian thực khi nạp, không backdate và không ghi SQL để tạo thành tích giả.
