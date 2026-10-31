# Dữ liệu câu hỏi A1–A2 để chia sẻ và demo

File [demo-questions-a1-a2.json](../statics/dataset/demo-questions-a1-a2.json) chứa nội dung tự biên soạn có hỗ trợ AI, đáp án và giải thích tiếng Việt. Không lấy dữ liệu người dùng hoặc xuất DB cá nhân vào Git.

| Chủ đề | Mức dự kiến | Bài học | Kho bộ đề cuộc thi |
|---|---|---|---|
| A school day | A1 | 20 câu học + 4 câu ôn | 20 câu |
| At the weekend market | A1–A2 | 20 câu học + 4 câu ôn | 20 câu |
| A weekend in Green Town | A2 | 20 câu học + 4 câu ôn | 20 câu |

Mỗi bài học có 4 GUIDED, 16 INDEPENDENT và 4 REVIEW_VARIANT. Mỗi dạng SINGLE_CHOICE, FILL_BLANK, MATCHING, ORDERING có 5 câu học và 1 câu ôn. Có 3 bài đọc đi kèm; không thêm file nghe/ảnh. Mức A1–A2 là mục tiêu biên soạn, chưa phải phân loại được khảo sát chuẩn hóa.

Kho cuộc thi dùng lại 20 câu chính của từng chủ đề để thuận tiện demo. Đó là bản dữ liệu riêng trong bộ đề, không tham chiếu QuestionVersion của bài học. Người đã học các bài này có thể biết trước nội dung; cần bộ khác nếu tổ chức thi đánh giá thực tế.

## Chạy trên máy người nhận

Cần Git, Docker Desktop đang chạy và Node.js 24. Chạy từ thư mục gốc repository đã lấy commit chứa bộ dữ liệu này. Khởi tạo `.env` riêng trên máy nhận:

```powershell
powershell -ExecutionPolicy Bypass -File scripts/init-env.ps1
```

Để không đụng DB cũ, dùng project demo riêng. Trong cùng một cửa sổ PowerShell:

```powershell
$env:APP_PORT='15343'
$env:API_PORT='18143'
$env:WS_ALLOWED_ORIGINS='http://localhost:15343,http://127.0.0.1:15343'
$env:BOOTSTRAP_ADMIN_ENABLED='true'
$env:DEMO_ENABLED='true'
docker compose -p english-learning-demo-questions up -d --build --wait
```

Chỉ chạy lệnh nạp sau khi Compose thành công:

```powershell
node scripts/seed-demo-questions.mjs --base-url http://localhost:15343 --apply
```

Script đọc ADMIN từ `BOOTSTRAP_ADMIN_EMAIL`/`BOOTSTRAP_ADMIN_PASSWORD` trong `.env` của máy nhận. Nếu DB dùng tài khoản quản trị khác, đặt `DEMO_SEED_EMAIL` và `DEMO_SEED_PASSWORD` trong môi trường trước khi chạy; không ghi mật khẩu vào file được commit. Bootstrap không đổi mật khẩu của tài khoản đã tồn tại.

Mở **http://localhost:15343**:

- ADMIN → Quản lý học liệu: có 3 Unit mới mang nhãn `[Demo]`, mỗi Unit một bài đã công bố cùng 24 câu và một học liệu đọc. ADMIN là người sở hữu nội dung do script tạo.
- ADMIN → Cuộc thi → Kho bộ đề: có 3 bộ đề **READY**, mỗi bộ 20 câu. Tạo cuộc thi và chọn một bộ đề; tự đặt thời gian theo buổi demo. Script không tạo lịch thi cố định vì lịch có thể hết hạn khi người nhận chạy sau.
- STUDENT: các bài học mới vẫn mở theo thứ tự vòng hiện hành, sau các vòng đã có. Có thể xem ngay bằng preview ADMIN; muốn học thật phải hoàn thành các vòng trước. Script không tạo sẵn điểm, XP, lịch sử học hoặc bỏ qua khóa vòng.
- Muốn giáo viên trông thi: ADMIN cấp quyền Giám sát và chọn giáo viên trong cuộc thi. Script không thay quyền giáo viên hiện có.

