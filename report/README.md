# Báo cáo dự án English Learning

Nội dung được biên soạn ngày **27/10/2026** theo [kế hoạch báo cáo](../docs/REPORT_PLAN.md) và các yêu cầu trong [Instruction](../docs/course/Instruction.md), [Topics](../docs/course/Topics.md), [Submission](../docs/course/Submission.md).

## Tệp báo cáo

- [Bản nội dung Markdown](../docs/REPORT.md): nguồn văn bản để rà soát và chỉnh sửa trong repository.
- [Sơ đồ và nguồn Mermaid](assets/): kiến trúc, vòng đời Attempt và sequence replay khi mất response sau commit. Hình dùng trong bản Word và Markdown là các PNG cùng thư mục.

Thông tin nhóm, lớp, giảng viên, thành viên và MSSV trên bìa được để trống theo yêu cầu, bổ sung sau. Nội dung có đủ tóm tắt, từ khóa và 16 phần chính từ giới thiệu đến phụ lục, tương ứng 19 nội dung của kế hoạch khi tính tên đề tài, tóm tắt và từ khóa.

Bản Word tạo thử được giữ trong `.test-state/report/`, chưa bàn giao như bản nộp: runtime Windows không có LibreOffice và bước xuất qua Word Automation không hoàn tất. Chưa kiểm tra được bố cục từng trang của DOCX; việc kiểm tra Markdown/sơ đồ không thay thế bước này.

## Phạm vi bằng chứng

Báo cáo đối chiếu source main `fac44f4`, hợp đồng API và các biên bản thực tế trong `statics/results/`. Mỗi kết quả giữ ngày, phiên bản và phạm vi riêng. Workload M05 là phép đo lịch sử ngày 22/10/2026 trên một backend local; không được diễn giải thành benchmark của toàn bộ source mới nhất hoặc bằng chứng hiệu quả học tập.

Lần biên soạn này không chạy lại test ứng dụng hoặc workload. Các kiểm tra đã thực hiện dành cho báo cáo gồm đối chiếu kỹ thuật/số liệu, liên kết, định dạng Markdown và hình sơ đồ. Không ghi kết quả Compilatio khi chưa thực hiện.

## Trước khi nộp

- Bổ sung thông tin bìa và bảng phân công theo đóng góp thật của các thành viên.
- Chốt source, chạy các kiểm tra/thực nghiệm còn cần cho bản nộp; chỉ cập nhật bảng kết quả khi có log tương ứng.
- Kiểm tra Compilatio và ngưỡng **Similarity + Plagiarism ≤ 20%** theo tài liệu học phần.
- Đổi tên báo cáo thành `NhomXX_EnglishLearning_Report.docx` với số nhóm thật; chuẩn bị Source/Report và kênh bàn giao theo Submission/thông báo lớp.
- Hạn theo Submission là **23:59 ngày 31/10/2026**.
