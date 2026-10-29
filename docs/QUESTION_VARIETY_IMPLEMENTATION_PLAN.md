> Quyết định mới 29/10/2026: bài chọn dạng tùy ý trong bảy loại theo thứ tự chuẩn, không bắt đủ dạng; 15–20 câu LEARN, ít nhất hai câu mỗi dạng với một GUIDED đầu tiên. Phần dưới là lịch sử thiết kế trước quyết định này.

> Cập nhật quyết định 28/10/2026: vẫn 18 câu, bớt một câu tự làm ở mỗi phần đọc để thêm 2 câu nghe chọn đáp án ở cuối. Cấu trúc mới 3/2/2/3/3/3/2, 7 GUIDED + 11 INDEPENDENT. Các mô tả sáu phần/6+12 dưới đây ghi lại thiết kế ban đầu; tiến độ hiện hành tại QUESTION_VARIETY_PROGRESS.md. Bài/lượt cũ được giữ nguyên.

# Kế hoạch mở rộng dạng bài và số câu trong vòng học

Ngày lập: **28/10/2026**. Nền mã nguồn tham chiếu: nhánh `main`, commit `fac44f4`.

**Trạng thái: đã triển khai và kiểm tra đợt bài mẫu trên working tree ngày 28/10/2026, chưa commit.** Xem [tiến độ thực tế](QUESTION_VARIETY_PROGRESS.md) và [bằng chứng kiểm tra](../statics/results/question-variety-validation.md). Theo yêu cầu chạy tiếp theo, demo chính 5176/8083 đã lên V12 và có bài mẫu Lesson 11 ở Vòng 2; mở rộng toàn bộ ngân hàng và bài viết tiếp tục để sau.

## 1. Mục tiêu và phạm vi

- Bài nâng cấp có **đúng 18 câu/bài/lượt LEARN**, gồm 6 GUIDED và 12 INDEPENDENT.
- Giữ đủ bốn dạng đang có: trắc nghiệm, điền từ, ghép cặp, sắp xếp từ thành câu.
- Theo điều chỉnh của người dùng: **mỗi dạng hoạt động có đúng 1 câu GUIDED mở đầu; các câu còn lại là INDEPENDENT**. Hai kiểu đọc được tính là hai hoạt động riêng dù tái sử dụng kiểu đáp án cũ.
- Bổ sung trải nghiệm **đọc đoạn văn dài và trả lời các câu tương ứng**, gồm điền chỗ trống và trắc nghiệm đúng bốn lựa chọn.
- Đưa một bài viết theo chủ đề vào cuối lộ trình mở rộng, **triển khai sau**, không nằm trong đợt đọc hiểu.

### Phân biệt vòng, bài và lượt làm

Trong hệ thống hiện tại, **Unit là một vòng**, một vòng có nhiều **Lesson**, và học viên tạo một **Attempt** cho mỗi lần làm Lesson. Vì vậy cần công bố cả số câu của từng bài và tổng số câu trong vòng.

**Đã chốt:** 18 câu cho mỗi bài/lượt LEARN, không phải tổng 18 câu cho cả Unit. Một vòng có hai bài nâng cấp sẽ có tổng 36 câu. Quy tắc 18 câu thuộc cấu trúc bài nâng cấp và được kiểm tra khi biên soạn/xuất bản theo cấu trúc đó; không cắt hoặc nhân thêm câu trong bộ chọn để ép mọi bài lịch sử thành 18 câu.

Mốc này áp dụng cho bài mới hoặc phiên bản bài được nâng cấp. Không ép lượt đang làm hoặc bài lịch sử phải có thêm câu, không áp mốc 18 cho REVIEW hay PRACTICE cũ.

### Các quyết định đã được xác nhận

