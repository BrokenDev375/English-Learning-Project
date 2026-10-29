# Kiểm tra bài 18 câu và hoạt động đọc

Ngày kiểm tra: **28/10/2026**. Nền Git `main`/`fac44f4`, thay đổi chưa commit. [Kế hoạch](../../docs/QUESTION_VARIETY_IMPLEMENTATION_PLAN.md), [triển khai và cách tạo mẫu](../../docs/QUESTION_VARIETY_PROGRESS.md).

## Dạng bài và số câu linh hoạt — 29/10/2026

- `mvn -q '-Dlogging.level.org.hibernate.SQL=WARN' '-Dtest=LessonActivitiesTest,AttemptActivityProjectionTest,AuthoringApiTest,AttemptApiTest' test`: **73/73 PASS** (9 + 1 + 17 + 46), không failure/error/skip. Bao gồm bài chọn bốn dạng với 15, 18, 20 câu; từ chối 14/21, lặp dạng và thiếu slot; cấu trúc sáu/bảy phần cũ; hồi quy biên soạn/lượt học.
- Production build TypeScript/Vite **PASS** trong Docker từ source cuối. JSON OpenAPI, `node --check` script và `git diff --check` đạt.
- Browser trên project riêng: lượt sáu ca đầu **5/6 PASS**, ca mới thất bại vì fixture chuyển tài khoản qua API nhưng React vẫn giữ user cũ. Sau khi sửa fixture reload tài khoản và hoàn thiện bộ chọn số câu/nhãn truy cập, chạy lại hai ca biên soạn **2/2 PASS**, gồm chọn bảy dạng và bỏ dạng/xuất bản/làm bài 15 câu; sau khi thêm thu hồi bài phụ, chạy lại ca tùy chọn **1/1 PASS**. Sáu ca khác nhau qua các lượt, không phải một lượt 6/6 trên cùng build. Những lượt dừng giữa chừng dùng build cũ không tính PASS.
- Môi trường `english-learning-e2e-flex-seed` riêng trên 15178/18085: tạo lượt cũ 10 câu, nâng Lesson 1 bằng script hai lần cùng kết quả, GET lượt cũ vẫn 10 câu và phiên bản cũ; lượt mới có 18 câu, bốn dạng 6/4/6/2, audio Range 206, snapshot không lộ đáp án/transcript. Giả lập checkpoint chưa nhận phản hồi sau publish đã commit, replay cùng requestId trả đúng phiên bản 7. Không gọi đây là kiểm tra timeout mạng thật. Project test đã dừng, volume được giữ.
- Trên baseline 5176/8083, đã sao lưu DB trước nâng, build source và chạy script hai lần; GET học viên xác nhận Lesson 1 có 4 GUIDED + 14 INDEPENDENT, 5 câu ôn và bốn nhóm 6/4/6/2. SQL chỉ đọc sau nâng xác nhận 3 user, 3 Unit, 6 Lesson, 98 Question, 13 Material, 0 Attempt, 0 Submission, 0 XP ledger. Không chạy E2E ghi dữ liệu lên demo. Xem [chi tiết backup/checkpoint](../../docs/BASELINE_TESTING.md).

## Điều chỉnh thêm nghe cuối bài — 28/10/2026

Lượt kiểm tra riêng sau quyết định giữ 18 câu: cấu trúc mới **3/2/2/3/3/3/2**, 7 GUIDED + 11 INDEPENDENT. Giảm một câu tự làm ở mỗi phần đọc; hai câu nghe chọn đáp án cuối tham chiếu AUDIO thật `daily-routine.wav`. Bài sáu phần cũ vẫn được validator chấp nhận, lượt cũ giữ snapshot đã chốt. Không migration mới, không đổi luật chấm/thưởng.

