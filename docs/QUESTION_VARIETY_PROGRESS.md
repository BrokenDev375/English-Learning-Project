# Bài 18 câu và hai hoạt động đọc

Ngày triển khai: **28/10/2026**. Nền `main` tại `fac44f4`; thay đổi hiện ở working tree, chưa commit. Quyết định sản phẩm tại [kế hoạch](QUESTION_VARIETY_IMPLEMENTATION_PLAN.md).

## Điều chỉnh 29/10: dạng và số câu linh hoạt

Bài có metadata activities được chọn 1–7 dạng theo thứ tự hiện hành, bỏ qua dạng chưa cần; mỗi dạng có ít nhất hai câu, một GUIDED mở đầu và các câu còn lại INDEPENDENT. Tổng 15–20 câu LEARN; không đổi bài không có activities và vẫn chấp nhận hai mẫu sáu/bảy phần 18 câu cũ. Giao diện biên soạn cho thêm/bỏ từng dạng, chọn số câu và phân bổ từ ngân hàng. READING nếu có vẫn tách ví dụ và đoạn chính; LISTENING_CHOICE cần AUDIO và 3–6 lựa chọn, tương thích câu nghe nền ba lựa chọn. Snapshot pin câu/phiên bản khi tạo lượt như trước.

Bài vòng đầu trên baseline 5176 đã được nâng thành 18 câu gồm 6 trắc nghiệm, 4 ghép cặp, 6 điền từ và 2 nghe; tái sử dụng 10 câu có sẵn và audio nền, bổ sung 8 câu cùng chủ đề, giữ 5 câu REVIEW_VARIANT. Script `scripts/upgrade-beginner-lesson.mjs` tạo Question mới và phiên bản bài qua API có receipt/checkpoint; không tạo Unit hay lượt học. Thao tác trên baseline được ghi riêng trong [hướng dẫn test](BASELINE_TESTING.md) và [biên bản](../statics/results/question-variety-validation.md).

Các phần bên dưới ghi lại đợt sáu/bảy hoạt động ban đầu để đối chiếu lịch sử.

## Hành vi đã triển khai

- Bài dùng cấu trúc mới có **18 câu LEARN**, 7 GUIDED + 11 INDEPENDENT. Bảy phần: **trắc nghiệm (3) → ghép cặp (2) → xếp từ (2) → điền từ (3) → đọc điền từ (3) → đọc hiểu (3) → nghe chọn đáp án (2)**.
- Mỗi hoạt động bắt đầu bằng một GUIDED, các câu sau tự làm; hint/check vẫn tùy chọn. Bộ chọn lưu đúng thứ tự này khi tạo Attempt; resume không sắp lại lượt cũ.
- Hai hoạt động đọc đều có một đoạn ví dụ riêng cho GUIDED, hai câu INDEPENDENT dùng chung đoạn chính. Câu đọc hiểu có đúng bốn lựa chọn.
- Đọc điền từ nhập ngay trong đoạn: một ô ví dụ và hai ô trong đoạn chính. Mỗi ô ánh xạ tới item/blank ID riêng, dùng autosave, revision, receipt và conflict M06 hiện có.
- Runner có bảy lối chọn hoạt động, điều hướng từng câu/nhóm; kết quả và chữa bài dùng cùng vị trí ô trống. Đọc hiểu có bố cục hai cột trên desktop và vùng đoạn đọc cuộn được trên điện thoại.
- Đoạn đọc hiểu có thể thu gọn/mở lại; preview hiển thị số câu thực tế đang biên soạn và chỉ trình bày một lần đoạn chính dùng chung.
- Trình biên soạn Lesson có cấu trúc bảy hoạt động, chọn câu hướng dẫn/tự làm, hiển thị tên ví dụ/đoạn chính và nút tạo ánh xạ ô. Preview có thứ tự hoạt động và ô nhập; xuất bản kiểm tra số câu, role, kiểu đáp án, liên kết phiên bản, văn bản và vị trí trống.
- Bài thông thường không có activities tiếp tục dùng hợp đồng cũ. Không sửa điểm/FIRST/Submission, luật REVIEW, XP, League hoặc điều kiện hoàn thành vòng. Chưa triển khai bài viết.

Hai câu nghe cuối dùng audio thật `daily-routine.wav` sẵn có trong dự án, một GUIDED rồi một INDEPENDENT. Bớt một câu tự làm ở mỗi phần đọc; các ví dụ đọc vẫn giữ nguyên. Backend vẫn chấp nhận cấu trúc sáu phần cũ, lượt đã tạo không đổi. Bộ mẫu tăng lên `activities-1.1.0`, checkpoint tách theo phiên bản, nên chạy seed phiên bản mới tạo bài riêng; không tự nâng Lesson 11 trên demo.

## Dữ liệu và tương thích

**V12** thêm cột JSON nullable `lesson_versions.activities_json`. Không sửa V1–V11, không viết lại LessonVersion hoặc Attempt cũ. Bản nháp mới kế thừa activities của phiên bản gốc. Cập nhật audio hàng loạt giữ cấu trúc và remap QuestionVersion ID khi tạo phiên bản mới.

