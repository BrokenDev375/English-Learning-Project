package vn.edu.ptit.english.bootstrap;
import org.slf4j.*;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.repository.UserRepository;
import vn.edu.ptit.english.security.*;

@Component @Order(200) @ConditionalOnProperty(name="app.demo.enabled",havingValue="true")
public class CurriculumDemoInitializer implements ApplicationRunner {
    private static final Logger log=LoggerFactory.getLogger(CurriculumDemoInitializer.class);
    private final UserRepository users;private final CurriculumSeedService seed;private final String email;
    public CurriculumDemoInitializer(UserRepository users,CurriculumSeedService seed,@Value("${app.demo.teacher-email}") String email){this.users=users;this.seed=seed;this.email=email;}
    @Override public void run(ApplicationArguments args){var teacher=users.findByEmail(AuthValidation.normalizeEmail(email)).orElseThrow();if(teacher.role()!=Role.TEACHER||teacher.status()!=AccountStatus.ACTIVE){log.info("Demo curriculum skipped: configured author is not an active teacher");return;}log.info("M02 curriculum seed completed: {}",seed.seed(UserPrincipal.from(teacher)));}
}
