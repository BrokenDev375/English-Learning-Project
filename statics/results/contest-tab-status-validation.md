# Trạng thái tab trong giám sát cuộc thi — 30/10/2026

Nhánh `feature/online-contests`, bổ sung sau [trạng thái thí sinh](contest-monitor-status-validation.md). Bản kiểm tra tại `http://localhost:15223`, API 18133; không thay ứng dụng 15183.

## Hành vi và giới hạn

- API Participant chiếu thêm `tabHidden` từ thuộc tính đã lưu trong DB. Không thêm migration hoặc đổi luật phạt/thu hồi.
- Trong giờ thi, người APPROVED đã check-in, chưa chốt và chưa hết giờ hiện **Trong tab thi** hoặc **Ngoài tab thi** khi heartbeat còn mới. Tín hiệu visibility do writer hợp lệ gửi, server chấp nhận rồi phát WebSocket invalidation; giám thị GET đối soát. Không chờ chu kỳ heartbeat để báo một lần rời/quay lại được gửi thành công.
- Sau 45 giây không có heartbeat mới, hiện **Chưa rõ trạng thái tab**, tách với **Không có kết nối thi**. `tabHidden` vẫn là tín hiệu cuối đã nhận, không khẳng định vị trí hiện tại khi offline. Không hiển thị nhãn trong/ngoài tab cho người đã nộp, bị thu hồi, chưa vào phòng hoặc đã hết giờ.
- Khi tải lại trang/claim, client đối chiếu trạng thái document với tín hiệu cuối server hoặc cuối hàng đợi. Gửi tín hiệu quay lại nếu cần; giữ thứ tự journal/receipt và không tăng lượt rời màn khi gửi trùng. Khi phòng chuyển từ chờ sang thi, đối soát cả trạng thái document hiện tại.
- Thu hồi không xóa bản ghi: vẫn có trong **Tất cả** và **Đã thu hồi**, hiện **Đã thu hồi quyền thi**; không hiện đang ở trong/ngoài tab thi. Không khóa tài khoản website. Lần rời thứ ba vẫn chỉ cảnh báo để ADMIN quyết định.
- Dựa trên Page Visibility của trình duyệt; không biết tab đích hoặc chứng minh sự chú ý. Trình duyệt đóng/mất mạng có thể chưa gửi được tín hiệu mới; cần chờ hết cửa sổ kết nối để thể hiện chưa rõ.

## Kiểm tra trên source cập nhật

- **13/13 backend PASS**, 0 failure/error/skip, `ContestApiTest`, tổng 1:45 phút. Ca bổ sung kiểm tra hidden/returned, heartbeat hết hạn nhưng giữ tín hiệu cuối, replay cũ không ghi lùi trạng thái hoặc tăng lượt, và REVOKED còn trong ALL/REVOKED.
- **3/3 browser PASS** trong một lượt, 1.1 phút:
  - Đăng ký/duyệt/check-in/chờ → thi; giám thị thấy Trong → Ngoài → tải lại/claim → Trong; số lần rời vẫn là 1. Sau vi phạm tiếp theo, phạt/cảnh báo/thu hồi vẫn đúng; thí sinh còn trong hai bộ lọc, mất quyền thi nhưng còn đăng nhập website.
  - Mất phản hồi nộp, khôi phục sau reload và ngăn tab thứ hai chiếm writer còn hoạt động.
  - Metadata có kiểm soát cho tiến trình, hidden, offline và heartbeat cũ dù online=true; không hiện tab cũ là trạng thái hiện tại. Nhãn chốt/thu hồi/hết hạn không đi kèm nhãn tab. Bảng ở 390px không tràn ngang.
- TypeScript build và Docker build server/client PASS; Compose mysql/server/client healthy, UI HTTP 200 và API health UP. DB V17 giữ nguyên, không chạy lại seed thủ công.
- Hợp đồng OpenAPI có thuộc tính bắt buộc `tabHidden` kiểu boolean và tất cả `$ref` nội bộ hợp lệ. Kiểm tra whitespace PASS.

Tái hiện: Maven `-Dtest=ContestApiTest test`; Playwright `test e2e/online-contests.spec.ts --grep 'tab departures|monitor separates|lost submit'` với E2E_BASE_URL trỏ môi trường contest riêng. Log và ảnh trong `.test-state`/file log đã ignore; không đưa trace, credentials vào Git. Các kết quả module/kho bộ đề trước đó là bằng chứng lịch sử riêng.
