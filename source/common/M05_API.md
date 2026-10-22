# Hợp đồng M05 — Gamification và Giải đấu tuần

Phiên bản 1.0.0, ngày 21/10/2026. Policy `m05-p0-v1` theo [quyết định P0](../../docs/M05_DECISIONS.md). **Đã triển khai cả 8 endpoint, subscription GAMIFICATION/LEAGUE, worker và chốt tuần.** Schema: [openapi-m05.json](openapi-m05.json). Phạm vi kiểm tra và giới hạn: [tiến độ](../../docs/M05_PROGRESS.md).

## 1. Chung

- Chỉ STUDENT. Cookie JSESSIONID + CSRF theo M01. Danh tính từ principal, không nhận userId để thao tác thay người khác.
- Mọi response nghiệp vụ M05 đặt `Cache-Control: no-store, private`. GET chỉ đọc, không tự sync, tạo profile/season/member hoặc cộng XP.
- JSON dùng camelCase; ngày YYYY-MM-DD theo Asia/Ho_Chi_Minh, thời điểm ISO 8601 UTC. XP/ID/revision là số nguyên không âm trong miền an toàn JavaScript; ID tài nguyên dương.
- POST chỉ nhận `{requestId}` UUID chữ thường. Field lạ, thiếu field hoặc kiểu sai bị từ chối; không cho gửi điểm/ngày/hạng/nhóm. Không tự ép string thành số hoặc boolean trong body.
- Phân trang page >= 0, size 1–50 mặc định 20. Page ngoài phạm vi trả items=[]; totalPages có thể 0 khi rỗng. Query có tên không hỗ trợ bị từ chối trong các endpoint mới.
- GET/POST không trả answerKey/transcript/storageKey/passwordHash/email người khác. Leaderboard dùng memberKey công khai, không userId.
- Cùng requestId/payload/operation replay status và body cũ, header `Idempotent-Replayed: true`. Không phát lại thưởng. Cùng key khác payload/operation trả 409 REQUEST_ID_REUSED. Khi mất ACK giữ key cũ; dùng GET đối soát sau replay.

## 2. GET /api/me/gamification

Summary gồm:

| Trường | Ý nghĩa |
|---|---|
| serverTime, zone | Đồng hồ/múi giờ dùng cho ngày và tuần |
| policyVersion, activatedAt | Bộ luật và mốc bật đã lưu; không đổi theo restart |
| revision | Phiên bản projection cá nhân |
| totalXp | Lifetime SUM awardedXp đã commit |
| activityLevel, levelFloorXp, nextLevelAtXp | 1 + floor(totalXp/100), ngưỡng mức hiện tại/kế tiếp |
| day | date, awardedXp, capXp=200, qualifiedSubmissions, isActive của hôm nay |
| streak | current, best, lastActiveDate (null nếu chưa có) |
| badges | earnedCount, totalCount=3 |
| backlog | pendingSubmissions, deferredSubmissions |
| processingState | IDLE/PENDING/DEFERRED; PENDING ưu tiên nếu còn nguồn có thể xử lý |

Profile chưa có row: totalXp=0, level=1, revision=0, streak=0; không INSERT do GET. Nguồn trước activation không tăng XP/cấp độ/streak/huy hiệu; revision vẫn có thể tăng khi ghi quyết định BEFORE_ACTIVATION để lịch sử và client đối soát được. Đây là summary cá nhân, không phải điểm bài M03.

## 3. POST /api/me/gamification/sync

```json
{"requestId":"9f219d91-5324-4a3b-89ba-d1001e5d6737"}
```

200 SyncResult: requestId, processedSubmissions (0–100), remainingSubmissions, deferredSubmissions, revision, processedAt. Mỗi batch cùng transaction receipt và dữ liệu thưởng. BEFORE_ACTIVATION được tính đã xử lý khi đã ghi quyết định cuối; nguồn lỗi không được tính thành công.

Worker tự quét nền, không buộc mở màn gamification. Endpoint này là đường đồng bộ/đối soát thêm. Còn remaining thì dùng key mới cho batch tiếp theo; replay key cũ không xử lý thêm nguồn. Schema không hỗ trợ trả 503 GAMIFICATION_SCHEMA_UNSUPPORTED; rollback batch, ghi retry/quarantine bằng cơ chế riêng, không marker hoàn tất giả. Quyền bị thu hồi trả 401/403 theo M01.

## 4. GET /api/me/gamification/activities

Query: submissionId (tùy chọn, ID dương), page, size. Sắp submittedAt DESC rồi submissionId DESC. Mỗi item là một quyết định Submission, không phải mỗi lần worker chạy:

