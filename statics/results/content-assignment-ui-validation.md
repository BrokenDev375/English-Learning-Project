# Lối vào phân công → biên soạn → duyệt trên website

ADMIN có sidebar Phân công biên soạn và tab Phân công trong học liệu; chọn giảng viên theo vòng ngay ở form riêng chỉ có assignee. Quản lý vòng học và Xem bài chờ duyệt nối trực tiếp sang danh mục/hàng đợi hiện có. TEACHER thấy Vòng được giao và Soạn bài, lọc theo unit trong URL và chọn sẵn vòng khi thêm bài mới. API/quyền/receipt/revision và database không đổi; giữ mọi phần việc trước đó trên feature/m07-profile-statistics.

Production TypeScript/Vite PASS. Browser **35/35 PASS**, một worker, 2.2 phút trên project riêng english-learning-content-assignment-ui-e2e, 15219/18129. Bao gồm tám ca governance, học theo vòng, thống kê giảng viên, danh mục, biên soạn câu/bài và điều hướng.

Ca mới đi từ menu thật: admin Phân công biên soạn → chọn giảng viên/lưu → teacher Vòng được giao/Soạn bài → giữ bộ lọc vòng qua reload và chọn sẵn khi thêm bài → gửi duyệt → admin Xem bài chờ duyệt/duyệt công bố. Đối chiếu title/topicId/roundNumber giữ nguyên khi phân công; học viên chưa thấy bài trước khi duyệt và thấy đúng bài sau khi duyệt. Dữ liệu câu hỏi/bài nháp được chuẩn bị bằng API test; những bước phân công/gửi duyệt/duyệt và điều hướng được thao tác bằng giao diện.

Đã xem ảnh trang phân công và form một trường giảng viên; legacy form đầy đủ vẫn có tại Danh mục. Backend dùng lại đúng image đang chạy, giữ hash toàn main source/pom để xác minh không thay đổi; không chạy lại backend tests cho thay đổi chỉ ở frontend. Không migration/ghi dữ liệu test vào 15183. Log/ảnh ở staging .test-state.

Docker hết address pool khi tạo mạng thử mới: đã thu hồi đúng project admin-online-list-e2e cũ sau khi xác minh mọi container dừng, giữ named volumes; không dọn network/container của ứng dụng đang dùng.

## Rollout local — PASS

Đã áp dụng source trên feature/m07-profile-statistics, HEAD cb31f80. Chỉ thay image client của localhost:15183, giữ nguyên image/container backend và schema V13. JavaScript path/hash trùng bản thử; HTTP 200 và health UP. Không reset/xóa volume hoặc thay dữ liệu học. Các file ngoài phạm vi khớp baseline.

Đã dừng/gỡ container và mạng của riêng project thử content-assignment-ui-e2e, giữ named database/asset volumes để không chiếm thêm dải mạng Docker. 15183/18093 tiếp tục hoạt động. Chưa commit/push.
