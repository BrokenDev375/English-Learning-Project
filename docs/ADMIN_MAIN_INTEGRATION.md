# Tích hợp main vào nhánh giám sát phiên — 31/10/2026

Nguồn: `feature/admin-session-monitoring` tại `7c171f1`, ghép `origin/main` tại `fe3ab0c`. Báo cáo kết quả kiểm thử tại [biên bản tích hợp](../statics/results/admin-main-integration-validation.md). Các số kiểm thử trong báo cáo của từng nhánh trước đó là lịch sử, không thay kết quả của lần tích hợp này.

## Xử lý 13 file conflict

| Nhóm | Cách xử lý |
|---|---|
| `connection/manager.ts` | Giữ đồng thời metadata `tabId` của HELLO/HEARTBEAT, gắn lại tab khi HELLO ACK chậm, subscription contest/permission và reconnect đối soát. Không gắn `tabId` vào command subscription. |
| `learning/session.ts` | Giữ flush nháp khi hết phiên của nhánh admin, đồng thời dọn/fence session bài viết và cuộc thi khi đổi user/đăng xuất. `hasUnsavedLearning` tính cả ba loại lượt. Bổ sung flush local writing/contest trước khi fence storage; vẫn giới hạn chờ tối đa hai giây và báo nếu chưa xác nhận lưu. |
| `AttemptRunner.tsx` | Giữ tracking hoạt động/attempt và RestrictedAudio, cùng hoạt động bảy phần, game theo hoạt động, câu điền ngay trong bài đọc và layout đọc hiểu. Truyền cả `attemptId`/`userId` và props bài đọc. |
| `AttemptResult.tsx` | Kết quả/chữa bài vẫn hỗ trợ inline reading và audio giới hạn chung trong cùng Attempt; không tạo thêm lượt nghe khi chuyển sang kết quả. |
| `LearningMaterial.tsx` | Kết hợp props audio và nội dung đọc tùy biến/thu gọn. Audio trong lượt học dùng RestrictedAudio; nơi xem trước học liệu giữ player hiện có. |
| `scripts/run-e2e.mjs` | Giữ một khai báo projectName, kiểm tra tiền tố môi trường test trước khi chạy Compose. |
| `scripts/test-connection.mjs` | Giữ cả hai ca gắn lại tab và hai ca contest/permission; tổng 14 kịch bản. |
| `AttemptPersistenceTest`, `ContentGovernanceMigrationTest`, `EngagementMigrationTest`, `OrderingMigrationTest`, `RoundNumberMigrationTest` | Cập nhật kỳ vọng theo chuỗi V1–V18; giữ các kiểm tra dữ liệu và chạy lại không tạo thay đổi. |
| `AGENTS.md` | Giữ điểm vào và lưu ý của hai nhánh, ghi rõ mốc V18 mới và tách bằng chứng lịch sử. |

## Các điểm cần ghép ngoài dấu conflict

- Giữ nguyên tất cả migration V1–V17 từ main. Chuyển nguyên nội dung `V14__attempt_audio_limits.sql` của nhánh admin thành `V18__attempt_audio_limits.sql`; chỉ còn một V14 cho lesson activities.
- `ContestPaperMigrationTest` tính cả V17 và V18 sau nền V16. Thêm kiểm tra nâng từ main V17 lên V18: giữ tài khoản, lịch sử V14 vẫn là activities, có bảng audio mới và chạy lại không làm thêm.
- Khi hết phiên, App giữ URL cuộc thi để đăng nhập lại có thể khôi phục đúng phòng. Writing/Contest session dừng lịch gửi HTTP và flush IndexedDB, không purge nháp như đăng xuất chủ động.
- Khi claim lại contest sau khôi phục, giữ đáp án local chưa đồng bộ; vẫn yêu cầu đối chiếu nếu server thay đổi khác, không ghi đè bản local bằng đáp án cũ chỉ vì vừa nhận lại quyền làm bài.
- Các test contest nhảy đồng hồ quá năm phút đăng nhập lại trước khi kiểm tra luật thi, giữ nguyên idle timeout ở runtime và bộ kiểm thử phiên riêng.

## Database đã chạy trước tích hợp

Database mới và database theo chuỗi main đến V17 dùng V18 bình thường. Database từng áp dụng `V14__attempt_audio_limits.sql` thuộc lịch sử nhánh admin cũ; V14 đó khác V14 của main và **không tương thích trực tiếp với chuỗi sau merge**. Trước khi chạy bản tích hợp trên loại DB này, cần sao lưu cả MySQL/file, xác minh lịch sử và chuyển đổi trên bản sao riêng, giữ bộ đếm lượt nghe cùng dữ liệu nghiệp vụ. Lượt tích hợp code này không sửa lịch sử Flyway, repair, reset hoặc xóa volume của database người dùng; không có bước chuyển đổi DB nhánh cũ tự động.

Môi trường browser integration dùng project/volume mới `english-learning-e2e-admin-main-20261031`, cổng 15323/18233. Do Docker local hết pool subnet tự cấp, override IPAM chỉ nằm trong `.test-state/merge-network.compose.yml`; không đổi cấu hình mạng mặc định của dự án hoặc dọn network cũ. Không chạy E2E ghi dữ liệu vào demo/contest 15223.
