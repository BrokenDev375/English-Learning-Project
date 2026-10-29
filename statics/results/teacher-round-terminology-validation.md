# Kiểm tra tên vòng đấu ở giao diện giáo viên — 27/10/2026

## Phạm vi

Từ working tree sạch tại cb31f80, nhánh feature/m07-profile-statistics. Thay nhãn Unit bằng Vòng đấu trong danh mục/biên soạn/chọn vòng/sắp xếp bài/duyệt công bố/thống kê giáo viên và trang chủ vai trò khác học viên. Bỏ badge Chặng; giữ stage/sortOrder trong mục Sắp xếp vòng đấu với Nhóm sắp xếp/Vị trí trong nhóm.

Chỉ frontend, cập nhật selector ở bốn file kiểm thử hiện có và tài liệu. Không thay backend, API, schema, dữ liệu nội dung, số vòng toàn lộ trình, khóa vòng hoặc phần thưởng. Tên do tác giả đã nhập vẫn giữ nguyên. [Quy ước](../../docs/TEACHER_ROUND_TERMINOLOGY.md).

## Kiểm tra đã chạy

- Build Docker client: tsc -b + Vite production PASS; typecheck cuối PASS.
- Chrome/Playwright, project riêng english-learning-teacher-rounds-e2e, UI 15199/API 18109, DB/volume riêng. Backend dùng image hiện có; không chạy E2E trên preview 15183.
- Lượt tập trung gồm module-two-catalog, module-two-authoring, module-seven-teacher, navigation và hai ca giáo viên/admin duyệt giới thiệu của M03: **23 passed, 1 failed / 24**, 58,4 giây. Một assertion điều hướng còn chờ tiêu đề cũ Thêm unit; cập nhật sang Thêm vòng đấu.
- Chạy lại navigation.spec.ts: **5/5 PASS**, 10 giây. Tổng 24 kịch bản khác nhau đã qua qua hai lượt, không ghi là một lượt 24/24.
- Ca tạo vòng mở mục Sắp xếp vòng đấu và nhập nhóm thứ tự 2, lưu, sửa tên và tải lại. Chọn vòng cho bài, xuất bản/sắp xếp bài, readonly giáo viên/admin và quyền nguồn giữ hành vi cũ.
- Thống kê kiểm tra bộ lọc Vòng đấu của tôi, vòng thuộc người khác bị từ chối, trạng thái chưa có vòng, reload/history và viewport điện thoại. Đã xem ảnh m07-teacher-mobile.png có nhãn mới, không thêm ảnh vào Git.
- Điều hướng Back/Forward/direct URL và form editor vẫn phục hồi. URL/API tiếp tục dùng unit/units/unitId và tên type nội bộ giữ nguyên.
- Không thêm test chỉ để kiểm tra đổi chuỗi; điều chỉnh các kịch bản thao tác sẵn có. Không chạy lại backend vì không đổi backend.

## Cập nhật bản chạy

Chỉ cần thay client image ở preview, giữ backend process và MySQL/assets volume. Đối chiếu số tài khoản/lượt làm/bài nộp và hash kết quả cũ trước/sau; không tạo tài khoản hoặc nội dung giả trong bản người dùng.
