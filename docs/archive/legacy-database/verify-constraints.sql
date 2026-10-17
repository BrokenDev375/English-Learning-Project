-- Isolated design verification only. Synthetic fixture; never import into the application database.
SET NAMES utf8mb4;
SET time_zone = '+00:00';
CREATE TEMPORARY TABLE verification_results (name VARCHAR(120) PRIMARY KEY, result VARCHAR(12) NOT NULL);
DELIMITER //
CREATE PROCEDURE assert_true(IN p_name VARCHAR(120), IN p_ok BOOLEAN)
BEGIN
    DECLARE v_message VARCHAR(128);
    IF NOT COALESCE(p_ok, FALSE) THEN
        SET v_message = CONCAT('Assertion failed: ', p_name);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
    END IF;
    INSERT INTO verification_results VALUES (p_name, 'PASS');
END//
CREATE PROCEDURE assert_rejected(IN p_name VARCHAR(120), IN p_sql TEXT, IN p_errno INT)
BEGIN
    DECLARE v_errno INT DEFAULT 0;
    DECLARE v_message VARCHAR(128);
    SET @verification_sql = p_sql;
    BEGIN
        DECLARE EXIT HANDLER FOR SQLEXCEPTION
            GET DIAGNOSTICS CONDITION 1 v_errno = MYSQL_ERRNO;
        PREPARE verification_statement FROM @verification_sql;
        EXECUTE verification_statement;
        DEALLOCATE PREPARE verification_statement;
    END;
    IF v_errno <> p_errno THEN
        SET v_message = CONCAT(p_name, ': expected ', p_errno, ', got ', v_errno);
        SIGNAL SQLSTATE '45000' SET MESSAGE_TEXT = v_message;
    END IF;
    INSERT INTO verification_results VALUES (p_name, 'PASS');
END//
DELIMITER ;
CALL assert_true('empty_scratch_schema_has_44_tables',
    (SELECT COUNT(*) FROM information_schema.tables WHERE table_schema = DATABASE()) = 44);
CALL assert_true('all_foreign_keys_standard',
    @@restrict_fk_on_non_standard_key = 1);
CALL assert_true('label_catalog_16', (SELECT COUNT(*) FROM learning_labels) = 16);

INSERT INTO users (id,email,password_hash,full_name,role) VALUES
(1,'teacher@fixture.invalid','fixture-no-login','Teacher','TEACHER'),
(2,'learner@fixture.invalid','fixture-no-login','Learner','STUDENT'),
(3,'other@fixture.invalid','fixture-no-login','Other learner','STUDENT');
INSERT INTO classrooms (id,teacher_id,name,join_code,create_request_id)
VALUES (1,1,'Fixture class','DBTEST01',UUID());
INSERT INTO class_members VALUES (1,2,CURRENT_TIMESTAMP(6));
INSERT INTO assets (id,owner_id,storage_key,original_name,mime_type,size_bytes,sha256)
VALUES (1,2,'fixture/recording-1.webm','recording.webm','audio/webm',100,REPEAT('a',64));

INSERT INTO resources (id,owner_id,skill) VALUES
(1,1,'READING'),(2,1,'LISTENING'),(4,1,'SPEAKING'),(5,1,'WRITING');
INSERT INTO resource_versions
(id,resource_id,skill,version_no,title,resource_kind,activity_type,status,content_json) VALUES
(11,1,'READING',1,'Reading v1','MAIN','MCQ','PUBLISHED',JSON_OBJECT('paragraphs',JSON_ARRAY(JSON_OBJECT('paragraphId','p1','text','Fixture paragraph.')))),
(12,1,'READING',2,'Reading v2','MAIN','MCQ','PUBLISHED',JSON_OBJECT('paragraphs',JSON_ARRAY())),
(21,2,'LISTENING',1,'Dictation','REMEDIAL','DICTATION','PUBLISHED',JSON_OBJECT()),
(41,4,'SPEAKING',1,'Speaking','MAIN','SPEAKING_RECORDING','PUBLISHED',JSON_OBJECT()),
(51,5,'WRITING',1,'Essay','MAIN','WRITING_ESSAY','PUBLISHED',JSON_OBJECT());
INSERT INTO resource_labels VALUES (11,'READING','READ_DETAIL');
INSERT INTO questions
(resource_version_id,question_key,skill,sort_order,question_text,primary_label_code,label_status) VALUES
(11,'q1','READING',1,'Question one','READ_DETAIL','TEACHER_REVIEWED'),
(11,'q2','READING',2,'Question two',NULL,'UNDETERMINED'),
(12,'q3','READING',1,'New-version question','READ_DETAIL','TEACHER_REVIEWED');
INSERT INTO question_options VALUES
(11,'q1','a','First',1),(11,'q1','b','Second',2),
(11,'q2','x','First',1),(11,'q2','y','Second',2),
(12,'q3','a','First',1),(12,'q3','b','Second',2);
UPDATE questions SET correct_option_key = 'b' WHERE resource_version_id=11 AND question_key='q1';
UPDATE questions SET correct_option_key = 'x' WHERE resource_version_id=11 AND question_key='q2';
UPDATE questions SET correct_option_key = 'a' WHERE resource_version_id=12 AND question_key='q3';

