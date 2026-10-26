# Kiểm tra bản đồ vòng chơi và điều hướng — 26/10/2026

## Phạm vi

- STUDENT có lối vào **Vào chơi**, **Luyện tự chọn**, **Ôn tập cá nhân**; tab bên trong gồm **Chơi theo vòng** và **Lịch sử làm bài**. Trang chủ tập trung vào chơi/tiếp tục, ba cách học và tổng quan, bỏ danh sách vòng bị lặp.
- Bản đồ hiển thị nút tròn, số vòng, đường nối, tiến độ và trạng thái mở/hoàn thành/khóa. Vòng khóa chỉ rõ vòng cần hoàn thành. Mobile 390px và desktop 1440px đã được xem trực tiếp qua ảnh chụp.
- Hoàn thành đủ Lesson đang công bố của vòng trước để mở vòng sau, không yêu cầu điểm tối thiểu. Số vòng và khóa tính trên thứ tự toàn cục stage/sortOrder/id trước lọc/phân trang. Vòng COMPLETED luôn cho chơi lại.
- Server chặn tạo LEARN mới bằng 409 ROUND_LOCKED, kể cả mở URL trực tiếp. GET `/api/me/rounds/{id}` cung cấp trạng thái cho màn hình liên kết trực tiếp, chỉ STUDENT và private/no-store.
- Receipt replay và lượt IN_PROGRESS đã mở từ trước vẫn tiếp tục được; không sửa Submission, FIRST, điểm hoặc thưởng cũ. PRACTICE/REVIEW không bị khóa và không tính vượt vòng. Không thêm migration.

## Bằng chứng

| Kiểm tra | Kết quả |
|---|---|
| Backend | **86/86** trong một lượt: AttemptApiTest 46, PersonalReviewApiTest 13, StatisticsApiTest 15, GamificationApiTest 12; BUILD SUCCESS và package JAR thành công |
| Browser hồi quy | **98 ca khác nhau đã qua qua hai lượt**: lượt toàn bộ 96/98, sau sửa fixture chạy lại đúng hai ca 2/2 |
| Browser bản đồ mới | **3 ca khác nhau đã qua qua các lượt**: hai ca khóa/mở vòng qua từ đầu; ca luyện tự chọn qua sau sửa selector và số câu theo dữ liệu seed |
| Tổng browser | **101 ca khác nhau đã qua qua các lượt**, không phải một lượt 101/101 |
| Frontend | TypeScript + Vite production build PASS, 75 modules; image dùng đúng source hiện tại |
| Hợp đồng M03 | 25/25 fixtures (18 hợp lệ, 7 không hợp lệ) |

Hai ca hồi quy ORDERING/MATCHING ban đầu mở màn hình vòng khóa trước khi helper hoàn thành các vòng tiên quyết qua HTTP. Điều hướng lại cùng URL có hash không tải lại metadata nên màn hình vẫn giữ trạng thái cũ. Fixture cuối tải lại trang và kiểm tra server đã mở khóa; cả hai ca qua. Không đổi component câu hỏi để xử lý lỗi fixture.

Ca luyện tự chọn mới dùng accessible role `combobox` để chọn chủ đề và yêu cầu hai câu EASY hiện có trong chủ đề thứ hai. Lượt đầu dùng getByLabel exact không tìm đúng select, lượt kế tiếp giả định ba câu dù seed chỉ có hai; đây là lỗi chuẩn bị kiểm thử. Hành vi trả ít câu hơn yêu cầu vẫn được mô tả trong ứng dụng.

Backend xác minh khóa không đổi khi tìm kiếm/phân trang, chặn POST trực tiếp trước khi tạo Attempt, chỉ mở sau khi đủ mọi bài, tiến độ riêng từng học viên, không viết lại receipt lỗi, tương thích lượt cũ, chơi lại vòng đã hoàn thành, retire vòng trước và quyền/cache của API chi tiết. Các fixture backend retire học liệu từ ca trước trong DB Testcontainers riêng để mỗi ca có lộ trình độc lập.

Browser mới xác minh vòng 2/3 khóa với tài khoản mới; đường dẫn Unit/Lesson và API không vượt khóa; làm bài đầu vẫn 1/2 và chưa mở vòng sau; hoàn thành bài thứ hai mở vòng 2, vòng 3 vẫn khóa; F5 giữ trạng thái; vòng 1 chơi lại được; luyện chủ đề khác và ôn tập cá nhân độc lập với lộ trình. Hồi quy bao gồm M01–M07, authoring, audio/media, điều hướng, mất ACK/timeout, IndexedDB và phiên đăng nhập.

Kiểm thử chạy tại **15187/18097**, project `english-learning-round-map-e2e`, tách khỏi dữ liệu người dùng **15183/18093**. Không chạy E2E trên bản người dùng, không xóa volume hoặc backfill kết quả. Lộ trình dùng nội dung đang công bố: thêm bài có thể tăng mẫu số, retire bài/vòng trước có thể đổi số thứ tự và điều kiện mở.

Log tạm: `.test-state/round-map-backend.log`, `round-map-client-image.log`, `round-map-browser-new.log`, `round-map-browser-practice-final.log`, `round-map-browser-regression.log`, `round-map-browser-journey-final.log` trong thư mục kiểm tra. Kết quả của các phiên bản trước nằm ở [biên bản vòng học](learning-journey-validation.md) và [biên bản hoàn thành bài](lesson-completion-flow-validation.md).
