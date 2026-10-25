# M05 — Biên bản chặng 1: schema và bằng chứng ôn đến hạn

Ngày 21/10/2026. Nền commit `57f2951`, thay đổi M05 trong working tree chưa commit. Chặng này có migration V6 và hook context; chưa có worker/API thưởng, cấp độ/streak/badge hoặc Giải đấu tuần hoạt động.

## Phần đã triển khai

- V6 thêm 9 bảng policy/context/processed/claim/XP/profile/day/badge/retry failure, không sửa V1–V5.
- Policy `m05-p0-v1` và activatedAt/effectiveAt được lưu một lần qua Flyway.
- ORM/repository cho policy và review context. Các bảng thưởng mới chỉ là nền schema; mapping/service sử dụng ở chặng 2–3.
- ReviewRewardContextRecorder chạy MANDATORY trong transaction create REVIEW của M03. Bằng chứng có owner, objective, dueAt/stage/cycle/selectedAt và eligibility. Provider không có lịch M04 thì không tạo bằng chứng giả.
- Resume/replay không ghi lại context; các cột context bất biến khi cập nhật ORM. Lỗi ghi context rollback Attempt/item/exposure/receipt.
- Clock được inject vào create Attempt; không đổi public Result schema 1 hoặc logic chấm M03.

## Kết quả thực tế

| Kiểm tra | Kết quả |
|---|---|
| Compile backend source/test | PASS trong image test; lượt chạy chính mount source hiện tại read-only và clean test |
| GamificationContextApiTest | **16/16**, gồm 13 ca M04 kế thừa và 3 ca context: owner/due/replay/resume, lỗi sai chưa đến hạn, rollback và retry |
| GamificationMigrationTest | **1/1**, nâng V5→V6 giữ user/topic, activation không đổi khi migrate lại, CHECK cap/XP và unique nguồn/chu kỳ/ngày |
| AttemptPersistenceTest | **7/7**, nâng từ V3 giữ dữ liệu, DB mới đến V6, roundtrip/ràng buộc dữ liệu M03 |
| GamificationCycleKeyTest | **1/1**, chuẩn microsecond; đổi owner/objective/stage/mốc tạo khóa khác |
| Tổng lượt chính | **25/25**, 0 fail/error/skip; Maven 5 phút 35 giây |
| Hồi quy bổ sung provider REVIEW M03 | **1/1**, AttemptApiTest#reviewIsNotReadyUntilProviderReturnsValidVersions, image đồng bộ source cuối; Maven 1 phút 11 giây |
| Hợp đồng M05 | 50 fixture vẫn đúng mong đợi, 8 operation/31 schema/10 example; chưa là test endpoint M05 thật |

Lỗi database được inject và lỗi unique/check dự kiến có thể xuất hiện trong log. Kết quả pass lấy từ tổng hợp JUnit/Surefire, không suy đoán chỉ vì thấy stack trace hoặc server khởi động.

## Lệnh và môi trường

```powershell
docker build --target test -t english-learning-m05-test ./source/server
docker run --rm --name english-learning-m05-foundation-tests -v /var/run/docker.sock:/var/run/docker.sock -v D:/English-Learning-Project/source/server/src:/app/src:ro -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m05-test mvn -B -ntp '-Dtest=GamificationContextApiTest,GamificationCycleKeyTest,GamificationMigrationTest,AttemptPersistenceTest' clean test
python scripts/verify-m05-contract.py
```

Java 17, MySQL 8.4 Testcontainers, Docker Desktop. Compile từ bind mount trên Windows chậm hơn copy source vào image; không đổi code để né môi trường. Log trong `.test-state/m05-stage0/foundation-tests.log`; build cuối đồng bộ image với source nằm trong `foundation-final-build.log`.

Sau build cuối, chạy riêng `-Dtest=AttemptApiTest#reviewIsNotReadyUntilProviderReturnsValidVersions` bằng cùng image để kiểm tra provider không có lịch M04 vẫn cấp REVIEW an toàn, không bịa context. Log `provider-regression.log`. Hai lượt 25/25 và 1/1 là hai lượt riêng, không mô tả như một lượt 26/26 duy nhất.

## Giới hạn và việc tiếp theo

Chưa chạy toàn bộ backend hoặc browser regression sau thay đổi chặng 1; frontend không thay đổi và build chặng 0 vẫn là bằng chứng build frontend trong lượt này. Chưa chạy persistence toàn M05 qua server recreate vì worker/ledger service chưa có. Các ca thuộc chặng 2–6 vẫn là checklist dự kiến.

Database demo và .env giữ nguyên, không xóa volume/restart demo. Khi triển khai image source mới, Flyway sẽ áp dụng V6 lên database được cấu hình; lượt kiểm tra này chỉ áp dụng trên database test riêng. Không tự repair checksum hoặc sửa V1–V5.
