# Kiểm tra audio không tua và hai lượt

Ngày 29/10/2026, nhánh `feature/admin-session-monitoring`, working tree trên `3f3b0c5`. [Quy tắc và API](../../docs/AUDIO_LISTENING_POLICY.md). Chưa commit/push, chưa áp dụng V14 lên demo đang chạy. Không sửa `.env` hoặc file báo cáo người dùng.

## Kết quả

| Nhóm | Bằng chứng |
|---|---|
| API Attempt gồm bốn ca quota mới và 47 ca hồi quy | **51/51 PASS**, 0 failure/error/skip; `.test-state/audio-limit-backend.log` |
| Nâng schema/đọc dữ liệu cũ/Hibernate validate | **7/7 PASS**, BUILD SUCCESS, 58.772s; `.test-state/audio-limit-persistence-final.log` |
| Các đường nâng schema từ V8/V9/V11/V12 tới hiện tại | **4/4 PASS**, BUILD SUCCESS, 1m13s; `.test-state/audio-limit-migrations.log` |
| Browser audio + học + phục hồi | **32/32 PASS**, một lượt 2.2 phút; `.test-state/audio-limit-browser.log` |
| Audio source tiếp theo với media-error/retry | **5/5 PASS**, một lượt 28s; `.test-state/audio-limit-browser-final.log` |
| Media-error không bị GET quota thành công che lỗi | **1/1 PASS** sau sửa trạng thái lỗi file riêng, 5s; `.test-state/audio-limit-media-retry.log` |
| Audio + idle mặc định policy, timeout test 20s | **1/1 PASS**, 25.2s; `.test-state/audio-limit-idle.log` |
| TypeScript/Vite | PASS, gồm build lại bởi E2E trên source cuối; cảnh báo chunk >500 kB đã có |
| Quan sát mobile và kiểm tra overflow | PASS; `.test-state/audio-limit-mobile.png`, viewport 390×844 |
| Mechanical UI detector và diff whitespace | PASS; `.test-state/audio-limit-design.json` là `[]` |

Backend có 62 ca khác nhau qua các lượt (51 API + 7 persistence + 4 migration), không ghi thành một lượt 62/62. Lượt đầu tổng 58 có hai assertion migration vẫn kỳ vọng V13, trong khi Flyway thực sự lên V14 đúng; cập nhật ba giá trị kỳ vọng rồi chạy lại cả nhóm persistence 7/7. Bốn test migration khác đi tới bản mới nhất cũng tăng số migration kỳ vọng thêm một và chạy lại 4/4. Không sửa migration runtime để né test.

Browser có 34 ca khác nhau qua các lượt: 32 ban đầu, thêm một ca media error và một ca idle. Bốn ca audio trong lượt 32 được chạy lại trong lượt 5; ca media được mở rộng và chạy lại 1/1 trên source cuối. Không cộng các lượt trùng thành 39/39. Có một lượt build test bị lỗi phạm vi biến fixture khi bổ sung câu cùng audio; đã sửa trước lượt 5/5, không phải lỗi runtime.

## Các hành vi đã chứng minh

- Không có native controls hoặc range input tua; seek tiến/lùi bằng currentTime bị trả lại vị trí hợp lệ, không tự phát trước khi được cấp lượt.
- Pause/resume không gửi start mới. Câu cùng MaterialVersion và rời sang câu khác rồi trở lại vẫn giữ quyền phát/vị trí trong document.
- Start đã commit nhưng response bị bỏ: client giữ requestId/payload và replay đúng một lần cấp, quota vẫn 1. Start thứ hai dùng lượt thứ hai; reload/tab mới không reset quota.
- Clip có start/end khác 0: nghe hai lần xong bị khóa, mobile không tràn; chuyển sang chữa bài vẫn dùng quota cũ, không cấp thêm hai lượt.
- File audio 503 có thể tải lại và nghe tiếp bằng quyền phát cũ; lỗi media còn hiển thị kể cả GET quota thành công. Không gửi NaN vào vị trí nháp khi metadata chưa sẵn sàng.
- REST ba start khác key chạy đồng thời từ hai phiên của cùng user: đúng hai 200 và một 409, DB giữ 2. Replay key cũ không tăng count. GET/context không sửa revision Attempt hoặc tạo trial.
- Owner/role/CSRF: anon 401, foreign 404, TEACHER 403, material ngoài lượt 404, thiếu CSRF 403, trường thừa/số lượt client tự gửi 400.
- Lỗi lưu receipt sau khi đã ghi bộ đếm rollback cả lượt nghe, GET vẫn 0. Khóa actor → receipt → khóa Attempt dùng cùng transaction như các mutation hiện có.
- M03/M06 hồi quy qua draft/lost ACK/reload/offline/submit/result/correction/progress/auth generation, nháp nhiều tab và socket/rewards subscriptions. Không thay luật chấm hoặc dữ liệu Submission.
- Khi chỉ phát audio và phát sinh timeupdate/vị trí, không có tương tác mới, phiên vẫn tự hết hạn. Bộ phát không autoplay lượt thứ hai.

## Lệnh/môi trường

Backend dùng Docker target test, MySQL 8.4 Testcontainers, Docker socket và `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. Maven: `-Dtest=AttemptApiTest,AttemptPersistenceTest`; sau sửa kỳ vọng V14 chạy lại `-Dtest=AttemptPersistenceTest`.

Trong source/client:

```powershell
npm run build
$env:E2E_PROJECT_NAME='english-learning-e2e-audio-limit'
$env:E2E_APP_PORT='5182'
$env:E2E_API_PORT='8092'
$env:PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH='C:/Program Files/Google/Chrome/Application/chrome.exe'
npm run test:e2e -- audio-listening.spec.ts module-three-learning.spec.ts module-six-recovery.spec.ts --workers=1
npm run test:e2e -- audio-listening.spec.ts --workers=1
```

Idle chạy riêng project english-learning-e2e-audio-idle, cổng 5183/8093, env E2E_IDLE_TESTS=1, SESSION_IDLE_TIMEOUT_SECONDS=20, WARNING=5, SWEEP_MS=1000; `session-idle.spec.ts --grep 'audio playback'`. Các stack đã stop và giữ volume; không xóa demo hoặc ghi đè cấu hình demo. Artifact/log/ảnh được ignore.

## Phạm vi và giới hạn

Chưa chạy lại toàn bộ backend/browser sau chính sách mới, hoặc ca default 300s thật trên source này; số toàn bộ của module giám sát là lịch sử. Đã thay bốn ca E2E cũ cho phép seek/restore tự do bằng kiểm tra hạn mức/no-seek phù hợp luật mới, không skip để bỏ qua chúng.

Hai lượt là hai lần bắt đầu phát được cấp trước khi gọi play; không cam kết mỗi lượt đã nghe trọn nếu reload, thoát trang hoặc lỗi mạng. Quyền resume giữ trong document/vòng auth, count giữ DB; reload/auth mới không hoàn lượt. Đây không phải DRM chặn trình duyệt bị can thiệp hoặc URL media được đọc ngoài bộ phát. Preview tác giả không theo quota Attempt.
