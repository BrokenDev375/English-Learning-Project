> Ghi chú tích hợp 29/10: hướng dẫn và kết quả dưới đây thuộc bản trước V13. Script dùng TEACHER tạo vòng/xuất bản trực tiếp, chưa tương thích luồng phân công/duyệt trên main mới; không chạy --apply trên bản tích hợp.

# Lớp học giả lập để demo bảng xếp hạng

Bộ dữ liệu bổ sung được nạp chủ động bằng `node scripts/seed-demo-classroom.mjs --apply` từ thư mục gốc, sau khi chạy `start-main.cmd`. Mặc định dùng http://localhost:5176. Chạy không có `--apply` chỉ in kế hoạch, không ghi dữ liệu. Script dùng Node.js 24 và các API HTTP hiện có, không cần cài thêm package.

Đây là dữ liệu giả lập có nhãn `[Demo]`. Tài khoản, nội dung và lịch sử cũ được giữ nguyên. Không có migration hoặc thay đổi luật học/thưởng. Không cần build lại container để nạp bộ demo này.

## Đăng nhập và dữ liệu

Mật khẩu chung của **sáu tài khoản demo mới**: `Demo@Test12345`.

| Học sinh | Email | Bài đã nộp/hoàn thành | Bài dở | XP cá nhân/tuần ở lượt nạp 27/10/2026 |
|---|---|---:|---:|---:|
| [Demo] Minh Anh | demo.minhanh@english.local | 10 | 0 | 200 |
| [Demo] Hoàng Nam | demo.hoangnam@english.local | 9 | 1 | 170 |
| [Demo] Thu Hà | demo.thuha@english.local | 8 | 1 | 135 |
| [Demo] Gia Bảo | demo.giabao@english.local | 6 | 1 | 100 |
| [Demo] Lan Anh | demo.lananh@english.local | 4 | 1 | 50 |
| [Demo] Đức Minh | demo.ducminh@english.local | 2 | 1 | 25 |

Các mức XP trên là kết quả thực tế của worker/service ở lượt kiểm tra, có áp dụng trần 200 XP/ngày. Bảng xếp hạng vẫn gồm các tài khoản khác đang có XP hợp lệ.

Hai Unit mới được đặt sau các vòng mẫu ban đầu:

| Unit | Bài học |
|---|---|
| [Demo] School & clubs | A morning at Green School; Our Friday science club |
| [Demo] Healthy life & sports | Anna’s healthy Saturday; An afternoon at the sports centre |

Mỗi bài mới có một bài đọc tự biên soạn và 9 câu: 3 GUIDED, 3 INDEPENDENT, 3 REVIEW_VARIANT. Tổng bổ sung **4 bài đọc, 36 câu** đủ SINGLE_CHOICE, FILL_BLANK, MATCHING và ORDERING. LEARN cấp 6 câu/bài mới; 3 REVIEW_VARIANT dùng cho ngân hàng ôn. Bộ seed M02 gốc không đổi.

## Cách demo

1. Đăng nhập Minh Anh → **Bảng xếp hạng** → xem **Toàn hệ thống** và **Nhóm thi đấu**. Sáu học sinh đã tham gia tuần trước khi làm bài để XP đủ điều kiện được tính vào nhóm.
2. Mở **Vào chơi**: Minh Anh đã hoàn thành 5 vòng/10 bài, có thể mở và chơi lại các bài mới. Các học sinh khác có tiến độ khác nhau và vòng sau còn khóa theo luật hiện tại.
3. Đăng nhập Hoàng Nam → tiếp tục bài cuối của Unit thể thao: bản nháp trên server đã có 2 câu trả lời. Thu Hà, Gia Bảo, Lan Anh và Đức Minh cũng có một lượt dở.
4. Mở **Lịch sử làm bài**, kết quả và **Hồ sơ học tập** để xem số bài, điểm đúng/sai và XP khác nhau. Các kết quả có đầy đủ đáp án nhưng có câu sai; không phải tài khoản nào cũng đạt 100%.
5. Đăng nhập tài khoản giáo viên demo hiện có → quản lý học liệu/thống kê: xem hai Unit, bốn bài, câu hỏi và học liệu mới thuộc giáo viên đó.

**Lịch sử được tạo bằng thao tác học giả lập ở thời điểm chạy thật**, không sửa ngày trong quá khứ. Vì vậy bộ này chưa tạo streak nhiều ngày hoặc lịch sử League của các tuần đã chốt; bảng tuần sẽ đổi khi sang tuần mới.

## Cơ chế nạp lại và kiểm tra

- Tác giả đăng nhập bằng cấu hình `DEMO_TEACHER_EMAIL`/`DEMO_TEACHER_PASSWORD` trong môi trường hoặc `.env`; không in credentials vào log. Mặc định dự phòng là tài khoản giáo viên demo trong README.
- Tạo/xuất bản nội dung, đăng ký STUDENT, tạo lượt, nộp, xác nhận hoàn thành, lưu nháp, tham gia League và sync M04/M05 đều đi qua API có session/CSRF. Không INSERT/UPDATE điểm, Submission hay ledger bằng SQL.
- Script đi theo thứ tự vòng hiện tại, hoàn thành vòng trước rồi mới tạo LEARN cho vòng sau. Các học sinh có số bài và tỷ lệ trả lời sai khác nhau.
- Checkpoint trong `.test-state/demo-classroom/<origin-hash>.json` lưu requestId/payload trước khi gửi. Lỗi/mất phản hồi thì chạy lại cùng lệnh để gửi lại đúng thao tác. Giữ file này khi nạp lại trên cùng DB; không xóa để cố tạo lại dữ liệu. Report nằm ở `.test-state/demo-classroom/report.json`, không đưa vào Git.
- Cùng một origin chỉ chạy một script tại một thời điểm. Nếu tiến trình bị tắt cưỡng bức để lại `.lock`, xác minh không còn script chạy rồi mới xóa đúng file `.lock`; giữ `.json` checkpoint.
- Bộ này dành cho demo local mặc định với ba Unit M02 gốc. Không dùng lại checkpoint cho DB đã reset hoặc giáo viên khác. Nếu sửa/xóa/nộp tiếp các lượt demo bằng tay, dữ liệu thực tế có thể khác bảng trên; script không reset hoặc ghi đè hoạt động đó.
- Có thể đặt `DEMO_BASE_URL` cho một origin HTTP localhost khác; đó phải là DB demo mà bạn chủ động muốn thêm dữ liệu. Script từ chối host ngoài localhost và không tự khởi động/tắt Docker.

Bằng chứng thực tế: [biên bản kiểm tra](../statics/results/demo-classroom-validation.md).
