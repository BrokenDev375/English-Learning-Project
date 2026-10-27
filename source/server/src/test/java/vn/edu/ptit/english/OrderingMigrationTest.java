package vn.edu.ptit.english;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class OrderingMigrationTest {
    @Test void upgradeFromV8PreservesQuestionDataAndExtendsTheTypeConstraintExactlyOnce()throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4")) {
            mysql.start();
            Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("8").load().migrate();
            String insertOrdering="""
                INSERT INTO question_versions(question_id,version_no,type,topic_id,objective_id,difficulty,prompt,content_json,answer_json,explanation,source_json)
                SELECT question_id,2,'ORDERING',topic_id,objective_id,'EASY','Arrange the words',
                    JSON_OBJECT('tokens',JSON_ARRAY(JSON_OBJECT('id','w1','text','She'),JSON_OBJECT('id','w2','text','runs'))),
                    JSON_OBJECT('acceptedOrders',JSON_ARRAY(JSON_ARRAY('w1','w2'))),'She runs',source_json
                FROM question_versions WHERE version_no=1
                """;
            String original;
            try(var connection=mysql.createConnection("");var s=connection.createStatement()) {
                s.executeUpdate("INSERT INTO users(email,password_hash,full_name,role) VALUES('ordering-migration@test.local','hash','Teacher','TEACHER')");
                s.executeUpdate("INSERT INTO topics(owner_id,code,name) VALUES(1,'ordering-migration','Preserved topic')");
                s.executeUpdate("INSERT INTO learning_objectives(owner_id,topic_id,code,name) VALUES(1,1,'ordering-objective','Preserved objective')");
                s.executeUpdate("INSERT INTO questions(owner_id,origin_key) VALUES(1,UUID())");
                s.executeUpdate("""
                    INSERT INTO question_versions(question_id,version_no,type,topic_id,objective_id,difficulty,prompt,content_json,answer_json,explanation,source_json)
                    VALUES(1,1,'SINGLE_CHOICE',1,1,'EASY','Preserved question',
                    JSON_OBJECT('options',JSON_ARRAY(JSON_OBJECT('id','a','text','A'),JSON_OBJECT('id','b','text','B'))),
                    JSON_OBJECT('correctOptionId','a'),'Preserved explanation',JSON_OBJECT('label','Self authored','url',NULL,'license',NULL))
                    """);
                assertThatThrownBy(()->s.executeUpdate(insertOrdering)).isInstanceOf(java.sql.SQLException.class);
                try(var rows=s.executeQuery("SELECT CONCAT(prompt,'|',content_json,'|',answer_json,'|',source_json) FROM question_versions WHERE version_no=1")){rows.next();original=rows.getString(1);}
            }
            var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(3);flyway.validate();
            try(var connection=mysql.createConnection("");var s=connection.createStatement()) {
                try(var rows=s.executeQuery("SELECT CONCAT(prompt,'|',content_json,'|',answer_json,'|',source_json) FROM question_versions WHERE version_no=1")){rows.next();assertThat(rows.getString(1)).isEqualTo(original);}
                assertThat(s.executeUpdate(insertOrdering)).isEqualTo(1);
                assertThatThrownBy(()->s.executeUpdate("UPDATE question_versions SET type='UNKNOWN' WHERE version_no=2")).isInstanceOf(java.sql.SQLException.class);
                try(var rows=s.executeQuery("SELECT COUNT(*) FROM question_versions")){rows.next();assertThat(rows.getInt(1)).isEqualTo(2);}
            }
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
}