- submissionId, attemptId, mode, submittedAt, processedAt, activityDate, policyVersion.
- disposition: PROCESSED hoặc BEFORE_ACTIVATION. requestedXp, awardedXp, qualifiedActivity.
- decisions: mỗi nguồn câu hoặc mục tiêu có rewardType, attemptQuestionId/objectiveId (nullable theo loại), requestedXp, awardedXp, reason.
- league: seasonId (nullable), countedXp và reason COUNTED/NOT_JOINED/BEFORE_JOIN/LATE_AFTER_CUTOFF/NO_AWARDED_XP/BEFORE_ACTIVATION.

Response ActivitiesPage có items/totalElements/page/size/totalPages và requestedSubmission:

- Không lọc submissionId: requestedSubmission=null.
- Có lọc: `{submissionId,state}`; state PENDING/DEFERRED/PROCESSED/BEFORE_ACTIVATION. PENDING/DEFERRED có thể items=[]; UI không suy ra thưởng cuối cùng bằng 0.
- Submission không tồn tại hoặc không thuộc mình: 404 NOT_FOUND. Không trả state cho người khác.

Trần/các source bị bỏ qua vẫn được giải thích bằng decisions đã lưu trong processed decision. Không chấm lại theo QuestionVersion mới hoặc đọc lịch M04 hiện tại để tính lại thưởng cũ. Rebuild projection lấy ledger đã quyết định, không phát thêm nguồn thưởng.

## 5. GET /api/me/badges

Response `{serverTime,items}` với đúng ba badgeCode: FIRST_LESSON, DUE_REVIEW, SEVEN_DAY_STREAK. Mỗi item gồm code, name, description, earnedAt/evidenceSourceAt (cùng null nếu chưa đạt), progress, target. progress được chặn ở target; không có XP riêng cho huy hiệu.

FIRST_LESSON cần lessonCompleted và nguồn đủ ngày hoạt động; DUE_REVIEW cần claim ôn hợp lệ dù bị trần XP chặn; SEVEN_DAY_STREAK cần bestStreak >= 7. Đổi account phải xóa cache badge cũ.

## 6. GET /api/me/league

LeagueOverview gồm serverTime, revision, state, canJoin, season, membership. season/membership được null theo trạng thái:

| state | Ý nghĩa |
|---|---|
| UNAVAILABLE | Chưa có season do worker chưa khởi tạo; season và membership null, canJoin=false |
| NOT_JOINED | Có season hiện tại, chưa tham gia; membership null; canJoin=true chỉ khi season OPEN và giờ < endAt |
| WAITING_PREVIOUS_SETTLEMENT | Chưa tham gia tuần mới, membership cũ của chính mình chưa chốt; canJoin=false |
| ACTIVE | Đang ở nhóm tuần OPEN, có membership, canJoin=false |
| CLOSING | Membership đang đợi chốt tuần, canJoin=false |
| FINALIZED | Membership đã chốt; kết quả bất biến, canJoin=false |

Season có id/startAt/endAt/cutoffAt/status/policyVersion và rules (capacity, minimumMembers, minimumActive, promotionPercent, demotionPercent). State theo thời gian server, không dựa duy nhất job đã cập nhật status hay chưa. Khi đã sang tuần mới mà chưa tham gia, API hiển thị season mới; kết quả cũ xem ở history. Không hiển thị season cũ là ACTIVE vì scheduler chậm.

Membership có memberKey, groupId, tier, joinedAt, weeklyXp, rank (null khi chưa có snapshot rank), projectedOutcome và result (null đến khi group finalized). projectedOutcome là PROMOTE/KEEP/DEMOTE, chưa phải kết quả cuối. result có frozen rank, memberCount/activeCount, weeklyXp, tierBefore/tierAfter, outcome, holdReason và finalizedAt. Nếu group chưa đủ điều kiện thì projectedOutcome=KEEP.

## 7. POST /api/me/league/join

Body chỉ requestId, cùng schema sync. 200 JoinResult gồm requestId, created, revision, season và membership. Server tự chọn season/tier/group. Không nhận seasonId/tier/groupId/userId từ client.

- Lần đầu: created=true, weeklyXp=0, joinedAt lúc giữ khóa kiểm tra còn OPEN; xếp nhóm có chỗ ở đúng tier.
- UUID khác trên cùng membership: created=false, không đổi joinedAt hoặc cộng lại XP cũ.
- Replay key gốc: nguyên response trước đó, có thể created=true; GET mới là trạng thái hiện tại.
- Membership đã tồn tại của cùng season được nhận diện trước kiểm tra quota tạo mới. Khi request key mới đến sau biên tuần thì xử lý season hiện tại mới; không tự tham gia tuần khác theo response replay cũ.
- User có group tuần trước chưa chốt: 409 LEAGUE_SETTLEMENT_PENDING. Không có season OPEN: 409 LEAGUE_NOT_OPEN.

