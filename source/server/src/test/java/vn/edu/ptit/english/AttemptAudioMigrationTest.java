package vn.edu.ptit.english;

import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.MySQLContainer;
import static org.assertj.core.api.Assertions.*;

class AttemptAudioMigrationTest {
    @Test void v18UpgradesMainV17WithoutChangingExistingAccountsOrHistory()throws Exception{
        try(var mysql=new MySQLContainer<>("mysql:8.4")){
            mysql.start();var before=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).target("17").load();before.migrate();
            try(var c=mysql.createConnection("");var s=c.createStatement()){
                s.executeUpdate("INSERT INTO users(email,password_hash,full_name,role) VALUES('preserved@migration.test','hash','Preserved learner','STUDENT')");
            }
            var after=Flyway.configure().dataSource(mysql.getJdbcUrl(),mysql.getUsername(),mysql.getPassword()).load();
            assertThat(after.migrate().migrationsExecuted).isEqualTo(1);after.validate();
            try(var c=mysql.createConnection("");var s=c.createStatement()){
                try(var r=s.executeQuery("SELECT email,full_name,role FROM users")){assertThat(r.next()).isTrue();assertThat(r.getString(1)).isEqualTo("preserved@migration.test");assertThat(r.getString(2)).isEqualTo("Preserved learner");assertThat(r.getString(3)).isEqualTo("STUDENT");assertThat(r.next()).isFalse();}
                try(var r=s.executeQuery("SELECT version,script FROM flyway_schema_history WHERE version IN ('14','18') ORDER BY installed_rank")){assertThat(r.next()).isTrue();assertThat(r.getString(2)).isEqualTo("V14__lesson_activities.sql");assertThat(r.next()).isTrue();assertThat(r.getString(2)).isEqualTo("V18__attempt_audio_limits.sql");assertThat(r.next()).isFalse();}
                try(var r=s.executeQuery("SELECT COUNT(*) FROM attempt_audio_listens")){r.next();assertThat(r.getInt(1)).isZero();}
            }
            assertThat(after.migrate().migrationsExecuted).isZero();
        }
    }
}
