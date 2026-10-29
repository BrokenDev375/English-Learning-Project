# Kế hoạch báo cáo dự án English Learning

Ngày lập: **27/10/2026**. Trạng thái: **dàn ý và danh sách công việc chuẩn bị báo cáo**.

Mục tiêu là viết báo cáo môn **Lập trình mạng** theo dạng bài viết kỹ thuật, giải thích được bài toán, thiết kế, đóng góp và bằng chứng thực nghiệm của English Learning. Bản kế hoạch này chưa phải báo cáo cuối cùng và không ghi nhận một lượt kiểm thử hay đo hiệu năng mới.

## 1. Căn cứ từ 4 file môn học

| Tài liệu đã đọc | Nội dung áp dụng vào kế hoạch |
|---|---|
| [course/README.md](course/README.md) | Giới thiệu học phần và minh họa cấu trúc một project. Nội dung hiện nói về **Air Hockey Multiplayer System**, nên chỉ tham khảo cách tổ chức; không đưa tên, công nghệ hoặc kết quả của ví dụ đó vào báo cáo English Learning. |
| [course/Topics.md](course/Topics.md) | English Learning phù hợp **Chủ đề 3 — Ứng dụng mạng**, trong lĩnh vực giáo dục. Cần làm rõ vai trò client/server, giao tiếp, dữ liệu, đồng thời, lỗi, đóng góp và cách đánh giá, đặc biệt tại mục 4.7 và 5. |
| [course/Instruction.md](course/Instruction.md) | Mục 8 quy định nội dung báo cáo theo technical/research paper; mục 5–7 yêu cầu concurrency, error handling và novelty; mục 9 yêu cầu thực nghiệm; mục 10 quy định Compilatio; mục 13 quy định README. |
| [course/Submission.md](course/Submission.md) | Quy định tên file, một báo cáo trên Compilatio, source chạy được, gói bàn giao Source/Report và hạn **23:59 ngày 31/10/2026**. |

Các yêu cầu cần giữ xuyên suốt:

- Có đủ nội dung của 19 mục báo cáo tại Instruction §8; có thể nhóm thành chương khi trình bày.
- Giải thích cơ chế mạng và quyết định thiết kế, không chỉ liệt kê màn hình hoặc bảng dữ liệu.
- Có ít nhất một nhóm thực nghiệm phù hợp, kết quả thực tế và phần phân tích. Số ca test PASS không thay cho latency, throughput hoặc bằng chứng hiệu quả học tập.
- **Novelty / Contributions là bắt buộc**; mỗi đóng góp gắn với phần tự phát triển, cách tiếp cận đối chiếu và bằng chứng. UI, màu sắc, animation riêng lẻ không đủ làm đóng góp kỹ thuật chính.
- Trích dẫn nguồn trong nội dung; theo Instruction §10, **Similarity + Plagiarism ≤ 20%**. Thành viên phải hiểu code, kiến trúc, message flow và kết quả mình trình bày.

## 2. Định hướng nội dung cho English Learning

**Tên dự kiến:** “English Learning — Ứng dụng học tiếng Anh theo mô hình Client–Server với cơ chế lưu và phục hồi lượt học”.

**Câu hỏi trung tâm:** Hệ thống phối hợp trình duyệt và máy chủ như thế nào để lưu, tiếp tục và nộp lượt học nhất quán khi có timeout, mất phản hồi, kết nối lại hoặc thao tác đồng thời?

Phạm vi chức năng cần giới thiệu ngắn gọn:

- **M01–M02:** tài khoản/phân quyền và biên soạn học liệu có phiên bản, tài liệu đọc/nghe/ảnh, xuất bản.
- **M03–M04:** lượt học, lưu nháp, nộp/chấm/chữa, tiến độ và ôn tập cá nhân. Hai lối vào chính hiện tại là **Vào chơi** và **Ôn tập cá nhân**; PRACTICE còn phục vụ API/lịch sử cũ.
- **M05:** XP, cấp độ, streak, huy hiệu, nhóm thi đấu và bảng xếp hạng tuần toàn hệ thống; phân biệt hai loại bảng xếp hạng.
- **M06:** heartbeat, presence, subscription, lưu cục bộ, journal, reconnect và đối soát.
- **M07:** hồ sơ học viên, thống kê theo Unit của giáo viên và thống kê quản trị.
- Vòng học, bốn dạng câu hỏi và cảnh săn kho báu là phần trải nghiệm tích hợp; trình bày vừa đủ để hiểu luồng sử dụng. **M08 đang hoãn**, không đưa vào danh sách đã hoàn thành.