INSERT INTO listening_media (id,owner_id,source_type,video_id,text_in_video)
VALUES (1,1,'YOUTUBE','fixture0001','NONE'),(2,1,'YOUTUBE','fixture0002','UNKNOWN');
INSERT INTO listening_transcript_versions
(id,media_id,start_seconds,end_seconds,revision_no,transcript_text,reviewed_by,reviewed_at) VALUES
(31,1,120,270,1,'The first transcript.',1,CURRENT_TIMESTAMP(6)),
(32,1,120,270,2,'The revised transcript.',1,CURRENT_TIMESTAMP(6)),
(33,2,120,270,1,'Another media transcript.',1,CURRENT_TIMESTAMP(6));
INSERT INTO listening_segments
(resource_version_id,segment_key,media_id,title,sort_order,start_seconds,end_seconds,transcript_version_id)
VALUES (21,'s1',1,'Segment one',1,120,270,31);

INSERT INTO assignments
(id,classroom_id,created_by,resource_version_id,purpose,recipient_mode,status,max_attempts,create_request_id,request_fingerprint)
VALUES
(101,1,1,11,'PRACTICE','CLASS_SNAPSHOT','PUBLISHED',3,UUID(),REPEAT('a',64)),
(201,1,1,21,'REMEDIAL','SELECTED','PUBLISHED',3,UUID(),REPEAT('b',64)),
(401,1,1,41,'PRACTICE','SELECTED','PUBLISHED',3,UUID(),REPEAT('c',64)),
(501,1,1,51,'PRACTICE','SELECTED','PUBLISHED',3,UUID(),REPEAT('d',64));
INSERT INTO assignment_recipients (assignment_id,student_id)
VALUES (101,2),(201,2),(401,2),(501,2);
INSERT INTO learning_attempts
(id,assignment_id,student_id,resource_version_id,attempt_no,create_request_id,request_fingerprint) VALUES
(1,101,2,11,1,UUID(),REPEAT('a',64)),(2,201,2,21,1,UUID(),REPEAT('b',64)),
(3,401,2,41,1,UUID(),REPEAT('c',64)),(4,501,2,51,1,UUID(),REPEAT('d',64)),
(5,101,2,11,2,UUID(),REPEAT('e',64));
INSERT INTO attempt_drafts (attempt_id,cursor_json) VALUES (1,JSON_OBJECT('paragraphId','p1'));
INSERT INTO attempt_answers (attempt_id,resource_version_id,question_key,selected_option_key)
VALUES (1,11,'q1','b'),(1,11,'q2',NULL);
INSERT INTO attempt_listening_progress (attempt_id,resource_version_id,segment_key,draft_text,last_playback_seconds)
VALUES (2,21,'s1','The first',165);
INSERT INTO listening_segment_submissions
(id,attempt_id,resource_version_id,segment_key,transcript_version_id,submitted_text,request_id,request_fingerprint)
VALUES (401,2,21,'s1',31,'The first transcript.',UUID(),REPEAT('f',64));

