# Tiến độ M07 — Hồ sơ và thống kê

Cập nhật 25/10/2026. Nhánh feature/m07-profile-statistics; M07 gốc 85399a4, đã tích hợp main 20652a7 đã có M05/M06.

## Thống kê giảng viên — 24/10

- [x] Quyền theo Unit.ownerId của principal, foreign/missing Unit cùng 404; không cho teacher đọc hồ sơ học viên hoặc thống kê admin.
- [x] Năm API GET: overview, Unit sở hữu, hiệu quả từng bài, câu thường sai và breakdown chủ đề/mục tiêu. Có bộ lọc Unit/khoảng thời gian/độ khó và phân trang.
- [x] Chỉ LEARN theo LessonVersion đã chốt → Lesson → Unit; không lẫn câu dùng chung từ Unit khác hoặc PRACTICE/REVIEW không có Unit nguồn. Giữ phiên bản cũ và bài retire.
- [x] Người bắt đầu/lượt dở/hoàn thành, FIRST/hỗ trợ/câu mới/cũ, số câu mở gợi ý, làm lại/chữa; không trả danh tính, đáp án hoặc Attempt ID học viên.
- [x] TeacherStatistics trên UI: Unit của tôi, khoảng kết quả, hiệu quả bài/câu, lỗi mạng/thử lại, dữ liệu rỗng, deep link/reload/Back/Forward.
- [x] Production build đạt. API tập trung 15/15 trước khi bổ sung ca lịch sử hai phiên bản; toàn backend **173/173 PASS**, riêng M07 16/16 (15 API + 1 presence), chi tiết shutdown JVM trong biên bản.
- [x] Browser: lượt đầy đủ 62/63, lỗi duy nhất ở fixture mới đọc nhầm detail.topicId thay vì detail.unit.topicId khi tạo PRACTICE; sửa fixture rồi cả nhóm teacher 3/3 đạt. 63 ca khác nhau đã qua, không phải một lượt 63/63 duy nhất.
- [x] Xem ảnh teacher mobile 390×844, không tràn ngang trang; bảng cuộn trong khung.
- [x] Cập nhật kế hoạch, API/OpenAPI 1.1.0 và checklist theo phạm vi Unit đã thống nhất.
- [x] Áp dụng đủ 30 file M07 vào thư mục dự án, build/chạy lại localhost 5173/8080. Chrome đăng nhập TEACHER mở Thống kê học liệu, API 200, không page error; kiểm tra số lượng dữ liệu trước/sau không đổi, Flyway V5. Chưa commit/push.

## Bản nền học viên/admin — 21/10

- [x] Thống nhất phép đo FIRST/support/seen/retry/correction và phạm vi thời gian.
- [x] API STUDENT: overview, breakdown theo chủ đề/mục tiêu, bài hoàn thành.
- [x] API ADMIN: overview, độ khó, câu thường sai theo phiên bản.
- [x] Controller/service/repository interface + implementation; JPQL aggregate, chỉ đọc.
- [x] Giao diện Hồ sơ học tập và Thống kê học tập, phân trang, liên kết M03/M04, lỗi mạng và dữ liệu rỗng.
- [x] Interface LearnerRewardsReader để M05 tích hợp; chưa có provider thì trả UNAVAILABLE/null.
- [x] Ước lượng kết nối mở từ M01, đếm một account khi có nhiều tab; chưa thay thế presence heartbeat của M06.
- [x] Build frontend và backend.
- [x] Backend đầy đủ 165/165; riêng M07 8/8 (7 API + 1 presence); 55 ca browser hồi quy và 5 ca M07 đã qua, chi tiết phạm vi trong biên bản.
- [x] Xem ảnh mobile hồ sơ/admin và kiểm tra không tràn ngang ở 390×844; kiểm tra liên kết Markdown và JSON OpenAPI.
- [x] Áp dụng vào thư mục dự án, build/chạy local chuẩn 5173/8080; Chrome mở Hồ sơ học tập, GET API 200, không có page error. Số bản ghi cũ giữ nguyên; Flyway vẫn V5. Đã dừng môi trường test riêng.

Không thêm migration, giữ V1–V5. Chưa làm xuất báo cáo P1, phân quyền giảng viên xem hồ sơ học viên, hiệu quả học tập hoặc benchmark tải. M05/M06 đã được đưa vào từ main; không thay luật thưởng hoặc phục hồi. Hồ sơ không thay đổi dữ liệu học/nộp/ôn.

Xem [kế hoạch](M07_IMPLEMENTATION_PLAN.md), [API](../source/common/M07_API.md), [ca thao tác](M07_MANUAL_TEST_CASES.md) và [kiểm tra](../statics/results/module-7-validation.md).

## Tích hợp main M05/M06 — 25/10

- Giữ đầy đủ điều hướng hồ sơ, thống kê TEACHER/ADMIN và phần thưởng/League cùng cơ chế lưu nháp/phục hồi M06. Quyền giảng viên tiếp tục theo Unit.ownerId.
- M05LearnerRewardsReader tái sử dụng GET service M05 với principal gốc: tổng XP/cấp độ/streak, chỉ huy hiệu đã nhận, tuần hiện tại nếu đã tham gia và tối đa 5 tuần chốt gần nhất. GET hồ sơ không sync, thưởng, tạo tuần hoặc join; hỗ trợ level kiểu long như M05.
- Snapshot kết nối quản trị chỉ đếm socket mở trong cửa sổ heartbeat M06, nhiều tab tính một tài khoản. Không đếm kết nối hết liveness và không suy ra số người đang làm bài.
- Backend: 265/266 ở lượt đầy đủ, lỗi duy nhất do tên bảng trong fixture mới; sửa fixture rồi cả 3/3 ca tích hợp thưởng qua. Tổng cộng **266 ca backend khác nhau đã qua qua các lượt**, không phải một lượt 266/266 duy nhất. Browser **91/91 PASS** trong một lượt, **4.7 phút**. Build TypeScript/Vite và manager **9/9 PASS**. Chi tiết tại biên bản M07. Không thay migration V1–V8, không ghi test vào demo.

## Người đang online — 28/10/2026

ADMIN có một danh sách tên/vai trò trong Quản lý tài khoản, mỗi tài khoản một dòng; bỏ ô lặp ở Trang chủ/Thống kê, các nút cập nhật và đoạn giải thích. API users chỉ ADMIN, đọc batch ACTIVE từ snapshot socket còn heartbeat. WebSocket PRESENCE_CHANGED cập nhật ngay, giữ đối soát/reconnect/fallback và thay danh sách theo ID. Không migration. Xem [kiểm tra danh sách](../statics/results/admin-online-list-validation.md); các mốc trước tại [online ban đầu](../statics/results/admin-online-validation.md) và [WebSocket](../statics/results/admin-presence-live-validation.md).
