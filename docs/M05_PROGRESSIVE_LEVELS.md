# Level tăng dần — M05 v3, 26/10/2026

## Mục tiêu và ngưỡng

Các cấp đầu dễ đạt để người mới có phản hồi tiến bộ sớm. Cấp cao hơn cần nhiều XP hơn. Chi phí từ cấp L lên L+1 là **10 × L XP**; tổng XP tối thiểu tại cấp L là **5 × L × (L−1)**.

| Cấp hiện tại | XP cần để lên cấp tiếp | Mốc tổng XP của cấp tiếp |
|---|---:|---:|
| 1 | 10 | 10 |
| 2 | 20 | 30 |
| 3 | 30 | 60 |
| 4 | 40 | 100 |
| 5 | 50 | 150 |
| 6 | 60 | 210 |
| 7 | 70 | 280 |
| 8 | 80 | 360 |
| 9 | 90 | 450 |
| 10 | 100 | 550 |
| 20 | 200 | 2.100 |

Ví dụ: 25 XP ở cấp 2, đã đi được 15/20 XP trong cấp và còn 5 XP để lên cấp 3. 50 XP ở cấp 3, đã đi được 20/30 XP và còn 10 XP để lên cấp 4. Một bài demo làm đúng các câu tự làm đủ điều kiện thường có 25 XP; đây là ví dụ theo luật thưởng, không phải thưởng cố định cho mọi bài.

Level tiếp tục là cấp hoạt động. Không suy ra trình độ tiếng Anh hoặc quyền mở vòng từ level. Tiến độ vòng phụ thuộc hoàn thành bài như trước.

## Tính điểm và tương thích

- Tổng XP kiếm được, ledger/claim, FIRST, điểm nộp, badges và streak giữ nguyên. Không cộng XP để tạo cảm giác lên cấp.
- Thưởng câu mới/ôn đến hạn vẫn 5 XP, trần vẫn 200 XP/ngày. Không thêm trần theo tuần và không đổi luật giải đấu.
- V11 chỉ INSERT policy `m05-p0-v3` từ v2, giữ activatedAt, đặt effectiveAt mới và curve INCREASING (firstStepXp=10, stepIncreaseXp=10). Không sửa V1–V10 hoặc policy cũ. Không sửa bất kỳ season/rule snapshot/kết quả League hiện có nào.
- v1 vẫn tính 100 XP/cấp; v2 vẫn 50 XP/cấp khi đọc tại thời điểm tương ứng. Summary hiện tại dùng policy hiệu lực lúc đọc. Ledger/Activity và nguồn xử lý trễ tiếp tục chọn policy theo submittedAt. Context REVIEW đã lưu không bị đổi.
- Level được suy ra từ tổng XP theo ngưỡng hiện hành, không lưu như một phần thưởng cố định. Do đó số cấp có thể khác v2 sau nâng cấp. Bản demo đã được kiểm tra trước triển khai: tổng XP hiện có nằm trong vùng mà v3 không làm giảm cấp. Không có cơ chế bảo lưu cấp v2 cho tài khoản XP cao trong một DB khác.

## API và giao diện

Giữ cấu trúc Summary: activityLevel, levelFloorXp, nextLevelAtXp. PolicyVersion chấp nhận thêm v3; nextLevelAtXp tối thiểu 10 với v3, vẫn tối thiểu 50 cho v2 và 100 cho v1.

Trang phần thưởng hiển thị phần còn thiếu, chi phí của **cấp hiện tại → cấp tiếp theo** và mốc tổng XP tiếp theo. Thanh tiến độ lấy (totalXp−floor)/(next−floor). Bỏ câu mô tả mỗi cấp cùng một lượng XP. Trang chủ/hồ sơ đọc cùng nguồn level từ backend.

ActivityLevels dùng so sánh số nguyên và tìm kiếm nhị phân để xác định cấp tại đúng mốc; không lặp theo số cấp hoặc làm tròn căn bậc hai. Hỗ trợ toàn bộ miền XP an toàn của API đến 9.007.199.254.740.991, chặn dữ liệu ngoài miền.

Kiểm thử và triển khai: [biên bản](../statics/results/progressive-levels-validation.md).