## 8. GET /api/me/league/leaderboard

Query page/size, chỉ lấy group của membership trong season hiện tại. Không nhận groupId/seasonId làm bộ lọc tùy ý. Không có membership: 404 LEAGUE_MEMBERSHIP_NOT_FOUND; UI dùng overview để hiển thị nút join, không báo lỗi mạng.

LeaderboardPage gồm serverTime, seasonId, groupId, tier, revision, provisional, groupStats, self, items và thông tin phân trang. self luôn có dù nằm ngoài trang. Mỗi member chỉ gồm memberKey, displayName, isSelf, rank, weeklyXp, outcome; không email/userId hoặc lịch sử câu trả lời.

groupStats: memberCount, activeCount, capacity, promotionQuota, demotionQuota, movementEnabled, holdReason. holdReason là NONE/TOO_FEW_MEMBERS/TOO_FEW_ACTIVE (kiểm tra thiếu thành viên trước). Thiếu điều kiện thì quotas=0 và toàn group KEEP. Tại bậc đầu/cuối, quota phía bị chặn=0. Mỗi người có outcome dùng chung thuật toán xếp hạng/chốt; các khối hòa vắt quota giữ bậc.

Sorting weeklyXp DESC rồi memberKey ASC chỉ để ổn định hiển thị. Competition rank vẫn bằng nhau khi XP bằng nhau; không dùng thứ tự hiển thị phá hòa. provisional=false chỉ khi group đã finalized. GET sau cutoff mà group chưa chốt vẫn hiển thị bảng đang chốt, không cho rằng rank đã chính thức.

## 9. GET /api/me/league/history

Query page/size. HistoryPage gồm items/totalElements/page/size/totalPages. Chỉ trả membership có group finalized của chính mình, season startAt DESC. Mỗi item có season, groupId, result. Giữ bậc khi vắng tuần; không tạo history giả cho tuần không tham gia.

## 10. Lỗi và phục hồi

ApiError giữ format hiện tại: `{code,message,fieldErrors}`. Không đưa trace hoặc nội dung SQL ra response.

| HTTP | code điển hình | Xử lý |
|---|---|---|
| 400 | VALIDATION_FAILED / INVALID_REQUEST | Sửa field, kiểu hoặc query; không đổi kiểu tự động |
| 401 | UNAUTHENTICATED / SESSION_REVOKED | Đăng nhập lại, không giữ dữ liệu user khác |
| 403 | FORBIDDEN / CSRF_INVALID | Kiểm tra vai trò/CSRF hiện hành |
| 404 | NOT_FOUND / LEAGUE_MEMBERSHIP_NOT_FOUND | Không lộ tài nguyên người khác; phân biệt chưa join |
| 409 | REQUEST_ID_REUSED | Không dùng UUID cho operation/payload khác |
| 409 | LEAGUE_SETTLEMENT_PENDING / LEAGUE_NOT_OPEN | Tải lại overview, đợi kết quả hoặc season |
| 503 | GAMIFICATION_SCHEMA_UNSUPPORTED / DATABASE_UNAVAILABLE | Giữ key khi ACK chưa rõ, đối soát và thử lại |

## 11. Socket — phần bổ sung ở chặng 5

Không thay HELLO/HEARTBEAT hiện tại. SUBSCRIBE/UNSUBSCRIBE có version=1, requestId, resource=GAMIFICATION hoặc LEAGUE. Server lấy user/group từ principal và membership; không cho truyền groupId/userId. ACK chỉ xác nhận đăng ký kênh.

Server gửi InvalidationEvent với version=1, eventId UUID, type GAMIFICATION_CHANGED/LEADERBOARD_CHANGED/LEAGUE_CHANGED, resourceId (chuỗi), revision, serverTime. Có thể lặp hoặc đảo thứ tự; client GET snapshot, không cộng số XP trong event. Hết phiên/đổi account phải hủy kênh và đóng socket theo M01. Khi reconnect luôn GET, kể cả outbox đã đánh dấu gửi mà client không nhận được.

## 12. Kiểm tra hợp đồng

Fixture nằm tại `source/server/src/test/resources/contracts/m05-fixtures.json`. Chạy từ root:

```powershell
python scripts/verify-m05-contract.py
```

Kiểm tra này xác thực cấu trúc OpenAPI/JSON Schema và fixture hợp lệ/không hợp lệ, không chứng minh endpoint runtime hoặc transaction đúng. Các ca nghiệp vụ/lock/worker nằm trong [ma trận M05](../../docs/M05_MANUAL_TEST_CASES.md); phải chạy khi chặng tương ứng có implementation.
