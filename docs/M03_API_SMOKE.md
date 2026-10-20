# Thử API M03 — tạo lượt, nháp, hỗ trợ, nộp và chữa

Hướng dẫn chạy liền mạch bằng PowerShell với STUDENT và học liệu demo. Các phần dùng chung session và biến từ phần trước; chạy theo thứ tự. Đây là thao tác ghi dữ liệu thật vào môi trường local đã chọn. Kiểm thử tự động dùng DB riêng, xem các báo cáo ở cuối file.

Từ root, mở Docker Desktop rồi chạy `start.cmd` nếu cần build/chạy mã mới. UI/proxy mặc định ở 5173; Flyway áp dụng migration còn thiếu khi backend khởi động, không xóa volume để nâng schema. Hướng dẫn thao tác trên web xem [M03_UI_SMOKE](M03_UI_SMOKE.md).

## 1. Đăng nhập và chọn bài

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
if (-not $units.items) { throw 'Chưa có Unit công bố để thử.' }
$unitId = $units.items[0].id
$unit = Invoke-RestMethod "$base/api/units/$unitId" -WebSession $learningSession
if (-not $unit.lessons) { throw 'Unit chưa có bài công bố để thử.' }
$lessonId = $unit.lessons[0].id
$createJson = @{ requestId = [Guid]::NewGuid().ToString(); mode = 'LEARN'; lessonId = $lessonId } | ConvertTo-Json
$attempt = Invoke-RestMethod "$base/api/attempts" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $createJson
$attemptId = $attempt.attemptId
$attempt | Select-Object attemptId, mode, state, revision
$attempt.items | Select-Object attemptQuestionId, practiceRole, position
```

Lượt mới trả 201; cùng lựa chọn còn IN_PROGRESS trả lượt cũ 200, giữ bộ câu/permutation và không tăng exposure lại. Replay nguyên `$createJson` trả status/body đã commit với `Idempotent-Replayed: true`. TEACHER/ADMIN không dùng attempt/progress API.

<a id="draft"></a>

## 2. Lưu và khôi phục nháp

Chọn một SINGLE_CHOICE theo option ID thực trong snapshot. Gửi đủ mọi item, kể cả response=null cho câu chưa làm:

```powershell
$chosen = $attempt.items | Where-Object { $_.question.type -eq 'SINGLE_CHOICE' } | Select-Object -First 1
if (-not $chosen) { throw 'Chọn một bài demo có câu SINGLE_CHOICE để thử đoạn này.' }
$answers = @($attempt.items | ForEach-Object {
    $response = $_.response
    if ($_.attemptQuestionId -eq $chosen.attemptQuestionId) {
        $response = @{ type = 'SINGLE_CHOICE'; optionId = $chosen.question.content.options[0].id }
    }
    @{ attemptQuestionId = $_.attemptQuestionId; response = $response }
})
$saveJson = @{
    requestId = [Guid]::NewGuid().ToString()
    baseRevision = $attempt.revision
    answers = $answers
    playerState = $attempt.playerState
} | ConvertTo-Json -Depth 20
$ack = Invoke-RestMethod "$base/api/attempts/$attemptId/draft" -Method Put -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $saveJson
$ack
$attempt = Invoke-RestMethod "$base/api/attempts/$attemptId" -WebSession $learningSession
$attempt.items | Where-Object { $_.attemptQuestionId -eq $chosen.attemptQuestionId } | Select-Object attemptQuestionId, response
```

Kỳ vọng: revision tăng 1, ACK có savedAt/answeredCount, GET khôi phục đúng đáp án/playerState. Lưu nháp không chấm hoặc tạo trial. Snapshot không có answerKey/explanation/transcript/storageKey, kể cả sau nộp.

Replay **nguyên `$saveJson`** nhận ACK cũ, không tăng revision lần nữa. Sau thao tác mới hơn, replay vẫn trả revision cũ; phải GET đối soát, không dùng ACK cũ thay bản nhập mới.

<a id="feedback-submit"></a>

## 3. Hint và check GUIDED

Hàm dưới đây tạo đáp án đầy đủ về cấu trúc để thử cả ba dạng; không bảo đảm trả lời đúng. Không lấy answerKey từ API tác giả để dựng bài học viên.

```powershell
function New-LearningResponse($item) {
    $content = $item.question.content
    switch ($item.question.type) {
        'SINGLE_CHOICE' { return @{ type = 'SINGLE_CHOICE'; optionId = $content.options[0].id } }
        'FILL_BLANK' {
            $values = @($content.parts | Where-Object { $_.kind -eq 'BLANK' } | ForEach-Object {
                @{ blankId = $_.blankId; value = 'demo' }
            })
            return @{ type = 'FILL_BLANK'; values = $values }
        }
        'MATCHING' {
            $pairs = @(for ($i = 0; $i -lt $content.leftItems.Count; $i++) {
                @{ leftId = $content.leftItems[$i].id; rightId = $content.rightItems[$i].id }
            })
            return @{ type = 'MATCHING'; pairs = $pairs }
        }
    }
}