Trọng tâm kỹ thuật: HTTP/JSON và WebSocket cùng phiên đăng nhập; transaction và request receipt; khóa/revision khi ghi đồng thời; IndexedDB và gửi lại đúng lệnh; phiên bản học liệu và kết quả bất biến; worker/outbox và GET đối soát dữ liệu thưởng.

Khi viết cần đối chiếu source đã chốt với [AGENTS.md](../AGENTS.md), [README](../README.md) và tài liệu từng module. Không mặc định tài liệu thiết kế cũ, DB demo hay mọi bằng chứng lịch sử đều trùng trạng thái source hiện tại. Không tuyên bố nhiều backend, offline đầy đủ hoặc cải thiện hiệu quả học tiếng Anh khi chưa có bằng chứng tương ứng.

## 3. Mục lục báo cáo dự kiến và việc cần viết

| Mục | Nội dung tóm tắt cần thực hiện | Tư liệu/đầu ra cần chuẩn bị |
|---|---|---|
| **1. Tên đề tài — Title** | Chốt tên English Learning, thông tin học phần, giảng viên, nhóm và thành viên theo thông tin thực tế. | Trang bìa; số nhóm, họ tên, MSSV và lớp cần bổ sung. |
| **2. Tóm tắt — Abstract** | Tóm tắt bài toán, giải pháp client–server, triển khai, kết quả chính và đóng góp. Viết sau khi chốt thực nghiệm. | Một đoạn tóm tắt; chỉ dùng số liệu có nguồn kiểm chứng. |
| **3. Từ khóa — Keywords** | Chọn 4–6 từ khóa sát nội dung. | Gợi ý 6 từ khóa: Client–Server; HTTP/REST; WebSocket; Idempotency; Concurrency; Fault tolerance. |
| **4. Giới thiệu — Introduction** | Bối cảnh học tiếng Anh qua mạng; nhu cầu lưu/tiếp tục bài; vấn đề khi mất mạng hoặc nhiều tab; mục tiêu, phạm vi và đóng góp chính. | Phát biểu bài toán; danh sách mục tiêu có thể kiểm tra. |
| **5. Cơ sở và công trình liên quan — Background / Related Work** | Kiến thức HTTP, session/CSRF, WebSocket, transaction, idempotency, lưu cục bộ; tham khảo giải pháp học trực tuyến và phục hồi dữ liệu liên quan. Nêu điểm kế thừa, khác biệt và giới hạn so sánh. | Nguồn chính thức/bài nghiên cứu đã đọc; bảng so sánh theo tiêu chí; trích dẫn trong bài. |
| **6. Định nghĩa bài toán — Problem Definition** | Input: học liệu và thao tác học; output: nháp, kết quả, tiến độ, thưởng/thống kê. Mô tả ba vai trò, use case, yêu cầu chức năng/phi chức năng, giả định một backend và lỗi mạng cần xử lý. | Sơ đồ use case gọn; bảng yêu cầu và tiêu chí nghiệm thu; phân biệt dữ liệu demo với dữ liệu người học thật. |
| **7. Giải pháp đề xuất — Proposed Approach** | Giải thích vòng đời Attempt, chốt phiên bản câu hỏi, lưu/nộp full snapshot, FIRST/Submission bất biến, receipt/replay và phục hồi pending command. Nêu lý do chọn HTTP cho nghiệp vụ và WebSocket cho kết nối/invalidation. | Sơ đồ trạng thái; luồng dữ liệu; giả mã ngắn cho xử lý mutation và phục hồi. |
| **8. Kiến trúc hệ thống — System Architecture** | Trình duyệt React → nginx/Vite proxy → Spring Boot → MySQL và kho file. Phân vai client/server, các lớp backend, worker, vùng dữ liệu RAM/IndexedDB/DB/volume. | Sơ đồ triển khai và thành phần; ERD rút gọn theo nhóm dữ liệu; giải thích ranh giới transaction. |
| **9. Thiết kế giao tiếp mạng — Network Communication Design** | HTTP request/response, cookie/CSRF, JSON/error format, upload/media Range; mở/đóng WebSocket, xác thực, heartbeat/ACK, subscription, reconnect. Trình bày requestId, revision và xử lý timeout/401/403/404/409/503 theo hợp đồng thực tế. | Bảng endpoint/message tiêu biểu; payload đã ẩn thông tin nhạy cảm; sequence đăng nhập, lưu/nộp, mất phản hồi và reconnect. |
| **10. Triển khai — Implementation** | Công nghệ và thư viện; module/lớp cốt lõi; cách chấm bốn loại câu; lưu/khôi phục nháp; ôn tập; thưởng/worker/outbox; thống kê chỉ đọc. Giải thích xử lý nhiều request/socket, khóa actor → tài nguyên, đọc lại sau chờ khóa, revision và ghi receipt cùng transaction. | Bảng module–trách nhiệm–lớp; trích đoạn code ngắn có mục đích; phân biệt cơ chế framework cung cấp và phần nhóm tự viết. |
| **11. Thiết lập thực nghiệm — Experimental Setup** | Máy, OS, phiên bản phần mềm, cấu hình mạng/proxy, một backend, dữ liệu, số client/socket, công cụ đo, kịch bản và cách lặp lại. Chốt commit và thay đổi chưa commit nếu có. | Manifest môi trường, lệnh chạy, dataset, ma trận test và định nghĩa metric. |
| **12. Kết quả — Results** | Tách kết quả chức năng/hồi quy, độ tin cậy/concurrency và hiệu năng mạng. Trình bày bảng/biểu đồ từ log thực tế; ghi mẫu đo, lần chạy, điều kiện và nguồn. | Bảng test theo phiên bản; số đo latency/throughput/error; kết quả replay/conflict/recovery; ảnh demo tiêu biểu. |
| **13. Thảo luận — Discussion** | Giải thích kết quả, đánh đổi autosave cục bộ/server, vai trò invalidation, ảnh hưởng của worker, số client và môi trường local. Phân tích lỗi hoặc số đo bất thường. | Nhận xét gắn với từng bảng/hình; mức độ đáp ứng yêu cầu; tránh suy rộng từ workload nhỏ. |
| **14. Điểm mới và đóng góp — Novelty and Contributions** | Nêu 2–3 đóng góp kỹ thuật có bằng chứng theo mục 5 của kế hoạch; phân biệt nội dung kế thừa và phần nhóm phát triển. Nếu có codebase gốc, mô tả rõ baseline đó. | Bảng đóng góp–cách đối chiếu–implementation–experiment. Không khẳng định tính mới trên toàn thế giới. |
| **15. Hạn chế — Limitations** | Một backend, session/socket trong RAM; cần đăng nhập lại khi restart; phạm vi offline và dữ liệu chưa commit; giới hạn học liệu/dataset, tải thử và hiệu quả học tập chưa được đánh giá. | Danh sách giới hạn đối chiếu đúng bản cuối và các nhánh chưa kiểm tra. |
| **16. Hướng phát triển — Future Work** | Đề xuất công việc từ hạn chế: quản lý phiên khi mở rộng backend, tăng khả năng phục hồi, đo tải rộng hơn, mở rộng học liệu và nghiên cứu hiệu quả học tập. | Hướng phát triển có lý do và cách kiểm chứng; ghi rõ là chưa triển khai. |
| **17. Kết luận — Conclusion** | Trả lời lại bài toán, giải pháp, kết quả thật và đóng góp đã chứng minh. | Kết luận nhất quán với Abstract/Results; không thêm tuyên bố mới. |
| **18. Tài liệu tham khảo — References** | Dùng một kiểu trích dẫn thống nhất; dẫn nguồn ý tưởng, công nghệ, học liệu, hình/bảng và số liệu bên ngoài. | Danh mục tài liệu được dẫn trong nội dung; nguồn học liệu và thư viện/code kế thừa. |
| **19. Phụ lục — Appendix** | Đưa chi tiết dài ra phụ lục: API/payload, cấu hình mẫu, schema, test case, kết quả bổ sung, hướng dẫn tái hiện và phân công thực tế. | Phụ lục chọn lọc; không chép toàn bộ repository hoặc thông tin xác thực. |