INSERT INTO speaking_outline_versions (id,attempt_id,version_no,english_outline)
VALUES (501,3,1,'Topic, reason, example.'),(502,3,2,'Revised topic, reason, example.');
INSERT INTO submissions
(id,attempt_id,assignment_id,student_id,resource_version_id,outline_version_id,body_text,request_id,request_fingerprint) VALUES
(301,1,101,2,11,NULL,NULL,UUID(),REPEAT('a',64)),
(302,2,201,2,21,NULL,NULL,UUID(),REPEAT('b',64)),
(303,3,401,2,41,501,NULL,UUID(),REPEAT('c',64)),
(304,4,501,2,51,NULL,'An original complete essay.',UUID(),REPEAT('d',64));
INSERT INTO submission_answers VALUES (301,11,'q1','b'),(301,11,'q2',NULL);
INSERT INTO submission_assets VALUES (303,1,'RECORDING',0);
INSERT INTO attempt_review_events
(attempt_id,resource_version_id,question_key,event_type,payload_json,event_request_id)
VALUES (1,11,'q1','SELF_REPORT',JSON_OBJECT('reason','NOT_FOUND','skipped',FALSE),UUID());
CALL assert_true('unanswered_option_is_null', (SELECT selected_option_key IS NULL FROM submission_answers WHERE submission_id=301 AND question_key='q2'));
CALL assert_true('assignment_keeps_version_1', (SELECT resource_version_id FROM assignments WHERE id=101) = 11);
CALL assert_true('dictation_keeps_transcript_1', (SELECT transcript_version_id FROM listening_segment_submissions WHERE id=401) = 31);
CALL assert_true('recording_keeps_outline_1', (SELECT outline_version_id FROM submissions WHERE id=303) = 501);
-- Direct fixture update demonstrates separate storage; future API must reject draft writes after final submission.
UPDATE attempt_answers SET selected_option_key='a' WHERE attempt_id=1 AND question_key='q1';
CALL assert_true('draft_changes_do_not_rewrite_submission',
    (SELECT selected_option_key FROM submission_answers WHERE submission_id=301 AND question_key='q1')='b');

INSERT INTO rubric_versions (id,rubric_code,version_no,skill,task_type,rubric_type,criteria_json)
VALUES (901,'SPEAKING',1,'SPEAKING','SPEAKING','IELTS_BAND',JSON_OBJECT('fixture',TRUE)),
(902,'WRITING_TASK2',1,'WRITING','TASK_2','IELTS_BAND',JSON_OBJECT('fixture',TRUE));
INSERT INTO teacher_assessments
(id,submission_id,student_id,teacher_id,rubric_version_id,revision_no,status,score_scale,score_value,criteria_json,returned_at)
VALUES (901,304,2,1,902,1,'RETURNED','BAND',6.0,JSON_OBJECT('fixture',TRUE),CURRENT_TIMESTAMP(6)),
(902,303,2,1,901,1,'RETURNED','BAND',5.5,JSON_OBJECT('fixture',TRUE),CURRENT_TIMESTAMP(6));
INSERT INTO skill_assessments
(id,student_id,skill,confirmed_by,level_framework,score_value,method_version,evidence_json,performed_at) VALUES
(601,2,'READING',1,'IELTS_BAND',6.0,'FIXTURE_ONLY',JSON_OBJECT('fixture',TRUE),CURRENT_TIMESTAMP(6)),
(602,3,'READING',1,'IELTS_BAND',5.5,'FIXTURE_ONLY',JSON_OBJECT('fixture',TRUE),CURRENT_TIMESTAMP(6)),
(603,2,'LISTENING',1,'IELTS_BAND',5.5,'FIXTURE_ONLY',JSON_OBJECT('fixture',TRUE),CURRENT_TIMESTAMP(6));
INSERT INTO learner_profiles (student_id) VALUES (2);
INSERT INTO skill_profiles (student_id,skill,current_assessment_id)
VALUES (2,'READING',601),(2,'LISTENING',603),(2,'SPEAKING',NULL),(2,'WRITING',NULL);
INSERT INTO profile_history
(id,student_id,confirmed_by,scope,changed_assessment_id,components_json,overall_estimate,method_version)
VALUES (701,2,1,'PROFILE',601,JSON_OBJECT('READING',601,'LISTENING',603,'SPEAKING',NULL,'WRITING',NULL),NULL,'FIXTURE_ONLY');
UPDATE learner_profiles SET current_history_id=701 WHERE student_id=2;
CALL assert_true('missing_skills_keep_overall_null', (SELECT overall_estimate IS NULL FROM profile_history WHERE id=701));