1. Mỗi bài/lượt LEARN nâng cấp có 18 câu.
2. Mỗi dạng xuất hiện trong bài có đúng một GUIDED mở đầu, lặp lại quy tắc này ở mỗi bài. Không bắt buộc CHECK câu hướng dẫn để làm tiếp.
3. Thứ tự: **trắc nghiệm → ghép cặp → sắp xếp từ → điền từ → đọc điền từ → đọc hiểu**. Ba phần đầu giữ thứ tự tương đối từ đề xuất trước; ba phần cuối theo yêu cầu người dùng.
4. Đọc điền từ cho nhập **trực tiếp trong đoạn văn**; mỗi ô là một câu lưu/chấm riêng bằng ID ổn định.
5. Mỗi hoạt động đọc có một đoạn ví dụ ngắn riêng cho GUIDED, sau đó là đoạn chính dành cho INDEPENDENT.
6. Triển khai một bài mẫu hoàn chỉnh để nghiệm thu trước, rồi mở rộng ngân hàng; nội dung cũ nâng bằng phiên bản mới và giữ lượt/kết quả lịch sử.
7. Giữ cách chấm, hoàn thành và chính sách thưởng hiện hành; kiểm tra tác động XP. Bài viết theo chủ đề thực hiện sau.

## 2. Hiện trạng đã đối chiếu

| Thành phần | Hiện trạng | Hệ quả khi triển khai |
|---|---|---|
| Bộ chọn LEARN | `AttemptSelector` lấy toàn bộ GUIDED rồi INDEPENDENT trong LessonVersion; loại REVIEW_VARIANT | Không có giới hạn cố định 10 câu cần đổi thành 18; cần bổ sung nội dung và quy tắc thứ tự theo hoạt động để mỗi câu hướng dẫn đứng trước phần tự làm tương ứng |
| Bộ seed nền | `DemoCurriculum` kiểm tra 6 Lesson, mỗi bài 5 GUIDED + 5 INDEPENDENT + 5 REVIEW_VARIANT | 15 câu trong ngân hàng của bài hiện chỉ tạo 10 câu LEARN; không tính câu ôn vào mốc 15+ |
| Dạng câu | Đã hỗ trợ SINGLE_CHOICE, FILL_BLANK, MATCHING, ORDERING | Giữ nguyên hợp đồng chấm bốn dạng; seed nền chưa có ORDERING |
| Học liệu đọc | READING có `readingText`, parser hiện cho tối đa 12.000 ký tự | Có nền để chứa đoạn dài, không cần nhét cả bài đọc vào prompt |
| Liên kết học liệu | Nhiều câu có thể cùng tham chiếu `materialVersionId`; snapshot gom material theo phiên bản | Tái sử dụng một đoạn cho nhiều câu và giữ nội dung đã chốt trong lượt |
| Giao diện học | Hiển thị học liệu của câu đang chọn; điều hướng từng câu | Cần bổ sung cách trình bày nhóm đọc và chuyển câu thuận tiện |
| Giới hạn biên soạn | Lesson nhận tối đa 100 slot; FILL_BLANK hiện tối đa 5 ô/câu | Mẫu 18 câu nằm trong giới hạn; không gom cả bộ đọc thành một câu quá nhiều ô |

Các con số seed trên là của bộ nền trong source, không phải khẳng định dữ liệu demo đang chạy có đúng cấu trúc đó. Những script dữ liệu khác cần kiểm tra riêng trước khi cập nhật.

Tài liệu nền: [vòng học hiện tại](LEARNING_JOURNEY_PROGRESS.md), [kế hoạch M03](M03_IMPLEMENTATION_PLAN.md), [hợp đồng M02–M03](../source/common/M02_M03_API.md).

## 3. Cấu trúc bài đã chốt

Mẫu khởi đầu **18 câu được chấm riêng**:

| Phần | Nội dung | Số câu | Phân bổ vai trò |
|---|---|---:|---|
| 1 | Trắc nghiệm thông thường | 3 | 1 GUIDED + 2 INDEPENDENT |
| 2 | Ghép cặp | 2 | 1 GUIDED + 1 INDEPENDENT |
| 3 | Sắp xếp từ thành câu | 2 | 1 GUIDED + 1 INDEPENDENT |
| 4 | Điền từ thông thường | 3 | 1 GUIDED + 2 INDEPENDENT |
| 5 | Đọc điền từ: ví dụ ngắn và đoạn chính | 4 | 1 GUIDED ở ví dụ + 3 INDEPENDENT ở đoạn chính |
| 6 | Đọc hiểu: ví dụ ngắn và đoạn chính, mỗi câu có 4 đáp án | 4 | 1 GUIDED ở ví dụ + 3 INDEPENDENT ở đoạn chính |
| **Tổng LEARN** | **6 GUIDED + 12 INDEPENDENT** | **18** | Không gồm REVIEW_VARIANT |

