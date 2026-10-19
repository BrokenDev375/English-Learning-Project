# Nghiệm thu M03 bước 0 — nền tích hợp

Ngày chạy: 19/10/2026. Windows, Docker Desktop, Maven/Java 17 trong image test, MySQL 8.4 qua Testcontainers. Baseline mã nguồn `a023088`, nhánh `feature/m03-learning-attempts`.

## Thay đổi

- Thêm overload receipt nhận status/body do action chọn trong transaction, chuẩn bị cho create Attempt 201/200. Cách gọi status cố định của M02 được giữ.
- Replay không chạy lại action; trả nguyên status/body đã commit. Khác operation/payload dùng cùng key bị từ chối.
- Có test hai request đồng thời, rollback sau khi ghi business data/receipt và response 204/null của API cũ.
- Có [đối chiếu hợp đồng](../../docs/M03_CONTRACT_REVIEW.md), fixture dương/âm và script kiểm tra bằng JSON Schema.

## Kết quả thực tế

| Kiểm tra | Kết quả |
|---|---|
| Backend baseline trước sửa receipt | 70/70 PASS; 0 failure/error/skipped |
| Frontend baseline Docker build | PASS; các layer build TypeScript/Vite hợp lệ được tái sử dụng từ cache |
| Fixture M03/OpenAPI, jsonschema 4.26.0 | 21/21 PASS: 16 mẫu hợp lệ, 5 mẫu âm bị từ chối đúng |
| Hồi quy backend M01/M02 sau sửa receipt | 70/70 PASS; 0 failure/error/skipped trong các nhóm cũ |
| RequestReceiptServiceTest sau sửa cấu hình web test | 5/5 PASS; 0 failure/error/skipped |

Lần chạy tổng hợp đầu có 5 lỗi khởi tạo context của test receipt mới vì môi trường MOCK thiếu WebSocket ServerContainer; 70 test cũ đều đạt. Đã sửa test mới sang RANDOM_PORT và chạy lại riêng, 5/5 đạt. Không gọi lần chạy tổng hợp đầu là BUILD SUCCESS; mã ứng dụng receipt không đổi giữa hai lần này.

## Tái hiện

Tại thư mục gốc, Docker Desktop đang chạy:

```powershell
docker build --target test -t english-learning-m03-step0-test ./source/server
docker run --rm -v /var/run/docker.sock:/var/run/docker.sock -e TESTCONTAINERS_HOST_OVERRIDE=host.docker.internal english-learning-m03-step0-test mvn -B -ntp test
docker build -t english-learning-m03-baseline-client ./source/client
```

Lượt kiểm tra sau sửa dùng cùng image Maven có dependencies và bind mount `source/server/src` read-only vào `/app/src`, để compile lại đúng mã nguồn hiện tại. Lượt chạy lại dùng `mvn -B -ntp -Dtest=RequestReceiptServiceTest test`. Testcontainers tạo database/container riêng; không dùng DB hoặc volume demo. Log local ignored: `.test-state/m03-baseline.log`, `m03-step0.log`, `m03-step0-receipts.log`, `m03-baseline-client.log`.

Kiểm tra fixture theo lệnh trong [đối chiếu hợp đồng](../../docs/M03_CONTRACT_REVIEW.md). Môi trường Python và thư viện kiểm chứng nằm dưới `.test-state`, không bổ sung dependency ứng dụng.

## Giới hạn

Chưa có endpoint Attempt, V4 hoặc UI làm bài ở bước 0. Chưa chạy lại Playwright vì bước này chỉ đổi receipt backend; hồi quy backend bao gồm các API M01/M02. Chưa có đo tải hoặc thử nghiệm hiệu quả học tập.
