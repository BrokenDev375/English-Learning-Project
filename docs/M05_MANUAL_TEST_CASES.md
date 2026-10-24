# M05 — Ma trận nghiệm thu theo chặng

Ngày cập nhật 22/10/2026. Luật chuẩn: [m05-p0-v1](M05_DECISIONS.md); payload: [API](../source/common/M05_API.md). Bảng dưới giữ tiêu chí P0 gốc. Kết quả/đối chiếu hiện tại trong [nghiệm thu M05](../statics/results/module-5-validation.md), có phân biệt test thật, fixture UI, phép đo local và giới hạn. Không diễn giải như đã thao tác tay mọi biến thể hoặc thử mọi lịch crash/interleaving.

Môi trường ghi dữ liệu: Testcontainers hoặc Compose project riêng `english-learning-m05-e2e`, volume riêng. Không chạy fixture ghi dữ liệu lên demo, không down -v. Dùng Clock test, không sửa giờ máy. Hai tài khoản dùng hai browser context/hồ sơ riêng; hai tab cùng hồ sơ dùng chung session theo thiết kế M01.

| ID | Chặng | Thiết lập / thao tác | Kết quả phải chứng minh |
|---|---:|---|---|
| C01 | 0 | Validate OpenAPI, mọi ref và ví dụ | Không ref hỏng; session/CSRF/STUDENT/no-store được khai báo |
| C02 | 0 | Request có userId/XP/date/tier/group/season | Schema từ chối field thêm, key thiếu/sai kiểu/không UUID |
| C03 | 0 | Response có email/userId/passwordHash/answerKey ngoài schema | Schema từ chối; chưa được coi là test quyền runtime |
| D01 | 1 | Tạo DB mới với tất cả migration | Flyway/JPA validate; checksum V1–V5 giữ nguyên |
| D02 | 1 | DB V5 có users/nội dung/submissions/review progress, nâng schema | Giữ ID, dữ liệu và lịch sử; thêm đúng bảng M05 |
| D03 | 1 | Cấp REVIEW gồm hai objective đến hạn | Context chỉ chứa objective thực cấp, dueAt/stage/cycle chuẩn microsecond |
| D04 | 1 | Lỗi ghi context khi tạo Attempt | Attempt/item/exposure/receipt/context rollback cùng nhau |
| D05 | 1 | Resume và replay create sau qua ngày | Context/cycle/selectedAt không đổi, exposure không tăng |
| D06 | 1 | Sync một Submission toàn trống làm lastSubmissionId đổi | dueAt/stage không đổi thì cycleKey không đổi |
| X01 | 2 | LEARN có 5 GUIDED + 5 independent mới, tất cả đúng | 25 XP; không tính 5 GUIDED, không có bonus nộp cố định |
| X02 | 2 | FIRST sai, final đúng; sau nộp CORRECTION đúng | Không nhận XP từ sửa final/trial chữa |
| X03 | 2 | FIRST có hỗ trợ/GUIDED/partial/null | 0 cho nguồn không đủ điều kiện, lý do chính xác |
| X04 | 2 | Matching nhiều cặp/fill nhiều ô cùng đúng | 5 XP/câu, không cộng theo cặp/ô |
| X05 | 2 | Copy/version mới cùng origin và hai Attempt liên quan | Không nhận thưởng câu mới lần hai |
| X06 | 2 | Cấp rồi bỏ câu, tạo lượt mới | wasSeenBefore=true ở lượt sau; không XP câu mới, giải thích đúng |
| X07 | 2 | REVIEW đến hạn, câu mới đúng; cùng objective | XP câu mới + 5 bonus objective, không bonus mỗi item |
| X08 | 2 | REVIEW cần luyện ngay vì sai nhưng chưa có lịch tốt đến hạn | Không bonus đến hạn; vẫn có thể thưởng câu mới |
| X09 | 2 | Một item objective sai/trống/có hỗ trợ | Không bỏ item khỏi mẫu số để cấp bonus objective |
| X10 | 2 | Hai context cùng cycle; context khác cycle cùng objective/ngày | Tối đa một claim/cycle và một bonus/objective/ngày |
| X11 | 2 | Attempt trước M05 không có context | Không bịa due bonus; XP thường đủ điều kiện vẫn được xét |
| X12 | 2 | DailyXp=195, nguồn có hai câu mới + bonus | Tổng ngày=200; source sau bị cap tiêu thụ, không chuyển ngày |
| X13 | 2 | Retry UUID cũ/UUID mới và mất ACK sau commit | XP/claim/outbox không tăng lại, receipt replay đúng body |
| X14 | 2 | Hai sync cùng user chạy đồng thời | Một nguồn chỉ xét/commit một lần; unique và actor lock thực sự bảo vệ |
| X15 | 2 | Inject lỗi ledger/profile/day/outbox/processed/receipt | Rollback toàn batch, retry có đủ kết quả, không dữ liệu một nửa |
| X16 | 2 | Nguồn ID nhỏ commit sau ID lớn đã được worker thấy | NOT EXISTS không bỏ mất nguồn; trần theo quyết định đã lưu |
| X17 | 2 | Restart worker/server sau commit trước thông báo | Đọc bù nguồn chưa xét; nguồn xong không thưởng lại |
| X18 | 2 | Facts schema không hỗ trợ hoặc user bị khóa/đổi role | Không marker thành công giả, không thưởng trái quyền; user khác vẫn được xử lý |
| X19 | 2 | Nguồn trước/bằng/sau activatedAt, restart nhiều lần | Trước mốc không thưởng; mốc không đổi, nguồn bằng mốc được xét |
| S01 | 3 | Nhiều bài hợp lệ cùng ngày | Một activity day, không tăng nhiều ngày streak |
| S02 | 3 | Đạt cap hoặc luyện câu cũ nhưng đủ ngưỡng FIRST | Vẫn giữ streak; không cần XP mới |
| S03 | 3 | Toàn GUIDED/trống; độc lập chưa đủ ngưỡng; không có câu đúng | Không tạo ngày hoạt động |
| S04 | 3 | 23:59:59.999999 / 00:00:00 Việt Nam, client chỉnh giờ | Hai ngày theo server, không theo clock client |
| S05 | 3 | Worker nhận ngày 1,3 rồi nhận bù ngày 2 | Current/best nối chuỗi đúng; không cộng theo thứ tự chạy |
| S06 | 3 | Ngày cuối hôm nay/hôm qua/cách đây hai ngày | Current đúng, best giữ lịch sử; không GET ghi reset row |
| S07 | 3 | totalXp=0/99/100/199/200 | Cấp độ 1/1/2/2/3, ngưỡng mức đúng |
| S08 | 3 | LessonCompleted + ngày hợp lệ; due claim bị cap; streak 7 | Mỗi badge một lần, chứng minh từ đúng nguồn, không có XP badge |
| L01 | 4 | Hai request cùng user join một tuần, key cũ/mới | Một membership; created/replay/joinedAt đúng |
| L02 | 4 | Hai user tranh chỗ cuối nhóm 24 người | Một nhóm 25, người còn lại sang nhóm khác; không vượt capacity |
| L03 | 4 | Hai nhóm đầy, allocator đồng thời tạo nhóm mới | Không trùng groupNo, chọn ổn định nhóm còn chỗ |
| L04 | 4 | Nguồn trước/sát/bằng joinedAt | Chỉ nguồn >= joinedAt tính nhóm, lifetime không mất |
| L05 | 4 | N=9; N=10 có 4 active; N=10 có 5 active | Hai trường hợp đầu KEEP toàn bộ, trường hợp cuối có quota2 |
| L06 | 4 | Nhóm 25 điểm phân biệt | Tối đa 5 PROMOTE, 5 DEMOTE, còn lại KEEP |
| L07 | 4 | Tie positions 5–6; tie positions 20–21; toàn group bằng 0 | Tie vắt biên KEEP, không phân xử bằng ID; group rỗng hoạt động KEEP |
| L08 | 4 | Tie nằm trọn trong vùng top/bottom; bậc Đồng/Kim Cương | Cùng quyền lợi trong khối, không vượt bậc, không lấp suất thừa |
| L09 | 4 | Nguồn trước/bằng endAt và trước/bằng cutoff | Khoảng nửa mở; nguồn đúng endAt thuộc tuần mới; đúng cutoff bị loại tuần cũ |
| L10 | 4 | Transaction giữ khóa trước cutoff nhưng commit sau | Finalizer chờ, không bỏ khoản đã được nhận ở điểm tuần tự hóa |
| L11 | 4 | Nguồn tuần cũ tới sau cutoff/outage kéo dài | Lifetime/activity đủ điều kiện vẫn được ghi; không sửa tuần cũ hoặc chuyển điểm mới |
| L12 | 4 | Hai finalizer; chết sau một nhóm; chạy lại sau FINALIZED | Một kết quả/member/group, tiếp tục nhóm còn lại, không reset lifetime/streak |
| L13 | 4 | Join tuần mới khi group cũ chưa/xong chốt; vắng một tuần | Chờ đúng user, bậc từ kết quả cũ, vắng tuần không tự hạ |
| U01 | 5 | Summary rỗng; pending source; deferred; source xét 0 | UI phân biệt đúng trạng thái, không báo mất thưởng khi chưa xử lý |
| U02 | 5 | Hai học viên độc lập xem bảng nhóm; sửa query/ID/subscribe | Không đọc nhóm khác, không lộ email/internal userId/đáp án |
| U03 | 5 | TEACHER/ADMIN thử API thưởng/league; đổi account trong browser | Quyền đúng, không trộn cache/member/subscription |
| U04 | 5 | Socket ngắt, event lặp/đảo revision, outbox gửi lại | Client GET đối soát, không tự cộng XP hoặc ghi đè snapshot mới |
| U05 | 5 | Mất ACK join/sync sau commit rồi refresh/reconnect | Không duplicate, giữ key tới đối soát; GET có dữ liệu thực |
| U06 | 5 | Mobile 390px, bàn phím, lỗi/rỗng, self ngoài trang | Không tràn ngang, self vẫn rõ, trạng thái đang chốt và luật tie dễ hiểu |
| A01 | 6 | Restart/recreate và seed chạy lại trên volume test riêng | Ledger/claim/context/badge/season/history giữ nguyên |
| A02 | 6 | Đối soát tổng ledger với profile/day/member đã chốt | SUM khớp, không repair bằng phát lại thưởng |
| A03 | 6 | Hồi quy M01–M04 đúng source hiện tại | Auth/quyền/học liệu/save/submit/media/review không hỏng |
| A04 | 6 | Backlog nhiều user + nhiều socket, ghi cấu hình và latency | Có số đo thật; không tự tuyên bố quy mô/hiệu quả học tập |

Các ca transaction/lock phải chạy MySQL thật; mock chỉ bổ trợ. Các ca biên thời gian phải assert cả ledger, projection và lịch sử đã chốt, không chỉ nhìn nhãn UI. Biên bản ghi rõ ca nào tự động, ca nào thao tác tay và ca nào chưa chạy.