Metadata tác giả chứa nhóm, danh sách QuestionVersion và các phần TEXT/BLANK của đoạn cloze. Các phần ghép lại bằng `____` phải khớp `readingText` của MaterialVersion. Không nhúng đáp án vào metadata này. Snapshot chỉ chiếu các item và material thuộc lượt, thay tham chiếu bằng attemptQuestionId. Với REVIEW/PRACTICE chọn một câu, những ô không thuộc lượt trở thành văn bản `____`, không sinh thêm câu hay đòi thêm đáp án.

API biên soạn chấp nhận `activities` tùy chọn. Payload bài cũ không tự thêm trường rỗng vào dữ liệu tính receipt hash, giữ replay các lệnh cũ. Snapshot bài cũ không có trường activities rỗng. GET giới thiệu bài mới chỉ thêm `activitySummary` gồm loại hoạt động và số câu, không trả nội dung/khóa/ID học liệu.

## Tạo bài mẫu

Đợt đầu cung cấp **một bài mẫu riêng**, không tự nâng toàn bộ ngân hàng. Có 18 câu học, 4 REVIEW_VARIANT và 4 material đọc + 1 AUDIO; các đoạn chính khoảng 150–300 từ. Bài được tạo bằng API tác giả và xuất bản toàn bộ phụ thuộc qua luồng chuẩn.

Xem kế hoạch thao tác, chưa ghi dữ liệu:

```powershell
node scripts/seed-activity-sample.mjs
```

Áp dụng vào một ứng dụng local đã chạy source mới (ví dụ môi trường test riêng tại 5174):

```powershell
$env:SAMPLE_BASE_URL = 'http://localhost:5174'
node scripts/seed-activity-sample.mjs --apply
```

Script dùng `SAMPLE_AUTHOR_EMAIL`/`SAMPLE_AUTHOR_PASSWORD` nếu được cấp trong môi trường; nếu thiếu thì đọc cấu hình bootstrap admin local từ `.env` trong bộ nhớ. Không in hoặc lưu mật khẩu/cookie. Checkpoint riêng theo origin, actor và datasetVersion dưới `.test-state/activity-sample/`, giữ nguyên requestId/payload trước khi gửi và không ghi đè bài đã tồn tại khi chạy lại. Nếu có lệnh chưa rõ kết quả, chạy lại cùng cấu hình để replay. Không xóa checkpoint để xử lý lỗi chưa đối soát; có thể tạo bản sao ngoài ý muốn.

Script tạo Unit mới và tuân thủ khóa vòng hiện có. Học viên hoàn thành các vòng trước để vào Unit này; không có cơ chế vượt khóa.

Theo yêu cầu chạy code tiếp theo của người dùng ngày 28/10, đã chạy `scripts/start-main.ps1`, build cả server/client và nâng DB demo chính từ V11 lên **V12**. UI **http://localhost:5176**, API **http://localhost:8083**. Đã nạp bài mẫu qua script: **Unit 6, Vòng 2 — Everyday adventures, Lesson 11 “A day out — Sáu thử thách”, LessonVersion 11**. Checkpoint demo: `.test-state/activity-sample/0ae6f571866778a9.json`.

Đã sao lưu DB trước khi nâng tại `.test-state/main-activities-run/before-v12-20261028T162150Z.sql`; xác nhận sau chạy vẫn có **9 tài khoản, 58 Attempt, 47 Submission**, không tạo lượt học giả để mở khóa. Frontend/API healthy và API xác nhận đúng 6 GUIDED + 12 INDEPENDENT + 4 câu ôn riêng. Người đang mở giao diện cũ cần tải lại, có thể phải đăng nhập lại do backend restart.

## Kiểm tra và giới hạn

Lượt kiểm tra sau thêm nghe: **15/15 backend tập trung, 5/5 browser, build TypeScript/Vite PASS**; không chạy lại toàn bộ hồi quy. Demo vẫn bản sáu phần cũ.

Bằng chứng kiểm tra được cập nhật tại [biên bản bài 18 câu](../statics/results/question-variety-validation.md). Không dùng số PASS của các mốc trước làm kết quả cho thay đổi mới.

Bằng chứng lịch sử trước khi thêm phần nghe: đã có **320 ca backend và 119 ca browser khác nhau qua các lượt**, không phải hai lượt đầy đủ toàn PASS. Lượt đầy đủ backend 309/320 rồi hai nhóm Docker 20/20 xử lý môi trường ffprobe/kỳ vọng V12; browser đầy đủ 118/119, nhóm liên quan 39/40 rồi sáu ca cuối 6/6. Build TypeScript/Vite và image cuối đạt, manager 9/9, IndexedDB/journal 6/6. Recreate server giữ nguyên snapshot nháp, Result và replay receipt; script bài mẫu chạy hai lần trả cùng ID. Chi tiết nguyên nhân, thời gian và giới hạn nằm trong biên bản.

- Việc biên soạn nội dung vẫn cần rà soát bằng người, đặc biệt để hint/giải thích ví dụ không vô tình gợi đáp án phần tự làm.
- Lưu/khôi phục theo ID câu; chưa phục hồi vị trí cuộn chính xác của đoạn đọc.
- Cơ chế nhóm chỉ được bật cho bài có metadata mới hợp lệ. Các bài cũ không tự được phân loại lại theo material hoặc prompt.
- Chưa mở rộng tất cả bài của ngân hàng, chưa thêm bài viết hoặc đổi chính sách thưởng.
