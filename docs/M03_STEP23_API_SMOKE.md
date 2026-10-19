# Thử API M03 bước 2–3

Phạm vi hiện có: tạo/tiếp tục lượt LEARN/PRACTICE, danh sách lượt, snapshot, quyền media và lưu/khôi phục nháp. REVIEW trả REVIEW_NOT_READY khi chưa nối M04. Hint/check/submit/result/retry và giao diện runner chưa có.

Backend đang chạy từ image cũ sẽ chưa có API mới. Từ thư mục gốc, mở Docker Desktop rồi chạy `start.cmd` để build/chạy mã hiện tại. Flyway sẽ áp dụng V4 nếu database chưa có; không xóa volume.

Các lệnh sau chạy trong PowerShell với demo STUDENT mặc định. Dùng môi trường local để thử vì PUT ghi nháp thật.

```powershell
$base = 'http://localhost:5173'
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -SessionVariable learningSession
$headers = @{}
$headers[$csrf.headerName] = $csrf.token
$login = @{ email = 'student@english.local'; password = 'Student@12345' } | ConvertTo-Json
Invoke-RestMethod "$base/api/auth/login" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $login
$csrf = Invoke-RestMethod "$base/api/auth/csrf" -WebSession $learningSession
$headers[$csrf.headerName] = $csrf.token

$units = Invoke-RestMethod "$base/api/units" -WebSession $learningSession
$unitId = $units.items[0].id
$unit = Invoke-RestMethod "$base/api/units/$unitId" -WebSession $learningSession
$lessonId = $unit.lessons[0].id
$create = @{ requestId = [Guid]::NewGuid().ToString(); mode = 'LEARN'; lessonId = $lessonId } | ConvertTo-Json
$attempt = Invoke-RestMethod "$base/api/attempts" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $create
$attemptId = $attempt.attemptId
$attempt | Select-Object attemptId, mode, state, revision
$attempt.items | Select-Object attemptQuestionId, practiceRole, position
```

Chọn một câu SINGLE_CHOICE và chọn theo option ID thực có trong snapshot. Lưu toàn bộ câu, kể cả câu chưa trả lời với response=null:

```powershell
$chosen = $attempt.items | Where-Object { $_.question.type -eq 'SINGLE_CHOICE' } | Select-Object -First 1
$answers = @($attempt.items | ForEach-Object {
    $response = $_.response
    if ($_.attemptQuestionId -eq $chosen.attemptQuestionId) {
        $response = @{ type = 'SINGLE_CHOICE'; optionId = $chosen.question.content.options[0].id }
    }
    @{ attemptQuestionId = $_.attemptQuestionId; response = $response }
})
$save = @{
    requestId = [Guid]::NewGuid().ToString()
    baseRevision = $attempt.revision
    answers = $answers
    playerState = $attempt.playerState
}
$saveJson = $save | ConvertTo-Json -Depth 20
$ack = Invoke-RestMethod "$base/api/attempts/$attemptId/draft" -Method Put -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $saveJson
$ack
$restored = Invoke-RestMethod "$base/api/attempts/$attemptId" -WebSession $learningSession
$restored.items | Where-Object { $_.attemptQuestionId -eq $chosen.attemptQuestionId } | Select-Object attemptQuestionId, response
```

Kết quả mong đợi: revision tăng 1, ACK có savedAt/answeredCount; GET khôi phục đúng đáp án và playerState. Lưu nháp không chấm bài hoặc tạo trial. Snapshot không có answerKey/explanation/transcript/storageKey.

Thử lại nguyên `$saveJson` bằng cùng lệnh PUT: nhận ACK cũ, revision không tăng lần hai, header `Idempotent-Replayed: true`. Nếu chưa có thao tác khác thì GET revision bằng ACK; khi đã có thao tác mới, replay vẫn trả ACK cũ nên phải GET lại để đối soát.

Để lưu lần tiếp theo, lấy revision từ snapshot mới nhất và sinh requestId mới cho thao tác mới. Không tự tăng baseRevision rồi gửi đè khi gặp 409. Cùng requestId nhưng đổi payload bị REQUEST_ID_REUSED; key mới với revision cũ bị REVISION_CONFLICT.

Tạo PRACTICE bằng POST `/api/attempts` với `{requestId,mode:"PRACTICE",topicId,difficulty:"EASY",count:10}`. Nếu chỉ có ít câu phù hợp, server cấp số thực có, không lặp origin cho đủ số. Cùng lựa chọn IN_PROGRESS trả lượt cũ 200; lượt mới trả 201.

Danh sách: GET `/api/attempts?mode=LEARN&state=IN_PROGRESS&page=0&size=20`. Media đọc qua URL `/api/assets/{id}` trong snapshot bằng session của chủ Attempt; AUDIO hỗ trợ Range. PlayerState lưu số giây tuyệt đối, giới hạn request 0–86400; player ở bước UI sẽ clamp theo thời lượng/đoạn nghe.

Kiểm thử tự động và giới hạn xem [nghiệm thu bước 2–3](../statics/results/module-3-step23-validation.md). Các kiểm tra đồng thời, rollback và khóa tài khoản chạy trên MySQL test riêng, không cần can thiệp database demo.