- `mvn -q '-Dtest=LessonActivitiesTest,AttemptActivityProjectionTest,AttemptPersistenceTest' test`: **15/15 PASS** (7 cấu trúc, 1 projection, 7 persistence), không failure/error/skip. Có kiểm tra cấu trúc mới đủ 18 câu, thiếu/sai loại AUDIO, sai số option và cấu trúc cũ.
- `npm run build`: **PASS** TypeScript/Vite. Lượt đầu Vite bị sandbox chặn child process (`spawn EPERM`); chạy lại với quyền thực thi phù hợp đạt.
- `npm run test:e2e -- activity-variety.spec.ts`, project riêng `english-learning-e2e-activities`, Edge: **5/5 PASS**, không flaky/skip. Đã kiểm tra bảy nhóm/18 câu, audio ready và play tăng currentTime, HTTP Range 206, snapshot không lộ transcript/keys, autosave/reload đoạn đọc, conflict, replay nộp mất ACK, kết quả bất biến, preview/xuất bản và pin phiên bản cũ.
- `node --check` cho fixture và seed, seed chế độ chỉ mô tả: **PASS**. Chưa chạy seed 1.1.0 qua checkpoint thực trên demo; browser sử dụng cùng provision qua API.
- Môi trường test đã dừng, giữ volume riêng. Demo 5176/8083 và Lesson 11 chưa cập nhật phần nghe. Source và tài liệu chưa commit.

Các mục dưới đây là bằng chứng lịch sử của bản sáu phần trước thay đổi này; không cộng thành kết quả hồi quy toàn bộ cho bản nghe.

## Môi trường và phạm vi

- Java 17, Maven, MySQL 8.4 Testcontainers; thêm image `english-learning-activities-test` build từ source hiện tại có ffprobe/ffmpeg.
- Trình duyệt Edge Chromium trên Windows, project/volume riêng `english-learning-e2e-activities`, UI 5174/API 8081. Không chạy test hoặc seed mẫu vào demo 5176/8083.
- V12 thêm activities_json nullable vào LessonVersion; không chỉnh migration cũ. Bài nền vẫn 10 câu LEARN, bộ mẫu mới có 18 câu LEARN + 4 REVIEW_VARIANT và 4 material đọc.
- Sau nghiệm thu ban đầu đã dừng các container của project test, giữ nguyên volume; thời điểm đó chưa nâng DB demo. Lượt chạy demo theo yêu cầu sau đó được ghi riêng bên dưới.

## Backend

Lệnh đầy đủ tại `source/server`:

```powershell
mvn -B -ntp '-Dlogging.level.org.hibernate.SQL=WARN' '-Dlogging.level.org.springframework.web=WARN' test
```

Lượt này chạy **320 ca, 309 PASS, 6 failure, 5 error**, không skip, thời gian **17:33**. Hai failure do fixture kỳ vọng số migration trước V12; chín failure/error còn lại nằm trong MaterialApiTest/CurriculumSeedTest do Windows thiếu ffprobe. Không coi lượt này là 320/320 PASS.

Sau khi cập nhật đúng số migration, build image từ source hiện tại:

```powershell
docker build --target test -t english-learning-activities-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-activities-test mvn -B -ntp '-Dtest=MaterialApiTest,OrderingMigrationTest,EngagementMigrationTest' test
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-activities-test mvn -B -ntp '-Dtest=CurriculumSeedTest' test
```

- Material/migration: **14/14 PASS**, không failure/error/skip, **3:06**.
- Seed nền: **6/6 PASS**, không failure/error/skip, **5:11**; gồm chạy lặp, giữ nháp tùy chỉnh, seed đồng thời và rollback khi constraint chủ động từ chối ghi.
- Tổng cộng **320 ca backend khác nhau đã qua qua các lượt**, không phải một lượt 320/320 duy nhất.
- Bảy ca mới: LessonActivitiesTest **6/6**, AttemptActivityProjectionTest **1/1**, đều qua. Kiểm tra cấu trúc/thứ tự/role, từ chối metadata lạ/ID sai, liên kết vật liệu/vị trí trống, giữ payload receipt bài cũ, remap phiên bản và chỉ cấp một ô thuộc REVIEW.
- Hồi quy trong lượt đầy đủ gồm AttemptApiTest **46/46**, AuthoringApiTest **17/17**, AttemptPersistenceTest **7/7** và các nhóm M04/M05/M06/M07.

