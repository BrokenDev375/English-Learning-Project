# Test lại với dữ liệu nền main

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