$attempt = Invoke-RestMethod "$base/api/attempts/$attemptId" -WebSession $learningSession
if ($attempt.state -ne 'IN_PROGRESS') { throw 'Hãy tạo một lượt LEARN mới trước khi thử.' }
$guided = @($attempt.items | Where-Object { $_.practiceRole -eq 'GUIDED' })
if (-not $guided.Count) { throw 'Lượt này không có câu GUIDED để thử hint/check.' }
$itemId = $guided[0].attemptQuestionId
$hintJson = @{ requestId = [Guid]::NewGuid().ToString(); baseRevision = $attempt.revision } | ConvertTo-Json
Invoke-RestMethod "$base/api/attempts/$attemptId/items/$itemId/hint" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $hintJson

foreach ($item in $guided) {
    $attempt = Invoke-RestMethod "$base/api/attempts/$attemptId" -WebSession $learningSession
    $checkJson = @{
        requestId = [Guid]::NewGuid().ToString()
        baseRevision = $attempt.revision
        response = New-LearningResponse $item
    } | ConvertTo-Json -Depth 20
    Invoke-RestMethod "$base/api/attempts/$attemptId/items/$($item.attemptQuestionId)/check" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $checkJson
}
```

Chỉ GUIDED đang làm được hint/check. Hint mở lần đầu tăng revision; mở lại bằng key mới và revision hiện tại không tăng thêm. Không có hint trả HINT_NOT_AVAILABLE. Check yêu cầu response đầy đủ, lưu answer và FIRST/GUIDED_RETRY, trả keys/explanation **không có transcript**. FIRST sau khi đã mở hint có supportUsed=true; check đầu tiên chưa dùng hint ghi false trước khi mở feedback.

## 4. Nộp, đọc kết quả và chữa

Submit gửi full answers/playerState mới nhất, không cần PUT draft trước. Ví dụ tiếp tục dùng hàm và session ở trên:

```powershell
$attempt = Invoke-RestMethod "$base/api/attempts/$attemptId" -WebSession $learningSession
$submitJson = @{
    requestId = [Guid]::NewGuid().ToString()
    baseRevision = $attempt.revision
    answers = @($attempt.items | ForEach-Object {
        @{ attemptQuestionId = $_.attemptQuestionId; response = New-LearningResponse $_ }
    })
    playerState = $attempt.playerState
} | ConvertTo-Json -Depth 20
$result = Invoke-RestMethod "$base/api/attempts/$attemptId/submit" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $submitJson
$result | Select-Object submissionId, submittedRevision, totalQuestions, answeredCount, correctCount, scorePercent, lessonCompleted

$stored = Invoke-RestMethod "$base/api/attempts/$attemptId/result" -WebSession $learningSession
$stored.items | Select-Object attemptQuestionId, firstOutcome, firstSupportUsed, finalOutcome
$stored.materialFeedback

