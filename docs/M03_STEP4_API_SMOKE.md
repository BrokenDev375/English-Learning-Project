# Thử API M03 bước 4

Chạy backend từ mã nguồn hiện tại bằng `start.cmd`, rồi thực hiện phần đăng nhập/tạo lượt trong [hướng dẫn bước 2–3](M03_STEP23_API_SMOKE.md). Các biến `$base`, `$learningSession`, `$headers`, `$attemptId` được dùng tiếp bên dưới. Đây là thao tác ghi lượt thật trên môi trường local; kiểm thử tự động dùng database riêng.

Các lệnh minh họa hoàn thành cấu trúc câu trả lời, không bảo đảm trả lời đúng. Không lấy answerKey từ API tác giả để dựng bài học viên.

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
$guided = @($attempt.items | Where-Object { $_.practiceRole -eq 'GUIDED' })
if ($attempt.state -ne 'IN_PROGRESS') { throw 'Hay tao mot luot LEARN moi truoc khi thu.' }

# Mở hint; lần mở đầu tăng revision, mở lại bằng key mới không tăng thêm.
$itemId = $guided[0].attemptQuestionId
$hintJson = @{ requestId = [Guid]::NewGuid().ToString(); baseRevision = $attempt.revision } | ConvertTo-Json
$hint = Invoke-RestMethod "$base/api/attempts/$attemptId/items/$itemId/hint" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $hintJson
$hint

# Kiểm tra từng GUIDED. Mỗi thao tác dùng revision mới nhất.
foreach ($item in $guided) {
    $attempt = Invoke-RestMethod "$base/api/attempts/$attemptId" -WebSession $learningSession
    $checkJson = @{
        requestId = [Guid]::NewGuid().ToString()
        baseRevision = $attempt.revision
        response = New-LearningResponse $item
    } | ConvertTo-Json -Depth 20
    Invoke-RestMethod "$base/api/attempts/$attemptId/items/$($item.attemptQuestionId)/check" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $checkJson
}

# Submit luôn gửi đủ bộ câu và playerState; không cần PUT draft trước.
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

# Chữa sau nộp: tăng revision, thêm CORRECTION, giữ nguyên điểm và kết quả nộp.
$attempt = Invoke-RestMethod "$base/api/attempts/$attemptId" -WebSession $learningSession
$retryJson = @{
    requestId = [Guid]::NewGuid().ToString()
    baseRevision = $attempt.revision
    response = New-LearningResponse $attempt.items[0]
} | ConvertTo-Json -Depth 20
Invoke-RestMethod "$base/api/attempts/$attemptId/items/$($attempt.items[0].attemptQuestionId)/retry" -Method Post -WebSession $learningSession -Headers $headers -ContentType 'application/json' -Body $retryJson
Invoke-RestMethod "$base/api/attempts/$attemptId/result" -WebSession $learningSession
Invoke-RestMethod "$base/api/me/learning-progress?unitId=$unitId&page=0&size=20" -WebSession $learningSession
```

Kỳ vọng: check trả answerKey/explanation nhưng không có transcript; result sau nộp có transcript. Bài LEARN hoàn thành khi mọi câu trả lời đủ và mọi GUIDED đã CHECK; không bắt buộc đúng hết. Có thể nộp sớm với response=null hoặc partial, khi đó bài chưa hoàn thành. PRACTICE/REVIEW không hoàn thành Lesson.

Gửi lại **nguyên `$submitJson`** nhận cùng Result/Submission ID và header `Idempotent-Replayed: true`, kể cả sau khi chữa. Khi timeout phải giữ nguyên key/payload, đối soát bằng GET; không tạo key mới để nộp lại. Key cũ đổi payload trả REQUEST_ID_REUSED; thao tác mới dùng revision cũ trả REVISION_CONFLICT; submit mới sau nộp trả ATTEMPT_FINISHED. GET result trước nộp và retry trước nộp trả RESULT_NOT_READY.

Tiến độ gồm bài đang công bố chưa bắt đầu và lịch sử của chính người học; bài nháp riêng không xuất hiện. Bài đã nghỉ chỉ còn trong tiến độ của người có lịch sử, currentPublishedVersionId=null. updateAvailable so bản đang công bố với bản hoàn thành gần nhất; nếu chưa hoàn thành, so với bản của lượt gần nhất. NOT_STARTED không có bản cũ để so. Phân trang theo Unit, thứ tự bài và ID, size tối đa 50.

Giao diện runner thuộc bước 5. Hướng dẫn này được rà soát theo hợp đồng API; bằng chứng chạy tự động xem [nghiệm thu bước 4](../statics/results/module-3-step4-validation.md).