Tài khoản STUDENT/TEACHER dùng cấu hình demo riêng trong `.env`; ADMIN cũng xem trong `.env`. Không chia sẻ `.env` của máy người tạo dữ liệu.

Nếu ứng dụng đã chạy ở một origin local khác và DB đúng chuỗi migration V1–V17, dùng origin đó cho `--base-url`. Không khởi động source mới vào các volume demo/baseline cũ có V12 activities khác lịch sử migration của main; xem [BASELINE_TESTING](BASELINE_TESTING.md).

## Chỉ kiểm tra hoặc nạp riêng một phần

Lệnh mặc định chỉ kiểm tra JSON và in số câu, không đăng nhập hoặc gọi API:

```powershell
node scripts/seed-demo-questions.mjs
node scripts/seed-demo-questions.mjs --target learning --base-url http://localhost:15343 --apply
node scripts/seed-demo-questions.mjs --target contests --base-url http://localhost:15343 --apply
```

`--target all` là mặc định. Có thể nạp một phần trước rồi chạy `all` sau, không tạo lại phần đã hoàn thành. Muốn dùng file khác, thêm `--file duong-dan.json`; giữ đúng schema của file mẫu và đổi `datasetVersion` khi tạo bộ dữ liệu mới.

## Chạy lại và xử lý gián đoạn

Script giữ checkpoint trong `.test-state/demo-questions/`, tách theo origin, ADMIN và `datasetVersion`. Trước mỗi mutation, lưu requestId và payload chính xác; khi mất phản hồi, chạy lại **cùng lệnh, cùng file, cùng origin và ADMIN** để replay request cũ. Những bước đã hoàn thành được bỏ qua, sau đó GET đối chiếu bài đã công bố/bộ đề READY. Nếu tài nguyên bị sửa hoặc lưu trữ sau đó, script dừng để kiểm tra, không ghi đè.

Giữ checkpoint cùng DB của máy đó. Không đưa checkpoint lên Git, không sao chép checkpoint sang DB khác, không xóa checkpoint rồi nạp lại cùng bộ. Nếu DB đã reset, phải dùng checkpoint riêng cho DB mới; file cũ chỉ còn dùng để đối chiếu. `localhost` và `127.0.0.1` là hai origin khác nhau, hãy dùng nhất quán một địa chỉ.

Mỗi scope có một file `.lock` để tránh hai lần nạp đồng thời. Nếu tiến trình bị tắt cưỡng bức, xác minh nó đã dừng trước khi xóa đúng `.lock`; giữ JSON checkpoint. Kết quả ID Unit/Lesson/Paper được ghi vào file report trong cùng thư mục local.

Nạp diễn ra qua nhiều transaction API. Lỗi giữa chừng có thể để lại nội dung đã tạo; chạy lại để tiếp tục. Không có thao tác truncate, sửa migration hoặc INSERT SQL trực tiếp. Bài/câu/lượt học có sẵn được giữ nguyên.

## Kiểm tra script

```powershell
node --test --test-isolation=none scripts/seed-demo-questions.test.mjs
```

Kiểm tra dữ liệu/đáp án bốn dạng, nạp đầy đủ, chạy lại không ghi thêm, mất ACK sau publish và replay đúng request cũ, nạp từng phần, quyền ADMIN, cookie/CSRF và chặn origin ngoài local. Kiểm tra API thật trên Docker được ghi tại [biên bản](../statics/results/demo-question-data-validation.md).

## Đưa lên Git

Chia sẻ JSON, script, test và tài liệu. Người nhận tự tạo `.env`, DB và checkpoint trên máy mình. Không commit `.env`, backup SQL, volume, checkpoint, log hoặc các báo cáo Word đã sinh trong `report/`.
