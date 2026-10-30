# Kiểm tra cuộc thi online V16 — 30/10/2026

Đây là bằng chứng V16 trước khi tách kho bộ đề/quyền V17. Xem [kiểm tra hiện tại](contest-paper-library-validation.md); các số dưới đây chỉ áp dụng cho mốc V16 đã mô tả.

Source trên `feature/online-contests`, nền `9d3b743`, chưa commit/push tại thời điểm lập biên bản. Không dùng số kiểm thử lịch sử M01–M07 làm bằng chứng cho contest.

## Môi trường

- Native Maven 3.9.11, JDK 21 biên dịch release 17, MySQL 8.4 Testcontainers. Không sửa ddl-auto để bỏ validate.
- Browser Chrome headless qua Playwright của repository. Docker build/runtime Java 17, UI/API riêng `15223/18133`, project `english-learning-e2e-online-contests-20261030`, DB riêng V16.
- Giữ nguyên demo 15183 và các project/volume khác; không chạy E2E lên DB demo. Không seed contest trong ứng dụng sản phẩm. `.env`, dependency, trace và log chứa thông tin phiên không được đưa vào Git.

## Kết quả

| Nhóm | Bằng chứng |
|---|---|
| Backend hồi quy | **108/108 PASS**, 0 failure/error/skip, một lượt BUILD SUCCESS 6:41. Gồm AccountSocketHandler 7, AnswerGrader 13, AttemptApi 47, AttemptPersistence 7, ContentGovernanceApi 13, ContentGovernanceMigration 1, ContestApi 11, EngagementMigration 1, GamificationMigration 1, OrderingMigration 1, RoundNumberMigration 1, WritingApi 5 |
| Backend contest cuối | **12/12 PASS**, thêm một ca đồng hạng; 1:17, 0 failure/error/skip. Có 11 ca trùng lượt trước, không cộng thành 120 ca khác nhau. Tổng 109 ca backend khác nhau qua hai lượt |
| Browser hồi quy và contest | Lượt rộng **34/35 PASS**, ca còn lại lỗi selector `getByLabel` trên textarea đã có nội dung sẵn. Đổi sang textbox theo accessible name, chạy riêng **1/1 PASS** (3.2s). Tổng **35 ca khác nhau đã qua qua các lượt**, không phải một lượt 35/35 |
| Phạm vi 35 browser | Ôn cá nhân 6, phục hồi/kết nối M06 15 (gồm các ca parameterized), điều hướng 5, contest 5, bài viết cuối vòng 4 |
| Connection manager | **11/11 PASS**: thêm contest subscribe, đợi ACK, scope, reconnect, unsubscribe, bỏ callback socket cũ |
| Kho IndexedDB/journal cũ | **6/6 PASS** trên trình duyệt thật; bản dirty không bị dọn mất, replay immutable và fence giữ hoạt động |
| Build | TypeScript/Vite PASS; Docker server/client build và health PASS. Vite còn cảnh báo bundle JS >500kB; đây không phải lỗi build |
| Schema | Fresh DB V1→V16 và dữ liệu lịch sử V3/V8/V9/V11/V12 nâng qua V16 giữ dữ liệu, Flyway validate và Hibernate schema validate PASS trong các ca persistence/migration |

## Ma trận contest đã kiểm tra

