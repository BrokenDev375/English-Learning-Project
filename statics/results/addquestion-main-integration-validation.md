# Tích hợp main vào addquestionsystem — 30/10/2026

Nền addquestionsystem `1b860fc`, main `4cbd4bc` đã chứa ôn luyện M07 `8e7bbc4`. Merge tự động, không có file unmerged; năm file cùng sửa là AGENTS.md, README.md, learning/types.ts, openapi-m02-m03.json và AttemptSelector.java. Không cần sửa logic ứng dụng để giải quyết xung đột. Giữ số vòng/phân công/duyệt, activities và bài viết cuối vòng; không thêm migration ngoài V1–V15.

## Kiểm tra trên source đã merge

| Phạm vi | Kết quả |
|---|---|
| Production TypeScript/Vite | PASS, host và image Docker |
| Connection manager | 10/10 PASS |
| IndexedDB/journal qua Chrome | 6/6 PASS |
| Backend tập trung, lượt đầu | 99 ca: 98 qua, 1 error do Origin của bộ chạy test; 0 failure/skip |
| Chạy lại đúng ca WebSocket sau sửa cấu hình Origin | 1/1 PASS |
| Browser trên image tích hợp | 33/33 PASS, một worker, 2.3 phút |
| Hợp đồng tĩnh | Ba OpenAPI JSON parse được, 792 tham chiếu nội bộ hợp lệ; giữ reviewKind mới/cũ, activities và writing |

99 ca backend khác nhau đã qua qua hai lượt, không phải một lượt 99/99 toàn PASS. Các nhóm gồm AttemptApiTest 47, AttemptRequestsTest 6, ContentGovernanceApiTest 13, LessonActivitiesTest 9, AttemptActivityProjectionTest 1, PersonalReviewApiTest 18 và WritingApiTest 5. Maven native 3.9.11, Java compile release 17; MySQL 8.4 Testcontainers riêng.

Lượt đầu bộ chạy tạm gán WS_ALLOWED_ORIGINS theo cổng browser 15221, trong khi AttemptApiTest.socketInvalidatesOnlyCommittedOwnedMutationsAndNotReplays gửi Origin 5173; handshake trả 403. Sửa biến môi trường của bộ chạy về Origin mặc định dành cho backend test, chạy lại riêng ca này thành công. Không sửa hay bỏ assertion, không nới quyền WebSocket của ứng dụng. Lưu XML lượt đầu trước khi chạy lại để giữ bằng chứng.

Lệnh backend:

```text
mvn.cmd -B -ntp -Dtest=AttemptRequestsTest,PersonalReviewApiTest,AttemptApiTest,LessonActivitiesTest,AttemptActivityProjectionTest,WritingApiTest,ContentGovernanceApiTest test
mvn.cmd -B -ntp -Dtest=AttemptApiTest#socketInvalidatesOnlyCommittedOwnedMutationsAndNotReplays test
```

Browser dùng project riêng english-learning-e2e-addquestion-main-20261030, UI 15221/API 18131, sáu file activity-variety.spec.ts, content-governance.spec.ts, learning-journey.spec.ts, module-four-review.spec.ts, round-map.spec.ts và unit-writing.spec.ts. Bao gồm chọn ba kiểu ôn, lịch sử lỗi/receipt, thứ tự hoạt động/đọc/nghe, duyệt học liệu, mở vòng, bài viết/chấm bài, mất ACK và xung đột hai tab.

Ca kết hợp mới xác nhận một câu GUIDED đọc điền từ sai trong LEARN vẫn được cấp vào WRONG, snapshot chỉ chứa ô/học liệu của câu được chọn, nhập/tải lại giữ đáp án, nộp ôn đúng cùng origin làm số nguồn sai về 0. Chuẩn bị/nộp dữ liệu bằng API; nhập và tải lại bằng trình duyệt. Không dùng câu trả lời phía client để tự chấm.

## Môi trường và giới hạn

Vite bị sandbox chặn spawn EPERM ở lần đầu; chạy với quyền thực thi phù hợp thành công. Script storage dùng Chrome cài sẵn thay cho Chromium chưa cài. Docker hết address pool; thu hồi đúng project review-choices-e2e cũ sau khi kiểm tra mọi container đã dừng, giữ named volumes rồi chạy môi trường tích hợp riêng.

Không chạy E2E/seed ghi dữ liệu vào demo 15183. Không reset/xóa volume hoặc sửa database demo. Chỉ kiểm tra hồi quy tập trung, không tuyên bố toàn bộ backend/browser hay đo tải. Log và XML nằm trong worktree kiểm tra .tmp-addquestion-main-20261030/.test-state: integration-backend.log, integration-backend-first-reports, integration-backend-socket.log, integration-browser.log và integration-images.log; không đưa artifact hoặc thông tin xác thực vào Git.