INSERT INTO learner_needs (id,student_id,skill,label_code,source,status,confirmed_by,details_json)
VALUES (801,2,'SPEAKING','SPEAK_PRONUNCIATION','TEACHER','CONFIRMED',1,JSON_OBJECT('phonemes',JSON_ARRAY('/θ/'))),
(802,2,'READING','READ_DETAIL','AI','SUGGESTED',NULL,JSON_OBJECT());
INSERT INTO learner_need_events (need_id,student_id,skill,actor_id,event_type,submission_id,teacher_assessment_id,after_json)
VALUES (801,2,'SPEAKING',1,'TEACHER_NOTE',303,902,JSON_OBJECT('phonemes',JSON_ARRAY('/θ/')));
CALL assert_true('unicode_ipa_preserved',
    (SELECT HEX(JSON_UNQUOTE(JSON_EXTRACT(details_json,'$.phonemes[0]'))) FROM learner_needs WHERE id=801) = '2FCEB82F');
INSERT INTO gamification_rule_versions (id,rule_code,version_no,xp_amount,conditions_json,is_enabled)
VALUES (1,'FIXTURE_ONLY',1,10,JSON_OBJECT('fixture',TRUE),TRUE);
INSERT INTO reward_events (id,student_id,assignment_id,submission_id,rule_version_id,xp_amount,basis_json)
VALUES (1,2,101,301,1,10,JSON_OBJECT('fixture',TRUE));
INSERT INTO daily_learning_activities (student_id,learning_date,timezone,first_submission_id,rule_version_id)
VALUES (2,'2026-10-12','Asia/Ho_Chi_Minh',301,1);
INSERT INTO badge_definitions VALUES ('FIXTURE_BADGE',1,'Fixture',JSON_OBJECT(),TRUE),('FIXTURE_BADGE',2,'Fixture v2',JSON_OBJECT(),TRUE);
INSERT INTO earned_badges (student_id,badge_code,definition_version,evidence_json) VALUES (2,'FIXTURE_BADGE',1,JSON_OBJECT());

