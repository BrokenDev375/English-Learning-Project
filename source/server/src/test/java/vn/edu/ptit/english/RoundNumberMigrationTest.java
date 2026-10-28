package vn.edu.ptit.english;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class RoundNumberMigrationTest {
    @Test void upgradePreservesPublishedNumbersAndAppendsDraftsExactlyOnce()throws Exception {
        try(var mysql=new MySQLContainer<>("mysql:8.4")) {
            mysql.start();Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("11").load().migrate();
            try(var c=mysql.createConnection("");var s=c.createStatement()) {
                s.executeUpdate("INSERT INTO users(email,password_hash,full_name,role) VALUES('round@test.local','hash','Teacher','TEACHER')");
                s.executeUpdate("INSERT INTO topics(owner_id,code,name) VALUES(1,'round','Preserved')");
                s.executeUpdate("INSERT INTO units(owner_id,topic_id,title,stage,sort_order,revision) VALUES(1,1,'Draft first',1,0,3),(1,1,'Published two',2,0,4),(1,1,'Published one',1,1,5),(1,1,'Retired',1,0,6)");
                s.executeUpdate("INSERT INTO lessons(owner_id,unit_id) VALUES(1,2),(1,3),(1,4)");
                s.executeUpdate("INSERT INTO lesson_versions(lesson_id,version_no,title,instructions_json) VALUES(1,1,'Two',JSON_ARRAY()),(2,1,'One',JSON_ARRAY()),(3,1,'Retired',JSON_ARRAY())");
                s.executeUpdate("UPDATE lessons SET published_version_id=id");
                s.executeUpdate("UPDATE lessons SET retired_at=CURRENT_TIMESTAMP(6) WHERE id=3");
            }
            var flyway=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            assertThat(flyway.migrate().migrationsExecuted).isEqualTo(2);flyway.validate();
            try(var c=mysql.createConnection("");var s=c.createStatement();var r=s.executeQuery("SELECT id,round_number,revision FROM units ORDER BY round_number")) {
                long[] ids={3,2,1,4}, revisions={5,4,3,6};
                for(int i=0;i<4;i++){assertThat(r.next()).isTrue();assertThat(r.getLong(1)).isEqualTo(ids[i]);assertThat(r.getInt(2)).isEqualTo(i+1);assertThat(r.getLong(3)).isEqualTo(revisions[i]);}
                assertThat(r.next()).isFalse();
            }
            try(var c=mysql.createConnection("");var s=c.createStatement()) {
                assertThatThrownBy(()->s.executeUpdate("UPDATE units SET round_number=0 WHERE id=1")).isInstanceOf(java.sql.SQLException.class);
                assertThatThrownBy(()->s.executeUpdate("UPDATE units SET round_number=1 WHERE id=1")).isInstanceOf(java.sql.SQLException.class);
            }
            assertThat(flyway.migrate().migrationsExecuted).isZero();
        }
    }
}
