# Kiểm tra backend sau khi tách interface/implementation

Ngày chạy: **13/10/2026**. Kiểm tra từng snapshot nhánh bằng Maven với MySQL Testcontainers riêng, chạy lần lượt.

| Nhánh | Java class/interface | Kiểm thử API | Kết quả |
|---|---:|---:|---|
| main — module 1 | 34 | 12 | PASS, không lỗi/skip |
| feature/speaking | 68 | 20 (12 + 8) | PASS, không lỗi/skip |
| feature/writing | 69 | 23 (12 + 11) | PASS, không lỗi/skip |

Tổng 55 lượt kiểm thử qua; có các kiểm thử module 1 chạy lại trên nhánh kỹ năng. Bộ test xác nhận Spring inject interface đúng implementation, transaction/khóa JPA và hành vi HTTP sau đổi package: tài khoản/CSRF/quyền/lớp, nháp/xuất bản/phiên bản/giao bài, chống trùng/đồng thời, audio và hình bảo vệ.

AuthService/ClassroomService/LearningService/AssetService và UserRepository/ClassroomRepository/LearningRepository là interface; impl giữ logic và annotation. Các *JpaRepository tiếp tục do Spring Data triển khai. DTO, entity, migration, schema và API giữ nguyên. Helper xác thực/kiểm tra vai trò dùng chung ở security; content handler ở service.impl.

Frontend không đổi trong lần tách tầng. Các kết quả browser/persistence trước đó giữ trong báo cáo module; lần này kiểm tra API/backend là trọng tâm, không chạy lại browser suite. Chưa có benchmark hoặc đánh giá hiệu quả học tập.

Tái lập: tại source/server của từng nhánh, chạy .\mvnw.cmd test khi Docker engine sẵn sàng. Database/port Testcontainers tạm độc lập với database ứng dụng.
