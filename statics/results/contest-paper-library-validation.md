# Kiểm tra kho bộ đề, phân quyền và UI contest V17 — 30/10/2026

Nhánh `feature/online-contests`, nền `9d3b743`, chưa commit/push. Đây là bằng chứng sau thay đổi luồng dùng bộ đề có sẵn; không thay số lịch sử V16 bằng kết quả mới.

## Kết quả

| Kiểm tra | Kết quả |
|---|---|
| Backend hồi quy | **117/117 PASS**, 0 failure/error/skip, một lượt BUILD SUCCESS **7:10** |
| Chi tiết backend | ContestApi 12, ContestPaperApi 6, ContestPaperMigration 1, AccountSocketHandler 8, AnswerGrader 13, AttemptApi 47, AttemptPersistence 7, ContentGovernanceApi 13, ContentGovernanceMigration 1, EngagementMigration 1, GamificationMigration 1, OrderingMigration 1, RoundNumberMigration 1, WritingApi 5 |
| Browser luồng mới | **5/5 PASS** trong một lượt **1.6 phút**: quyền riêng → soạn bộ đề → duyệt → chọn/reuse; giám thị không đọc đề; phòng thi/lưu/tải lại; cảnh báo/trừ giờ/thu hồi; mất ACK nộp và một writer |
| Bố cục và bản sao cuối | Chạy riêng **1/1 PASS** sau chỉnh căn radio và chặn hiển thị bộ đề cũ khi URL chuyển sang bản sao; thêm kiểm tra editor ở 390px, clone/sửa trên UI và xác minh đề nguồn không đổi. Đây là ca đầu đã có trong nhóm 5, không cộng thành 6 ca khác nhau |
| Connection manager | **12/12 PASS**, có đối soát quyền khi reconnect, event quyền hiện hành và bỏ callback socket cũ |
| Build | TypeScript/Vite và Docker server/client PASS, health HTTP 200/UP. Còn cảnh báo bundle JS >500kB; không phải lỗi build |
| Hợp đồng | OpenAPI JSON v2, 34 paths/36 schemas, kiểm tra 257 schema reference nội bộ hợp lệ; chưa dùng validator OpenAPI bên ngoài |

Browser phát hiện tràn ngang trang kết quả khi lồng grid ở 390px; sửa min-width của grid/panel và căn checkbox/radio theo hàng. Lượt 5/5 cuối đã qua kiểm tra chiều rộng settings và kết quả trên di động. Kiểm tra trực quan ảnh settings desktop, editor desktop và kết quả mobile; ảnh nằm trong `.test-state/`, không đưa dữ liệu phiên/trace vào Git.

## Phạm vi đã xác minh

- Admin cấp riêng canAuthor/canProctor, API chỉ cho giáo viên ACTIVE; teacher không tự cấp quyền. Người ra đề không tự có quyền trông thi; giám thị cần thêm phân công theo từng contest.
- Chủ bộ đề có quyền ra đề chỉ xem bộ đề của mình; người khác cùng quyền trả 404. STUDENT bị chặn đường tác giả. Giám thị không nhận questions, answerKey hoặc explanation từ Author contest; không thấy Kho bộ đề nếu không có canAuthor.
- DRAFT → PENDING → READY; admin trả về DRAFT kèm note. Teacher không được duyệt, không sửa PENDING/READY. READY chỉ nhân bản để soạn bản khác. Các ID câu của bản sao khác nguồn.
- Một bộ đề READY được chọn cho hai contest; mỗi contest giữ source ID/revision/title và snapshot riêng. Archive bộ đề không thay snapshot đã công bố; contest nháp chưa công bố phải chọn nguồn READY khác.
- Thu hồi ra đề chặn cả GET, lệnh mới và replay receipt cũ có nội dung riêng; tài khoản website vẫn hoạt động. Thu hồi giám sát gỡ subscription, UI ngừng hiển thị monitor qua event, GET lại quyền khi reconnect.
- Hai lệnh sửa bộ đề cùng revision chỉ một lệnh thành công, lệnh còn lại 409. Replay create dùng cùng requestId không tạo bộ đề thứ hai.
- Bộ đề không vào ngân hàng học, không sinh XP/lịch ôn/tiến độ vòng. Bốn dạng tự chấm, privacy, deadline, trừ giờ, chống nộp trùng, đồng hạng và archive contest vẫn qua 12 ca ContestApiTest.
- V17 từ V16 có dữ liệu nháp/công bố giữ nguyên JSON đề và nguồn, backfill quyền/owner đúng, migrate lại không chạy thêm. Các đường nâng V3/V8/V9/V11/V12 tới V17 và Hibernate validate cũng qua trong hồi quy.
- UI: form contest không có editor câu hỏi; input cao 36px, tên/bộ đề hai cột, lịch ba cột trên desktop; danh sách gọn. Editor bộ đề có danh sách câu bên trái, hỗ trợ sắp xếp/xóa/thêm và bốn dạng. Bộ đề có trạng thái duyệt/số cuộc thi sử dụng.

## Môi trường và bảo toàn dữ liệu

Native Maven 3.9.11/JDK 21 release 17, Testcontainers MySQL 8.4. Browser Chrome/Playwright; runtime Docker Java 17. Môi trường riêng `english-learning-e2e-online-contests-20261030`, UI **15223**, API **18133**, DB **V17**.

Trước nâng, sao lưu DB vào `.test-state/before-contest-library-20261030-145504.sql` (**1,309,570 bytes**). Không xóa/truncate/repair DB; V1–V16 giữ nguyên. Demo 15183 và các project/volume khác không đổi. E2E tạm đổi quyền giáo viên demo và khôi phục hai quyền ban đầu trong afterAll; các bộ đề/contest test mang tên riêng, không đổi nội dung người dùng đã lưu.

## Tái hiện

Từ `source/server`:

```powershell
.\mvnw.cmd -B -ntp '-Dtest=Contest*Test,AccountSocketHandlerTest,AnswerGraderTest,AttemptApiTest,AttemptPersistenceTest,*MigrationTest,*Writing*Test,*Governance*Test' test
```

Từ `source/client`:

```powershell
npm run build
npm run test:connection
npm run test:e2e -- e2e/online-contests.spec.ts
```

Chỉ chạy E2E trên DB riêng. Không dùng 15183 làm E2E_BASE_URL. `.env`, backup SQL, auth trace, node_modules, dist và target không đưa vào Git.

## Giới hạn

Không tuyên bố đã đo tải hoặc chống gian lận tuyệt đối. Kho bộ đề hiện bốn dạng tự chấm và context chữ, chưa có hoạt động file nghe/ảnh hoặc tự luận. Authoring chưa có journal offline; phòng thi học viên vẫn giữ phục hồi M06. Bằng chứng nghiệm thu trước V17 được giữ riêng tại [V16](online-contests-validation.md).
