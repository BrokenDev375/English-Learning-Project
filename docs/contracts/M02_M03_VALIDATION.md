# Kiểm tra đặc tả M02–M03 — 16/10/2026

Phạm vi: tài liệu và DDL **dự kiến**. Chưa triển khai controller/service/JPA/UI M02–M03; chưa áp dụng migration vào database dự án.

## DDL

- Dùng container MySQL 8.4.9 riêng, network=none, không publish port và không mount volume dự án.
- Chạy V1, V2 hiện có rồi V3/V4 dự kiến: thành công, 24 bảng ứng dụng (không tính Flyway history vì kiểm tra DDL trực tiếp).
- Fixture quan hệ đi qua đủ 20 bảng mới: thành công. Fixture này kiểm tra SQL/FK, không thay thế validator nghiệp vụ publish.
- Tám trường hợp bị từ chối đúng ràng buộc: con trỏ phiên bản sai root; Submission trùng Attempt; receipt trùng actor/requestId; question type P1; Attempt state không hỗ trợ; loại FIRST trial sai; asset quá 5 MiB; LessonQuestion có FK không tồn tại.
- Đã xóa riêng container kiểm tra và anonymous volume của nó sau kiểm tra; không thay đổi database/volume đang chạy.

## JSON

- Parse OpenAPI/ví dụ thành công.
- 45 path, 59 operation ID riêng biệt, 76 schema.
- 593 local reference phân giải được; path parameters được khai báo.
- 23 request/response mẫu khớp các keyword schema đang sử dụng: type, required, const, enum, oneOf/anyOf, additionalProperties, array/items, uniqueItems, pattern, format, giới hạn số/độ dài.
- Hai mẫu âm: thêm answerKey vào PublicQuestion, bỏ baseRevision của lưu nháp — bị từ chối.

Đây là kiểm tra tĩnh bằng script nhỏ và MySQL thực. Chưa chứng nhận toàn bộ chuẩn OpenAPI bằng validator bên ngoài; chưa test runtime quyền, concurrent submit, mất ACK, file Range, seed hoặc Hibernate validate. Những mục này là điều kiện nghiệm thu code trong [bàn giao](../M02_M03_HANDOFF.md).