- Đề riêng không vào questions/attempts/submissions/XP ledger của luồng học. STUDENT không đọc Author; trước giờ không nhận nội dung đề, trước chốt toàn contest không nhận answerKey/explanation/result/BXH.
- Học viên đăng ký nhưng chưa được duyệt không vào thi; đã duyệt nhưng chưa check-in trước giờ không được bắt đầu muộn. Mốc giờ do Clock server quyết định.
- Bốn dạng SINGLE_CHOICE/FILL_BLANK/MATCHING/ORDERING chấm đúng bằng AnswerGrader; context null hợp lệ; client không được đưa UUID câu lạ hoặc bộ đếm vi phạm vào lệnh.
- hidden lặp và replay cùng requestId không đếm trùng; visible→hidden lần hai trừ 10 phút đúng một lần; khi còn 5 phút thì kết thúc tại lúc phạt, không lùi thời gian thực tế. Lần ba chỉ bật cờ; ADMIN quyết định thu hồi.
- Thu hồi làm đề biến mất, ngừng ghi, giữ bài/audit/lý do, loại khỏi BXH; `/api/auth/me` của thí sinh vẫn 200. Giám thị đúng phạm vi được acknowledge; không được publish/approve/revoke/close.
- Một writer; tab khác bị từ chối khi lease còn sống, được claim sau hết lease. Stale revision không ghi đè. Autosave không tăng controlRevision nên không làm quyết định thu hồi vô hiệu chỉ vì có lưu đáp án mới.
- Nộp đồng thời tạo đúng một bài bất biến; replay request thắng vẫn 200 và Idempotent-Replayed. Gửi sau hạn trả 409 và chốt đáp án server cũ; replay cùng lệnh vẫn 409; tick nhiều lần không tạo bài mới.
- Worker xử lý trễ ghi submittedAt bằng hạn hiệu lực, processedAt bằng lúc xử lý; thời gian BXH tính từ giờ bắt đầu chung, bằng điểm/thời gian đồng hạng. BXH bỏ ID tài khoản/participant khi trả ra ngoài; archive không thay kết quả.
- WebSocket thật gửi invalidation sau commit, không kèm đề/đáp án. Unit test kiểm tra một thí sinh không nhận thay đổi riêng thí sinh khác; giám thị có subscription nhận được. Thay đổi trạng thái chung được phát tới mọi subscriber hợp lệ.
- Browser: ADMIN tạo/công bố đề bằng UI; giáo viên sửa đề riêng được giao và thiếu các nút ADMIN; học viên vào phòng chờ, làm/lưu/tải lại/nộp, chỉ mở đáp án sau đóng thi. Cảnh báo, trừ giờ, giám sát/thu hồi realtime và nhật ký chạy xuyên suốt. Mất ACK nộp bài rồi reload khôi phục/replay đúng request; tab thứ hai không lấy lượt đang mở. Layout admin/kết quả học viên ở 390px không tràn ngang.

## Tái hiện

Từ `source/server`, dùng Maven đã cài hoặc wrapper:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=ContestApiTest,AccountSocketHandlerTest,AnswerGraderTest,AttemptApiTest,AttemptPersistenceTest,*MigrationTest,*Writing*Test,*Governance*Test' test
```

Từ `source/client`:

```powershell
npm run build
npm run test:connection
npm run test:recovery-storage
npm run test:e2e -- e2e/online-contests.spec.ts e2e/navigation.spec.ts e2e/module-six-recovery.spec.ts e2e/module-four-review.spec.ts e2e/unit-writing.spec.ts
```

Runner E2E mặc định dựng DB riêng. Có thể đặt E2E_PROJECT_NAME/E2E_APP_PORT/E2E_API_PORT cho project test mới; chỉ dùng E2E_BASE_URL khi chắc chắn là môi trường test. Windows có thể cần PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH trỏ Chrome đã cài. Không hardcode secret vào lệnh/log.

## Giới hạn bằng chứng

Chưa đo tải contest, chưa kiểm chứng broadcast nhiều backend, chưa có giám sát tab đích/thiết bị khác. Thử sự kiện visibility trong browser dùng tín hiệu document theo cơ chế thực của client, không chứng minh chống gian lận tuyệt đối. Có kiểm tra worker chạy trễ và dữ liệu deadline bền vững; không tuyên bố đã diễn tập restart process trong lúc một contest thật đang diễn ra. Xem phạm vi sản phẩm tại [ONLINE_CONTESTS](../../docs/ONLINE_CONTESTS.md).
