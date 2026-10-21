# Kiểm tra M04 — Ôn tập cá nhân

Ngày 21/10/2026. Bằng chứng của bản M04 P0 phát triển trên nhánh feature/m04-personal-review, nền origin/main tại be92240. Trạng thái commit/merge/push hiện tại tra cứu bằng Git.

## Phạm vi đã làm

| Phần | Kết quả |
|---|---|
| Database | Flyway V5 thêm ba bảng progress/evidence/processed Submission; JPA validate, không sửa V1–V4 |
| Kết quả học tập | Đọc FIRST trong Result schema 1, tách hướng dẫn/hỗ trợ/bỏ trống/câu đã gặp; CORRECTION không làm mới bằng chứng |
| Đồng bộ | Backfill tối đa 100 Submission/request, actor lock, receipt, ledger và dữ liệu cùng transaction |
| Ôn tập | DUE/MISTAKES, đúng mục tiêu/mức, ưu tiên biến thể mới, dedup origin và cấp số câu thực có |
| Lịch | 1–3–7 ngày có cấu hình, tối đa một lần tăng stage mỗi ngày Việt Nam |
| Mức luyện | Tối thiểu hai bộ mới đủ câu, có nội dung mức kế tiếp; báo rõ thiếu dữ liệu/học liệu |
| Giao diện | Tab cá nhân, bộ lọc, lý do, mốc, khả năng cung cấp câu, gợi ý mức; link từ kết quả; dùng chung runner M03 |

## Bằng chứng đã chạy

| Kiểm tra | Kết quả thực tế |
|---|---|
| Toàn backend, compile Java 17, MySQL 8.4 Testcontainers | **157/157**, 0 fail/error/skip, 10 phút 11 giây |
| Riêng M04 trong lượt backend trên | **21/21**: 13 API/integration + 8 policy |
| Frontend production build | **PASS**, TypeScript + Vite |
| Hồi quy trình duyệt M01/M02/M03, navigation và audio | **50/50** |
| Nhóm trình duyệt M04 sau sửa fixture chờ đăng xuất | **5/5**, 16,6 giây |
| Restart máy chủ test, lịch/progress/receipt/ledger và seed chạy lại | **PASS**, cùng dữ liệu trước/sau, replay receipt đúng và không xử lý Submission lần hai |
| Local demo sau nâng cấp | **PASS**, V4 → V5, backend UP, frontend phục vụ đúng bundle M04 |

Lượt E2E toàn bộ đầu tiên có 54/55 qua; ca đổi tài khoản gửi đăng ký quá sớm trong khi logout đang chạy nên nhận CSRF 403. Đã bổ sung chờ nút Đăng nhập xuất hiện và chạy lại toàn bộ file M04 đạt 5/5. Không sửa production code để bỏ kiểm tra CSRF. Tổng cộng 55 ca trình duyệt khác nhau đã qua sau bước này; không mô tả là một lượt 55/55 duy nhất.

Đã build/chạy bản thật ở localhost:5173/8080 với volume cũ. Trước và sau nâng cấp đều giữ 7 users, 6 lessons, 90 questions, 13 materials, 7 assets, 1 attempt và 0 submissions; Flyway tăng từ 4 lên 5. Không dùng demo để chạy ca E2E. Container test M04 riêng đã dừng sau kiểm tra. Đây là trạng thái tại thời điểm kiểm tra; xác nhận image đang chạy khi tiếp tục kiểm thử local.

Backend kiểm tra hai học viên yếu khác mục tiêu nhận bộ ôn khác nhau; câu mới/cũ và hai bộ tốt; lỗi ghi ledger rollback progress/receipt; sync đồng thời và replay; blank/FIRST/guided/correction; thiếu nội dung mức tiếp; bài retire; REVIEW lưu/resume/nộp và bài pha nhiều mức khó. M03 migration tests đã cập nhật kỳ vọng schema hiện tại là V5; vẫn kiểm tra nâng từ V3 giữ dữ liệu và tạo DB mới.

Trình duyệt M04 kiểm tra dữ liệu rỗng; biến thể chưa gặp; lưu nháp/F5/nộp; mốc sau lượt đúng; mất ACK sync và create sau server commit; đổi tài khoản cùng trình duyệt. Đã xem ảnh mobile 390×844 và kiểm tra không tràn ngang.

## Môi trường và lệnh

Mọi test ghi dữ liệu dùng MySQL/Testcontainers hoặc Compose project riêng english-learning-m04-e2e ở 15174/18081. Không dùng database demo để chạy E2E, không xóa volume.

Backend image được bind source hiện tại read-only và chạy clean test, tránh kết quả từ source cũ:

```powershell
docker build --target test -t english-learning-m04-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m04-test mvn -B -ntp clean test
```

Frontend từ source/client:

```powershell
npm ci
npm run build
npm run test:e2e -- module-four-review.spec.ts
```

Runner chuẩn dùng project english-learning-e2e; cần giữ cấu hình .env khớp volume test hoặc chọn môi trường test riêng. Khi dùng E2E_BASE_URL, phải trỏ đúng app test riêng, không trỏ demo 5173. Môi trường kiểm tra lần này tạo project mới bằng cấu hình demo công khai để tránh dùng nhầm mật khẩu volume test cũ.

Log/XML/ảnh thô nằm trong thư mục tạm kiểm tra của phiên, không đưa credentials, node_modules, dist hoặc artifact build vào Git.

## Giới hạn

- Seed chỉ có 5 REVIEW_VARIANT mỗi bài, chia nhiều độ khó; số câu mới cùng mục tiêu/mức có thể rất ít.
- Lịch và ngưỡng là heuristic prototype, chưa chứng minh tối ưu hay đo hiệu quả học tập.
- Lịch hiện cấu hình chung ở backend; chưa có API/giao diện cho giảng viên chỉnh theo Unit. Chưa có email/thông báo đẩy hoặc bài kiểm tra ngắn riêng để gợi ý điểm bắt đầu.
- Stage theo mục tiêu, không theo từng từ/âm hay mọi lỗi ngữ nghĩa trong câu. Độ cụ thể phụ thuộc nhãn giảng viên M02.
- Dashboard được cập nhật khi sync; có pending thì yêu cầu cập nhật trước tạo lượt mới.
- Lưu nháp vẫn là nút chủ động và bộ nhớ trang của M03. Chưa có IndexedDB/outbox/autosave; chưa làm XP, rank hoặc AI.
- Không có benchmark hàng nghìn người dùng hoặc xác nhận toàn bộ checklist thủ công.

Xem [tiến độ](../../docs/M04_PROGRESS.md), [kế hoạch](../../docs/M04_IMPLEMENTATION_PLAN.md), [API](../../source/common/M04_API.md) và [ca thao tác](../../docs/M04_MANUAL_TEST_CASES.md).