Đây là cấu trúc cho bài mẫu và các bài được nâng cấp theo đợt này: đúng 18 câu, đủ bốn dạng cũ và cả hai kiểu đọc mới. Không thay đổi nội dung bài lịch sử để cưỡng ép theo mẫu. REVIEW_VARIANT được biên soạn riêng theo mục tiêu ôn, không lấy đáp án đã lộ của bài chính làm câu mới.

Trong mỗi hoạt động, câu GUIDED đầu tiên cho phép mở gợi ý và kiểm tra để làm quen cách làm; các câu tiếp theo tự làm, chỉ xem đáp án/giải thích sau nộp. Câu GUIDED vẫn nằm trong tổng 18 câu và vẫn được tính theo luật hiện hành, không phải câu mẫu cộng thêm. Tổng vẫn là **6 GUIDED + 12 INDEPENDENT**, nhưng phân bổ một câu hướng dẫn cho từng hoạt động thay vì dồn vào trắc nghiệm và điền từ thông thường. Không bắt buộc CHECK câu hướng dẫn mới được làm các câu sau.

Cách đếm thống nhất:

- Một câu đọc trắc nghiệm là một AttemptQuestion, dù dùng chung bài đọc với các câu khác.
- Một chỗ trống của đoạn đọc mới là một AttemptQuestion FILL_BLANK có một blank. Một chỗ ở đoạn ví dụ GUIDED và ba chỗ ở đoạn chính INDEPENDENT được tính là bốn câu.
- Một câu MATCHING vẫn tính là một câu, không tính mỗi cặp là một câu; không đổi cách đếm FILL_BLANK nhiều ô của nội dung cũ.
- Đoạn văn học liệu không tự được tính thêm một câu. Tổng câu trên giới thiệu, runner, kết quả và thống kê phải thống nhất.

Độ dài đoạn chính khởi đầu đề xuất: khoảng **150–300 từ tiếng Anh**, điều chỉnh theo trình độ sau khi thử nội dung. Đoạn ví dụ GUIDED ngắn hơn và dùng nội dung riêng. Đây là định hướng biên soạn, không phải giới hạn parser mới hoặc cam kết thời gian hoàn thành.

## 4. Hai kiểu bài đọc dài

### 4.1. Đọc hiểu và chọn một trong bốn đáp án

1. Tạo và xuất bản hai Material READING: một đoạn ví dụ ngắn cho GUIDED và một đoạn chính cho INDEPENDENT, có tiêu đề, chia đoạn rõ, ghi nguồn.
2. Tạo 1 câu SINGLE_CHOICE GUIDED tham chiếu phiên bản đoạn ví dụ và 3 câu INDEPENDENT cùng tham chiếu phiên bản đoạn chính; mỗi câu có đúng 4 option ID duy nhất và một đáp án đúng.
3. Nội dung câu có thể hỏi ý chính, chi tiết, suy luận đơn giản hoặc nghĩa từ trong ngữ cảnh, phù hợp trình độ bài.
4. Đưa các câu vào cùng hoạt động đọc trong Lesson: câu đầu GUIDED, ba câu sau INDEPENDENT. Hint và giải thích của câu đầu chỉ hướng dẫn câu đó, không tiết lộ đáp án các câu sau.

Yêu cầu bốn lựa chọn áp dụng cho bộ đọc mới, không tự sửa toàn bộ câu trắc nghiệm cũ. Nếu bổ sung quy tắc xuất bản cho loại nhóm đọc này, cần định danh nhóm rõ ràng; không áp điều kiện bốn option cho mọi câu có học liệu READING.

### 4.2. Đọc đoạn văn và điền chỗ trống

1. Tạo một Material READING ví dụ ngắn có một chỗ trống cho GUIDED và một Material READING chính có ba chỗ trống cho INDEPENDENT; không chứa bản hoàn chỉnh kèm đáp án.
2. Mỗi vị trí có một câu FILL_BLANK riêng, tham chiếu MaterialVersion tương ứng; prompt ghi rõ chỗ cần điền. `content.parts` có nhãn ngữ cảnh và một blank ID ổn định.
3. `answerKey` giữ các từ chấp nhận, cùng cờ chuẩn hóa hiện có. Giữ luật NFC/whitespace/case; không tự thêm fuzzy matching.
4. Trình biên soạn và preview cần giúp đối chiếu số chỗ trong đoạn với câu tương ứng, phát hiện thiếu, trùng số hoặc số không tồn tại.
5. Câu GUIDED dùng đoạn ví dụ riêng; ba câu INDEPENDENT dùng đoạn chính. Feedback của câu hướng dẫn chỉ giải thích ví dụ, không trả bản điền đầy đủ của đoạn chính hoặc gợi ý trực tiếp đáp án phần tự làm.

