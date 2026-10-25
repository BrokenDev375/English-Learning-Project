# M07 — Hồ sơ và thống kê

Phạm vi cập nhật 24/10/2026 theo yêu cầu thống kê giảng viên, nhánh feature/m07-profile-statistics, nền main tại 57f2951. M07 đọc dữ liệu hiện có của M01–M04; không chấm lại bài, tạo lịch ôn hoặc thưởng XP.

## Phạm vi P0

- STUDENT có Hồ sơ học tập: thông tin tài khoản, lượt học/luyện/ôn đã nộp, lượt đang làm, bài hoàn thành, độ chính xác theo chủ đề/mục tiêu, nội dung cần ôn và lịch sử có thể mở lại.
- ADMIN có Thống kê học tập: tài khoản theo vai trò/trạng thái, số người có bài nộp, số lượt theo chế độ, kết quả theo độ khó và câu thường sai theo từng QuestionVersion.
- TEACHER có Thống kê học liệu: chỉ số tổng hợp của các Unit do mình làm chủ, chọn tất cả Unit của mình hoặc một Unit. Có người đã bắt đầu, lượt đang làm/đã nộp, hoàn thành từng bài, kết quả theo chủ đề/mục tiêu/độ khó, câu thường sai theo phiên bản, số câu mở gợi ý và số lần làm lại/chữa. Không được đọc hồ sơ cá nhân học viên hoặc thống kê toàn hệ thống.
- Có bộ lọc ALL/7D/30D/90D, phân trang, tải lại, lỗi mạng và trạng thái dữ liệu rỗng. Bộ lọc khoảng thời gian áp dụng thống kê bài đã nộp; danh sách bài hoàn thành/lượt dở và lịch ôn là trạng thái toàn bộ lịch sử, được ghi rõ trên UI.

## Căn cứ tính toán

- Dùng AnswerTrial FIRST của các Attempt đã có Submission, theo submittedAt của Attempt. FIRST của bài chưa nộp không xuất hiện trong thống kê.
- Tách bốn nhóm không giao nhau: GUIDED; câu không GUIDED có supportUsed; tự làm câu mới; tự làm câu đã gặp. Tổng bốn nhóm bằng tổng FIRST.
- Tỷ lệ đúng = correct / (correct + incorrect). UNANSWERED ghi riêng; không có câu đã trả lời thì trả null, không giả thành 0% hay 100%.
- GUIDED_RETRY và CORRECTION tính riêng. Những lần này không ghi đè FIRST, không tự coi là bằng chứng độc lập. Đếm các lần chữa của bài trong khoảng đã chọn đến thời điểm đọc.
- Mỗi lần làm trong một Attempt là một quan sát; làm lại cùng câu ở Attempt khác có thể tăng cỡ mẫu. Số lần quan sát không phải số người hoặc số kiến thức đã thành thạo.
- Metadata câu lấy đúng QuestionVersion đã chốt; ngừng công bố hoặc có phiên bản mới không xóa thống kê cũ. Danh sách câu thường sai xếp theo số FIRST sai giảm dần, tách từng phiên bản, kèm cỡ mẫu và số người.
- Bài hoàn thành đếm LessonProgress COMPLETED theo root Lesson, không cộng nhiều lần do học lại. Tiêu đề danh sách là phiên bản hoàn thành gần nhất; điểm và đường dẫn lượt gần nhất có thể thuộc lần học lại sau đó và được đặt nhãn rõ.
- M04 được đọc như projection hiện tại, kèm pendingSubmissions. GET thống kê không âm thầm chạy sync hoặc tạo ReviewProgress.

## Phạm vi thống kê giảng viên — chốt 24/10