## 4. Tư liệu cần gom trước khi viết chi tiết

Các liên kết dưới đây là **đầu mối tra cứu**, chưa có nghĩa mọi tài liệu đã được kiểm chứng lại cho bản nộp cuối.

| Nhóm tư liệu | Nguồn trong dự án | Công việc cần làm |
|---|---|---|
| Kiến trúc và ORM | [Cấu trúc backend](BACKEND_STRUCTURE.md), [ORM](ORM_ARCHITECTURE.md), [architecture.mmd](../statics/architecture.mmd), [protocol.mmd](../statics/protocol.mmd) | Kiểm tra sơ đồ có còn khớp source; cập nhật/xuất hình đọc rõ; bổ sung các sequence chính. |
| Hợp đồng mạng | [M01](../source/common/API.md), [M02–M03](../source/common/M02_M03_API.md), [M04](../source/common/M04_API.md), [M05](../source/common/M05_API.md), [M06](../source/common/M06_API.md), [M07](../source/common/M07_API.md) và các OpenAPI tương ứng trong `source/common/` | Chọn endpoint/message đại diện; đối chiếu controller/DTO trước khi đưa payload vào báo cáo. |
| Tiến độ và phạm vi | [M02](M02_PROGRESS.md), [M03](M03_PROGRESS.md), [M04](M04_PROGRESS.md), [M05](M05_PROGRESS.md), [M06](M06_PROGRESS.md), [M07](M07_PROGRESS.md), [vòng học](LEARNING_JOURNEY_PROGRESS.md) | Tạo bảng chức năng đã có, giới hạn và điểm chưa nghiệm thu của bản chốt. |
| Kiểm thử theo module | [M01](../statics/results/module-1-validation.md), [M02 seed](../statics/results/module-2-seed-validation.md), [M03](../statics/results/module-3-validation.md), [M04](../statics/results/module-4-validation.md), [M05](../statics/results/module-5-validation.md), [M06](../statics/results/module-6-validation.md), [M07](../statics/results/module-7-validation.md) | Ghi ngày, source/commit, môi trường và phạm vi từng lượt; chọn lại kiểm tra cần chạy cho bản cuối. |
| Thay đổi gần đây | [Hai luồng học](../statics/results/two-learning-modes-validation.md), [gameplay](../statics/results/gameplay-validation.md), [cấp độ](M05_PROGRESSIVE_LEVELS.md), [kiểm tra cấp độ](../statics/results/progressive-levels-validation.md), [bảng xếp hạng chung](M05_GLOBAL_LEADERBOARD.md), [kiểm tra bảng xếp hạng](../statics/results/global-leaderboard-validation.md) | Không dùng số hồi quy M07 lịch sử để thay bằng chứng cho thay đổi mới hơn. Đối chiếu luật hiện hành, tránh chép thông số cấp độ/League đã cũ. |
| Dataset và hình minh họa | [Nguồn học liệu](../statics/dataset/README.md), `statics/results/` | Chốt dataset thực nghiệm; ghi nguồn/quyền sử dụng; chụp ảnh bản cuối; nhận diện dữ liệu giả lập và không diễn giải thành nghiên cứu trên người học thật. |
| Hướng dẫn chạy và hồ sơ nộp | [README](../README.md), [report/README.md](../report/README.md), [Submission](course/Submission.md) | Rà lại build/run/test, thư viện ngoài, cấu hình mẫu và các link môn học cũ; chuẩn bị báo cáo cuối trong `report/`. |

