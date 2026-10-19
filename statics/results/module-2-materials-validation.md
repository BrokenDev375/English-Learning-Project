# Kiểm tra M02 bước 2 — file và tài liệu

Ngày chạy: 17/10/2026. Windows, Docker Desktop, Java 17 trong container Maven, MySQL 8.4 qua Testcontainers và Chromium qua Chrome có sẵn. Kết quả này xác nhận bước file/tài liệu, chưa phải nghiệm thu toàn bộ M02.

## Kết quả thực tế

| Kiểm tra | Kết quả |
|---|---|
| Backend package và Docker server build | PASS |
| Flyway V1/V2/V3 trên MySQL trống + Hibernate validate | PASS |
| MaterialApiTest mới | 12 test, 0 failure/error/skipped |
| CatalogApiTest hồi quy | 8/8 PASS |
| AccountAdministrationApiTest hồi quy | 10/10 PASS |
| ModuleOneApiTest hồi quy | 12/12 PASS |
| Tổng backend lần cuối | 42/42 PASS |
| TypeScript + Vite build, Docker client build | PASS |
| Playwright toàn bộ lần cuối | 14/14 PASS: 5 M01, 4 catalog, 5 materials |
| Desktop 1440 × 1000, mobile 390 × 844 | PASS; đã xem ảnh, không tràn ngang |
| Recreate server, đăng nhập lại qua frontend proxy | PASS |
| Seed và lưu trữ sau recreate | Vẫn đúng 3 material demo, ID/version/revision không đổi; hash 2 file và Range không đổi |
| Docker mysql/server/client | Đang chạy, mysql/server healthy |
| git diff --check | PASS |

## Hành vi đã kiểm tra

- Upload PNG/JPEG, MP3/WAV/OGG thật, SHA-256 và MIME theo nội dung. PNG được tải với phần mở rộng khác vẫn nhận dạng đúng. File rỗng, giả ảnh/audio, file vượt 5 MiB và tên có đường dẫn bị chặn. MP3/OGG fixture được tạo bằng ffmpeg trong test.
- Ảnh được giải mã bằng ImageIO sau kiểm tra header, giới hạn 20 triệu pixel; audio được probe bằng ffprobe, chọn demuxer theo chữ ký, chỉ cấp protocol file, một stream audio, timeout 8 giây/đầu ra 64 KiB. Docker có ffprobe/ffmpeg; môi trường chạy trực tiếp cần cài chúng.
- CSRF, quyền upload, file riêng tư, nháp của người khác và quyền ADMIN. STUDENT không được đọc file vì M03 chưa cấp grant theo lượt làm. Tác giả đọc được asset dùng trong bản công bố và tài liệu mình sở hữu, kể cả khi tài liệu gốc đã ngừng cấp.
- Upload thử lại đúng requestId/payload trả lại cùng asset; cùng key khác nội dung bị chặn. Khi lưu DB lỗi, transaction rollback và gỡ file vừa ghi; không để receipt thành công. Thiếu file trên đĩa trả lỗi rõ ràng.
- GET file 200, Range hợp lệ 206 đúng byte và range không thể đáp ứng 416; response giữ quyền/cookie, MIME và cache riêng tư. Không chỉ kiểm tra header Accept-Ranges.
- Tạo/sửa READING/AUDIO/IMAGE; strict DTO, trường nullable bắt buộc, số và revision đúng kiểu, asset đúng loại, đoạn audio không vượt thời lượng. Thời gian được chuẩn hóa mili giây trước hash/lưu.
- Nháp thiếu nội dung vẫn lưu được; preview báo lỗi và chặn xuất bản. Preview cũ sau khi sửa bị PREVIEW_STALE. Xuất bản đóng băng version; tạo nháp mới không sửa bản công bố, version cũ vẫn đọc được qua API.
- Bỏ nháp 204 có replay, root rỗng ẩn khỏi danh sách, số version không tái sử dụng. Ngừng cấp mới giữ bản công bố/file lịch sử. Fixture tham chiếu chứng minh chặn xóa nháp đang được dùng và chặn retire khi bài công bố dùng phiên bản câu cũ tham chiếu tài liệu.
- Browser: đọc thiếu nội dung → lỗi preview → sửa → xuất bản → tạo nháp mới → chuyển tab vẫn thấy bản công bố cũ.
- Browser: upload audio đã commit nhưng mất ACK → thử lại đúng requestId → thành công; nghe thử và tua bị giới hạn trong đoạn đã chọn; transcript không hiện trong phần trình bày. Test dùng APIRequestContext cùng cookie/file/requestId để tạo commit đầu tiên vì Chromium interception không kèm byte file trong postData; lần retry đi qua upload thật của trình duyệt.
- Browser: chặn file ảnh quá lớn, tải ảnh thật, giải mã hiển thị, xuất bản, ngừng cấp, tạo/bỏ nháp. Tác giả khác xem được tài liệu demo đã công bố nhưng không thấy nút chỉnh sửa/ngừng cấp.
- Recreate container backend mà không xóa volume: so sánh cả 3 root và published version, SHA-256 của PNG/WAV, byte range trước/sau; không nhân bản seed và file vẫn đọc được.

## Tái hiện

Docker Desktop đang chạy, tại root dự án:

```powershell
docker build --target test -t english-learning-project-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-project-test mvn -B -ntp test
```

Testcontainers tạo database riêng; không dùng/xóa database demo. Lần chạy đã lưu báo cáo Maven qua bind mount vào `.test-state/m02-material-reports` (thư mục ignored).

Frontend cần app đang chạy ở localhost:5173, tại `source/client`:

```powershell
npm ci
npm run build
npm run test:e2e
```

Cần Chromium của Playwright hoặc đặt `PLAYWRIGHT_CHROMIUM_EXECUTABLE_PATH`. E2E đọc admin từ `.env`/biến E2E, tạo tác giả kiểm thử riêng và dữ liệu có tiền tố e2e trên DB demo. Không chạy E2E trên dữ liệu sử dụng thực tế.

Ảnh thật đã xem: [kho tài liệu desktop](module-2-materials-desktop.png), [kho tài liệu mobile](module-2-materials-mobile.png), [đọc tài liệu mobile](module-2-reader-mobile.png). Seed/nguồn ở [dataset](../dataset/README.md). Quyền và transaction theo [hợp đồng](../../docs/M02_M03_HANDOFF.md); cơ chế probe tham khảo [ffprobe](https://ffmpeg.org/ffprobe.html), HTTP Range tham khảo [Spring MVC Range requests](https://docs.spring.io/spring-framework/reference/web/webmvc/mvc-range.html).

## Phạm vi còn lại

Chưa có ngân hàng câu hỏi, bài học, learner catalog, PublishedContentReader, seed 6 bài/90 câu hoặc lượt làm/chấm bài M03. UI xem bản nháp/bản công bố hiện tại; lịch sử phiên bản cũ có API, chưa có màn liệt kê toàn bộ lịch sử. Bỏ nháp giữ asset đã upload; chưa có công cụ dọn file không còn tham chiếu. Xem [tiến độ M02](../../docs/M02_PROGRESS.md).