- Quyền lấy từ Unit.ownerId của principal TEACHER hiện hành; không dựa vào người sở hữu câu hỏi hoặc người thao tác tạo bài. Unit của người khác và Unit không tồn tại cùng trả 404. ADMIN dùng thống kê tổng hợp riêng, không giả danh TEACHER.
- Chỉ tính LEARN qua Attempt.lessonVersionId → LessonVersion → Lesson → Unit. Không nối ngược ngân hàng câu hỏi/LessonQuestion hiện tại để suy ra nguồn, vì một câu có thể được dùng ở nhiều Unit. Lượt PRACTICE/REVIEW không có Unit nguồn nên không đưa vào số liệu Unit; UI nêu rõ giới hạn.
- Giữ dữ liệu các phiên bản cũ và bài đã retire. Kết quả câu lấy đúng QuestionVersion của Attempt; mỗi phiên bản câu thống kê riêng. Tên ở bảng bài học là phiên bản mới nhất người biên soạn quản lý, số liệu gộp lịch sử của root Lesson.
- Người bắt đầu là distinct học viên có lượt LEARN trong phạm vi, toàn bộ thời gian. Đang làm là số lượt IN_PROGRESS hiện tại; hoàn thành là cặp học viên/bài COMPLETED, không tăng do học lại. Bảng từng bài hiển thị số người hoàn thành bài đó.
- Khoảng thời gian chỉ lọc lượt đã nộp và kết quả câu. Số câu mở gợi ý đếm mỗi AttemptQuestion một lần trong bài đã nộp, không lấy GUIDED_RETRY/CORRECTION ghi đè FIRST.
- Chỉ trả số tổng hợp và metadata Unit/bài/câu; không trả học viên nào làm gì, email, đáp án, keys, transcript hoặc Attempt ID. API/giao diện có phân trang, trạng thái rỗng, lỗi mạng và thử lại. Unit/khoảng thời gian giữ trên URL để reload/Back/Forward.

## Tích hợp M05/M06

- LearnerRewardsReader nhận principal gốc; M05LearnerRewardsReader đã tích hợp GET service M05 để đọc XP/cấp độ/streak/huy hiệu/Giải đấu tuần đã commit. M07 không tự tính hoặc cộng thưởng.
- Chưa có provider: rewards.state=UNAVAILABLE, các giá trị null. UI ghi chưa có dữ liệu; không dùng số 0 hay huy hiệu giả.
- Số kết nối quản trị dùng LiveConnections M06, chỉ socket còn mở/chưa hết cửa sổ heartbeat; distinct user cho nhiều tab. Đây là snapshot RAM của một backend, không phải số người đang học hoặc presence nhiều server.
- Không thêm bảng/migration, không chiếm số phiên bản migration mà M05/M06 có thể dùng. Giữ nguyên V1–V8 sau tích hợp M05.

## API và kiến trúc

StatisticsController → StatisticsService/interface + impl → StatisticsRepository/interface + impl → JPA EntityManager (JPQL aggregate). Dữ liệu chỉ đọc, transaction REPEATABLE_READ cho mỗi response DB; no-store/private. Không trả Entity, JSON kết quả có keys, transcript, nội dung đáp án, session hoặc dữ liệu định danh của học viên trong thống kê quản trị.

Hợp đồng: [M07_API.md](../source/common/M07_API.md). Quyền xem hồ sơ học viên theo lớp hoặc export CSV cần đặc tả riêng ở P1; thống kê tổng hợp theo Unit của TEACHER đã thuộc phạm vi hiện tại.

## Nghiệm thu

- Đối chiếu FIRST/support/seen/blank/correction; bài chưa nộp; bộ lọc thời gian; tổng theo mục tiêu/chủ đề; phân trang; lịch sử bài retire và học lại.
- Chặn đọc chéo user, chặn STUDENT/TEACHER truy cập thống kê admin, chặn session đã khóa; không lộ keys/PII.
- Trình duyệt: hồ sơ rỗng, kết quả thật, liên kết M03/M04, F5/deep link, đổi tài khoản, lỗi mạng/thử lại, quản trị và kích thước mobile.
- Không công bố benchmark tải, hiệu quả học tập hoặc hoàn thành tích hợp M05/M06 khi chưa có bằng chứng.

Xem [tiến độ](M07_PROGRESS.md), [ca thao tác](M07_MANUAL_TEST_CASES.md), [kết quả kiểm tra](../statics/results/module-7-validation.md).

Tích hợp 25/10: dữ liệu thưởng toàn lịch sử, không phụ thuộc bộ lọc kết quả; tuần chưa tham gia trả null, lịch sử tối đa 5 tuần chốt, huy hiệu chỉ lấy earnedAt khác null. M07 không tự chấm/thưởng/tính hạng mới.
