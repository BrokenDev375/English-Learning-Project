# M05 — Nhịp tăng level và giải đấu nhóm nhỏ v2

Ngày 26/10/2026, theo yêu cầu người dùng. Luật cũ tại [quyết định P0](M05_DECISIONS.md) là v1; không sửa migration hoặc kết quả đã lưu của v1.

## Luật hiện hành

| Nội dung | v1 | v2 |
|---|---|---|
| XP cho mỗi cấp hoạt động | 100 | **50** |
| Thành viên tối thiểu để xét League | 10 | **2** |
| Thành viên có weeklyXp > 0 tối thiểu | 5 | **2** |
| Suất mỗi phía | floor(N × 20%) | max(1, floor(N × 20%)) khi đủ điều kiện |
| Nhóm tối đa | 25 | 25 |
| XP câu mới / ôn đến hạn | 5 / 5 | 5 / 5 |
| Trần XP mỗi ngày | 200 | 200 |

Nhóm hai người có XP khác nhau có một suất lên, một suất xuống; giới hạn Đồng/Kim Cương vẫn áp dụng. Nếu hai người bằng XP thì khối hòa vắt ranh giới, cả hai giữ bậc. Một người hoặc nhóm chỉ có một người có XP chưa đủ điều kiện. Đây là thứ hạng cạnh tranh; không tự tăng bậc cho nhóm solo.

Level vẫn là cấp độ hoạt động, không phải chứng nhận trình độ tiếng Anh. GET tính `1 + floor(totalXp / step)` từ luật hiệu lực lúc đọc; floor/next threshold theo cùng step. Người đã có XP có thể tăng cấp khi nhận luật v2 nhưng không được cộng thêm XP. Giữ nguyên FIRST, exposure, unique claim, streak, huy hiệu và điều kiện thưởng; chơi lại không tự tạo thưởng câu mới.

## Phiên bản và dữ liệu

V10 thêm `m05-p0-v2`, sao chép mốc activatedAt ban đầu và đặt effectiveAt tại thời điểm nâng cấp. Giữ nguyên bản ghi v1. Context REVIEW chọn policy theo lúc cấp lượt; worker chọn policy theo submittedAt của từng nguồn, không theo thời điểm worker chạy. Ledger/Activity ghi đúng policy của nguồn. Nguồn cũ chưa xử lý vẫn dùng v1; nguồn trước activation ban đầu vẫn BEFORE_ACTIVATION. Context ôn đã chốt tiếp tục được xét theo bằng chứng cũ vì v2 không đổi điều kiện hoặc số XP.

Theo yêu cầu áp dụng nhịp dễ hơn ngay, V10 cập nhật **tuần hiện tại còn OPEN và chưa hết endAt**, tăng revision và lưu rules/policyVersion v2. Đây là thay đổi có chủ đích so với quy định không đổi trong tuần của baseline v1. Tuần đã hết, CLOSING hoặc FINALIZED không bị đổi. Không sửa điểm thành viên, ledger hay result_json. Những tuần tạo sau đó lấy policy có hiệu lực lúc tạo thay vì hardcode luật.

LeagueRules thêm minimumMovementSlots. Rules JSON cũ thiếu trường này đọc thành 0 và giữ cách floor cũ; JSON/DTO mới trả rõ 0 hoặc 1. Giao diện hiển thị ngưỡng từ rules của đúng season, kể cả lịch sử, không hardcode 10/5 hoặc 2/2 cho mọi tuần.

Repository chọn policy theo effectiveAt, dùng Clock được inject; khóa policy theo version có thứ tự và hỗ trợ nhiều hàng. Không còn giả định bảng policy chỉ có một dòng. M07 đọc level qua dịch vụ M05 như trước.

## Vận hành và phần game

Không sửa V1–V9 hoặc bật Hibernate update. Nâng bản local cần backup trước V10, dùng image đã kiểm thử, giữ MySQL/asset volumes và kiểm tra lại các bản ghi XP/Submission/kết quả tuần cũ. Bằng chứng lần này ở [biên bản v2](../statics/results/engagement-v2-validation.md).

Lồng game là hướng tiếp theo, tách khỏi thay đổi luật thưởng. [Đề xuất cụ thể](GAMEPLAY_INTEGRATION_PROPOSAL.md) chọn Xây cầu bằng từ (ORDERING) và Nối thẻ mở khóa (MATCHING) trước, giữ hai luồng học và không cấp đáp án độc lập trước nộp. Các hoạt cảnh đó chưa được triển khai trong v2 này.