**Đã chốt nhập trực tiếp giữa đoạn văn ngay trong đợt đầu.** Đoạn chính hiển thị đồng thời ba ô nhập; mỗi ô ánh xạ tới một AttemptQuestion và blank ID riêng. Focus một ô cập nhật câu đang chọn; sửa ô đó chỉ cập nhật đáp án item tương ứng, không làm mất hai ô còn lại. Đoạn ví dụ có ô GUIDED riêng và nút hint/check theo luật hiện hành.

Cần hợp đồng cấu trúc gồm phần văn bản, vị trí trống có ID và ánh xạ tới câu/blank thuộc phiên bản bài. Snapshot công khai ánh xạ sang attemptQuestionId của lượt, không mang khóa đáp án. Không phân tích chuỗi dấu gạch dưới hoặc số hiển thị để xác định nơi lưu. Khi F5, resume, xung đột và chữa bài, dùng cùng ánh xạ ID để phục hồi đúng từng ô. REVIEW chọn một item phải hiển thị được ô cần làm và ngữ cảnh, không yêu cầu lưu/nộp các item không thuộc lượt REVIEW.

### 4.3. Tái sử dụng mô hình hiện có

Đọc dài là **kiểu hoạt động học**, còn kiểu đáp án vẫn là SINGLE_CHOICE hoặc FILL_BLANK. Đợt đầu không cần thêm enum READING_CHOICE/READING_CLOZE hoặc một bộ chấm thứ hai.

Mỗi hoạt động đọc có liên kết nhóm rõ ràng để gắn câu GUIDED dùng đoạn ví dụ với các câu INDEPENDENT dùng đoạn chính. Không thể chỉ nhóm theo `materialVersionId` vì hai phần dùng hai học liệu khác nhau. Bốn hoạt động thông thường cũng cần có thứ tự ổn định trong cấu trúc bài nâng cấp; loại hoạt động không được suy ra chỉ từ việc câu hỏi có material READING.

Cần chốt quy tắc nhóm/thứ tự cho bài nâng cấp trước khi triển khai: bộ chọn hiện gom tất cả GUIDED lên đầu, nên chỉ đổi số lượng role sẽ chưa tạo được trình tự mong muốn. Khi tạo lượt mới, xếp theo hoạt động, trong mỗi hoạt động đặt GUIDED trước INDEPENDENT, giữ thứ tự position tương ứng, rồi lưu thứ tự đã chọn vào AttemptQuestion như cơ chế hiện có. Không chỉ sắp lại mảng ở giao diện; không sắp lại lượt đang tồn tại. Nếu một bài có nhiều nhóm cùng dạng hoặc dùng chung material cho nhiều hoạt động, cần định danh nhóm rõ thay vì đoán từ chuỗi prompt. Mỗi câu vẫn giữ questionVersionId, originKey, objective, difficulty và trạng thái riêng.

Chặng thiết kế kỹ thuật phải đặc tả cấu trúc nhóm, thứ tự và vị trí trống trong authoring/DTO/snapshot, cách pin theo phiên bản và fallback cho bài cũ. Tái sử dụng các trường JSON phù hợp nếu đáp ứng được hợp đồng; không mặc định là chỉ đổi frontend hoặc chắc chắn không cần migration. Source hiện đã có V11; nếu cần thay schema thì thêm migration mới theo trạng thái Git lúc triển khai, không sửa migration đã áp dụng.

## 5. Trải nghiệm học và biên soạn

### Học viên