## Frontend và trình duyệt

Build TypeScript/Vite PASS. Connection manager **9/9**, IndexedDB/journal **6/6** PASS.

Năm ca E2E mới đã qua trong một lượt (**44,8 giây**) trước bổ sung kiểm tra thu gọn đoạn đọc và publish phiên bản mới:

1. Đúng 18 câu, sáu phần đúng thứ tự, mỗi phần một GUIDED; snapshot không có khóa; nhập trong đoạn, autosave/F5, nộp đúng 18/18 và chữa không đổi Result.
2. Mobile 390 px: giữ bản local khi một HTTP request khác commit nháp; chặn request lưu của browser bằng route rồi thả để nhận xung đột thực; người dùng chọn bản server, các ô khôi phục đúng. Đây là mô phỏng ghi đồng thời qua HTTP, không gọi là thao tác hai tab thật.
3. Gửi submit thật qua `route.fetch()`, server commit thành công rồi chặn phản hồi; retry giữ nguyên toàn bộ key/payload 18 đáp án và trả cùng Result. Đây là mất phản hồi sau commit có chủ động chặn, không phải timeout mạng tự nhiên.
4. Tác giả chọn đủ sáu hoạt động, tạo ánh xạ bốn ô bằng UI, lưu nháp và preview nhập trực tiếp; điều kiện xuất bản hợp lệ.
5. Metadata tham chiếu câu ngoài đoạn bị preview báo lỗi và POST publish từ chối; lượt đã chốt không đổi.

Hồi quy toàn bộ **119 ca: 118 PASS, 1 timeout**, **9,2 phút**, không skip. Ca timeout là `module-two-authoring` điền từ, kẹt ở bước tìm Email trên trang đăng nhập trắng, chưa tới form tác giả. Trace đã được kiểm tra; không sửa nghiệp vụ hoặc bật retry để bỏ qua.

Sau bổ sung thu gọn đoạn đọc và số câu thật trong preview, chạy lại sáu spec liên quan: **39/40 PASS**, **4,6 phút**. Ca tác giả từng timeout đã PASS; một ca M03 khác timeout tại `page.goto('/#/learning')`, trước khi tạo lượt/nộp. Sau sửa bố cục nhãn/ô nhập cùng dòng và bỏ đoạn đọc lặp trong preview, build lại image frontend từ source cuối, chạy **6/6 PASS trong 30,4 giây**: cả năm ca mới và ca M03 vừa timeout. Không còn ca kiểm tra chức năng thất bại chưa được đối soát.

**Tổng 119 ca browser khác nhau đã qua qua các lượt**, không phải một lượt 119/119 duy nhất trên source cuối. Phần tinh chỉnh hiển thị cuối được kiểm tra bằng build image và sáu ca tập trung, gồm tác giả, desktop/mobile, thu gọn/mở lại đoạn đọc, publish bản mới giữ lượt cũ, xung đột và mất ACK. Log: `.test-state/activity-browser-full.log`, `.test-state/activity-browser-final.log`, `.test-state/activity-browser-last.log`.

Snapshot bài mẫu trước trả lời có **16.874 byte**; một GET local ở lượt cuối **34,9 ms**. Đây là một phép đo local trên bộ mẫu, không phải benchmark tải, percentile hoặc SLA.

## Bài mẫu và dữ liệu

`node scripts/seed-activity-sample.mjs` mặc định chỉ mô tả, không ghi. Đã chạy `--apply` **hai lần liên tiếp trên 5174**, cả hai trả cùng Lesson **17**, Unit **13**, LessonVersion **19**. Đây là ID trong DB test, không phải ID cố định trong demo. Checkpoint `.test-state/activity-sample/e27f87dcda4ec92b.json` giữ key/payload và kết quả; sau kiểm tra đã retire riêng bài test này để giữ ba Unit nền khi hồi quy.

