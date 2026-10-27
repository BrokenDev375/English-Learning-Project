package vn.edu.ptit.english;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class EngagementMigrationTest {
    @Test void v10AndV11KeepOldPoliciesXpAndFrozenResults()throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4")) {
            mysql.start();Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("9").load().migrate();
            String frozen,policy,submission;
            try(var c=mysql.createConnection("");var s=c.createStatement()) {
                s.executeUpdate("INSERT INTO users(email,password_hash,full_name,role) VALUES('preserved@engagement.test','hash','Preserved','STUDENT')");
                s.executeUpdate("INSERT INTO gamification_profiles(user_id,total_xp) VALUES(1,5)");
                s.executeUpdate("INSERT INTO attempts(user_id,mode,state,selection_json,player_state_json,submitted_at) VALUES(1,'PRACTICE','SUBMITTED',JSON_OBJECT(),JSON_OBJECT(),CURRENT_TIMESTAMP(6))");
                s.executeUpdate("INSERT INTO submissions(attempt_id,submitted_revision,total_questions,answered_count,correct_count,unanswered_count,independent_fresh_answered_count,independent_fresh_correct_count,score_percent,lesson_completed,result_json) VALUES(1,1,1,1,1,0,1,1,100,FALSE,JSON_OBJECT('factsSchemaVersion',1,'preserved',true))");
                s.executeUpdate("INSERT INTO gamification_reward_claims(user_id,submission_id,reward_type,source_key,activity_date) VALUES(1,1,'NEW_QUESTION','preserved-origin',CURRENT_DATE())");
                s.executeUpdate("INSERT INTO xp_ledger(claim_id,user_id,submission_id,policy_version,requested_xp,awarded_xp,activity_date,submitted_at,processed_at,league_reason) VALUES(1,1,1,'m05-p0-v1',5,5,CURRENT_DATE(),CURRENT_TIMESTAMP(6),CURRENT_TIMESTAMP(6),'NOT_JOINED')");
                for(int n=0;n<3;n++) {
                    String status=n==0?"FINALIZED":n==1?"OPEN":"CLOSING";
                    int ago=n==0?15:n==1?0:8;
                    s.executeUpdate("INSERT INTO league_seasons(start_at,end_at,cutoff_at,status,policy_version,rules_json) VALUES(DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL "+(ago*24+1)+" HOUR),DATE_ADD(DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL "+(ago*24+1)+" HOUR),INTERVAL 7 DAY),DATE_ADD(DATE_ADD(DATE_SUB(CURRENT_TIMESTAMP(6),INTERVAL "+(ago*24+1)+" HOUR),INTERVAL 7 DAY),INTERVAL 120 SECOND),'"+status+"','m05-p0-v1',JSON_OBJECT('capacity',25,'minimumMembers',10,'minimumActive',5,'promotionPercent',20,'demotionPercent',20))");
                    int id=n+1;s.executeUpdate("INSERT INTO league_group_allocators(season_id,tier) VALUES("+id+",'BRONZE')");
                    s.executeUpdate("INSERT INTO league_groups(season_id,tier,group_no,member_count) VALUES("+id+",'BRONZE',1,1)");
                    s.executeUpdate("INSERT INTO league_members(member_key,user_id,season_id,group_id,tier,joined_at,weekly_xp,result_json) VALUES(UUID(),1,"+id+","+id+",'BRONZE',CURRENT_TIMESTAMP(6),5,"+(n==0?"JSON_OBJECT('marker','frozen','outcome','KEEP')":"NULL")+")");
                }
                try(var r=s.executeQuery("SELECT MD5(CAST(result_json AS CHAR)) FROM league_members WHERE season_id=1")){r.next();frozen=r.getString(1);}
                try(var r=s.executeQuery("SELECT MD5(CAST(policy_json AS CHAR)) FROM gamification_policies WHERE version='m05-p0-v1'")){r.next();policy=r.getString(1);}
                try(var r=s.executeQuery("SELECT MD5(CAST(result_json AS CHAR)) FROM submissions WHERE id=1")){r.next();submission=r.getString(1);}
            }
            var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);flyway.validate();assertThat(flyway.migrate().migrationsExecuted).isZero();
            try(var c=mysql.createConnection("");var s=c.createStatement()) {
                try(var r=s.executeQuery("SELECT JSON_UNQUOTE(JSON_EXTRACT(policy_json,'$.activityLevelCurve.type')),JSON_EXTRACT(policy_json,'$.activityLevelCurve.firstStepXp'),JSON_EXTRACT(policy_json,'$.activityLevelCurve.stepIncreaseXp'),JSON_EXTRACT(policy_json,'$.dailyXpCap') FROM gamification_policies WHERE version='m05-p0-v3'")) {
                    r.next();assertThat(r.getString(1)).isEqualTo("INCREASING");assertThat(r.getInt(2)).isEqualTo(10);assertThat(r.getInt(3)).isEqualTo(10);assertThat(r.getInt(4)).isEqualTo(200);
                }
                try(var r=s.executeQuery("SELECT version,JSON_EXTRACT(policy_json,'$.xpPerActivityLevel'),activated_at FROM gamification_policies ORDER BY version")) {
                    assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("m05-p0-v1");assertThat(r.getInt(2)).isEqualTo(100);String activated=r.getString(3);
                    assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("m05-p0-v2");assertThat(r.getInt(2)).isEqualTo(50);assertThat(r.getString(3)).isEqualTo(activated);
                    assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("m05-p0-v3");assertThat(r.getObject(2)).isNull();assertThat(r.getString(3)).isEqualTo(activated);
                }
                try(var r=s.executeQuery("SELECT id,policy_version,JSON_EXTRACT(rules_json,'$.minimumMembers'),JSON_EXTRACT(rules_json,'$.minimumMovementSlots'),revision FROM league_seasons ORDER BY id")) {
                    r.next();assertThat(r.getString(2)).isEqualTo("m05-p0-v1");assertThat(r.getInt(3)).isEqualTo(10);assertThat(r.getLong(5)).isZero();
                    r.next();assertThat(r.getString(2)).isEqualTo("m05-p0-v2");assertThat(r.getInt(3)).isEqualTo(2);assertThat(r.getInt(4)).isEqualTo(1);assertThat(r.getLong(5)).isEqualTo(1);
                    r.next();assertThat(r.getString(2)).isEqualTo("m05-p0-v1");assertThat(r.getInt(3)).isEqualTo(10);
                }
                try(var r=s.executeQuery("SELECT MD5(CAST(policy_json AS CHAR)) FROM gamification_policies WHERE version='m05-p0-v1'")){r.next();assertThat(r.getString(1)).isEqualTo(policy);}
                try(var r=s.executeQuery("SELECT MD5(CAST(result_json AS CHAR)) FROM league_members WHERE season_id=1")){r.next();assertThat(r.getString(1)).isEqualTo(frozen);}
                try(var r=s.executeQuery("SELECT MD5(CAST(result_json AS CHAR)) FROM submissions WHERE id=1")){r.next();assertThat(r.getString(1)).isEqualTo(submission);}
                try(var r=s.executeQuery("SELECT p.total_xp,x.awarded_xp,x.policy_version,(SELECT COUNT(*) FROM gamification_reward_claims) FROM gamification_profiles p JOIN xp_ledger x ON x.user_id=p.user_id")){r.next();assertThat(r.getLong(1)).isEqualTo(5);assertThat(r.getInt(2)).isEqualTo(5);assertThat(r.getString(3)).isEqualTo("m05-p0-v1");assertThat(r.getInt(4)).isEqualTo(1);}
            }
        }
    }
}
