# M03 bước 2–3 — cấp lượt, snapshot, media và nháp

Ngày: 19/10/2026. Nền Git: `d37ba0b`. Các thay đổi trong lượt này chưa commit. Môi trường Windows/Docker Desktop, Maven/Java 17, MySQL 8.4 thật qua Testcontainers; không dùng DB/volume demo.

## Phạm vi triển khai

| API | Hành vi |
|---|---|
| POST `/api/attempts` | LEARN/PRACTICE, REVIEW provider/fallback; tạo mới 201 hoặc tiếp tục 200, receipt replay nguyên status/body |
| GET `/api/attempts` | Danh sách của chính STUDENT, lọc mode/state, phân trang và answeredCount |
| GET `/api/attempts/{id}` | Snapshot an toàn theo phiên bản/permutation đã pin; cả nháp/playerState và snapshot sau SUBMITTED |
| PUT `/api/attempts/{id}/draft` | Full snapshot, revision, membership/response/playerState validation; ACK sau commit; không chấm/tạo trial |
| GET `/api/assets/{id}` hiện có | Thêm grant theo Attempt của STUDENT, giữ quyền với phiên bản cũ/retire/sau nộp |

Không thêm migration hoặc sửa V1–V4. Attempt chỉ có IN_PROGRESS/SUBMITTED theo hợp đồng. Trong test, trạng thái SUBMITTED được thiết lập qua fixture để kiểm tra read/write guard; chưa có API submit hoặc nghiệp vụ chấm cuối.

## Kiểm tra đã chạy

- Nhóm tập trung: 35/35 PASS, không failure/error/skipped: 17 test HTTP AttemptApiTest, 13 grader và 5 DTO. Sau lượt này bổ sung hai test cho ít/lâu gặp và thay phiên bản, đã đạt trong lượt toàn bộ.
- Lần đầu có lỗi fixture AUDIO thiếu startSeconds bắt buộc của V3. Lần tiếp theo phát hiện fixture mock REVIEW trả danh sách rỗng mặc định, so sánh JSON int/long và quyền tạo trigger của user test. Đã sửa fixture/mock, so sánh JSON theo giá trị, dùng CHECK tạm trong DB test để gây lỗi receipt thay cho trigger. Lượt 35 test sau sửa đạt BUILD SUCCESS.
- Toàn bộ backend: **119/119 PASS**, 0 failure/error/skipped, BUILD SUCCESS; thời gian 7 phút 44 giây. Gồm 70 test M01/M02, 5 receipt, 25 nền M03 và 19 test HTTP bước 2–3. Lượt này compile và kiểm tra mã cuối, gồm khóa Lesson khi chọn PRACTICE/REVIEW và hai ca bổ sung.

## Bằng chứng trong AttemptApiTest

- LEARN 10 câu đúng nhóm/thứ tự; snapshot không lộ keys, explanation, transcript, hint hoặc storageKey; GET không đổi bộ câu.
- Create cùng key replay 201; key mới cùng lựa chọn trả 200 và revision hiện hành. Cả hai kiểu create đồng thời chỉ tạo một Attempt, exposures không tăng khi resume/GET.
- PRACTICE đúng Topic/difficulty, thiếu câu cấp ít hơn, origin của bản sao không lặp; ưu tiên chưa gặp, sau đó ít gặp và lâu gặp. REVIEW chưa tích hợp trả REVIEW_NOT_READY, provider test trả câu hợp lệ hoặc bị từ chối nếu trả GUIDED.
- Full draft giữ đúng SQL null, SINGLE_CHOICE optionId=null, FILL_BLANK/MATCHING partial và vị trí nghe khi đăng nhập lại/GET. Lưu/xóa đáp án cập nhật answeredCount, không tạo trial.
- Thiếu/trùng/lạ item, response sai type/ID, current item lạ, audio không thuộc lượt, vị trí âm hoặc audioPositions trùng đều không ghi dữ liệu/receipt thành công.
- Hai tab cùng revision: một PUT thành công, một REVISION_CONFLICT; GET giữ đúng đáp án của bên thắng. Hai PUT cùng key chỉ tăng revision một lần.
- Bỏ nội dung ACK sau commit rồi gửi lại: nhận receipt cũ. Sau một save mới hơn, replay cũ vẫn trả revision cũ nhưng GET giữ nháp mới; key cũ payload khác bị từ chối.
- Gây lỗi insert receipt bằng CHECK tạm sau khi đã ghi answers/playerState/revision: HTTP 503, toàn bộ dữ liệu rollback, không receipt thành công; bỏ CHECK rồi retry cùng key thành công.
- Giữ user lock trong transaction quản trị, cho save đi qua filter rồi chờ actor lock; commit LOCKED/securityVersion mới khiến save bị 401, không ghi nháp/receipt.
- Quyền owner/role/CSRF/no-store được kiểm tra; không đọc/sửa lượt của tài khoản khác. Audio Range thật trả 206 và đúng 16 byte; người không có lượt bị 404; retire và SUBMITTED vẫn giữ quyền file/lượt cũ.
- Lượt đã SUBMITTED cho phép học lại bằng lượt mới và wasSeenBefore=true. Retry key đã lưu trước nộp vẫn replay trước khi kiểm tra state; mutation mới sau nộp bị ATTEMPT_FINISHED.
- Xuất bản MaterialVersion/QuestionVersion/LessonVersion mới: GET và resume của lượt cũ giữ snapshot cũ; lượt mới nhận bản mới. Học viên có lượt cũ không được đọc asset mới chỉ vì cùng Material root; chủ lượt mới đọc được asset mới. Đây là fixture publication qua JPA, không tuyên bố đã chạy full E2E nút cập nhật audio M02→M03.

Selector khóa actor trước, rồi Lesson root tăng dần ID và đọc lại sau khi chờ để chọn nội dung đang công bố. LEARN resume theo Lesson root; PRACTICE/REVIEW so tham số chuẩn hóa theo giá trị, tránh khác biệt IntNode/LongNode sau MySQL JSON round-trip. Bộ câu/permutation và exposures được ghi cùng receipt trong một transaction.

## Tái hiện

Tại root, Docker Desktop đang chạy:

```powershell
docker build --target test -t english-learning-m03-step23-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m03-step23-test mvn -B -ntp '-Dtest=AttemptApiTest,AttemptRequestsTest,AnswerGraderTest' test
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m03-step23-test mvn -B -ntp test
```

Lượt thực tế dùng image `english-learning-m03-step0-test` đã có dependencies, bind mount source hiện tại read-only tại `/app/src` rồi Maven compile lại trước test. Log local ignored: `.test-state/m03-step23-focused*.log` và `m03-step23-full.log`.

SQL ERROR ở ca CHECK cố ý không phải lỗi bị bỏ qua: assertion kiểm tra mã lỗi HTTP và DB sau rollback. ReviewSelectionProvider trong test là mock, không bật thuật toán ôn giả trong app. Test quyền media dùng file bytes fixture, không chứng minh chất lượng audio hoặc bộ giải mã; M02 có test media riêng.

## Giới hạn

Chưa có hint/check/submit/result/retry/progress API hoặc runner UI. Chưa chạy Playwright cho M03; GET qua phiên đăng nhập khác kiểm tra khôi phục từ DB, không tuyên bố đã có luồng F5 trên giao diện. Chưa chạy server restart/E2E offline hoặc benchmark. Chưa rebuild/restart demo để áp dụng code mới; xem [hướng dẫn thử API](../../docs/M03_API_SMOKE.md#draft).
