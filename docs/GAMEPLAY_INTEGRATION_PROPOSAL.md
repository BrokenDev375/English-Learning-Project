# Game trong bài học — triển khai 26/10/2026

## Lối vào

Giữ hai luồng **Vào chơi** và **Ôn tập cá nhân**. Trang chủ có rương và mô tả Săn kho báu. Mở vòng → chọn bài → Bắt đầu chơi/Chơi lại bài; trong lượt làm có cảnh đảo, nhà thám hiểm và nút chọn trò theo các dạng câu thực tế. Bấm nút chọn câu đầu tiên của dạng đó và cuộn tới phần chơi. Các nút số câu vẫn cho phép đi lần lượt hoặc quay lại câu đang làm.

Lượt ôn cá nhân và PRACTICE cũ cũng dùng giao diện này. Điều kiện mở vòng, chọn câu và hoàn thành bài giữ nguyên.

## Các trò đã có

| Dạng câu hỏi | Tương tác | Khi máy chủ xác nhận đúng |
|---|---|---|
| MATCHING — **Ghép thẻ mở rương** | Chọn/kéo thẻ trái sang phải, hiển thị đường nối và mảnh chìa khóa; bỏ/ghép lại được | Rương mở, hiện kho báu và ánh sáng |
| ORDERING — **Xây cầu bằng từ** | Mỗi từ được chọn lắp một tấm ván; bấm, kéo hoặc dùng mũi tên đổi thứ tự | Nhà thám hiểm đi qua cầu |
| SINGLE_CHOICE — **Chọn cổng** | Các phương án là những cổng; chọn bằng radio | Cánh cửa được chọn mở ở câu hướng dẫn |
| FILL_BLANK — **Mở khóa từ** | Điền từ còn thiếu vào thông điệp | Hiển thị khóa đã mở ở câu hướng dẫn |

Ngân hàng demo hiện có SINGLE_CHOICE, FILL_BLANK và MATCHING. ORDERING có trình biên soạn và game renderer, chỉ xuất hiện trong bài có câu ORDERING được xuất bản. Không tự chuyển dạng hoặc thêm vào 90 câu demo. Kiểm thử tạo bài ORDERING trong môi trường riêng rồi retire.

## Tiến độ, chấm và phần thưởng

- Cảnh đảo tiến lên theo số câu **đã điền đủ**, không ngụ ý đã trả lời đúng. Ghép đủ thẻ/lắp đủ ván chỉ là trạng thái thao tác.
- GUIDED có thể bấm Kiểm tra đáp án để xem hoạt cảnh sớm. Chỉ mở khi feedback của máy chủ là CORRECT và đáp án hiện tại khớp câu đã chấm. Sửa/xóa đáp án bỏ xác nhận cũ. MATCHING/FILL so theo ID; ORDERING giữ thứ tự từ.
- INDEPENDENT và REVIEW_VARIANT chờ nộp mới biết đúng/sai. Không tải khóa đáp án vào game đang làm hoặc tự chấm ở client.
- Sau nộp mỗi câu có một rương. finalOutcome CORRECT mở rương; sai/chưa trả lời còn khóa. ORDERING đúng có hoạt cảnh qua cầu. Có thể chọn từng rương và xem lại hoạt cảnh.
- Sao minh họa: 0 khi không có câu đúng; 1 khi có câu đúng và điểm dưới 50; 2 khi đạt 50 đến dưới 80; 3 khi đạt từ 80. Điểm lấy từ Submission thật.
- Xem lại hoạt cảnh chỉ đổi state hiển thị React, không tạo lượt/trial/phần thưởng. XP lấy từ M05. FIRST/Submission không đổi khi chữa hoặc xem hoạt cảnh.
- Nộp đủ mọi câu vẫn hoàn thành bài dù có câu sai. Giữ toàn bộ lời giải và nút xác nhận về vòng. Sao/rương không phải điều kiện mở vòng.

## Điểm vào mã nguồn

- GameArtwork.tsx: SVG nhân vật, rương, đảo, cầu và chìa khóa, không phụ thuộc ảnh bên ngoài.
- GameWorld.tsx: hành trình, chọn trò, so đáp án với feedback, kho báu kết quả.
- InteractiveAnswers.tsx: đường nối theo kích thước thẻ, rương MATCHING và cầu ORDERING; hỗ trợ bàn phím/chạm/kéo thả.
- AnswerFields.tsx: bật giao diện bằng prop play; xem trước biên soạn và form chữa giữ điều khiển hiện có.
- AttemptRunner/AttemptResult/StudentHome/LessonIntroduction: tích hợp vào luồng đang dùng.
- gameplay.css: cảnh, chuyển động, bố cục điện thoại và prefers-reduced-motion.

Đáp án lấy từ draft M06 nên tải lại/tiếp tục/offline không có bản lưu game riêng. Không thêm thư viện, API, migration, mạng sống hoặc đồng hồ bắt buộc.

Kiểm tra và giới hạn thực tế: [biên bản gameplay](../statics/results/gameplay-validation.md).