Các ảnh QA ở `.test-state/activity-mobile.png`, `.test-state/activity-desktop.png`, `.test-state/activity-author-preview.png`; ảnh là dữ liệu học viên giả lập trong môi trường test.

Đã tạo lại riêng container server bằng `docker compose --project-name english-learning-e2e-activities --file compose.yml up -d --no-deps --force-recreate --wait server`, giữ volume. Sau đăng nhập lại:

- Snapshot nháp 18 câu, activities, đáp án và playerState **giống hệt** trước recreate.
- Gửi lại lệnh lưu với requestId/payload/revision cũ trả đúng ACK đã lưu.
- Result nộp đúng 18/18 giữ nguyên; replay submit trả cùng Result/Submission.
- Các vòng và học liệu seed vẫn giữ; retire riêng bài test sau kiểm tra, không xóa dữ liệu.

Bằng chứng: `.test-state/activity-recreate.log`, `.test-state/activity-recreate-result.json`. Snapshot đã nhập đủ có 17.885 byte; hai Attempt test 142/145, LessonVersion 33, chỉ có ý nghĩa trong DB test này. Lượt này kiểm tra recreate của server, không đo sự cố mất điện hoặc offline đầy đủ.

## Các vấn đề môi trường đã xử lý

- Volume `english-learning-e2e` cũ lệch checksum V4 từ lịch sử trước lượt này: giữ nguyên, chuyển sang project/volume mới; không repair, xóa volume hoặc sửa migration cũ.
- Vite bị sandbox chặn spawn: chạy build với quyền thực thi phù hợp. Máy không có Chrome ở đường dẫn mặc định; dùng Edge Chromium có sẵn.
- Một lượt Docker build lỗi DNS Docker Hub; thử lại thành công, không đổi code ứng dụng để né lỗi mạng.
- Fixture unit mới ban đầu có lỗi unbox null, đã sửa và chạy lại. Nhãn ô nhập được làm rõ bằng aria-label; selector test tác giả chuyển sang combobox và trạng thái lưu cụ thể.

## Giới hạn

Chưa chuyển toàn bộ ngân hàng thành 18 câu. Chưa triển khai bài viết, chấm viết tự động hoặc đổi XP. Nội dung hint/giải thích vẫn cần biên tập để không gợi đáp án phần tự làm. Các phép đo local nếu có không đại diện tải production hoặc hiệu quả học tập.

Kiểm tra bàn giao: `git diff --check` đạt; kiểm tra whitespace/UTF-8 của các file mới, liên kết Markdown và toàn bộ tham chiếu schema OpenAPI đạt. Không tạo nhánh, commit hoặc push. Các thay đổi báo cáo/demo lớp học có sẵn từ đầu phiên được giữ nguyên.

## Chạy demo theo yêu cầu tiếp theo — 28/10

Đã dùng `scripts/start-main.ps1` build và chạy **english-learning-main** trên **5176/8083**, giữ `english-learning-main_mysql_data` và `english-learning-main_asset_data`. Trước khi nâng đã sao lưu DB vào `.test-state/main-activities-run/before-v12-20261028T162150Z.sql` (2.164.917 byte, không đưa nội dung backup vào Git/log).

Flyway từ **V11 → V12** thành công; server/MySQL healthy, frontend HTTP 200 và API health đạt. Chạy script bài mẫu với `SAMPLE_BASE_URL=http://localhost:5176`: **Unit 6, Lesson 11, LessonVersion 11**, Vòng 2 theo danh mục hiện tại. API xác nhận sáu hoạt động đúng thứ tự, **6 GUIDED + 12 INDEPENDENT**, 4 REVIEW_VARIANT và đủ hai passage cloze.

Đối chiếu trước/sau: **users 9 → 9, attempts 58 → 58, submissions 47 → 47**. Chỉ thêm học liệu mẫu, không tạo học viên/lượt/kết quả giả hoặc vượt khóa vòng. Checkpoint `.test-state/activity-sample/0ae6f571866778a9.json`; log và số liệu xác nhận ở `.test-state/main-activities-run/`. Demo chính được giữ chạy để người dùng sử dụng.
