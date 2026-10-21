# Kiểm tra thủ công M04

Chạy Docker theo README, vào http://localhost:5173. Dùng hai tài khoản STUDENT khác nhau; không chạy bộ E2E vào database demo.

| Ca | Thao tác | Kỳ vọng |
|---|---|---|
| M04-01 Chưa học | Học viên mới → Học bài → Ôn tập cá nhân | Chưa có kết quả; nút ôn bị khóa; không tạo dữ liệu giả |
| M04-02 Nguồn ôn | Học một bài, trả lời sai một số câu rồi nộp → Xem nội dung cần ôn | Hiện đúng mục tiêu của bài, lý do cần luyện và số câu đang có |
| M04-03 Chọn câu | Chọn 1 câu rồi Ôn mục tiêu còn lỗi sai | Ưu tiên REVIEW_VARIANT chưa gặp, mức thấp nhất còn sai; không mở hint trước nộp |
| M04-04 Giữ nháp | Nhập → Lưu nháp lên máy chủ → F5 | Cùng Attempt và câu, đáp án/audio đã lưu được phục hồi như M03 |
| M04-05 Ôn đúng | Tự làm đúng câu ôn rồi nộp → Xem nội dung cần ôn | Xóa ưu tiên lỗi sai, stage tăng một lần, hiện mốc tiếp theo |
| M04-06 Ôn sai | Tự làm sai rồi nộp → Xem nội dung cần ôn | Stage về 0, tiếp tục ưu tiên ôn |
| M04-07 Bỏ trống | Nộp trắng bài mới | Chưa đủ dữ liệu; không tăng stage/độ khó |
| M04-08 FIRST | Hướng dẫn: trả lời sai, kiểm tra, sửa đúng, nộp | Lần đầu sai vẫn làm mục tiêu cần luyện |
| M04-09 Chữa sau nộp | Chữa đúng ở trang kết quả, quay lại cập nhật ôn | Không thêm lượt hoặc đổi bằng chứng FIRST đã lưu |
| M04-10 Câu cũ | Học lại cùng bài, trả lời đúng rồi nộp | Có thể nhắc ôn lại; số bộ câu mới không tăng |
| M04-11 Thiếu câu | Yêu cầu 10 câu ở mục tiêu/mức có ít câu | Nhận đúng số thực có, không nhân bản câu; không có câu thì báo rõ |
| M04-12 Cá nhân | Hai người làm sai hai mục tiêu khác nhau | Mỗi người có danh sách và bộ ôn riêng |
| M04-13 Đổi tài khoản | Đăng xuất A, đăng nhập B trên cùng trình duyệt | Không hiện tiến độ/danh sách ôn của A |
| M04-14 Mất kết nối | F12 Network → Offline khi mở/cập nhật ôn | Hiện lỗi và nút thử lại; khi online dùng đúng sync requestId nếu ACK chưa rõ |
| M04-15 Tạo lượt mất ACK | Theo E2E bỏ phản hồi sau server commit create | Gửi lại đúng request; chỉ một Attempt và giữ bộ câu |
| M04-16 Nội dung ngừng cấp | Giảng viên retire bài chứa câu ôn | Giữ lịch sử/Attempt đã tạo; không cấp câu từ bài retire cho lượt mới |
| M04-17 Cùng ngày | Làm nhiều lượt đúng trong một ngày Việt Nam | Không nhảy liên tiếp stage 1→2→3 trong một buổi |
| M04-18 Bộ lọc | Chọn chủ đề/kind/phân trang | Tổng theo chủ đề đúng, không xuất hiện dữ liệu người khác |
| M04-19 Mức luyện | Xem gợi ý sau ít dữ liệu hoặc thiếu câu mới | Ghi rõ chưa đủ; không suy ra tăng độ khó chỉ vì làm lại thuộc câu |

Seed chỉ có 5 câu REVIEW_VARIANT mỗi bài, chia nhiều mức khó. Khi lỗi ở EASY có thể chỉ nhận một câu biến thể mới và vài câu cũ cùng mức; đây là giới hạn nội dung demo.

Bộ backend có fixture riêng đủ câu để kiểm tra hai bộ mới → gợi ý mức tiếp và trường hợp hết câu mới. Không mô tả fixture test là tài liệu đã được giảng viên kiểm duyệt.

Bằng chứng tự động xem [module-4-validation.md](../statics/results/module-4-validation.md); chưa chạy thủ công thì không đánh dấu toàn bộ bảng là PASS.
