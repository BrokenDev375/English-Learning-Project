# Kiểm tra phân công và duyệt học liệu — V13

Nhánh `feature/m07-profile-statistics`, nền `cb31f80`. Giữ các thay đổi V12 và giao diện trước đó chưa commit. Nghiệp vụ tại [phân công và duyệt học liệu](../../docs/CONTENT_GOVERNANCE.md).

## Phạm vi

ADMIN quản lý chủ đề/mục tiêu/vòng/số thứ tự, phân công giáo viên và duyệt học liệu. TEACHER biên soạn bài trong vòng được giao, quản lý câu hỏi/tài liệu cá nhân, gửi duyệt và nhận lý do trả lại. API dùng quyền hiện hành, không suy quyền sửa bài từ ownerId. Giao diện có hàng đợi, bản chụp nội dung gửi duyệt và lịch sử.

V13 thêm assignee, khóa ghi học liệu và yêu cầu duyệt. Giữ người tạo, số vòng, nội dung đã công bố, version, kết quả và tiến độ. Khi bài sửa đang chờ, học viên tiếp tục dùng bản công bố cũ. Bản chụp duyệt vẫn đọc được sau khi rút yêu cầu rồi bỏ bản nháp; liên kết nội dung hiện tại hiển thị trạng thái trống có đường quay về danh sách.

## Backend

- Lượt hồi quy đầy đủ: **330 ca, 326 qua, 1 failure và 3 errors, không skip**, 24:52 phút. Bốn ca cũ dùng kỳ vọng owner hoặc fixture giáo viên tự công bố không còn đúng với V13.
- Sửa kỳ vọng admin vẫn là người tạo bài, giáo viên phụ trách được biên soạn; fixture bootstrap dùng publisher tin cậy, retire qua ADMIN. Không mở lại quyền tự công bố của giáo viên.
- Chạy lại `AuthoringApiTest,CurriculumSeedTest,ContentGovernanceApiTest`: **36 ca, 35 qua**, một phép so sánh JSON của ca tương thích receipt mới khác kiểu IntNode/LongNode sau khi lưu MySQL. Chuẩn hóa JSON mong đợi qua cùng bước parse; không thay logic ứng dụng.
- Lượt cuối `ContentGovernanceApiTest`: **13/13 qua**, không failure/error/skip. Tổng cộng **331 ca backend khác nhau đã qua qua các lượt**; đây không phải một lượt 331/331 duy nhất.

Nhóm mới kiểm tra quyền cấu trúc và phân công, chuyển/bỏ phân công, quyền đọc đúng dependency/file riêng, không được sửa câu hỏi của tác giả khác, khóa nháp PENDING, duyệt nguyên tử, trả lại bắt buộc lý do, snapshot bất biến, rút/replay, dependency stale, bản công bố cũ, quyết định đồng thời, quyền học viên và replay receipt giáo viên trước V13. Migration được kiểm tra trên MySQL thật, gồm giữ dữ liệu cũ và backfill assignee. Các nhóm học/làm bài/ôn tập/thưởng/League/thống kê và cập nhật audio đã qua hồi quy.

Các lệnh Maven chạy trong image kiểm thử với bind mount source hiện tại, cache Maven và Docker socket; MySQL Testcontainers tách khỏi database đang dùng. Bootstrap seed mới khởi động thành công với 13 Material, 90 Question, 6 Lesson, không restart loop. Runtime backend đóng gói từ bản sao source riêng; kiểm tra SHA-256 toàn bộ main source/pom trước khi dùng image.

## Frontend và hợp đồng

Production build TypeScript/Vite trong Docker đã qua. Lượt giao diện trước sửa trạng thái bỏ nháp: **122/122 qua** trên project riêng `english-learning-governance-final-e2e`, cổng `15209/18119`. Lượt cuối trên source có trạng thái bỏ nháp: **123/123 qua trong 7.4 phút**, project mới `english-learning-governance-acceptance-e2e`, cổng `15211/18121`, một worker.

Bảy ca giao diện mới thao tác thật các vai trò: admin phân công; teacher gửi bài và admin duyệt; trả lại/sửa/gửi lại; mất ACK/replay/rút; người mới tiếp quản dependency riêng; giữ bản công bố cũ đến lúc duyệt; giữ lịch sử sau bỏ nháp và mở lại/reload an toàn. Hồi quy giữ các ca game, hoàn thành bài/vòng, bảng xếp hạng, XP/League, khôi phục kết nối, học liệu và tài khoản.

Hợp đồng: **20** fixture V13 hợp lệ/không hợp lệ và **50** ví dụ/fixture M02/M03/M07 qua; **24** response API đọc thật ở môi trường riêng `15209` khớp schema. Kiểm tra schema OpenAPI, **745** tham chiếu nội bộ và liên kết Markdown thành công. Response receipt Unit cũ được phép thiếu trường phân công; Unit GET mới luôn có trường nullable. Không in câu hỏi, thông tin xác thực hay dữ liệu người dùng vào log kiểm tra.

Log, hash manifest và backup SQL nằm riêng trong staging `.test-state`, không đưa vào Git. Không chạy E2E lên `15183`. Lượt hồi quy dùng một worker trên máy local, không phải đo tải production.

## Rollout localhost:15183 — PASS

Đã sao lưu SQL riêng, nâng database đang dùng từ V12 lên V13 bằng đúng image backend/client của lượt nghiệm thu cuối tại 15211. HTTP 200, đường dẫn và SHA-256 JavaScript asset giống bản kiểm thử; backend /api/health UP. Source đã áp dụng vào dự án trên nhánh feature/m07-profile-statistics; HEAD vẫn cb31f80, chưa commit/push. Giữ các thay đổi V12 và giao diện có trước đó.

Đối chiếu từng hàng cũ bằng SHA-256 của JSON đầy đủ ở 34 bảng: chủ đề/mục tiêu/vòng và số thứ tự, tài liệu/file metadata, câu hỏi/bài và mọi version/liên kết, lượt làm/đáp án/FIRST trial, submissions, lesson_progress, receipt, ôn tập, XP/claim/profile/ngày/huy hiệu/policy và League đều giữ nguyên. Bảng units chỉ thêm assigned_teacher_id ngoài các cột cũ đã đối chiếu. Giáo viên tạo vòng cũ trở thành người phụ trách ban đầu đúng migration; vòng do admin tạo để chưa phân công. content_control có đúng singleton, không tạo yêu cầu duyệt giả cho nội dung cũ đã công bố.

Backup/log/audit chỉ lưu riêng trong staging .test-state, không đưa vào Git. Không chạy E2E vào 15183, không reset/xóa volume. Đã dừng ba môi trường kiểm thử 15207/15209/15211 và giữ volume. Bản 15183 vẫn chạy; kiểm tra lại frontend và health sau khi dừng môi trường thử.
