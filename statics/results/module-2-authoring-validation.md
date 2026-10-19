# Kiểm tra M02 bước 3 — câu hỏi và bài học

Ngày chạy 17/10/2026. Môi trường Windows, Docker Desktop, Java 17/Maven trong container, MySQL 8.4 thực qua Testcontainers, Chromium qua Chrome có sẵn. Đây là nghiệm thu phần biên soạn câu/bài; còn bộ seed 6 bài/90 câu của M02 và lượt làm/chấm bài M03.

## Kết quả thực tế

| Kiểm tra | Kết quả |
|---|---|
| Backend package, Docker server build | PASS |
| Schema V1/V2/V3 trên MySQL trống, Hibernate validate | PASS |
| AuthoringApiTest mới | 14/14 PASS |
| AccountAdministrationApiTest hồi quy | 10/10 PASS |
| ModuleOneApiTest hồi quy | 12/12 PASS |
| CatalogApiTest hồi quy | 8/8 PASS |
| MaterialApiTest hồi quy | 12/12 PASS |
| Tổng backend bản cuối | 56 test, 0 failure/error/skipped |
| TypeScript/Vite và Docker client build | PASS |
| Playwright toàn bộ | 23/23 PASS: 5 M01, 4 catalog, 5 materials, 9 authoring |
| Bổ sung kiểm tra chính form soạn câu/bài mobile | 2/2 PASS, sau lần chạy toàn bộ |
| Desktop 1440 × 1000, mobile 390 × 844 | PASS, ảnh đã xem và không tràn ngang |
| Recreate backend, đăng nhập lại qua frontend proxy | PASS |
| Seed sau recreate | Vẫn 3 câu và 1 bài demo; ID, revision, version, slot, Unit và learner introduction không đổi |
| Migration V3 | Không chỉnh sửa trong bước 3 |
| Đối chiếu response thật với OpenAPI 1.0.0 | 10 payload PASS: ba Question root, Lesson root, author Unit detail, Unit, learner introduction, hai bank page và public Unit detail |
| git diff --check | PASS |

Backend lần cuối hoàn tất 08:33:58 UTC; tổng thời gian Maven 5 phút 18 giây. Báo cáo XML cục bộ ở `.test-state/m02-authoring-reports`; log đầy đủ ở `.test-state/authoring-tests.log`. Các thư mục này ignored. Playwright đã chạy toàn bộ trước, sau đó chạy riêng hai luồng để bổ sung ảnh form mobile; báo cáo HTML hiện tại phản ánh lần chạy riêng cuối.

## Hành vi đã kiểm tra