- Trang giới thiệu ghi tổng câu thực làm, số phần đọc và mô tả ngắn; tránh nhầm với số câu dành riêng cho ôn.
- Nhóm đọc có tiêu đề và chỉ báo câu đã trả lời: đọc điền từ ở câu 11–14, đọc hiểu ở câu 15–18; mỗi nhóm bắt đầu bằng ví dụ GUIDED riêng và có điều hướng tới phần tự làm.
- Phần đọc điền từ cho nhập ngay trong đoạn; nhãn truy cập nêu số câu/chỗ trống. Điều hướng câu đưa focus tới đúng ô, không render thêm một form đáp án khác thiếu đồng bộ.
- Desktop bố trí đoạn đọc cạnh câu hỏi nếu đủ chỗ; điện thoại đặt đoạn đọc phía trên, có cách thu gọn/mở lại và giữ văn bản dễ đọc.
- Chuyển câu dùng cùng đoạn chính giữ vùng bài đọc ổn định; chuyển từ ví dụ sang phần tự làm đổi sang đúng học liệu chính. F5/tiếp tục phục hồi đúng câu đang chọn và đáp án bằng session/playerState M06. Khôi phục vị trí cuộn chính xác chưa là tiêu chí MVP.
- Giữ xuống dòng của đoạn văn, render văn bản an toàn, hỗ trợ bàn phím và không dùng màu làm dấu hiệu duy nhất.
- Lưu nháp, nộp, kết quả và chữa vẫn hoạt động theo từng câu. Không tự nộp riêng nhóm đọc hoặc tạo Submission phụ.
- Hai kiểu đọc có nhãn trong giao diện dù dùng lại kiểu đáp án cũ; điều hướng game cần phân biệt câu trắc nghiệm/điền từ thường với các phần đọc, thay vì chỉ lọc theo `question.type` như hiện tại.

### Giáo viên và quản trị

- Có hướng dẫn tạo đoạn ví dụ và đoạn chính → thêm câu tham chiếu đúng phiên bản → gắn nhóm và ánh xạ ô trống → sắp thứ tự → preview → xuất bản.
- Preview cho thấy bài đọc và bộ câu tương ứng, số lựa chọn, số chỗ trống, tổng GUIDED/INDEPENDENT/REVIEW_VARIANT.
- Kiểm tra bài theo cấu trúc nâng cấp có đúng 18 câu, đủ sáu hoạt động và phân bổ role/thứ tự theo mục 3 khi xuất bản; không áp kiểm tra này hồi tố lên bài cũ. Cho lưu nháp chưa đủ để tác giả tiếp tục biên soạn.
- Mỗi hoạt động có đúng một GUIDED mở đầu; preview hiển thị rõ phần hướng dẫn và phần tự làm, kiểm tra role và thứ tự. Với hai nhóm đọc, dùng đoạn ví dụ riêng cho GUIDED, rà soát hint/giải thích để không lộ đáp án đoạn chính.
- Cập nhật bài đọc phải xuất bản Material/Question/LessonVersion mới qua luồng hiện có. Không đổi học liệu đã chốt trong lượt đang làm.

## 6. Các quy tắc cần giữ

- Lượt cũ giữ nguyên số câu, thứ tự đã chốt, phép đảo đáp án và phiên bản học liệu. Chỉ lượt mới dùng LessonVersion mới.
- Submit vẫn nhận full snapshot, có thể nộp thiếu nhưng chỉ hoàn thành LEARN khi mọi đáp án đầy đủ; không yêu cầu đúng hết hoặc CHECK GUIDED trước.
- Chấm mỗi AttemptQuestion bằng bộ chấm hiện tại. Không tăng trọng số cho nhóm đọc trong đợt đầu; FIRST, điểm Submission và kết quả cũ giữ bất biến.
- Snapshot/IndexedDB trước nộp không được thêm answerKey, lời giải hoặc đoạn văn hoàn chỉnh làm lộ từ điền. Feedback và kết quả tuân thủ quyền hiện có.
- Autosave, receipt, replay đúng key/payload, xung đột revision, phân quyền owner và transaction M03/M06 giữ nguyên.
- REVIEW có thể chọn một câu đọc cùng học liệu cần thiết, không bắt đủ cả nhóm hoặc ép lên 18 câu. Không đổi ReviewPolicy hoặc cách xác định câu mới qua originKey.
- M05 tiếp tục dùng luật thưởng hiện hành; cần kiểm tra số câu tăng có ảnh hưởng XP/cap/streak/League thế nào, không tự đổi chính sách thưởng. M07 ghi nhận theo từng câu/FIRST, không cộng thêm bản ghi cho cả đoạn đọc.
- Vòng vẫn hoàn thành theo các Lesson đang công bố. Xuất bản phiên bản mới không xóa COMPLETED cũ; không thêm bài viết chưa triển khai vào điều kiện mở vòng.