**Quy tắc dùng bằng chứng:** không cộng số test của nhiều lượt thành kết quả một lượt chạy. Ví dụ, mốc M07 ghi 266 ca backend khác nhau đã qua qua các lượt, không phải một lượt 266/266. Mỗi kết luận cần truy được tới log/biên bản và phiên bản tương ứng; nếu artifact gốc không còn, nêu giới hạn hoặc đo lại phần cần dùng.

## 5. Đóng góp kỹ thuật dự kiến cần chứng minh

Chọn **hai đóng góp chính đầu tiên** làm trục báo cáo; đóng góp thứ ba bổ trợ nếu bằng chứng đủ rõ.

| Ứng viên đóng góp | Cách tiếp cận để đối chiếu | Bằng chứng cần chuẩn bị |
|---|---|---|
| **C1. Xử lý mutation nhất quán khi gửi lại và ghi đồng thời** | Cách gửi lại request thông thường không giữ định danh thao tác và không kiểm tra revision có nguy cơ ghi trùng/ghi đè. Giải pháp dùng requestId, receipt, transaction và khóa. | Mất phản hồi sau commit rồi replay đúng key/payload; cùng key khác payload bị từ chối; hai tab ghi cùng revision; kiểm tra chỉ một Submission và không thưởng trùng cho các ca tương ứng. |
| **C2. Phục hồi lượt học qua lưu cục bộ, journal và đối soát server** | Lưu trạng thái chỉ trong RAM trình duyệt hoặc chỉ phụ thuộc response autosave không đủ xác định trạng thái khi reload/timeout. | Lưu IndexedDB đã commit → reload; replay pending command → GET; ACK cũ không đè bản nhập mới; reconnect đăng ký lại; 409 giữ dữ liệu để người dùng đối chiếu. Đo thời gian phục hồi và số bản ghi/thao tác thiếu hoặc trùng trong mẫu thử. |
| **C3. Giữ ngữ nghĩa kết quả khi nội dung và dữ liệu dẫn xuất thay đổi** | Đọc/chấm lại theo học liệu hiện tại có thể làm lệch lượt cũ. Giải pháp chốt version, giữ FIRST/Submission và xử lý dẫn xuất theo ledger. | Publish/retire sau khi tạo lượt vẫn đọc/chấm bản đã chốt; chữa không đổi điểm gốc; restart/replay không tạo lại kết quả hoặc XP trong các ca đã kiểm tra. |