- Tạo/sửa/công bố SINGLE_CHOICE, FILL_BLANK và MATCHING theo đúng DTO; trường lạ/thiếu nullable bắt buộc, loại P1, FK hoặc quyền không hợp lệ bị chặn. Nháp thiếu nội dung vẫn lưu được, publish thiếu nội dung/khóa đáp án bị từ chối.
- Form điền từ có nhiều đáp án chấp nhận (mỗi dòng một đáp án), tùy chọn chữ hoa/khoảng trắng; form ghép cặp chọn các đầu ghép ổn định. Preview tương tác không hiển thị answerKey, hint, explanation hoặc transcript.
- Sửa sau công bố tạo version mới. Bản công bố cũ vẫn đọc được theo Version ID; nháp và bản công bố có tab riêng. Copy câu của tác giả khác tạo root thuộc mình và giữ originKey.
- TEACHER không đọc/sửa nháp của người khác, STUDENT không dùng author API; ADMIN được can thiệp. Không dùng ownerId client khai. Nháp/phụ thuộc riêng tư bị chặn.
- Bài học có hướng dẫn, mục tiêu và ba nhóm câu. Preview/publish chặn thiếu nhóm, lặp origin qua copy, sai Topic/Objective hoặc thiếu gợi ý của GUIDED; không ép số lượng 15 câu ở mọi bài.
- Publish bài công bố các Material/Question draft liên quan được phép sửa trong cùng transaction. Test gây lỗi CHECK tại lần ghi Lesson cuối, sau khi đã ghi các phụ thuộc: mọi con trỏ/state và receipt đều rollback; bỏ lỗi DB rồi gửi lại cùng request thành công.
- Sửa Question sau preview Lesson hoặc Material sau preview Question làm token cũ bị PREVIEW_STALE. Đồng thời sửa phụ thuộc/xuất bản không âm thầm ghi đè bản được xem trước.
- Root locks theo Material → Question → Lesson, tăng dần ID. Authoring writes và Material retire dùng READ_COMMITTED để thấy thay đổi sau khi chờ lock. Race retire Material/publish Lesson không thể cả hai cùng thành công làm hỏng tham chiếu công bố.
- Bỏ nháp câu đang được bài tham chiếu trả CONTENT_IN_USE; bỏ nháp bài không xóa câu độc lập. DELETE trả/replay đúng 204, không body. Retire câu đang được bài dùng qua phiên bản cũ vẫn bị chặn; retire bài rồi mới retire câu được, lịch sử bài còn nguyên.
- Thứ tự bài phải gửi đầy đủ Lesson root ID, không trùng/thiếu; Unit revision cũ bị 409. UI đổi vị trí rồi lưu; API PUT trả Unit theo hợp đồng, UI GET lại chi tiết Unit. Unit objectives suy từ phiên bản bài.
- Learner catalog chỉ hiện Unit/Topic/Objective có bài active published. GET giới thiệu bài có summary/hướng dẫn/mục tiêu, không có khóa đáp án/ngân hàng/transcript. UI STUDENT xem bài mẫu qua Khám phá bài học.
- UI giữ requestId và nội dung khi server commit nhưng mất ACK, thử lại không tạo root thứ hai. Form/preview câu và bài dùng được trên mobile; bộ lọc desktop đủ chỗ cho nội dung lựa chọn.
- Recreate container server mà không xóa volume: snapshot 3 Question root, 1 Lesson root, phiên bản, thứ tự slot, Unit và learner introduction trước/sau bằng nhau. Seed không đặt lại nội dung/trạng thái.

## Tái hiện

Tại root dự án, Docker Desktop đang chạy:

```powershell
docker build --target test -t english-learning-project-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-project-test mvn -B -ntp test
```

Testcontainers dùng database/container riêng, không dùng DB demo. Test lỗi DB tạm tạo CHECK trong database test và gỡ ở finally. Không thay migration hoặc schema demo.

App local đang chạy tại localhost:5173, tại `source/client`:

```powershell
npm ci
npm run build
npm run test:e2e
```

Cần Chromium của Playwright hoặc `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. E2E dùng admin từ `.env`/biến E2E, tạo tài khoản tác giả và dữ liệu e2e riêng trên DB demo. Test tài liệu tìm theo tên để không phụ thuộc tài liệu demo nằm ở trang đầu khi dữ liệu kiểm thử tăng lên. Không chạy trên dữ liệu sử dụng thực tế.

Ảnh thật: [kho câu hỏi desktop](module-2-questions-desktop.png), [mobile](module-2-questions-mobile.png), [form câu hỏi mobile](module-2-question-editor-mobile.png), [form bài desktop](module-2-lesson-editor-desktop.png), [form bài mobile](module-2-lesson-editor-mobile.png), [bài mẫu mobile](module-2-lesson-mobile.png), [giới thiệu học viên](module-2-learner-intro-mobile.png).

## Phạm vi còn lại

- Bài minh họa có 3 câu (1 GUIDED, 1 INDEPENDENT, 1 REVIEW_VARIANT); chưa phải seed 6 bài/90 câu, chưa đủ học liệu đọc/nghe cho từng Unit.
- Chưa có Attempt/runner/check/submit/chấm bài/tiến độ M03. Preview không sinh lịch sử hoặc điểm.
- PublishedContentReader chỉ dùng trong server; M03 còn phải kiểm tra quyền, chốt snapshot và lọc keys/transcript trước khi trả client, thêm AttemptAssetReadGrant.
- UI có nháp và bản công bố hiện tại; lịch sử cũ được giữ/đọc qua API, chưa có màn liệt kê toàn bộ lịch sử. Không có API xóa root/file công bố.

Theo dõi [tiến độ](../../docs/M02_PROGRESS.md), [hợp đồng API](../../source/common/M02_M03_API.md) và [nguồn/manifest minh họa](../dataset/README.md).