## 7. Các chặng triển khai

### Chặng 0 — Đặc tả kỹ thuật theo các quyết định đã chốt

- [x] Chốt 18 câu/bài/lượt, một GUIDED cho mỗi dạng ở mỗi bài, thứ tự sáu phần, nhập trong đoạn, ví dụ đọc riêng và phạm vi một bài mẫu trước.
- [x] Lập bảng bài mẫu: mỗi hoạt động đúng 1 GUIDED, số INDEPENDENT còn lại, objective, hai đoạn đọc chính, hai đoạn ví dụ và câu ôn riêng.
- [x] Đặc tả dữ liệu nhóm hoạt động nối hai material ví dụ/chính, vị trí trống và ánh xạ câu/blank ID, câu bốn lựa chọn; quyết định schema/DTO và tương thích bài cũ.
- [x] Chốt cách lưu/đọc thứ tự hoạt động cho bài nâng cấp để không gom cả sáu câu GUIDED lên đầu lượt; lượt cũ giữ nguyên.
- [x] Chốt tiêu chí kiểm tra, file API/tài liệu cần cập nhật; thiết kế tương thích lượt cũ trước khi sửa payload.

**Đầu ra:** đặc tả nội dung và ví dụ một bài 18 câu có đủ hai kiểu đọc.

### Chặng 1 — Nâng nội dung và hỗ trợ biên soạn

- [x] Bổ sung ORDERING thực tế và phân bổ đủ bốn dạng cũ trong bộ bài nâng cấp.
- [x] Phân bổ một GUIDED cho mỗi hoạt động, kiểm tra thứ tự chọn và lưu bộ câu của lượt mới theo cấu trúc đã chốt.
- [x] Biên soạn hai đoạn đọc chính và hai đoạn ví dụ ngắn, 4 câu đọc trắc nghiệm và 4 câu đọc điền từ cho bài mẫu; mỗi phần có 1 câu ví dụ GUIDED + 3 câu tự làm.
- [x] Hoàn thiện preview, kiểm tra liên kết/đáp án/số chỗ, thống kê số câu và hướng dẫn biên soạn.
- [x] Thiết kế bộ seed bổ sung có datasetVersion, khóa nội dung ổn định và checkpoint riêng qua API; giữ bộ nền và phần tác giả đã chỉnh sửa. Đợt đầu tạo Lesson riêng, chưa nâng cùng Lesson hoặc dùng seedKey nội bộ của bootstrap. Nếu nâng cùng Lesson sau này thì chỉ tạo phiên bản mới qua luồng có kiểm tra revision, không ghi đè draft.
- [x] Xác định rõ giới hạn kiểm tra cứng 90 câu/5–5–5 trong `DemoCurriculum`: giữ validator bộ nền hoặc tách validator bộ mở rộng, không chỉ sửa các con số để bỏ kiểm tra.

**Đầu ra:** bài mẫu 18 câu tạo và xuất bản được trong môi trường kiểm tra riêng; seed chạy lại không nhân bản hoặc làm mất dữ liệu.

### Chặng 2 — Giao diện đọc dài và điều hướng

- [x] Bổ sung nhóm đọc trong runner, bố cục desktop/mobile và phần giới thiệu.
- [x] Bổ sung render đoạn có ô nhập và ánh xạ theo ID; focus/điều hướng, autosave, phục hồi, conflict và chữa từng ô.
- [x] Bổ sung lối chọn hai kiểu đọc trong giao diện game; giữ các tương tác trắc nghiệm/điền từ/ghép/xếp cũ.
- [x] Giữ đáp án theo item ID, trạng thái câu hiện tại và khôi phục M06; kiểm tra đoạn văn nhiều dòng.
- [x] Bổ sung cách xem kết quả/giải thích theo nhóm đọc, chữa từng câu và trở về vòng.

**Đầu ra:** học viên làm trọn bài mẫu, lưu/tiếp tục/nộp/chữa được trên desktop và điện thoại.

### Chặng 3 — Kiểm tra tích hợp và bàn giao

