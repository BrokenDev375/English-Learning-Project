# Kiểm tra game trong bài học — 26/10/2026

## Phạm vi

Giao diện Săn kho báu trong lượt LEARN/REVIEW/PRACTICE cũ, trò ghép thẻ mở rương, xếp từ xây cầu, chọn cổng và điền từ mở khóa. Bổ sung hình rương ở trang chủ/giới thiệu, chọn trò trong bài và kho báu kết quả. Chỉ frontend/tài liệu; không đổi API, backend, schema, migration, ngân hàng demo, XP hoặc điều kiện hoàn thành.

Source xuất phát từ commit `02a80e9` trên `feature/m07-profile-statistics`. [Thiết kế và giới hạn](../../docs/GAMEPLAY_INTEGRATION_PROPOSAL.md).

## Môi trường và lệnh

- Docker project riêng `english-learning-game-e2e`, UI `15191`, API `18101`, DB mới, backend hiện có V10/M05 v2; không chạy test hoặc tạo tài khoản kiểm thử trên preview người dùng `15183`.
- Frontend build bằng Dockerfile hiện có, chạy `tsc -b && vite build`: PASS, gồm typecheck source và E2E.
- Chrome qua Playwright, một worker, không retry. `E2E_BASE_URL=http://127.0.0.1:15191`.

```powershell
npm run test:e2e
npm run test:e2e -- e2e/gameplay.spec.ts e2e/learning-journey.spec.ts e2e/round-map.spec.ts
```

## Kết quả thật

- Lượt toàn bộ: **107 passed, 1 failed / 108**, 5,8 phút. Ca mới chuẩn bị bài nộp gửi POST tới endpoint draft chỉ nhận PUT, bị 405. Đây là lỗi mã chuẩn bị kiểm thử; đã sửa sang PUT kèm CSRF.
- Sau sửa và hoàn thiện biểu tượng SVG/lối vào/cuộn tới trò, build lại frontend: PASS. Lượt tập trung trên bản cuối: **10/10 passed**, 31,8 giây, gồm cả ca trước đó thất bại.
- Tổng **108 kịch bản khác nhau đã qua qua các lượt**; không ghi là một lượt 108/108. Cả 104 ca cũ đã qua ở lượt tổng thể. Không chạy lại backend vì không thay backend.

## Bằng chứng chức năng

- Cảnh đảo xuất hiện từ câu đầu; các phương án có cổng. Nút Ghép thẻ mở rương chọn đúng câu và cuộn tới khu vực chơi.
- Ghép đúng/đổi cặp/bỏ cặp cập nhật đường nối và số chìa khóa. Bấm, bàn phím và kéo thả hoạt động; lưu rồi tải lại giữ cặp và vị trí câu.
- GUIDED mở rương sau feedback CORRECT; sửa đáp án khóa lại dù feedback cũ còn hiển thị. INDEPENDENT không có nút check hoặc khóa đáp án trước nộp; ghép đủ không tự mở rương.
- Bài ORDERING tự soạn trong test có cầu, lắp từ, lưu/tiếp tục, kéo và đổi thứ tự; chấm đúng cho qua cầu. Sau nộp, ba câu đúng mở ba rương và kết quả ORDERING vẫn có hoạt cảnh qua cầu.
- Bài seed trả lời đúng 10/10: 10 rương mở, 3 sao, server ghi đúng 25 XP theo luật M05. Chọn rương/xem lại hoạt cảnh không POST vào attempts, không đổi Result hoặc XP.
- Nộp sớm không trả lời: 0 sao, rương khóa, không hoàn thành bài. Không có hoạt cảnh mở rương sai.
- Điện thoại 390×844 không tràn ngang. Đã xem ảnh matching mobile/desktop và chạy matching dưới prefers-reduced-motion. Hình chìa khóa là SVG, không phụ thuộc glyph đặc biệt.
- Các ca cũ kiểm tra mở khóa vòng, hoàn thành 1/2 rồi 2/2 bài, xem lời giải/chữa, giao diện giáo viên, autosave/offline/receipt, nhiều tab, quyền và phần thưởng đã qua; không đổi các quy tắc này.

Ảnh do Playwright tạo trong test-results (không commit): game-matching-mobile.png, game-chest-open-desktop.png, game-results-mobile.png, journey-ordering-mobile.png và ảnh trang chủ.

## Giới hạn

Demo 90 câu chưa có ORDERING nên nút xây cầu chỉ xuất hiện trong bài có câu ORDERING được xuất bản. Test tạo bài trong môi trường riêng rồi retire, không thêm câu vào dữ liệu người dùng. Sao là minh họa cho lượt nộp, không phải tiền tệ hoặc XP mới. Chưa có âm thanh game, mạng sống hoặc thời gian bắt buộc.