Đây là **ứng viên đóng góp**, chưa phải kết luận thực nghiệm. Nếu dùng baseline khái niệm thì ghi rõ là phân tích thiết kế. Muốn kết luận cải thiện định lượng cần phép so sánh tái hiện được với cùng môi trường/dataset; không tự tạo số baseline hoặc tắt cơ chế bảo vệ trên demo để có số đối chiếu.

## 6. Kế hoạch kiểm thử và thực nghiệm

Ưu tiên ít nhất một nhóm đo hiệu năng ứng dụng mạng và một nhóm kiểm chứng độ tin cậy để hỗ trợ trực tiếp C1–C2. Các mức tải dưới đây là **đề xuất**, không phải tải hệ thống đã đạt.

| Nhóm | Kịch bản dự kiến | Chỉ số/kết quả cần ghi |
|---|---|---|
| **E1. Chức năng và quyền** | Luồng đăng nhập → học → lưu → nộp → kết quả/ôn/thưởng/thống kê; tác giả xuất bản; quyền giữa ba vai trò. | PASS/FAIL theo case và phiên bản; trạng thái HTTP; rò rỉ dữ liệu/quyền sai nếu có; tách test tự động và thao tác tay. |
| **E2. HTTP và nhiều client** | Đề xuất 1, 5, 10, 20 client có phiên riêng trên môi trường test; đọc catalog/snapshot, lưu nháp, nộp bài. Tách phép đo GET, mutation và request bị từ chối đúng thiết kế. | Latency p50/p95 theo endpoint, request thành công/giây, số request, lỗi/timeout và số client thực; kiểm tra trạng thái dữ liệu sau lượt đo. |
| **E3. Gửi lại và concurrency** | Bỏ response sau commit, replay cùng key, key bị dùng lại sai payload, hai writer cùng revision, submit đồng thời. | Số request so với mutation/Submission/ledger thực; số conflict đúng dự kiến; trạng thái sau GET; thời gian đối soát. |
| **E4. WebSocket và phục hồi** | Nhiều socket/nhiều tab; mất socket trong khi HTTP còn hoạt động; reconnect/resubscribe; lưu cục bộ rồi reload; restart backend và đăng nhập lại khi cần. | Số socket/user, thời gian kết nối lại và đối soát, thời gian phát hiện mất kết nối, dữ liệu còn/thiếu/trùng; điều kiện heartbeat/backoff. |
| **E5. Bền vững và dữ liệu dẫn xuất** | Restart/recreate giữ volume, chạy lại seed/replay, cập nhật học liệu, worker xử lý thưởng; đối chiếu nháp/kết quả/tiến độ/review/XP. | Trạng thái và số bản ghi trước/sau; không đổi Submission/FIRST trong mẫu kiểm tra; độ trễ thưởng nếu đo; giới hạn chưa kiểm chứng. |