- [x] Chạy kiểm tra theo mục 9 trên source mới và môi trường riêng.
- [x] Rà soát tác động M04/M05/M07, tải payload và thời gian đáp ứng của bài 18 câu có đoạn dài.
- [x] Ghi bằng chứng thực tế, lỗi và giới hạn; cập nhật tiến độ/API/README khi hành vi tương ứng đã đổi.
- [x] Chuẩn bị hướng dẫn áp dụng bộ nội dung nâng cấp cho demo có giữ dữ liệu; không tự reset volume hoặc nâng demo trong bước viết kế hoạch này.

**Đầu ra:** biên bản nghiệm thu riêng cho đợt tăng câu và đọc dài. Không dùng số PASS lịch sử để thay bằng chứng mới.

### Chặng 4 — Bài viết theo chủ đề, thực hiện sau

Định hướng: cuối vòng có **một nhiệm vụ viết theo chủ đề của Unit**. Chỉ bắt đầu sau khi chặng đọc dài ổn định và có đặc tả riêng.

Cần quyết định trước khi triển khai:

- Vị trí nhiệm vụ: Lesson cuối hoặc hoạt động riêng gắn Unit; số từ, đề bài và hướng dẫn theo trình độ.
- Cơ chế lưu bản nháp/nộp, quyền đọc và sửa; ai nhận bài, ai chấm và rubric nào được dùng.
- Cách phản hồi, sửa lại, thống kê; có/không tích hợp công cụ chấm tự động phải được quyết định riêng.
- Quan hệ với hoàn thành vòng, điểm và XP. Không mặc định bài viết được chấm đúng/sai bằng AnswerGrader hiện tại hoặc tự động có thưởng.

**Chưa tạo enum WRITING, migration, giao diện nhập bài viết hoặc điều kiện khóa vòng trong chặng 0–3.** Một bài viết dự kiến là một hoạt động riêng, không được dùng để làm đủ mốc 18 câu khách quan.

## 8. Điểm vào mã nguồn khi triển khai

| Phần việc | Thành phần cần xem |
|---|---|
| Bộ chọn và đếm câu | `AttemptSelector`, `LearnerCatalogService`, `LessonIntroduction.tsx` |
| Biên soạn và xác thực | `AuthoringRequests`, `QuestionValidator`, `AuthoringPublication`, `QuestionFields.tsx`, trình biên soạn Lesson và preview |
| Học liệu và snapshot | `MaterialRequests`, `MaterialData`, `PublishedContentReader`, `AttemptSnapshotReader`, `AttemptViews` |
| Giao diện học/đáp án/kết quả | `AttemptRunner.tsx`, `LearningMaterial.tsx`, `AnswerFields.tsx`, `AttemptResult.tsx`, `GameWorld.tsx`, CSS liên quan |
| Lưu và phục hồi | `session.ts`, `features/learning/types.ts`, storage/journal M06; chỉ đổi khi hợp đồng thật sự cần |
| Bộ chấm/hoàn thành | `AnswerGrader`, `AnswerValidator`, `AttemptCompletionService`, `LearningProgressService` |
| Dữ liệu mẫu | `DemoCurriculum`, `CurriculumSeedService`, `src/main/resources/demo/`; rà soát script dữ liệu bổ sung trước khi dùng |
| Tích hợp | `ReviewQuestionPool`, provider M04, luật/worker M05 và thống kê M07 theo phạm vi bị ảnh hưởng |

Giữ kiến trúc Controller → Service → Repository/JPA. Không chuyển nghiệp vụ chọn/chấm câu sang controller hoặc trình duyệt để làm tính năng đọc.

## 9. Ma trận kiểm tra và tiêu chí nghiệm thu

