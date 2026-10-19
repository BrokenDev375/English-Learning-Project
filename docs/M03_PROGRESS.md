# Tiến độ M03

Cập nhật 19/10/2026. **Đã triển khai bước 0–5; bước 6 chưa hoàn tất.** M03 có API và giao diện học/luyện tập từ tạo lượt đến chữa bài và xem tiến độ.

Tra cứu: [kế hoạch triển khai](M03_IMPLEMENTATION_PLAN.md), [hướng dẫn giao diện](M03_UI_SMOKE.md), [thử API](M03_API_SMOKE.md), [hợp đồng nghiệp vụ](../source/common/M02_M03_API.md). Điểm vào mã nguồn và các ràng buộc cần giữ nằm trong [AGENTS.md](../AGENTS.md).

## Trạng thái triển khai

| Bước | Kết quả | Commit / bằng chứng |
|---|---|---|
| 0 — Đối chiếu hợp đồng | Fixture dương/âm, baseline, receipt chọn status/body trong transaction | `bd913be`; [đối chiếu](M03_CONTRACT_REVIEW.md), [kiểm tra](../statics/results/module-3-step0-validation.md) |
| 1 — Dữ liệu và bộ chấm | Migration V4, entity/repository, DTO/parser, validator và grader cho ba dạng câu | `d37ba0b`; [kiểm tra](../statics/results/module-3-step1-validation.md) |
| 2–3 — Lượt làm và nháp | Tạo/tiếp tục, selector, snapshot/media, lưu và khôi phục full draft | `759ccb3`; [kiểm tra](../statics/results/module-3-step23-validation.md) |
| 4 — Feedback và kết quả | Hint/check, submit/result, CORRECTION và tiến độ | `2f09404`; [kiểm tra](../statics/results/module-3-step4-validation.md) |
| 5 — Giao diện | Catalog, giới thiệu bài, PRACTICE, lịch sử, runner, kết quả/chữa và đối soát lỗi | Chưa commit theo lần kiểm tra này; [kiểm tra](../statics/results/module-3-step5-validation.md) |
| 6 — Nghiệm thu và bàn giao | Chưa hoàn tất ma trận toàn module, giữ tiến độ qua restart/recreate/seed và hồ sơ PR | Theo [kế hoạch](M03_IMPLEMENTATION_PLAN.md) |

## Phạm vi hiện có

- LEARN/PRACTICE dùng phiên bản câu hỏi đã chốt, ID đáp án ổn định và media đúng quyền; resume không chọn lại câu hoặc tăng exposure. Snapshot không lộ keys/giải thích/transcript, kể cả sau nộp.
- Lưu nháp chủ động bằng nút; submit gửi full bản nhập. Mutation khóa actor/Attempt, kiểm tra revision và lưu receipt cùng transaction; replay trước kiểm tra state/revision. FIRST và Submission bất biến; chữa không đổi điểm hoặc tiến độ đã nộp.
- LEARN hoàn thành khi mọi đáp án đầy đủ và mọi GUIDED đã CHECK, không yêu cầu đúng hết. Học lại/nộp dở không hạ COMPLETED; tiến độ giữ lịch sử và báo phiên bản mới.
- UI giữ pending command đúng key/payload, replay rồi GET đối soát khi kết quả chưa rõ. 409 giữ bản nhập để lựa chọn sau so sánh; 401 dừng ghi. Bộ nhớ theo user/Attempt trong phiên trang, có cảnh báo reload khi còn thay đổi; chưa lưu bền vững tại máy.
- Trang giới thiệu bài có nút bắt đầu/tiếp tục/học lại theo trạng thái thực tế, mở kết quả nộp gần nhất, bỏ mô tả trùng và bố cục desktop/mobile. Kết quả gần nhất chọn theo submittedAt để không nhầm với lượt cũ vừa được chữa.

## Bằng chứng kiểm thử

- Backend tại mốc bước 4: **136/136 PASS**, gồm 36 AttemptApiTest. Có concurrent submit/save/check, replay sau bỏ body phản hồi và rollback khi lỗi progress/receipt sau insert Submission.
- Giao diện tại mốc bước 5: production build đạt; toàn bộ browser E2E **46/46 PASS** gồm 13 ca M03 và 33 ca hồi quy. Có timeout 15 giây thật, bỏ phản hồi sau commit, 409 hai tab, 401/503, đổi user, bàn phím và mobile; phạm vi fixture ghi trong báo cáo.
- Sau chỉnh trang giới thiệu: build đạt, **28/28 E2E liên quan PASS** gồm 14 M03, 9 biên soạn và 5 điều hướng. Đây là lượt kiểm tra tập trung, không phải chạy lại toàn bộ E2E.

Các số trên thuộc đúng mốc source ghi trong báo cáo, không tự áp dụng cho thay đổi tiếp theo. Lệnh tái hiện và giới hạn nằm trong từng báo cáo kiểm tra ở bảng trên.

## Việc còn lại

Hoàn tất bước 6 trước khi coi toàn module đã nghiệm thu: đối chiếu ma trận chấp nhận, chứng minh restart/recreate/seed giữ nháp/kết quả/tiến độ, hoàn thiện bàn giao và PR. Không xóa volume để nâng schema hoặc chạy thử.

REVIEW hiện trả REVIEW_NOT_READY khi chưa có provider M04. Lịch ôn M04, XP M05, offline bền vững M06 và thống kê nâng cao M07 chưa được triển khai đầy đủ trong phạm vi này.
