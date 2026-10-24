package vn.edu.ptit.english;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class GamificationMigrationTest {
    @Test void upgradeFromV5PreservesRowsAndActivationAndEnforcesRewardConstraints() throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("5").load().migrate();
            try(var connection=mysql.createConnection("");var s=connection.createStatement()) {
                s.executeUpdate("INSERT INTO users(email,password_hash,full_name,role) VALUES('preserved@m05.test','hash','Preserved','STUDENT')");
                s.executeUpdate("INSERT INTO topics(owner_id,code,name) VALUES(LAST_INSERT_ID(),'preserved-m05','Preserved')");
                s.executeUpdate("INSERT INTO learning_objectives(owner_id,topic_id,code,name) VALUES(1,1,'preserved-objective','Preserved objective')");
                s.executeUpdate("INSERT INTO attempts(user_id,mode,state,selection_json,player_state_json,submitted_at) VALUES(1,'PRACTICE','SUBMITTED',JSON_OBJECT(),JSON_OBJECT(),'2026-10-20 12:00:00')");
                s.executeUpdate("INSERT INTO submissions(attempt_id,submitted_revision,total_questions,answered_count,correct_count,unanswered_count,independent_fresh_answered_count,independent_fresh_correct_count,score_percent,lesson_completed,result_json) VALUES(1,1,1,1,1,0,1,1,100,FALSE,JSON_OBJECT('factsSchemaVersion',1,'preserved',true))");
                s.executeUpdate("INSERT INTO review_objective_progress(user_id,objective_id,stage,needs_practice,last_practiced_at,next_review_at,last_submission_id) VALUES(1,1,2,0,'2026-10-20 12:00:00','2026-10-23 12:00:00',1)");
            }
            var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3); flyway.validate();
            String activated;
            try(var connection=mysql.createConnection("");var s=connection.createStatement()) {
                try(var rows=s.executeQuery("SELECT COUNT(*) FROM topics WHERE code='preserved-m05'")) { rows.next(); assertThat(rows.getInt(1)).isEqualTo(1); }
                try(var rows=s.executeQuery("SELECT r.stage,r.next_review_at,JSON_EXTRACT(s.result_json,'$.preserved') FROM review_objective_progress r JOIN submissions s ON s.id=r.last_submission_id WHERE r.user_id=1")) {
                    rows.next();assertThat(rows.getInt(1)).isEqualTo(2);assertThat(rows.getString(2)).startsWith("2026-10-23 12:00:00");assertThat(rows.getString(3)).isEqualTo("true");}
                try(var rows=s.executeQuery("SELECT activated_at FROM gamification_policies")) { rows.next(); activated=rows.getString(1); }
                assertThatThrownBy(()->s.executeUpdate("INSERT INTO gamification_profiles(user_id,total_xp) SELECT id,-1 FROM users LIMIT 1"))
                    .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(()->s.executeUpdate("INSERT INTO gamification_daily_stats(user_id,activity_date,awarded_xp,cap_xp) SELECT id,'2026-10-21',201,200 FROM users LIMIT 1"))
                    .isInstanceOf(java.sql.SQLException.class);
                s.executeUpdate("INSERT INTO learning_objectives(owner_id,topic_id,code,name) SELECT owner_id,id,'m05-objective','Objective' FROM topics LIMIT 1");
                s.executeUpdate("INSERT INTO attempts(user_id,mode,state,selection_json,player_state_json,submitted_at) SELECT id,'PRACTICE','SUBMITTED',JSON_OBJECT(),JSON_OBJECT(),CURRENT_TIMESTAMP(6) FROM users LIMIT 1");
                s.executeUpdate("INSERT INTO submissions(attempt_id,submitted_revision,total_questions,answered_count,correct_count,unanswered_count,independent_fresh_answered_count,independent_fresh_correct_count,score_percent,lesson_completed,result_json) SELECT id,1,1,1,1,0,1,1,100,FALSE,JSON_OBJECT() FROM attempts ORDER BY id DESC LIMIT 1");
                String insert="INSERT INTO gamification_reward_claims(user_id,submission_id,reward_type,source_key,objective_id,activity_date) VALUES(1,1,";
                s.executeUpdate(insert+"'NEW_QUESTION','origin-a',NULL,'2026-10-21')");
                s.executeUpdate(insert+"'NEW_QUESTION','origin-b',NULL,'2026-10-21')");
                assertThatThrownBy(()->s.executeUpdate(insert+"'NEW_QUESTION','origin-a',NULL,'2026-10-22')"))
                    .isInstanceOf(java.sql.SQLException.class);
                s.executeUpdate(insert+"'DUE_REVIEW','cycle-a',1,'2026-10-21')");
                assertThatThrownBy(()->s.executeUpdate(insert+"'DUE_REVIEW','cycle-b',1,'2026-10-21')"))
                    .isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(()->s.executeUpdate(insert+"'DUE_REVIEW','cycle-a',1,'2026-10-22')"))
                    .isInstanceOf(java.sql.SQLException.class);
                s.executeUpdate(insert+"'DUE_REVIEW','cycle-b',1,'2026-10-22')");
                assertThatThrownBy(()->s.executeUpdate("INSERT INTO xp_ledger(claim_id,user_id,submission_id,policy_version,requested_xp,awarded_xp,activity_date,submitted_at,processed_at,league_reason) VALUES(1,1,1,'m05-p0-v1',5,6,'2026-10-21',CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),'NOT_JOINED')"))
                    .isInstanceOf(java.sql.SQLException.class);
            }
            assertThat(flyway.migrate().migrationsExecuted).isZero();
            try(var connection=mysql.createConnection("");var s=connection.createStatement();var rows=s.executeQuery("SELECT activated_at FROM gamification_policies")) {
                rows.next(); assertThat(rows.getString(1)).isEqualTo(activated);
            }
        }
    }
}
