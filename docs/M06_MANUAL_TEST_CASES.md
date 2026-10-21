# M06 — Quy trình nghiệm thu kết nối và phục hồi

Đọc cùng [kế hoạch](M06_IMPLEMENTATION_PLAN.md), [hợp đồng](../source/common/M06_API.md) và [biên bản](../statics/results/module-6-validation.md). Bảng dưới là dữ liệu/bước/kỳ vọng; kết quả chạy thực tế ghi riêng trong biên bản, không tự coi mọi dòng đã được thao tác bằng tay.

## Chuẩn bị

- Docker Desktop, Node/dependency theo lockfile; STUDENT A/B khác nhau và ADMIN trong môi trường test. Không ghi mật khẩu thật vào tài liệu.
- Dùng bài seed “A day with Linh — Morning routine”, 10 câu với chọn/điền/ghép và audio; PRACTICE/REVIEW dùng kho đang công bố. M04 sync có thể cần xử lý kết quả trước khi tạo REVIEW.
- E2E mặc định project `english-learning-e2e`, 5174/8081; script restart project `english-learning-m06-acceptance`, 5176/8083. Không trỏ test vào demo 5173/8080 hoặc xóa volume.
- Trình duyệt có IndexedDB. Web Locks hỗ trợ thì writer giữ qua reload và lane giải phóng khi document bị hủy; không hỗ trợ thì writer mới mỗi document và có nút nhận lại bản cũ. Không giả lập quyền bằng userId trong storage.

## Kết nối và quyền

| ID | Bước | Kỳ vọng |
|---|---|---|
| WS-01 | Đăng nhập, mở socket đúng cookie/origin; thử origin sai hoặc phiên không hợp lệ | Handshake đúng quyền; CONNECTED rồi HELLO/ACK; origin/phiên sai bị từ chối |
| WS-02 | Bỏ HELLO/heartbeat ACK bằng interception, vẫn để HTTP chạy | Client sau deadline 10 giây reconnect có backoff/jitter; HTTP vẫn lưu được, không tạo Attempt mới |
| WS-03 | Không gửi heartbeat, hoặc chỉ gửi message sai tới hết thời hạn | Presence hết hạn, socket bị dọn; message sai không kéo dài liveness |
| WS-04 | Mở hai tab A, gọi GET `/api/me/connections`; đóng một tab | 2 rồi 1 connection; online còn true; B không xem presence A, endpoint không trả ID phiên/socket |
| WS-05 | SUBSCRIBE Attempt A bằng A/B; đổi role/khóa/logout A | Chỉ owner STUDENT được subscribe; mất quyền đóng socket, không gửi event cho B |
| WS-06 | Lưu một mutation thật, replay cùng key; ép receipt lỗi cuối transaction | Chỉ commit thật phát ATTEMPT_CHANGED; replay/rollback không phát; event không có đáp án/keys |
| WS-07 | ACK sai requestId, callback socket cũ, dừng manager khi timer còn chờ | Không báo sẵn sàng giả, không dựng socket mới sau stop; cap/jitter đúng cấu hình |

WS-07 có harness clock/RNG kiểm soát; WS-03 có unit clock và presence integration. Để demo thủ công timeout thật, đợi hết timeout + sweep thay vì dùng số giây tuyệt đối làm điều kiện tức thời.

## Lưu tại máy, journal và nhiều tab