Cách ghi nhận để kết quả có thể tái hiện:

1. Chốt source, phần cứng, OS, phiên bản runtime/container, schema/policy, dataset, mạng và thời gian đo. Ghi rõ chạy trên cùng máy hay qua LAN; không gọi loopback là thử nghiệm Internet.
2. Dùng môi trường/volume test riêng, không dùng DB demo cho thao tác tải hoặc lỗi. Dataset phải đủ điều kiện tạo lượt mới; không để request resume/replay bị tính nhầm thành submit mới.
3. Với E2, đề xuất warm-up rồi đo 60 giây/mức tải, lặp 3 lần nếu đủ điều kiện. Nếu đổi cách đo, ghi lại thời lượng, mẫu và lý do; lưu kết quả từng lần.
4. Định nghĩa latency từ lúc client gửi đến khi nhận đủ response; throughput thành công là số request thành công chia thời gian đo. Báo riêng lỗi bất ngờ và response từ chối đúng thiết kế như 409 trong bài thử conflict.
5. Phân biệt mock/fixture, cố ý bỏ response sau commit và timeout/mất mạng thật. Đo thời gian phục hồi theo mốc bắt đầu/kết thúc đã định nghĩa, không chỉ dựa vào ảnh màn hình.
6. Lưu log và JSON/CSV đo thô đã loại thông tin nhạy cảm, bảng tổng hợp, hình và lệnh tái hiện. Kiểm tra số bản ghi nghiệp vụ sau tải; không lấy HTTP 2xx làm bằng chứng duy nhất cho tính đúng.

Sản phẩm cần có: bảng kết quả chức năng theo bản chốt; bảng E2 và biểu đồ latency/throughput; bảng E3–E5 trước/sau; một mục Discussion giải thích cả kết quả và giới hạn. Chỉ ghi PASS hoặc điền số sau khi thực sự chạy và kiểm tra bằng chứng.

## 7. Thứ tự thực hiện và đầu ra

Lịch dưới đây là **dự kiến** để kịp hạn trong Submission.md; điều chỉnh theo kết quả thực tế.

