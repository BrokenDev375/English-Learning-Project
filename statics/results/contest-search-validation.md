# Kiểm tra tìm kiếm bộ đề và chọn giám thị — 30/10/2026

Nhánh `feature/online-contests`, tiếp nối thư viện V17. Không migration, không commit/push trong lượt này. Runtime thử riêng 15223/18133, giữ DB V17 và demo 15183.

## Thay đổi

- Thay dropdown nạp toàn kho bằng dialog tìm theo tên bộ đề/người soạn, chỉ READY, 8 kết quả/trang. Hiện tên, người soạn, số câu và số cuộc thi sử dụng; chọn một bộ đề. Enter trong ô tìm không nộp form; Esc/đóng giữ lựa chọn trước đó và trả focus về nút mở.
- Giám thị: tìm theo họ tên/email, 6 người/trang, chỉ ACTIVE có canProctor. Checkbox phân công vào cuộc thi hiện tại; giữ các ID và tên đã chọn qua từ khóa/trang, có chip bỏ từng người và số đã chọn /20. Người đã chọn nhưng không còn quyền vẫn được đánh dấu để admin bỏ/chọn lại, không âm thầm xóa.
- Kho bộ đề cũng có tìm kiếm. API lọc scope/owner/state/quyền và keyword trước phân trang/count; ký tự %, _, ! được escape như chuỗi thường. q tối đa 160 ký tự.
- usePagedSearch chờ 200ms, bỏ phản hồi cũ, không cho chọn kết quả của từ khóa trước. Quay về kho sau khi tạo/nhân bản/sửa sẽ tải lại danh sách. Không allPages khi chọn bộ đề/giám thị.
- Author chỉ ADMIN nhận proctors[{userId,fullName,email,eligible}] cho các ID đã lưu. Teacher giám thị vẫn không nhận câu hỏi/đáp án hoặc danh sách riêng này.

## Bằng chứng

| Kiểm tra | Kết quả |
|---|---|
| Backend | **20/20 PASS**, 0 failure/error/skip, BUILD SUCCESS 2:15; ContestPaperApi 8 + ContestApi 12 |
| Ca search mới | READY/owner-scope trước phân trang; count/totalPages đúng; tìm tên/người soạn/email; %, _ là ký tự thường; q quá dài 400; giám thị không đủ quyền không chen vào trang; metadata tên/eligible giữ đúng khi thu hồi quyền |
| Browser | Lượt đầu **4/6 PASS**, phát hiện ô tên bị giãn cao và Esc trong input search chỉ xóa chữ. Sửa grid align-items và xử lý Esc; chạy lại hai ca **2/2 PASS** (11.7s). Tổng **6 ca khác nhau đã qua qua các lượt**, không phải một lượt 6/6 |
| Ca chọn đề/giám thị cuối | Chạy riêng **1/1 PASS** (5.9s) với dữ liệu giả lập phân trang đúng 6+1: chọn giám thị ở hai trang, đổi từ khóa không mất chọn, bỏ một chip, phản hồi slow đến sau fast không ghi đè. Mock chỉ cho GET danh sách, không lưu ID giả vào DB |
| Giao diện/điều hướng | Dialog tìm kiếm ở desktop/390px không tràn; chọn/cancel/Esc/Enter giữ đúng form; clone rồi về kho thấy bản mới; ô nhập tên giữ chiều cao gọn. Có ảnh tại .test-state, không đưa auth trace vào Git |
| Hồi quy phòng thi | Bốn ca còn lại qua ngay: giám thị đúng phạm vi/thu hồi, học viên lưu/tải lại/nộp/xem đáp án sau đóng, cảnh báo/trừ giờ/thu hồi, mất ACK/một writer |
| Build/hợp đồng | TypeScript, Vite và Docker health PASS; OpenAPI 2.1: 34 paths/37 schemas/258 local references hợp lệ. Không tuyên bố đã chạy validator OpenAPI bên ngoài |

## Tái hiện

```powershell
# source/server
.\mvnw.cmd -B -ntp '-Dtest=ContestPaperApiTest,ContestApiTest' test
# source/client — chỉ trên môi trường test riêng
npm run build
npm run test:e2e -- e2e/online-contests.spec.ts
```

Các số 117 backend của V17 trước đây giữ tại [biên bản thư viện](contest-paper-library-validation.md), không dùng thay kết quả 20 ca tập trung của thay đổi search. Không đo tải trong lượt này. Không thay luật chấm điểm, deadline, XP hoặc tiến độ học.