DELETE FROM class_members WHERE classroom_id=1 AND student_id=2;
CALL assert_true('leaving_class_preserves_submissions', (SELECT COUNT(*) FROM submissions WHERE student_id=2)=4);
CALL assert_true('leaving_class_preserves_recipients', (SELECT COUNT(*) FROM assignment_recipients WHERE student_id=2)=4);
INSERT INTO class_members VALUES (1,2,CURRENT_TIMESTAMP(6));
CALL assert_rejected('wrong_option_in_question', 'UPDATE questions SET correct_option_key=''x'' WHERE resource_version_id=11 AND question_key=''q1''', 1452);
CALL assert_rejected('wrong_skill_label', 'UPDATE questions SET primary_label_code=''LISTEN_DETAIL'' WHERE resource_version_id=11 AND question_key=''q1''', 1452);
CALL assert_rejected('wrong_attempt_version', 'INSERT INTO learning_attempts (assignment_id,student_id,resource_version_id,attempt_no,create_request_id,request_fingerprint) VALUES (101,2,12,3,UUID(),REPEAT(''a'',64))', 1452);
CALL assert_rejected('non_recipient_attempt', 'INSERT INTO learning_attempts (assignment_id,student_id,resource_version_id,attempt_no,create_request_id,request_fingerprint) VALUES (101,3,11,1,UUID(),REPEAT(''a'',64))', 1452);
CALL assert_rejected('duplicate_attempt_number', 'INSERT INTO learning_attempts (assignment_id,student_id,resource_version_id,attempt_no,create_request_id,request_fingerprint) VALUES (101,2,11,1,UUID(),REPEAT(''a'',64))', 1062);
CALL assert_rejected('answer_from_other_version', 'INSERT INTO attempt_answers (attempt_id,resource_version_id,question_key,selected_option_key) VALUES (1,12,''q3'',''a'')', 1452);
CALL assert_rejected('answer_from_other_question', 'UPDATE attempt_answers SET selected_option_key=''x'' WHERE attempt_id=1 AND question_key=''q1''', 1452);
CALL assert_rejected('wrong_transcript_media', 'INSERT INTO listening_segments (resource_version_id,segment_key,media_id,title,sort_order,start_seconds,end_seconds,transcript_version_id) VALUES (21,''bad'',1,''Bad'',2,120,270,33)', 1452);
CALL assert_rejected('invalid_listening_interval', 'INSERT INTO listening_segments (resource_version_id,segment_key,media_id,title,sort_order,start_seconds,end_seconds) VALUES (21,''bad'',1,''Bad'',2,120,120)', 3819);
CALL assert_rejected('wrong_transcript_revision', 'UPDATE listening_segment_submissions SET transcript_version_id=32 WHERE id=401', 1452);
CALL assert_rejected('duplicate_segment_submission', 'INSERT INTO listening_segment_submissions (attempt_id,resource_version_id,segment_key,transcript_version_id,submitted_text,request_id,request_fingerprint) VALUES (2,21,''s1'',31,''Retry'',UUID(),REPEAT(''a'',64))', 1062);
CALL assert_rejected('wrong_submission_student', 'INSERT INTO submissions (attempt_id,assignment_id,student_id,resource_version_id,request_id,request_fingerprint) VALUES (5,101,3,11,UUID(),REPEAT(''a'',64))', 1452);
CALL assert_rejected('duplicate_final_submission', 'INSERT INTO submissions (attempt_id,assignment_id,student_id,resource_version_id,request_id,request_fingerprint) VALUES (1,101,2,11,UUID(),REPEAT(''a'',64))', 1062);
CALL assert_rejected('wrong_submitted_option', 'UPDATE submission_answers SET selected_option_key=''x'' WHERE submission_id=301 AND question_key=''q1''', 1452);
CALL assert_rejected('wrong_recording_outline', 'UPDATE submissions SET outline_version_id=501 WHERE id=304', 1452);
CALL assert_rejected('wrong_profile_student', 'UPDATE skill_profiles SET current_assessment_id=602 WHERE student_id=2 AND skill=''READING''', 1452);
CALL assert_rejected('wrong_profile_skill', 'UPDATE skill_profiles SET current_assessment_id=601 WHERE student_id=2 AND skill=''WRITING''', 1452);
CALL assert_rejected('ai_cannot_confirm_speaking_need', 'UPDATE learner_needs SET source=''AI'' WHERE id=801', 3819);
CALL assert_rejected('speaking_ai_suggestion_event', 'INSERT INTO learner_need_events (need_id,student_id,skill,actor_id,event_type,after_json) VALUES (801,2,''SPEAKING'',1,''AI_SUGGESTION'',JSON_OBJECT())', 3819);
CALL assert_rejected('confirmation_needs_teacher', 'UPDATE learner_needs SET status=''CONFIRMED'' WHERE id=802', 3819);
CALL assert_rejected('invalid_ielts_band', 'UPDATE teacher_assessments SET score_value=9.5 WHERE id=901', 3819);
CALL assert_rejected('duplicate_xp_award', 'INSERT INTO reward_events (student_id,assignment_id,submission_id,rule_version_id,xp_amount,basis_json) VALUES (2,101,301,1,10,JSON_OBJECT())', 1062);
CALL assert_rejected('negative_xp', 'UPDATE reward_events SET xp_amount=-1 WHERE id=1', 3819);
CALL assert_rejected('duplicate_learning_day', 'INSERT INTO daily_learning_activities (student_id,learning_date,timezone,first_submission_id,rule_version_id) VALUES (2,''2026-10-12'',''Asia/Ho_Chi_Minh'',301,1)', 1062);
CALL assert_rejected('badge_not_reawarded_for_new_definition', 'INSERT INTO earned_badges (student_id,badge_code,definition_version,evidence_json) VALUES (2,''FIXTURE_BADGE'',2,JSON_OBJECT())', 1062);
CALL assert_rejected('referenced_version_not_deleted', 'DELETE FROM resource_versions WHERE id=11', 1451);
CALL assert_rejected('malformed_json', 'UPDATE resource_versions SET content_json=''{broken'' WHERE id=11', 3140);

SELECT name, result FROM verification_results ORDER BY name;
SELECT COUNT(*) AS checks_passed FROM verification_results;
SELECT VERSION() AS mysql_version;
