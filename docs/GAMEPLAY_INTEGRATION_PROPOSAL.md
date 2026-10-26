# Lồng game vào bài học — đề xuất 26/10/2026

## Mục tiêu

Giữ hai luồng Vào chơi và Ôn tập cá nhân. Bài trong vòng là nơi chơi game; Chơi lại bài/vòng dùng lại cùng giao diện. Không tạo thêm mục Luyện tự chọn hoặc một ngân hàng game độc lập.

Đây là thiết kế để xem xét, chưa phải tính năng đã triển khai. Bản hiện tại có thẻ ghép cặp, sắp xếp từ, bản đồ vòng, XP và League; chưa có các hoạt cảnh được mô tả dưới đây.

## Cách gắn với dữ liệu đang có

| Dạng câu hỏi | Cách thể hiện thành game | Dữ liệu chấm giữ nguyên |
|---|---|---|
| ORDERING | **Xây cầu bằng từ**: kéo thẻ để tạo các nhịp cầu; sau nộp, hoạt cảnh thể hiện kết quả câu | tokenIds, acceptedOrders |
| MATCHING | **Nối thẻ mở khóa**: chọn/kéo hai thẻ; sau nộp, các cặp đúng mở các ô thưởng minh họa | pairs với leftId/rightId |
| SINGLE_CHOICE | **Chọn cổng**: các phương án là các cổng; chọn một cổng rồi chuyển câu | optionId |
| FILL_BLANK | **Mở khóa từ**: nhập các từ còn thiếu để hoàn tất thông điệp | blankId/value |

Mỗi Lesson có thể trộn nhiều dạng câu. Một GameShell bao quanh AnswerFields, còn renderer thay đổi theo question.type. Không bắt buộc biến cả Lesson thành cùng một dạng game hoặc viết lại 90 câu seed.

## Phạm vi nên làm trước

1. Làm **Xây cầu bằng từ** trước: tận dụng OrderingWords đã có, thêm hình minh họa và hoạt cảnh kết quả; giữ điều khiển bấm/bàn phím cho điện thoại và khả năng tiếp cận.
2. Làm **Nối thẻ mở khóa**: thêm đường nối, chuyển động và kết quả sau nộp cho MatchingCards. Dùng nối thẻ đang hiện chữ; không tự chuyển thành lật thẻ có chấm tức thì khi client chưa được cấp đáp án.
3. GameShell hiển thị bài đang chơi, câu hiện tại/tổng câu, các câu đã điền và nút nộp. Ban đầu không thêm mạng, giới hạn thời gian bắt buộc hay cơ chế mất lượt.
4. Màn hình kết quả dùng điểm nộp thật và có thể thêm sao minh họa. Sao không phải một loại XP mới; XP vẫn lấy từ server, không cộng lại vì hoạt cảnh hoặc chơi lại bài.

React/CSS/SVG hiện có đủ cho hai game đầu; chưa cần thêm game engine/canvas, API mới hoặc thay đổi schema câu hỏi.

## Các luật cần giữ

- INDEPENDENT và REVIEW_VARIANT không được nhận khóa đáp án trước nộp. Khi chọn thẻ, chỉ thể hiện đã chọn/đã điền; không báo đúng/sai giả hoặc mở key ở client. GUIDED vẫn dùng hint/check theo quyền hiện hành.
- FIRST và đáp án lúc nộp tiếp tục do server ghi/chấm. Lưu nháp, replay, journal và vị trí câu theo M06; rời game rồi vào lại không tạo bộ câu mới hoặc làm mất dữ liệu.
- Ôn tập dùng cùng renderer game nhưng bộ chọn M04 quyết định mục tiêu/câu biến thể, không ép người học làm lại toàn bộ bài.
- Mở vòng và League vẫn dựa trên progress/XP server. Điểm hiệu ứng, thời gian trình diễn và client animation không phải bằng chứng cộng thưởng.
- Kiểm thử phải xác minh không lộ key, chấm đúng, tiếp tục sau tải lại/mất ACK, hỗ trợ chạm/bàn phím và không đổi FIRST/XP khi xem lại hoạt cảnh.