| ID | Bước | Kỳ vọng |
|---|---|---|
| REC-01 | Mở LEARN, tắt mạng, nhập đáp án và đổi câu/giây audio | Tiếp tục phần đã tải; sau commit IndexedDB báo Đã lưu tại máy; không báo đã lưu server |
| REC-02 | Có local draft, bật mạng rồi reload | Auth được xác minh trước hydrate; giữ ID/Version/permutation/đáp án/playerState; autosave đối soát đúng |
| REC-03 | PUT đã commit nhưng bỏ response; reload rồi retry | Gửi cùng requestId/payload, revision chỉ tăng một lần; không mất bản nhập mới hơn ACK |
| REC-04 | Xác nhận submit lúc offline, bật mạng/reload | Chờ nộp và khóa sửa; replay tạo đúng một Submission; F5 giữ Result bất biến |
| REC-05 | Create đã commit nhưng ACK mất; reload rồi gửi lại yêu cầu tạo lượt | Journal còn intent gốc, không bốc câu hoặc tạo lượt thứ hai |
| REC-06 | Hai tab cùng baseRevision có bản nhập khác; A lưu hoặc check/nộp | B giữ working copy, nhận conflict qua invalidation hoặc HTTP 409; không tự nâng revision gửi đè |
| REC-07 | Chọn dùng server hoặc giữ local sau conflict audio | Server khôi phục giây của server; local giữ giây đã nhập và chờ nút lưu rõ ràng |
| REC-08 | Đóng tab lúc sender giữ lane, mở lại; thử trình duyệt không có Web Locks | Lock browser giải phóng; fallback lease có hạn; bản cũ nhận lại qua lựa chọn, không dùng writer chung âm thầm |
| REC-09 | Ép IndexedDB quota/transaction abort khi save | Hiện chưa lưu tại máy, giữ RAM; không phát mutation chưa journal, không báo thành công giả |
| REC-10 | Sửa record pending thành route ngoài whitelist hoặc schema sai rồi reload | Cách ly record, không phát URL tùy ý; GET server để đối soát, giữ cảnh báo |
| REC-11 | Tạo hơn 100 bản clean và một dirty rất cũ, đăng nhập/reload | Dọn cache sạch theo tuổi/số lượng; dirty/pending không bị xóa để đủ quota |
| REC-12 | 401 khi ghi, đăng nhập lại A; sau đó logout và login B | A lấy lại bản riêng; B không thấy/gửi dữ liệu A; logout xác nhận và xóa dữ liệu riêng, fence callback cũ |
| REC-13 | Submit/chữa xong, review sync ACK mất hoặc reload giữa batch | FIRST/final/điểm giữ nguyên; sync dùng key bền vững, ledger/evidence không cộng đôi; pendingSubmissions đúng |

IndexedDB dump/trace có thể chứa đáp án fixture nên chỉ giữ trong thư mục ignored. Thử logout khi DB bị chặn xóa cần xác nhận marker chặn truy cập/replay, không báo xóa thành công khi xóa thất bại.

## Restart/recreate và hồi quy

Từ `source/client`:

```powershell
node scripts/test-connection.mjs
npm run build
npm run test:e2e -- module-six-recovery.spec.ts
npm run test:e2e
node scripts/verify-m06-recovery.mjs
```

Cài Chromium hoặc đặt PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH cho Chrome sẵn có. Script restart tự build source, tạo user test, bỏ draft ACK sau commit rồi restart server; reload yêu cầu đăng nhập lại và replay đúng command. Sau submit và sync M04, force recreate ba container giữ volume, đối chiếu Result/progress/dashboard/ledger và số hàng seed/receipt. Script dừng môi trường test khi xong, giữ volume.

Kiểm tra thêm M01 auth/admin/thu hồi phiên, M02 biên soạn/audio/seed, M03 ba renderer/nháp/nộp/chữa, M04 REVIEW/sync. Mobile 390px, bàn phím, Back/Forward và audio restore phải giữ hành vi hiện có.

## Giới hạn

Không có PWA/service worker, login offline hoặc bảo đảm file chưa tải phát được offline. Mất ACK bằng HTTP/WebSocket interception khác mất gói TCP vật lý; restart giữ volume khác mất ổ đĩa/khôi phục backup. Storage bị trình duyệt xóa có thể mất phần chưa lên server. Xem biên bản để phân biệt ca đã tự động hóa, harness và trường hợp chưa ép độc lập.