| Nhóm | Tiêu chí |
|---|---|
| Nội dung | Bài mẫu có 18 câu LEARN đúng 6 GUIDED + 12 INDEPENDENT; mỗi một trong sáu hoạt động có đúng 1 GUIDED mở đầu, còn lại INDEPENDENT; REVIEW_VARIANT không vào mẫu số |
| Chọn và phiên bản | Tạo/resume/replay không nhân đôi câu hoặc exposure; lượt mới theo thứ tự hoạt động, GUIDED trước phần tự làm tương ứng; lượt cũ giữ thứ tự, bộ câu và đoạn đọc cũ sau publish mới/retire |
| Đọc trắc nghiệm | Mỗi câu mẫu đúng 4 lựa chọn; lưu bằng ID, đảo lựa chọn ổn định, chấm đúng key theo phiên bản |
| Đọc điền từ | 4 chỗ khớp 4 item riêng, không thiếu/trùng nhãn; kiểm tra đúng/sai/rỗng, NFC, whitespace và case theo cờ hiện có |
| Nhóm đọc và ví dụ | GUIDED dùng đoạn ví dụ riêng; INDEPENDENT dùng đoạn chính; xuất bản kiểm tra nhóm, role, số câu và tham chiếu đúng phiên bản |
| Ô nhập trong đoạn | 1 ô ví dụ + 3 ô đoạn chính ánh xạ đúng item/blank ID; focus, sửa nhiều ô, chuyển câu, F5, conflict và chữa không mất/đổi chéo đáp án; REVIEW một câu không đòi đáp án ngoài lượt |
| Bảo mật | STUDENT chỉ nhận feedback đúng thời điểm; hint/check của GUIDED không lộ key/lời giải của INDEPENDENT cùng nhóm; đoạn cloze không lộ bản hoàn chỉnh; teacher/admin không tạo lượt học thật; owner và CSRF giữ đúng |
| Lưu và mạng | Autosave đủ 18 câu, đổi câu/F5/đăng nhập lại; mất ACK sau commit, replay cùng payload, 409 hai tab và lựa chọn đối chiếu không mất bản nhập |
| Nộp và chữa | Nộp full snapshot không phụ thuộc autosave; nộp thiếu chưa COMPLETED, đủ thì hoàn thành; chữa không đổi FIRST/Submission/điểm |
| UX | Desktop/mobile, xuống dòng đoạn dài, bàn phím, chuyển nhóm, chọn hai kiểu đọc, tiến độ và mẫu số kết quả thống nhất |
| M04/M05/M07 | REVIEW tải được đoạn cho câu được chọn; sync/thưởng idempotent; số liệu FIRST và phần thưởng theo luật hiện hành, không đếm cả nhóm thêm lần nữa |
| Dữ liệu | Seed mới chạy hai lần không nhân bản, không đè draft; restart/recreate với volume giữ lượt cũ, phiên bản và file |

Lệnh kiểm tra sẽ chọn theo diff thực tế: test backend tập trung cho DTO/validator/selector/API/persistence, build TypeScript/Vite, Playwright cho tạo nội dung và vòng học. Khi đổi thành phần dùng chung hoặc hoàn tất tích hợp, chạy hồi quy backend/browser phù hợp; kiểm tra connection/recovery khi chạm session/storage. Không chạy các bài ghi dữ liệu vào DB demo để nghiệm thu.

Biên bản mới phải ghi commit/source, môi trường, lệnh đã chạy, số ca thực tế và giới hạn. Hiện tài liệu này chưa ghi nhận kiểm thử tính năng vì chưa có triển khai.

## 10. Điều kiện hoàn tất đợt đầu

- [x] Bài nghiệm thu có đúng 18 câu thực làm, theo thứ tự trắc nghiệm → ghép cặp → xếp từ → điền từ → đọc điền từ → đọc hiểu; số câu cấp vòng hiển thị đúng.
- [x] Cả bốn dạng cũ tiếp tục hoạt động, có ORDERING trong nội dung thử thực tế.
- [x] Mỗi hoạt động chỉ có một GUIDED mở đầu, các câu còn lại INDEPENDENT; trình tự này giữ đúng khi tải lại/tiếp tục lượt.
- [x] Có một nhóm đọc trắc nghiệm bốn lựa chọn và một nhóm đọc điền từ hoàn chỉnh.
- [x] Hai nhóm đọc đều có ví dụ GUIDED riêng; phần đọc điền từ nhập trực tiếp trong đoạn và lưu/chấm riêng từng ô.
- [x] Học viên lưu/tiếp tục/nộp/xem kết quả/chữa được; lượt và kết quả cũ không bị thay đổi.
- [x] Bộ nội dung nâng cấp có thể áp dụng lặp lại mà giữ dữ liệu tác giả/học viên.
- [x] Các kiểm tra liên quan có bằng chứng mới và tài liệu hành vi được cập nhật.
- [x] Bài viết theo chủ đề vẫn được ghi là giai đoạn sau, chưa bị đưa vào yêu cầu hoàn thành vòng.
