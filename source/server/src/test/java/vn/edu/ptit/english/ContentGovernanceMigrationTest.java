package vn.edu.ptit.english;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;
class ContentGovernanceMigrationTest {
    @Test void v13BackfillsTeacherAssignmentsWithoutChangingExistingUnitsAndEnforcesOnePendingReview()throws Exception{
        try(var mysql=new MySQLContainer<>("mysql:8.4")){
            mysql.start();Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("12").load().migrate();String before;
            try(var c=mysql.createConnection("");var s=c.createStatement()){
                s.executeUpdate("INSERT INTO users(email,password_hash,full_name,role) VALUES('teacher@migration.test','hash','Teacher','TEACHER'),('admin@migration.test','hash','Admin','ADMIN')");
                s.executeUpdate("INSERT INTO topics(owner_id,code,name) VALUES(1,'governance-migration','Topic')");
                s.executeUpdate("INSERT INTO units(owner_id,topic_id,title,stage,sort_order,round_number,revision) VALUES(1,1,'Teacher owned',1,0,7,4),(2,1,'Admin owned',1,1,9,6)");
                s.executeUpdate("INSERT INTO questions(owner_id,origin_key) VALUES(1,UUID())");
                try(var r=s.executeQuery("SELECT GROUP_CONCAT(CONCAT_WS('|',id,owner_id,topic_id,title,stage,sort_order,round_number,revision,created_at,updated_at) ORDER BY id SEPARATOR ';') FROM units")){r.next();before=r.getString(1);}
            }
            var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);flyway.validate();
            try(var c=mysql.createConnection("");var s=c.createStatement()){
                try(var r=s.executeQuery("SELECT GROUP_CONCAT(CONCAT_WS('|',id,owner_id,topic_id,title,stage,sort_order,round_number,revision,created_at,updated_at) ORDER BY id SEPARATOR ';') FROM units")){r.next();assertThat(r.getString(1)).isEqualTo(before);}
                try(var r=s.executeQuery("SELECT assigned_teacher_id FROM units ORDER BY id")){r.next();assertThat(r.getLong(1)).isEqualTo(1);r.next();assertThat(r.getObject(1)).isNull();}
                String insert="INSERT INTO content_review_requests(kind,question_id,version_id,version_revision,preview_token,title,snapshot_json,submitted_by,submitted_at) VALUES('QUESTION',1,1,0,REPEAT('a',64),'Preserved snapshot',JSON_OBJECT('marker','original'),1,CURRENT_TIMESTAMP(6))";
                s.executeUpdate(insert);assertThatThrownBy(()->s.executeUpdate(insert)).isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(()->s.executeUpdate("UPDATE content_review_requests SET state='RETURNED',reviewed_by=2,reviewed_at=CURRENT_TIMESTAMP(6),note=' ' WHERE id=1")).isInstanceOf(java.sql.SQLException.class);
                s.executeUpdate("UPDATE content_review_requests SET state='RETURNED',reviewed_by=2,reviewed_at=CURRENT_TIMESTAMP(6),note='Please revise',revision=1 WHERE id=1");s.executeUpdate(insert);
                try(var r=s.executeQuery("SELECT JSON_UNQUOTE(JSON_EXTRACT(snapshot_json,'$.marker')) FROM content_review_requests WHERE id=1")){r.next();assertThat(r.getString(1)).isEqualTo("original");}
            }
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
}
