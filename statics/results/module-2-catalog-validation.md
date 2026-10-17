# Kiểm tra M02 bước 1 — danh mục biên soạn

Ngày chạy: 17/10/2026. Môi trường Windows, Docker Desktop, Java 17 trong container Maven, MySQL 8.4.11, Chromium qua Chrome có sẵn. Đây là kết quả chức năng danh mục; không phải nghiệm thu toàn bộ M02 hoặc benchmark.

## Kết quả thực tế

| Kiểm tra | Kết quả |
|---|---|
| Backend build/package | PASS |
| Flyway V1 → V2 → V3 trên MySQL Testcontainers trống, Hibernate validate | PASS |
| Nâng database demo V2 → V3, giữ tài khoản M01 | PASS |
| CatalogApiTest | 8 test, 0 failure/error/skipped |
| AccountAdministrationApiTest hồi quy | 10 test PASS |
| ModuleOneApiTest hồi quy | 12 test PASS |
| Tổng backend lần cuối | 30 test PASS |
| TypeScript + Vite / Docker client build | PASS |
| Playwright toàn bộ lần cuối | 9/9 PASS, gồm 5 M01 và 4 M02 |
| Desktop 1440 × 1000 và mobile 390 × 844 | PASS; đã xem ảnh, không tràn ngang |
| Docker mysql/server/client, backend health | PASS |
| DDL V3 runtime so với hợp đồng gốc, bỏ phần comment đầu | Khớp hoàn toàn |
| git diff --check | PASS |

## Hành vi được kiểm tra

- Chặn chưa đăng nhập/STUDENT; CSRF; ownerId/trường lạ; trường bắt buộc; code, phân trang, scope và ID không hợp lệ. Trường số nguyên không tự nhận số thập phân.
- Scope OWN/ALL và quyền sửa đúng chủ sở hữu/ADMIN; admin sửa giữ owner cũ. TEACHER không xin Unit ALL.
- Tạo Topic, Objective, Unit; liên kết đúng chủ đề; cập nhật; code/Topic của Objective không đổi qua API. Tìm ký tự `%` được hiểu là ký tự thường, không mở rộng thành wildcard.
- Gửi lại cùng requestId/payload trả đúng status/body và header replay; key khác payload bị 409. Replay cập nhật vẫn thành công sau khi revision hiện tại đã tăng tiếp.
- Hai request tạo đồng thời cùng key chỉ có một Topic; hai tác giả có quyền sửa cùng revision chỉ một lần thành công, lần kia 409.
- FK không tồn tại và code trùng không để lại receipt thành công. Seed chạy lại không nhân bản hoặc đặt lại nội dung đã sửa.
- Trình duyệt tạo chủ đề → mục tiêu → Unit, sửa Unit, tải lại và tìm được nội dung đã lưu.
- Trình duyệt mô phỏng server commit nhưng mất response, thử lưu lại giữ nguyên key và không tạo Topic thứ hai.
- Editor cũ nhận xung đột, giữ nội dung đang nhập và không ghi đè bản mới. Danh mục và editor dùng được trên mobile.

## Tái hiện

Backend: Java 17+ và Docker đang chạy, tại `source/server` chạy `./mvnw test` hoặc `.\mvnw.cmd test`. Lần kiểm tra này dùng image từ target `build` của Dockerfile để chạy Maven; mount Docker socket và đặt `TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal`. Testcontainers tạo database/container riêng, không dùng DB demo.

Frontend: app đang chạy, tại `source/client` chạy `npm ci` rồi `npm run test:e2e`. Cần Chromium của Playwright hoặc đặt `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. Admin lấy từ `.env`/biến E2E như M01; M02 tạo tài khoản tác giả kiểm thử riêng. Các bản ghi kiểm thử có tiền tố e2e.

Ảnh thật: [desktop](module-2-catalog-desktop.png), [mobile](module-2-catalog-mobile.png). Báo cáo Maven cục bộ nằm trong `.test-state/m02-backend-reports`; báo cáo Playwright trong `source/client/playwright-report`.

## Phạm vi chưa làm

Chưa có upload, Material/Question/Lesson authoring, preview/publish/retire, danh mục học viên, PublishedContentReader hoặc seed 90 câu. `objectiveIds` của Unit đang rỗng vì chưa có API tạo bài học. Việc V3 tạo sẵn các bảng không chứng minh các phần đó đã hoạt động. Xem [tiến độ và bước tiếp theo](../../docs/M02_PROGRESS.md).
