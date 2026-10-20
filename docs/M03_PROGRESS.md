# Tiến độ M03

Cập nhật 19/10/2026. **Đã hoàn tất triển khai và nghiệm thu bước 0–6 trong phạm vi M03.** M03 có API và giao diện học/luyện tập từ tạo lượt đến chữa bài và xem tiến độ. Người dùng sẽ tạo PR trên web sau; chưa mở PR hoặc merge.

Tra cứu: [kế hoạch triển khai](M03_IMPLEMENTATION_PLAN.md), [hướng dẫn giao diện](M03_UI_SMOKE.md), [thử API](M03_API_SMOKE.md), [hợp đồng nghiệp vụ](../source/common/M02_M03_API.md). Điểm vào mã nguồn và các ràng buộc cần giữ nằm trong [AGENTS.md](../AGENTS.md).

## Trạng thái triển khai

| Bước | Kết quả | Commit / bằng chứng |
|---|---|---|
| 0 — Đối chiếu hợp đồng | Fixture dương/âm, baseline, receipt chọn status/body trong transaction | `bd913be`; [đối chiếu](M03_CONTRACT_REVIEW.md), [kiểm tra](../statics/results/module-3-step0-validation.md) |
| 1 — Dữ liệu và bộ chấm | Migration V4, entity/repository, DTO/parser, validator và grader cho ba dạng câu | `d37ba0b`; [kiểm tra](../statics/results/module-3-step1-validation.md) |
| 2–3 — Lượt làm và nháp | Tạo/tiếp tục, selector, snapshot/media, lưu và khôi phục full draft | `759ccb3`; [kiểm tra](../statics/results/module-3-step23-validation.md) |
| 4 — Feedback và kết quả | Hint/check, submit/result, CORRECTION và tiến độ | `2f09404`; [kiểm tra](../statics/results/module-3-step4-validation.md) |
| 5 — Giao diện | Catalog, giới thiệu bài, PRACTICE, lịch sử, runner, kết quả/chữa và đối soát lỗi | `97a881d`; [kiểm tra](../statics/results/module-3-step5-validation.md) |
| 6 — Nghiệm thu và bàn giao | Ma trận, hồi quy, restart/recreate/seed/replay/media và tài liệu hoàn tất; PR do người dùng mở sau | `9b0f1e0`; [biên bản](../statics/results/module-3-validation.md), [ca nghiệm thu](M03_MANUAL_TEST_CASES.md) |

## Phạm vi hiện có

- LEARN/PRACTICE dùng phiên bản câu hỏi đã chốt, ID đáp án ổn định và media đúng quyền; resume không chọn lại câu hoặc tăng exposure. Snapshot không lộ keys/giải thích/transcript, kể cả sau nộp.
- Lưu nháp chủ động bằng nút; submit gửi full bản nhập. Mutation khóa actor/Attempt, kiểm tra revision và lưu receipt cùng transaction; replay trước kiểm tra state/revision. FIRST và Submission bất biến; chữa không đổi điểm hoặc tiến độ đã nộp.
- LEARN hoàn thành khi mọi đáp án đầy đủ và mọi GUIDED đã CHECK, không yêu cầu đúng hết. Học lại/nộp dở không hạ COMPLETED; tiến độ giữ lịch sử và báo phiên bản mới.
- UI giữ pending command đúng key/payload, replay rồi GET đối soát khi kết quả chưa rõ. 409 giữ bản nhập để lựa chọn sau so sánh; 401 dừng ghi. Bộ nhớ theo user/Attempt trong phiên trang, có cảnh báo reload khi còn thay đổi; chưa lưu bền vững tại máy.
- Trang giới thiệu bài có nút bắt đầu/tiếp tục/học lại theo trạng thái thực tế, mở kết quả nộp gần nhất, bỏ mô tả trùng và bố cục desktop/mobile. Kết quả gần nhất chọn theo submittedAt để không nhầm với lượt cũ vừa được chữa.

## Bằng chứng kiểm thử

- Bước 6 chạy lại backend từ source `97a881d`: **136/136 PASS**, 0 failure/error/skipped, 12 suite XML, gồm 36 AttemptApiTest; có concurrent submit/save/check, replay và rollback progress/receipt sau insert Submission.
- Production build đạt; toàn bộ browser E2E **47/47 PASS**, 0 failed/skipped, gồm **14 M03 + 33 hồi quy**. Có timeout 15 giây thật, bỏ phản hồi sau commit, 409 hai tab, 401/503, đổi user, bàn phím và mobile; phạm vi fixture ghi trong báo cáo.
- Script riêng đạt cả **restart server** và **recreate mysql/server/client giữ volume**: session cũ 401, đăng nhập lại giữ đúng snapshot/nháp/kết quả/tiến độ; seed/replay không thêm hàng, audio giữ SHA-256 và Range. Không tác động demo.

Các số trên thuộc đúng mốc source ghi trong báo cáo, không tự áp dụng cho thay đổi tiếp theo. Lệnh tái hiện và giới hạn nằm trong từng báo cáo kiểm tra ở bảng trên.

## Việc còn lại

### Cập nhật 20/10 — khôi phục vị trí audio sau xung đột

Đã sửa trường hợp chọn **Dùng bản máy chủ** khôi phục đáp án nhưng audio vẫn ở vị trí local. Session đánh dấu lần nhận playerState từ server; player seek theo mốc đó khi metadata sẵn sàng. Chọn giữ bản đang nhập và nhận ACK lưu thông thường vẫn giữ vị trí đang nghe.

Đã tái hiện lỗi trước sửa trên backend/audio thật, sau sửa **17/17 Playwright M03 đạt** (14 ca sẵn có + 3 hồi quy mới), TypeScript/Vite build đạt. Chi tiết và lệnh tái hiện: [bản sửa audio](../statics/results/module-3-audio-restore-validation.md).

Người dùng review rồi tạo PR vào main trên web. Bước 6 đã có commit `9b0f1e0`. Checklist thủ công là quy trình tái hiện; chưa có phiên thao tác thủ công độc lập chạy lại từng dòng, không coi việc có checklist là bằng chứng chạy tay.

REVIEW hiện trả REVIEW_NOT_READY khi chưa có provider M04. Lịch ôn M04, XP M05, offline bền vững M06 và thống kê nâng cao M07 chưa được triển khai đầy đủ trong phạm vi này.
