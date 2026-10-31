package vn.edu.ptit.english;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class ContestPaperMigrationTest {
    @Test void v17PreservesExistingDraftsAndFrozenPapersWhileBackfillingIndependentPermissions()throws Exception{
        try(var mysql=new MySQLContainer<>("mysql:8.4")){
            mysql.start();var before=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("16").load();before.migrate();String frozen;
            try(var c=mysql.createConnection("");var s=c.createStatement()){
                s.executeUpdate("INSERT INTO users(email,password_hash,full_name,role) VALUES('admin@migration.test','hash','Admin','ADMIN'),('teacher@migration.test','hash','Teacher','TEACHER')");
                s.executeUpdate("INSERT INTO contests(owner_id,state,title,description,registration_closes_at,starts_at,ends_at,proctor_ids_json,draft_json,paper_json,created_at) VALUES(1,'DRAFT','Saved draft','Original description','2026-10-30 08:00:00','2026-10-30 09:00:00','2026-10-30 10:00:00',JSON_ARRAY(2),JSON_ARRAY(JSON_OBJECT('marker','draft')),NULL,'2026-10-30 07:00:00'),(1,'PUBLISHED','Frozen exam','','2026-10-30 08:00:00','2026-10-30 09:00:00','2026-10-30 10:00:00',JSON_ARRAY(),JSON_ARRAY(JSON_OBJECT('marker','old draft')),JSON_ARRAY(JSON_OBJECT('marker','frozen')),'2026-10-30 07:00:00')");
                try(var r=s.executeQuery("SELECT MD5(CAST(paper_json AS CHAR)) FROM contests WHERE id=2")){r.next();frozen=r.getString(1);}
            }
            var after=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();assertThat(after.migrate().migrationsExecuted).isEqualTo(2);after.validate();
            try(var c=mysql.createConnection("");var s=c.createStatement()){
                try(var r=s.executeQuery("SELECT user_id,can_author,can_proctor FROM contest_teacher_permissions")){assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isEqualTo(2);assertThat(r.getBoolean(2)).isTrue();assertThat(r.getBoolean(3)).isTrue();}
                try(var r=s.executeQuery("SELECT owner_id,state,JSON_UNQUOTE(JSON_EXTRACT(questions_json,'$[0].marker')) FROM contest_papers ORDER BY id")){r.next();assertThat(r.getLong(1)).isEqualTo(2);assertThat(r.getString(2)).isEqualTo("DRAFT");assertThat(r.getString(3)).isEqualTo("draft");r.next();assertThat(r.getLong(1)).isEqualTo(1);assertThat(r.getString(2)).isEqualTo("READY");assertThat(r.getString(3)).isEqualTo("frozen");}
                try(var r=s.executeQuery("SELECT source_paper_id,source_paper_revision,MD5(CAST(paper_json AS CHAR)) FROM contests WHERE id=2")){r.next();assertThat(r.getLong(1)).isPositive();assertThat(r.getLong(2)).isZero();assertThat(r.getString(3)).isEqualTo(frozen);}
            }
            assertThat(after.migrate().migrationsExecuted).isZero();
        }
    }
}