| Mốc | Công việc | Đầu ra/điều kiện xong |
|---|---|---|
| **27/10** | Chốt đề cương, tên, thông tin nhóm; xác định source/dataset dùng báo cáo; lập danh sách tư liệu có sẵn và còn thiếu. | Kế hoạch này; bảng yêu cầu–bằng chứng; thông tin còn thiếu có người phụ trách. |
| **28/10** | Viết bài toán, cơ sở, kiến trúc, giải pháp và thiết kế mạng; dựng sơ đồ; chuẩn bị kịch bản đo. | Bản nháp mục 4–10, hình và bảng protocol; kịch bản/metric rõ ràng. |
| **29/10** | Chạy kiểm tra cần thiết trên bản chốt; thực hiện nhóm đo đã chọn; tổng hợp log và phân tích. | Mục 11–14 có số liệu thật, cấu hình, nguồn và giới hạn. Nếu thiếu kết quả, ghi rõ và ưu tiên đo bù. |
| **30/10** | Hoàn thiện hạn chế, hướng phát triển, kết luận, Abstract, References và phụ lục; rà lại nguồn, hình/bảng, README và báo cáo Word. | Bản báo cáo đầy đủ; source build/run/test theo hướng dẫn; các thành viên kiểm tra phần đóng góp thực tế. |
| **31/10, trước 23:59** | Kiểm tra Compilatio, chỉnh sửa có trích dẫn đúng, kiểm tra lại bản cuối; đóng gói và bàn giao theo thông báo học phần. | Một file báo cáo đúng tên, đạt ngưỡng; source đầy đủ; gói Source/Report; xác nhận đã nộp đúng phiên bản. |

Phân công theo công việc: nội dung bài toán/tài liệu liên quan; kiến trúc/giao thức/concurrency; thực nghiệm/số liệu; biên tập/trích dẫn/đóng gói. Điền người thực hiện theo đóng góp thật, không tự gán tên hoặc chia công việc giả định. Mọi thành viên cần giải thích được toàn bộ kiến trúc và các luồng quan trọng.

## 8. Checklist hoàn tất và nộp bài

- [ ] Chốt số nhóm, thành viên/MSSV/lớp, giảng viên, tên đề tài và phiên bản source.
- [ ] Báo cáo đủ 19 nội dung, có sơ đồ kiến trúc, sequence, protocol/message và phân tích concurrency/error handling.
- [ ] Có thực nghiệm, số đo thật, nguồn dữ liệu, cấu hình/lệnh tái hiện và Discussion; không dùng dữ liệu giả lập để khẳng định hiệu quả học tập.
- [ ] Contributions phân biệt phần kế thừa/tự phát triển; mỗi kết luận có bằng chứng; giới hạn và phần chưa kiểm tra được nêu rõ.
- [ ] Trích dẫn thống nhất; nguồn học liệu/hình/bảng/code được ghi; không có credentials thật hoặc dữ liệu riêng trong báo cáo/log/source nộp.
- [ ] Kiểm tra trên Compilatio và đạt **Similarity + Plagiarism ≤ 20%** theo Instruction §10.
- [ ] Chuẩn bị **01 báo cáo** trên Compilatio theo tên `NhomXX_EnglishLearning_Report.docx`; thay `XX` bằng số nhóm thật. Submission khuyến nghị `.docx`; ví dụ `report.pdf` ở Instruction không thay cho hướng dẫn nộp này.
- [ ] Chuẩn bị source hoàn chỉnh tên `NhomXX_EnglishLearning_Source`, có mã, cấu hình mẫu, migration/schema, dữ liệu/tài nguyên cần thiết, dependency hoặc hướng dẫn cài và README build/run/test.
- [ ] Gói bàn giao cuối theo Submission: `NhomXX_EnglishLearning/Source/` và `NhomXX_EnglishLearning/Report/`; thư mục Report chứa đúng báo cáo cuối đã nộp Compilatio. Lớp trưởng thu Source/Report vào USB.
- [ ] Kiểm tra thông báo/link **Google Drive** thực tế với lớp/giảng viên: Instruction §14 vẫn nêu kênh này nhưng chưa có link; Submission nêu bàn giao USB. Không tự coi yêu cầu Drive đã được bỏ.
- [ ] Thử build/run/test và kịch bản demo từ bộ source bàn giao; kiểm tra tên nhóm, đúng project, file không rỗng và đủ tài nguyên.
- [ ] Chuẩn bị bảo vệ: vẽ kiến trúc, giải thích một HTTP request và một WebSocket flow, replay/conflict/recovery, transaction, code quan trọng, metric và giới hạn thực nghiệm.
- [ ] Hoàn tất trước **23:59 ngày 31/10/2026**; thay đổi source/báo cáo sau hạn cần giảng viên xác nhận theo Submission §4.
