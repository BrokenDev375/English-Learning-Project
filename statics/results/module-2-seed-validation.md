# Kiểm tra M02 bước 4 — bộ seed hoàn chỉnh

Ngày chạy 17/10/2026. Windows, Docker Desktop, Java 17/Maven trong container, MySQL 8.4 thực qua Testcontainers, Playwright qua Chrome có sẵn. Phạm vi M02: biên soạn, phiên bản, xuất bản và cung cấp học liệu; lượt làm/chấm/tiến độ M03 chưa triển khai.

## Kết quả thực tế

| Kiểm tra | Kết quả |
|---|---|
| Python verifier: schema, manifest, SHA-256 và bằng chứng đáp án nghe | PASS |
| Seed chuẩn | 3 Topic, 3 Unit, 6 mục tiêu, 6 bài, 90 câu độc lập |
| Phân bố | Mỗi bài 5/5/5; toàn bộ 30 SINGLE_CHOICE, 30 FILL_BLANK, 30 MATCHING |
| Tài liệu và file | 6 READING + 6 AUDIO + 1 IMAGE; 6 WAV + 1 PNG |
| CurriculumSeedTest chạy riêng | 6/6 PASS, không failure/error/skipped |
| Backend tổng hợp | 62/62 PASS: seed 6, authoring 14, admin 10, material 12, M01 12, catalog 8; không failure/error/skipped |
| TypeScript/Vite, Docker server/client build | PASS |
| Playwright toàn bộ | 26/26 PASS: 5 M01, 4 catalog, 5 materials, 9 authoring, 3 seed |
| Playwright seed sau recreate, bổ sung lọc Unit | 3/3 PASS |
| Desktop 1440 × 1000, mobile 390 × 844 | PASS; ảnh đã xem, không tràn ngang |
| Triển khai trên DB demo cũ | 3 → 13 tài liệu, 3 → 90 câu, 1 → 6 bài trong kho OWN của TEACHER demo |
| Bảo toàn dữ liệu trước triển khai | 7 root mẫu cũ còn nguyên published history; Material/Question DTO không đổi; bài Linh có version mới 15 câu |
| Recreate server và đăng nhập lại | Snapshot 13 Material/90 Question/6 Lesson, Unit/introduction, 7 hash file và tổng asset/receipt/version toàn DB không đổi |
| Seed sau recreate | created/adopted/upgraded đều 0, preservedLessons=6 |
| Flyway V1/V2/V3 | Lịch sử/checksum không đổi; không thêm migration |
| Liên kết tài liệu/ảnh và git diff --check | PASS |

V3 SHA-256 trước/sau: `1AEB4E1EE9F3B5813079703AA7DE5B1E29AAA119BE6C4F539CFCEDB67AD2724E`.

Lần backend tổng hợp hoàn tất 16:26:26 UTC (23:26:26 giờ Việt Nam), Maven 10 phút 32 giây. Playwright toàn bộ hoàn tất 26 test trong 2 phút; ba test seed chạy lại sau recreate hoàn tất trong 12,2 giây.

## Hành vi seed đã kiểm tra trên MySQL

- Database trống: tạo đủ bộ chuẩn; lần hai không thêm root, version, file hoặc receipt.
- Bộ chuẩn đã có: sửa câu, mở nháp tài liệu, ngừng cấp bài, tạo câu riêng rồi seed lại; snapshot giữ nguyên cả nội dung, trạng thái và phiên bản.
- Mẫu cũ chưa sửa: nhận lại ba Material và ba Question, giữ ID/origin/published version; bài ba câu giữ root ID, công bố LessonVersion mới 15 câu. Version cũ vẫn đúng ba slot ban đầu.
- Mẫu cũ đã tùy chỉnh: giữ bài/nháp tài liệu cũ, tạo bộ chuẩn riêng. Tổng hàng có thể vượt 6/90; không âm thầm ghi đè dữ liệu người dùng.
- Lỗi DB tại lần ghi bài cuối: toàn bộ câu, bài, publication và receipt của bài rollback; 13 tài liệu/7 asset đã hoàn tất là checkpoint. Gỡ lỗi rồi chạy lại tạo đủ bộ, không thêm file trùng.
- Hai lượt seed đồng thời cùng tác giả: không nhân bản tài liệu/câu/bài/file.

