# Nghiệm thu M03 bước 1 — schema, persistence, DTO và grader

Ngày chạy: 19/10/2026. Nền bước 0: `bd913be`. Mã bước 1 để chưa commit theo yêu cầu người triển khai.

Môi trường: Windows/Docker Desktop; Maven + Java 17 trong image test; MySQL 8.4 thực bằng Testcontainers. Không dùng H2/mock repository để chứng minh persistence; không ghi DB/volume demo.

## Kết quả kiểm tra tập trung

| Nhóm | Kết quả | Nội dung |
|---|---|---|
| AnswerGraderTest | 13/13 PASS | Đúng/sai/trống/partial, NFC, ASCII whitespace, cờ case/whitespace, Locale.ROOT, đáp án thay thế, không bỏ dấu/punctuation, ID ổn định và phản hồi giả |
| AttemptRequestsTest | 5/5 PASS | Fixture request/response round-trip, enum/ID/UUID/required null, trường lạ, số thực giả ID, item trùng, playerState sai, JSON đa hình ba dạng |
| AttemptPersistenceTest | 7/7 PASS | V3 có dữ liệu lên V4 và Hibernate validate; DB trống lên V4; lưu/đọc đủ 7 bảng; owner/filter; JSON/SQL NULL; FK/CHECK/unique và tiến độ COMPLETED |
| Biên dịch/đóng gói backend nền bước 1 | PASS | Maven package; không chạy test trong lệnh đóng gói |
| Unit test sau bổ sung ca I/i dưới locale tr-TR | 18/18 PASS | 13 grader + 5 DTO; mã ứng dụng không thay đổi |
| Toàn bộ backend với V4 | 100/100 PASS | 0 failure/error/skipped; bao gồm 70 test M01/M02, 5 receipt và 25 test nền M03 |

Lượt tập trung đầu: 25 test, 0 failure/error/skipped. Lượt toàn bộ: BUILD SUCCESS, 100 test, 0 failure/error/skipped, thời gian 6 phút 38 giây. Ca I/i được bổ sung trong test sau khi lượt tổng hợp đã compile; đã chạy lại riêng toàn bộ 18 unit test và đạt, không thay mã ứng dụng.

## Những điều đã kiểm chứng

- Bản V4 áp dụng giữ nguyên DDL cột/ràng buộc trong hợp đồng dự kiến; chỉ đổi dòng chú thích đầu khi chuyển sang migration runtime. V1–V3 không đổi.
- Upgrade giữ user/topic tồn tại từ V3. MySQL mới chạy được V1→V2→V3→V4; Flyway validate và JPA validate thành công.
- JSON đa hình Answer, PlayerState, Display và Result đọc lại đúng kiểu/nội dung. SQL NULL khác response SINGLE_CHOICE có optionId=null vẫn được lưu/đọc.
- Kết quả lưu có Submission ID thật trong result_json; FIRST sai và final đúng là hai dữ kiện riêng. Trial sequence và Submission trùng bị unique constraint từ chối.
- FK QuestionVersion sai và các CHECK cho Attempt state/submittedAt, display JSON và loại FIRST trial đều từ chối dữ liệu sai; transaction lỗi rollback.
- Các truy vấn JPA khởi tạo được, đọc đúng owner/mode/state/Unit; khóa bi quan hoạt động trong transaction. Chưa kiểm thử concurrent submit vì chưa có service submit.
- Bộ chấm chỉ trả outcome/responseComplete. Check/retry yêu cầu đủ đáp án và việc tạo FIRST/XP thuộc tầng nghiệp vụ các bước sau.

## Tái hiện

Từ root dự án, Docker Desktop đang chạy:

```powershell
docker build --target test -t english-learning-m03-step1-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m03-step1-test mvn -B -ntp '-Dtest=AnswerGraderTest,AttemptRequestsTest,AttemptPersistenceTest' test
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m03-step1-test mvn -B -ntp test
```

Các lượt thực tế tái sử dụng image `english-learning-m03-step0-test` chứa Maven dependencies, bind mount thư mục `source/server/src` của nhánh hiện tại read-only vào `/app/src` và compile lại trước chạy test. Đây không phải chạy bytecode cũ trong image.

Log ignored: `.test-state/m03-step1-compile.log`, `m03-step1-unit.log`, `m03-step1-unit-final.log`, `m03-step1-focused.log`, `m03-step1-full.log`. Log có thông báo SQL ERROR ở các ca cố ý vi phạm ràng buộc; kết luận dựa trên kết quả test/Maven ở cuối lượt chạy.

## Giới hạn

Chỉ nghiệm thu nền bước 1; chưa có endpoint Attempt/progress, runner UI, phân quyền media theo lượt hoặc luồng nộp thật. Chưa chạy E2E UI vì không thay giao diện trong bước này. DB demo đang chạy không bị nâng migration, recreate hoặc xóa dữ liệu. Chưa có benchmark/đánh giá học tập.