$attempt = Invoke-RestMethod "$base/api/attempts/$attemptId" -WebSession $learningSession
$retryJson = @{
    requestId = [Guid]::NewGuid().ToString()
    baseRevision = $attempt.revision
    response = New-LearningResponse $attempt.items[0]
} | ConvertTo-Json -Depth 20
Invoke-RestMethod "$base/api/attempts/$attemptId/items/$($attempt.items[0].attemptQuestionId)/retry" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $retryJson
Invoke-RestMethod "$base/api/attempts/$attemptId/result" -WebSession $learningSession
```

Kỳ vọng: đúng một Submission, Result có ID thật/schema 1 và transcript sau nộp. LEARN hoàn thành khi mọi câu đầy đủ và mọi GUIDED đã CHECK; không đòi đúng hết. Được phép nộp sớm với null/partial nhưng không hoàn thành bài. FIRST giữ nguyên dù final khác; retry đầy đủ ghi CORRECTION và tăng revision, không thay final/điểm/tiến độ hoặc GET result đã lưu.

Replay **nguyên `$submitJson`** nhận cùng Result/Submission ID, kể cả sau khi chữa. Không tạo key mới để nộp lại chỉ vì mất phản hồi.

## 5. Danh sách, media, PRACTICE và tiến độ

```powershell
Invoke-RestMethod "$base/api/attempts?mode=LEARN&state=SUBMITTED&page=0&size=20" -WebSession $learningSession
Invoke-RestMethod "$base/api/me/learning-progress?unitId=$unitId&page=0&size=20" -WebSession $learningSession
```

Tiến độ gồm bài đang công bố chưa bắt đầu và lịch sử của chính học viên; bài nháp không xuất hiện. Học lại/nộp dở không hạ COMPLETED. Bài nghỉ có lịch sử riêng với currentPublishedVersionId=null. updateAvailable so bản đang công bố với bản hoàn thành gần nhất, hoặc bản lượt gần nhất nếu chưa hoàn thành; NOT_STARTED không có bản cũ để so. Phân trang theo Unit/thứ tự bài/ID, size tối đa 50.

Media lấy từ asset URL trong snapshot bằng session chủ Attempt; audio hỗ trợ Range. PlayerState nhận số giây tuyệt đối 0–86400; UI clamp theo đoạn nghe và thời lượng thực.

Tùy chọn tạo PRACTICE theo Topic của Unit đang chọn:

```powershell
$practiceJson = @{
    requestId = [Guid]::NewGuid().ToString()
    mode = 'PRACTICE'
    topicId = $unit.unit.topicId
    difficulty = 'EASY'
    count = 10
} | ConvertTo-Json
$practice = Invoke-RestMethod "$base/api/attempts" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $practiceJson
$practice | Select-Object attemptId, mode, state, revision
```

Thiếu câu phù hợp thì cấp số thực có, không lặp origin để đủ số; không có câu trả NO_QUESTIONS_AVAILABLE. PRACTICE/REVIEW không hoàn thành Lesson. REVIEW chưa nối provider M04 trả REVIEW_NOT_READY.

## 6. Đối soát và mã lỗi cần thử

| Tình huống | Kỳ vọng |
|---|---|
| Timeout/mất phản hồi/503 | Giữ nguyên requestId/payload; gửi lại rồi GET đối soát, chưa báo đã lưu |
| Cùng key/payload đã commit | Replay đúng status/body, header Idempotent-Replayed: true |
| Key cũ đổi payload/operation | 409 REQUEST_ID_REUSED |
| Thao tác mới với revision cũ | 409 REVISION_CONFLICT; giữ bản nhập để đối chiếu, không tự tăng revision gửi đè |
| Submit mới sau nộp | 409 ATTEMPT_FINISHED |
| GET result hoặc retry trước nộp | 409 RESULT_NOT_READY |
| Hint/check câu không phải GUIDED | 409 FEEDBACK_NOT_AVAILABLE |
| Hết phiên | 401, dừng ghi và đăng nhập lại |
| Lượt/item thuộc người khác | 404 theo hợp đồng riêng tư |

Thao tác mới dùng requestId mới và revision từ snapshot mới nhất; thao tác chưa rõ kết quả phải giữ nguyên key/payload cũ. Check/retry cần đáp án đầy đủ; draft/submit có thể chứa đáp án chưa đủ nhưng vẫn phải gửi đủ bộ item cho full snapshot.

## 7. Bằng chứng kiểm tra

Hướng dẫn PowerShell được kiểm tra cú pháp; lần gộp tài liệu không chạy các lệnh ghi dữ liệu vào demo. Không xem việc gửi lại request thủ công là bằng chứng đã thử mất gói TCP.

Kết quả tự động và giới hạn: [API lượt/nháp/media](../statics/results/module-3-step23-validation.md), [feedback/nộp/chữa/tiến độ](../statics/results/module-3-step4-validation.md), [giao diện và E2E](../statics/results/module-3-step5-validation.md). Payload chuẩn xem [OpenAPI](../source/common/openapi-m02-m03.json), hành vi xem [quy tắc API](../source/common/M02_M03_API.md).
