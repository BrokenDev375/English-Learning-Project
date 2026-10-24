# Tiến độ M06 — Kết nối và phục hồi

Cập nhật **23/10/2026**, nhánh `feature/m06-connection-recovery`, nền `57f2951`. M06 gốc đã commit `3956855`; bản sửa ba lỗi concurrency ngày 23/10 chưa commit/push. Trạng thái Git quyết định mốc chính xác.

## Bản sửa sau review ngày 23/10

- Dọn clean draft dùng một transaction readwrite cho đọc/kiểm tra/xóa, tránh xóa bản vừa được writer khác sửa. Giữ giới hạn 100 clean/30 ngày và fence user/epoch.
- Create giữ riêng intent/requestId. Persist lựa chọn B trước khi đối soát A; chỉ trả kết quả B để điều hướng, không mở A thay B. queuedAt tùy chọn giúp reload lấy lựa chọn mới nhất, tương thích record schema v1 cũ. Sender vẫn tuần tự theo lane; lỗi chưa rõ giữ cả command, không xóa A chỉ vì mất mạng.
- Auth callback sau socket close được kiểm tra generation cả khi thành công/lỗi; phản hồi của lượt cũ không phát session-expired hoặc tạo retry timer. 401 của lượt hiện tại vẫn yêu cầu đăng nhập.
- Thêm hai browser E2E chọn B từ tab mở sẵn, gồm trường hợp A đã commit/mất ACK và tải lại B giữa đối soát; thêm harness IndexedDB thật và hai regression manager.
- Kiểm tra tập trung: manager **7/7 PASS**, IndexedDB/journal **6/6 PASS**, production build PASS. Toàn bộ browser E2E **68/68 PASS** trong một lượt, 3,1 phút: 13 M06 + 55 hồi quy; xem biên bản cho môi trường và giới hạn.
- Không đổi backend, migration V1–V5, luật chấm hoặc lịch M04. Công việc M07 được giữ ở Git stash riêng trước khi chuyển nhánh.

## Phần đã triển khai

- [x] Hợp đồng bổ sung [M06_API](../source/common/M06_API.md), [OpenAPI presence](../source/common/openapi-m06.json) và chính sách dữ liệu cục bộ.
- [x] Heartbeat liveness/presence theo user và nhiều socket, timeout/sweep, rate/subscription limit; GET presence riêng tư, giữ thu hồi phiên M01.
- [x] Subscription đúng owner, ATTEMPT_CHANGED sau commit, không phát từ replay/rollback; client đăng ký rồi GET đối soát revision.
- [x] Connection manager tách khỏi App, CONNECTED/ACK deadline, backoff/jitter, hủy callback/timer cũ, HTTP vẫn hoạt động khi socket lỗi.
- [x] IndexedDB version 1 lưu snapshot/draft/playerState/feedback hợp lệ và pending/ACK, chia theo user/Attempt/writer; tự lưu tại máy 250 ms, server 1500 ms.
- [x] Journal cho create và review sync; pending Attempt/baseline gộp một hàng nguyên tử; giữ đúng requestId/payload qua reload và mất phản hồi.
- [x] Sender tuần tự theo lane, Web Locks khi có và lease IndexedDB dự phòng; bản từng tab riêng, khôi phục bản khác có lựa chọn.
- [x] 401/409/timeout/503, generation mới hơn ACK, submit chờ offline, hỗ trợ/chữa không chấm cục bộ. Logout/đổi user fence dữ liệu và callback cũ.
- [x] Cách ly record sai trước dispatch; cảnh báo lỗi IndexedDB; dọn tối đa 100 bản clean/30 ngày, không tự xóa dirty/pending.
- [x] Script browser restart/recreate/seed và giữ kết quả/tiến độ/ledger M04; checklist và biên bản bàn giao.

## Kiểm tra M06 gốc ngày 21/10

- Baseline: build frontend đạt; 59 test backend tập trung M01/M03/M04 đạt trước sửa.
- Backend M06: **162/162 PASS**, 0 failure/error/skipped; XML được lưu trong thư mục ignored.
- Harness manager: **5/5 tình huống PASS** với clock/RNG/transport kiểm soát, gồm phiên mất sau close 1006.
- Browser E2E bản bàn giao cuối: **66/66 PASS**, 0 failed/skipped, gồm 11 M06 + 55 hồi quy; đã gồm guard snapshot, fallback không Web Locks, auth/reconnect/logout.
- Script restart/recreate từ source M06: **PASS** cả hai checkpoint; replay giữ payload, snapshot/kết quả/progress/review ledger và số hàng không tăng trùng.

Lệnh, mốc source, kết quả lượt cuối và giới hạn: [nghiệm thu M06](../statics/results/module-6-validation.md). Không dùng các con số này cho thay đổi về sau mà chưa kiểm tra.

## Giới hạn và bàn giao

Chưa có service worker/PWA, login offline, tải trước toàn bộ media, Redis/nhiều backend hoặc XP M05. Bản chưa commit IndexedDB vẫn có cửa sổ mất dữ liệu; storage có thể bị trình duyệt xóa. Không Web Locks thì mỗi document dùng writer mới, nhận lại bản cũ qua nút khôi phục và lease có thể phải đợi hết hạn.

P0 chỉ có schema IndexedDB v1: đã thử khởi tạo và record sai, chưa có migration v1→v2 thực tế. Chưa ép độc lập trường hợp blocked-upgrade, xóa IndexedDB bị policy chặn hoặc hai thiết bị vật lý; ghi rõ trong biên bản. Không coi E2E interception là benchmark hoặc kiểm tra mất gói/ổ đĩa vật lý.

Người dùng review rồi quyết định commit/push/PR. Không tạo file mô tả PR riêng.
