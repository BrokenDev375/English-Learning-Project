-- Danh mục 16 nhãn hiện hành theo plan; không phải seed học liệu.
-- Không thêm hai nhãn Writing Task 2 còn đang đề xuất; không chốt cấu hình XP.
SET NAMES utf8mb4;
INSERT INTO learning_labels (code, skill, display_name, label_kind, description) VALUES
('READ_MAIN_IDEA', 'READING', 'Xác định ý chính', 'TARGET', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('READ_DETAIL', 'READING', 'Tìm thông tin chi tiết', 'TARGET', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('READ_CROSS_INFERENCE', 'READING', 'Suy luận liên đoạn', 'TARGET', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('READ_CONTEXT_VOCAB', 'READING', 'Hiểu từ trong ngữ cảnh', 'TARGET', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('LISTEN_DETAIL', 'LISTENING', 'Nghe thông tin chi tiết', 'TARGET', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('LISTEN_CORRECTION', 'LISTENING', 'Nhận biết thông tin đính chính', 'TARGET', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('LISTEN_PARAPHRASE', 'LISTENING', 'Hiểu diễn đạt tương đương', 'TARGET', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('LISTEN_MAIN_IDEA', 'LISTENING', 'Hiểu ý chính', 'TARGET', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('LISTEN_SPEECH_COMPREHENSION', 'LISTENING', 'Nghe hiểu lời nói', 'REMEDIAL', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('SPEAK_DEVELOPMENT', 'SPEAKING', 'Phát triển câu trả lời', 'REMEDIAL', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('SPEAK_PRONUNCIATION', 'SPEAKING', 'Phát âm theo âm', 'REMEDIAL', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('OVERVIEW_SELECTION', 'WRITING', 'Chọn thông tin và viết overview', 'REMEDIAL', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('DESCRIPTIVE_LANGUAGE', 'WRITING', 'Từ vựng và cách diễn đạt mô tả', 'REMEDIAL', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('TOPIC_VOCABULARY', 'WRITING', 'Từ vựng theo chủ đề', 'REMEDIAL', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('IDEA_LINKING', 'WRITING', 'Liên kết ý', 'REMEDIAL', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.'),
('GRAMMAR_REPAIR', 'WRITING', 'Sửa lỗi ngữ pháp', 'REMEDIAL', 'Xem điều kiện áp dụng và bằng chứng trong PLAN_IELTS_LEARNING_SYSTEM.md.');
