# Kiểm tra thiết kế database

Ngày chạy UTC: **2026-10-12T03:37:04.7224599Z**. Engine thực tế: **MySQL 8.4.9**.

Đã tạo **44 bảng** từ schema tham chiếu trên database trống. **39 kiểm tra qua**: 12 trường hợp hợp lệ và 27 trường hợp dữ liệu sai được từ chối với đúng mã lỗi. restrict_fk_on_non_standard_key=ON trong lần kiểm tra.

## Phạm vi

Kiểm tra DDL, PK/FK ghép, unique/check, giữ snapshot bản nộp/transcript/outline, profile nullable và Unicode IPA. Quyền HTTP/session, bất biến khi gọi service, schema JSON nghiệp vụ, chất lượng AI và kết quả học tập được kiểm tra ở các bước triển khai sau. Fixture chứa dữ liệu tổng hợp với rubric/điểm/XP minh họa, không phải điểm IELTS hoặc chính sách thưởng đã chốt.

Lần chạy dùng container MySQL tạm có filesystem dữ liệu tmpfs và không publish cổng. Script dọn container do mình tạo khi kết thúc; database ứng dụng không phải mục tiêu kiểm tra.

## Kết quả từng trường hợp

| Kiểm tra | Kết quả |
|---|---|
| ai_cannot_confirm_speaking_need | PASS |
| all_foreign_keys_standard | PASS |
| answer_from_other_question | PASS |
| answer_from_other_version | PASS |
| assignment_keeps_version_1 | PASS |
| badge_not_reawarded_for_new_definition | PASS |
| confirmation_needs_teacher | PASS |
| dictation_keeps_transcript_1 | PASS |
| draft_changes_do_not_rewrite_submission | PASS |
| duplicate_attempt_number | PASS |
| duplicate_final_submission | PASS |
| duplicate_learning_day | PASS |
| duplicate_segment_submission | PASS |
| duplicate_xp_award | PASS |
| empty_scratch_schema_has_44_tables | PASS |
| invalid_ielts_band | PASS |
| invalid_listening_interval | PASS |
| label_catalog_16 | PASS |
| leaving_class_preserves_recipients | PASS |
| leaving_class_preserves_submissions | PASS |
| malformed_json | PASS |
| missing_skills_keep_overall_null | PASS |
| negative_xp | PASS |
| non_recipient_attempt | PASS |
| recording_keeps_outline_1 | PASS |
| referenced_version_not_deleted | PASS |
| speaking_ai_suggestion_event | PASS |
| unanswered_option_is_null | PASS |
| unicode_ipa_preserved | PASS |
| wrong_attempt_version | PASS |
| wrong_option_in_question | PASS |
| wrong_profile_skill | PASS |
| wrong_profile_student | PASS |
| wrong_recording_outline | PASS |
| wrong_skill_label | PASS |
| wrong_submission_student | PASS |
| wrong_submitted_option | PASS |
| wrong_transcript_media | PASS |
| wrong_transcript_revision | PASS |

## Tái lập

Mở Docker Desktop, đứng tại thư mục project và chạy:

    powershell -ExecutionPolicy Bypass -File .\docs\database\verify.ps1

Các file tham gia: [schema](schema.mysql.sql), [danh mục nhãn](catalog-seed.sql), [fixture/kiểm tra ràng buộc](verify-constraints.sql), [script](verify.ps1). Report JSON: [validation.json](validation.json).

## Checksum trong lần chạy

| File | SHA-256 |
|---|---|
| schema.mysql.sql | 068FB7DD0138C6D62E24EEE235BBCA4DCE9041EA167325F748E92D05A1090E1B |
| catalog-seed.sql | E3A8EDFF3E9BD010B03B36F5A55D1392B6C1296C6EA6F7D7CE380E7A464F1A9A |
| verify-constraints.sql | 2B148EFB54340A78964BE4D4BEEE45C0FEBCCE03069E8B06832D34614BFFD6DF |
