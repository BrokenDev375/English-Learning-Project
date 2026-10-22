# M05 — Biên bản chặng 0

Ngày 21/10/2026. Nền main/origin/main `57f2951` (M04). Chặng này thêm hợp đồng/tài liệu/fixture và kiểm tra nền, **chưa triển khai runtime M05 hoặc migration M05**.

## Kết quả thực tế

| Kiểm tra | Kết quả / phạm vi |
|---|---|
| Git | Fetch thấy origin/main đã có M04; fast-forward local main be92240 → 57f2951; giữ thay đổi kế hoạch M05 |
| Backend image | Docker target test build thành công từ source 57f2951, Java 17; bước package dùng skipTests, không coi đây là test pass |
| Nhóm test nền M04 chạy lại | **21/21**, 0 failure/error/skip: PersonalReviewApiTest 13, ReviewPolicyTest 8; thời gian Maven 2 phút 55 giây |
| Frontend | `npm run build` thành công, TypeScript + Vite, 54 module; không thay dependency/lockfile |
| Hợp đồng M05 | **8 operation, 31 schema**, ref nội bộ phân giải được, 10 embedded example hợp lệ |
| Fixture M05 | **50/50** theo kết quả mong đợi: 18 valid, 32 invalid; mọi endpoint có fixture request/response tương ứng |
| Phạm vi fixture | Key/field injection, số âm/lẻ/string/unsafe integer, cap/batch bounds, UTC/date format, state cần membership/result, privacy fields và socket payload |

Script kiểm tra hợp đồng dùng jsonschema Draft 2020-12 và kiểm tra cấu trúc operation/ref/security/CSRF/cache/fixture coverage. Đây không phải một lượt chạy validator toàn bộ đặc tả OpenAPI, test business policy hoặc test endpoint HTTP thật.

## Các lượt bị ngắt và cách xử lý

Lượt frontend đầu trong sandbox bị spawn EPERM ở Vite; chạy lại với quyền thực thi phù hợp đã pass, không sửa code để né sandbox.

Lượt backend đầu chọn thêm AttemptPersistenceTest bị Docker CLI ngắt với `error waiting for container: unexpected EOF` trước kết quả tổng hợp. Không ghi lượt đó là pass hoặc kết luận lỗi code. Docker kiểm tra lại có phản hồi; đã chạy lại riêng nhóm M04 nêu trên và thu log kết quả đầy đủ. AttemptPersistenceTest/toàn backend chưa được xác nhận chạy xong ở chặng 0.

## Lệnh chính

```powershell
docker build --target test -t english-learning-m05-test ./source/server
docker run --rm --name english-learning-m05-review-baseline -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m05-test mvn -B -ntp '-Dtest=ReviewPolicyTest,PersonalReviewApiTest' test
python scripts/verify-m05-contract.py
```

Frontend chạy `npm run build` từ `source/client`. Log backend lần đạt nằm trong `.test-state/m05-stage0/m04-baseline-tests.log` (Git ignore). Testcontainers tạo MySQL riêng, không dùng database demo. Không chạy E2E ghi dữ liệu ở chặng này.

## Đầu ra và giới hạn

Decision log `m05-p0-v1`, API/OpenAPI, fixture, script kiểm tra và ma trận nghiệm thu đã sẵn cho chặng 1. Chưa có claim/ledger/worker/context M05, chưa cộng XP, chưa có màn hình hoặc bảng giải đấu hoạt động; không lấy fixture như bằng chứng tính công bằng đã được thực thi.

Các thay đổi M05 chưa commit/push. Không sửa .env, restart demo hoặc xóa volume. Chặng tiếp theo phải kiểm tra DB mới/nâng V5, transaction context và unique constraints trước khi làm XP.
