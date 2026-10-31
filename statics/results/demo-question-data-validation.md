# Kiểm tra bộ câu hỏi demo A1–A2 — 31/10/2026

Nền source `fe3ab0c` (main tại thời điểm kiểm tra); thay đổi chỉ gồm JSON câu hỏi, script nạp, test và tài liệu. Không sửa backend/frontend hoặc migration. Bộ dữ liệu `demo-a1-a2-1.0.0`.

## Kết quả

| Kiểm tra | Bằng chứng |
|---|---|
| Preflight JSON | 3 chủ đề × 20 câu học + 4 câu ôn = **72 câu nguồn**, có đủ 4 dạng; 3 đoạn đọc. Mỗi bộ đề lấy 20 câu chính, tổng 60 câu trong kho cuộc thi |
| Test Node | **5/5 PASS** trong một lượt cuối: đáp án/tham chiếu, nạp đầy đủ + chạy lại, mất ACK publish, nạp từng phần/quyền, HTTP cookie/CSRF/local origin |
| Build Docker | Server Maven package Java 17 và client TypeScript/Vite PASS; không chạy lại toàn bộ backend/browser regression. Còn cảnh báo bundle JS >500 kB đã có trên nền |
| Nạp API thật | Tạo 3 Unit, 3 Lesson công bố, 72 Question và 3 Material đọc; 3 bộ đề đi qua DRAFT → PENDING → READY, 20 câu/bộ |
| Nạp lại API thật | Lần chạy lại thành công; wrapper theo dõi API xác nhận **0 mutation**, không nhân đôi tài nguyên |
| LEARN bài mới | Lesson 7/8/9: mỗi lượt **20 câu, 20/20 đúng, COMPLETED**; gồm cả SINGLE_CHOICE, FILL_BLANK, MATCHING, ORDERING |
| Lộ trình nền | Học sinh thử hoàn thành 6 bài gốc theo thứ tự, mỗi bài 10/10 đúng; sau đó làm 3 bài mới. GET rounds trả 6 vòng COMPLETED |
| Contest thật | Tạo 3 contest kiểm thử, chọn 3 paper đã nạp, công bố → đăng ký → duyệt → check-in trước giờ → nộp → ADMIN đóng; cả 3 kết quả **20/20 đúng** |
| Dữ liệu trước nộp | Snapshot học và paper trong phòng thi không chứa answerKey/explanation. Đáp án dùng để kiểm tra được đọc riêng bằng ADMIN; không thêm đường truy cập keys cho học viên |

## Môi trường và phạm vi

Docker project riêng **`english-learning-demo-questions-check`**, UI **15343**, API **18143**, MySQL 8.4 và volume DB/file riêng. Build từ source hiện tại bằng Compose. ADMIN đọc từ `.env` local; không in mật khẩu hoặc đưa `.env` vào Git.

Tạo một tài khoản STUDENT kiểm thử và các lượt/contest để kiểm tra chấm trong DB riêng này. Không nạp JSON lên DB integrated/main/baseline hoặc DB contest cũ. Docker Desktop được bật theo quyền người dùng; các project tự chạy lại bởi restart policy được giữ nguyên.

Checkpoint và kết quả chi tiết nằm trong `.test-state/demo-questions/`; script kiểm tra API thật tạm nằm trong `.test-state/verify-demo-questions.mjs`. Không đưa các file local này, cookie, mật khẩu hoặc SQL dump lên Git. Môi trường kiểm thử được dừng sau xác minh, giữ volume để đối chiếu.

Chưa có kiểm tra trình duyệt riêng cho bộ nội dung mới, đo tải hoặc khảo sát phân loại CEFR. Không áp dụng số test backend/browser lịch sử vào thay đổi này. Việc tự sinh đáp án đúng từ answerKey xác minh schema, liên kết và đường chấm, không thay cho đánh giá sư phạm độc lập.

## Chạy lại

```powershell
node scripts/seed-demo-questions.mjs
node --test --test-isolation=none scripts/seed-demo-questions.test.mjs
node scripts/seed-demo-questions.mjs --base-url http://localhost:15343 --apply
```

Lệnh cuối chỉ dùng sau khi đã chạy đúng DB demo riêng và có ADMIN của DB đó. Hướng dẫn dành cho máy người nhận ở [DEMO_QUESTION_DATA](../../docs/DEMO_QUESTION_DATA.md).