Seeder dùng service upload/biên soạn thật và preview/publish, không INSERT trực tiếp published JSON. Mỗi bài cùng các câu và receipt commit nguyên tử. Mục tiêu còn thiếu được tạo trong transaction có AccountGuard. Không thay migration hoặc xóa volume.

## API, media và giao diện

Ba kiểm tra Playwright mới đọc lại sáu bài đã công bố, đối chiếu 90 prompt với dataset, kiểm tra 90 ID/origin khác nhau, type và slot/version/nhóm/thứ tự. Học viên đọc được sáu introduction với số câu 5/5/5; response không chứa answerKey, correctOptionId, materialVersionId hoặc transcript.

Đọc đủ bảy file qua API có quyền, tính SHA-256 khớp manifest; request `Range: bytes=0-15` trả 206 và đúng 16 byte đầu. Học viên chưa được cấp quyền media của lượt làm trước khi M03 có AttemptAssetReadGrant.

UI mở được ba Unit mẫu, mỗi Unit hai bài; kiểm tra mobile không tràn ngang. Ảnh desktop dùng bộ lọc **Tìm Unit: Bắt đầu** để tập trung ba Unit demo, tránh các fixture e2e đã công bố. Các dữ liệu kiểm thử thuộc tác giả riêng; số hàng toàn DB có thể lớn hơn bộ seed.

Ảnh thật: [ba Unit desktop](module-2-complete-units-desktop.png), [hai bài trong Unit ăn uống trên mobile](module-2-complete-food-mobile.png). Các ảnh form biên soạn ở [nghiệm thu bước 3](module-2-authoring-validation.md).

## Tái hiện

Tại root dự án, Docker Desktop đang chạy:

```powershell
python scripts/verify-demo-curriculum.py
docker build --target test -t english-learning-project-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-project-test mvn -B -ntp test
docker compose up -d --build --wait
```

Verifier cần Python `jsonschema`. Testcontainers dùng DB/container riêng; test lỗi DB chỉ thêm CHECK tạm trong DB test và gỡ ở finally. Dockerfile đã có ffprobe/ffmpeg. Không reset DB demo.

Tại `source/client`, app local đã chạy:

```powershell
npm run build
npm run test:e2e
```

Cần Chromium của Playwright hoặc `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. E2E dùng admin từ `.env`/biến E2E, tạo dữ liệu riêng trên môi trường demo; không chạy trên dữ liệu sử dụng thực tế. Log và snapshot cục bộ ở `.test-state/step4-*.log`, `step4-before.json`, `step4-after.json`, `step4-restart-before.json`; báo cáo backend ở `.test-state/m02-seed-reports`, log tổng hợp `seed-full-tests.log`. Thư mục này ignored. Báo cáo Playwright hiện tại phản ánh lần chạy riêng ba test seed cuối; log toàn bộ 26 test là `step4-e2e.log`.

## Giới hạn

M02 hoàn thành học liệu và biên soạn. Chưa có Attempt/runner/check/submit/chấm bài hoặc tiến độ M03; preview không sinh điểm/lịch sử học. PublishedContentReader chỉ dùng trong server, M03 phải lọc keys/transcript và cấp quyền file theo Attempt. Không có API xóa root/file công bố; lịch sử cũ đọc qua Version ID. Chưa đo tải hoặc đánh giá hiệu quả sư phạm.

[Tiến độ M02](../../docs/M02_PROGRESS.md), [dataset/nguồn/manifest](../dataset/README.md), [hợp đồng bàn giao M02–M03](../../docs/M02_M03_HANDOFF.md).
