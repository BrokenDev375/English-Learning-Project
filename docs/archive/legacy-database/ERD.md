# Sơ đồ quan hệ database

Thiết kế được chia thành bốn sơ đồ để đọc theo phần việc. Đây là ERD rút gọn; khóa ghép và các FK chi tiết nằm trong [từ điển dữ liệu](DATA_DICTIONARY.md) và [SQL](schema.mysql.sql).

## Ngân hàng tài liệu và giao bài

[Nguồn Mermaid](../../statics/database-resources.mmd).

```mermaid
erDiagram
    users ||--o{ classrooms : teacher
    classrooms ||--o{ class_members : contains
    users ||--o{ class_members : member
    users ||--o{ resources : owns
    resources ||--o{ resource_versions : versions
    resource_versions ||--o{ questions : includes
    questions ||--o{ question_options : choices
    learning_labels o|--o{ questions : primary_label
    resource_versions ||--o{ resource_labels : classified
    learning_labels ||--o{ resource_labels : definition
    resource_versions ||--o{ resource_assets : attachments
    assets ||--o{ resource_assets : file
    listening_media ||--o{ listening_transcript_versions : revisions
    listening_media ||--o{ listening_segments : source
    listening_transcript_versions o|--o{ listening_segments : reference
    resource_versions ||--o{ listening_segments : intervals
    classrooms ||--o{ assignments : receives
    resource_versions ||--o{ assignments : fixed_version
    assignments ||--o{ assignment_recipients : recipient_snapshot
    users ||--o{ assignment_recipients : learner
```

## Lần làm, nháp, bản nộp và review

[Nguồn Mermaid](../../statics/database-attempts.mmd).

```mermaid
erDiagram
    assignment_recipients ||--o{ learning_attempts : attempts
    assignments ||--o{ learning_attempts : fixed_version
    learning_attempts ||--o| attempt_drafts : draft
    learning_attempts ||--o{ attempt_answers : draft_choices
    questions ||--o{ attempt_answers : question
    question_options o|--o{ attempt_answers : selected
    learning_attempts ||--o{ attempt_listening_progress : segment_draft
    listening_segments ||--o{ attempt_listening_progress : segment
    learning_attempts ||--o{ listening_segment_submissions : immutable_dictation
    listening_segments ||--o{ listening_segment_submissions : fixed_transcript
    learning_attempts ||--o| submissions : final_snapshot
    submissions o|--o{ submissions : previous_revision
    submissions ||--o{ submission_answers : submitted_choices
    questions ||--o{ submission_answers : question
    submissions ||--o{ submission_assets : recordings
    assets ||--o{ submission_assets : stored_file
    learning_attempts ||--o{ attempt_review_events : review_history
    questions o|--o{ attempt_review_events : question_target
    listening_segments o|--o{ attempt_review_events : segment_target
```

## Chấm bài, profile và bổ trợ

[Nguồn Mermaid](../../statics/database-assessment.mmd).

```mermaid
erDiagram
    learning_attempts ||--o{ speaking_outline_versions : preparation
    speaking_outline_versions o|--o| submissions : used_outline
    submissions o|--o{ ai_analyses : suggestion
    speaking_outline_versions o|--o{ ai_analyses : outline_review
    submissions ||--o{ teacher_assessments : feedback_revisions
    rubric_versions ||--o{ teacher_assessments : rubric
    ai_analyses o|--o{ teacher_assessments : consulted
    assessment_cycles ||--o{ cycle_assignments : tasks
    assignments ||--o{ cycle_assignments : linked
    assessment_cycles o|--o{ skill_assessments : assessment_period
    users ||--o| learner_profiles : one_global_profile
    learner_profiles ||--o{ skill_profiles : four_skills
    skill_assessments o|--o| skill_profiles : current_confirmed_source
    users ||--o{ skill_assessments : confirmed_history
    users ||--o{ profile_history : profile_snapshots
    profile_history o|--o| learner_profiles : current_snapshot
    users ||--o{ learner_needs : needs
    learning_labels ||--o{ learner_needs : goal
    learner_needs ||--o{ learner_need_events : evidence_history
    learner_needs ||--o{ remedial_recommendations : remedial_choice
    resource_versions ||--o{ remedial_recommendations : chosen_material
    assignments o|--o{ remedial_recommendations : assigned
```

## Gamification và thực nghiệm

[Nguồn Mermaid](../../statics/database-gamification.mmd).

```mermaid
erDiagram
    users ||--o{ reward_events : experience
    assignments ||--o{ reward_events : unique_activity
    submissions ||--o{ reward_events : completion_evidence
    gamification_rule_versions ||--o{ reward_events : rule_snapshot
    users ||--o{ daily_learning_activities : unique_days
    submissions ||--o{ daily_learning_activities : first_valid_activity
    gamification_rule_versions ||--o{ daily_learning_activities : day_rule
    users ||--o{ earned_badges : earns
    badge_definitions ||--o{ earned_badges : definition_version
    users ||--o{ experiment_runs : records_measurement
```
