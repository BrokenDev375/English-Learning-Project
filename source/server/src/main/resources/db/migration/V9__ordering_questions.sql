-- Extend the published question type without rewriting existing versions or results.
ALTER TABLE question_versions DROP CHECK ck_question_versions_type;
ALTER TABLE question_versions ADD CONSTRAINT ck_question_versions_type
    CHECK (type IN ('SINGLE_CHOICE','FILL_BLANK','MATCHING','ORDERING'));
