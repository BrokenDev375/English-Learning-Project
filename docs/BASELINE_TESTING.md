# Test lại với dữ liệu nền main

**Sau tích hợp main ngày 29/10:** bản code mới chạy bằng project riêng `english-learning-integrated` tại **5176/8083**, với volume `english-learning-integrated_mysql_data` và `english-learning-integrated_asset_data`. Baseline cũ được dừng và giữ nguyên dữ liệu vì đã dùng V12 activities, khác V12 số vòng của main. Không chạy `start-baseline.cmd` hoặc `start-main.cmd` với source tích hợp vào các DB cũ này trước khi chuyển đổi lịch sử migration. Môi trường integrated dùng học liệu seed gốc, không tự nạp bài 18 câu hoặc tiến độ từ baseline. Các hướng dẫn baseline bên dưới là trạng thái trước tích hợp.

Sau khi đã tạo môi trường integrated, khởi động lại các container hiện có bằng `docker compose -p english-learning-integrated start`; kiểm tra bằng `docker compose -p english-learning-integrated ps`. Lệnh `start` giữ image/cấu hình cổng đã tạo; nếu cần build lại sau sửa source, phải đặt lại APP_PORT=5176, API_PORT=8083, WS_ALLOWED_ORIGINS tương ứng, BOOTSTRAP_ADMIN_ENABLED=true và DEMO_ENABLED=true trước lệnh `up -d --build --wait`.

Đã chạy và xác nhận ngày 29/10: build server/client đạt, MySQL/server healthy, UI HTTP 200, đăng nhập STUDENT và GET vòng học đạt (3 vòng). Flyway áp dụng đủ V1–V14 thành công, log seed hoàn tất; SQL chỉ đọc xác nhận 3 tài khoản, 3 Unit, 6 Lesson, 90 Question, 13 Material, 0 Attempt và 0 Submission. Không chạy E2E hoặc script nạp học liệu bổ sung trong lượt khởi động này. Tài liệu cập nhật local, chưa commit/push sau lần chạy.

Sau triển khai bài viết cùng ngày: integrated đã nâng **V14 → V15**, build lại server/client, UI và đăng nhập đạt. Backup trước nâng: `.test-state/unit-writing/before-v15.sql` (bị Git ignore). Đối chiếu ngay trước/sau nâng giữ **3 tài khoản, 1 Attempt, 0 Submission M03, 1 lesson_progress**; lượt test mới của người dùng đã được giữ nguyên. Seed báo preservedLessons=6, không tạo/đổi học liệu; chưa tạo đề viết, Work hoặc điểm chấm giả trong DB demo. V15 là bảng bổ sung riêng; hướng dẫn tại [bài viết cuối vòng](UNIT_WRITING.md).

**Cập nhật sau yêu cầu demo linh hoạt:** baseline tại 5176/8083 đã được nâng bài đầu Vòng 1, Lesson 1 “A day with Linh — Morning routine”, thành LessonVersion 7 gồm 18 câu (6 trắc nghiệm, 4 ghép cặp, 6 điền từ, 2 nghe), 4 GUIDED + 14 INDEPENDENT. Dùng 10 câu LEARN và audio nền, bổ sung 8 câu, giữ 5 REVIEW_VARIANT; các bài khác giữ dữ liệu ban đầu. Lượt đã tạo trước khi nâng vẫn dùng phiên bản đã pin. DB được sao lưu trước nâng ở `.test-state/baseline-flex/before-upgrade-complete-20261029-003348.sql`; volume demo cũ không bị sửa. Script `node scripts/upgrade-beginner-lesson.mjs --apply` dùng checkpoint `.test-state/baseline-flex/30c6c63af7801a90.json`, chạy lại trên cùng DB trả cùng phiên bản. Không xóa checkpoint hoặc dùng checkpoint của origin này cho DB khác.

Ngày chuyển môi trường: 29/10/2026, theo yêu cầu chỉ giữ bộ dữ liệu nền để test lại.

Chạy từ thư mục gốc:

```powershell
.\start-baseline.cmd
```

Ứng dụng dùng mã nguồn hiện tại trong working tree, không checkout/reset mã về commit cũ. Bộ seed `source/server/src/main/resources/demo/` không khác `main` tại `fac44f4`: ban đầu có 3 Unit, 6 Lesson, 90 Question, 13 Material. Khi tạo môi trường mới có các tài khoản admin/teacher/student theo `.env`, chưa có lượt làm, Submission, tiến độ hoặc XP do script học sinh giả lập tạo. Sau đó đã chủ động nâng một bài đầu như ghi ở trên; không nạp lớp học bổ sung.

UI: http://localhost:5176; API: http://localhost:8083. Cần đăng nhập lại sau khi đổi môi trường.

Compose project mới `english-learning-baseline`, volume riêng `english-learning-baseline_mysql_data` và `english-learning-baseline_asset_data`. Script dừng project demo cũ để nhường cổng nhưng giữ container/volume `english-learning-main`. Chạy lại `start-baseline.cmd` giữ các thao tác test đã lưu; không reset dữ liệu mỗi lần khởi động.

Để quay lại demo cũ:

```powershell
docker compose -p english-learning-baseline stop
.\start-main.cmd
```

Không xóa volume và không xóa checkpoint `.test-state/activity-sample/` hoặc `.test-state/demo-classroom/`. Chúng thuộc dữ liệu demo cũ. Nếu muốn nạp dữ liệu bổ sung vào baseline sau này phải dùng checkpoint đúng môi trường; không tự chạy seed bổ sung chỉ để kiểm tra baseline.

## Xác nhận sau khi chạy

- `start-baseline.ps1` build/khởi động thành công, health và log hoàn tất seed đạt.
- SQL chỉ đọc xác nhận: 3 tài khoản, 3 Unit, 6 Lesson, 90 Question, 13 Material; 0 Attempt, 0 Submission, 0 bản ghi XP ledger.
- UI trả HTTP 200; đăng nhập học viên demo và GET `/api/units` thành công, trả đúng 3 Unit. Không tạo lượt làm trong kiểm tra.
- Không chạy lại E2E vì bộ E2E sẽ tạo dữ liệu làm mất trạng thái ban đầu của môi trường này. Các kiểm tra hồi quy source trước đó vẫn nằm trong biên bản riêng.
