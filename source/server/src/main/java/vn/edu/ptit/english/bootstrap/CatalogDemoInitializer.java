package vn.edu.ptit.english.bootstrap;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.*;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.enums.*;
import vn.edu.ptit.english.repository.*;
import vn.edu.ptit.english.security.*;

@Component @Order(100)
@ConditionalOnProperty(name="app.demo.enabled",havingValue="true")
public class CatalogDemoInitializer implements ApplicationRunner {
    private final UserRepository users;
    private final TopicJpaRepository topics;
    private final ObjectiveJpaRepository objectives;
    private final UnitJpaRepository units;
    private final AccountGuard guard;
    private final String email;
    public CatalogDemoInitializer(UserRepository users, TopicJpaRepository topics, ObjectiveJpaRepository objectives,
        UnitJpaRepository units, AccountGuard guard, @Value("${app.demo.teacher-email}") String email) {
        this.users = users; this.topics = topics; this.objectives = objectives; this.units = units; this.guard = guard; this.email = email;
    }
    @Override @Transactional public void run(ApplicationArguments args) {
        var teacher = users.findByEmail(AuthValidation.normalizeEmail(email)).orElseThrow();
        if (teacher.status() != AccountStatus.ACTIVE || teacher.role() != Role.TEACHER) return;
        guard.lockCurrent(UserPrincipal.from(teacher));
        add(teacher.id(),"daily-routines","Sinh hoạt hằng ngày","Từ vựng và mẫu câu về các hoạt động mỗi ngày.","daily-actions","Mô tả thói quen hằng ngày",0);
        add(teacher.id(),"food-shopping","Ăn uống và mua sắm","Gọi món, hỏi giá và mua sắm trong tình huống quen thuộc.","order-food","Gọi món bằng mẫu câu đơn giản",1);
        add(teacher.id(),"travel-directions","Đi lại và chỉ đường","Phương tiện, địa điểm và cách hỏi đường cơ bản.","ask-directions","Hỏi và chỉ đường đến một địa điểm",2);
    }
    private void add(long owner, String code, String name, String description, String objectiveCode, String objective, int order) {
        var topic = topics.findByCode(code).orElseGet(() -> topics.saveAndFlush(new TopicEntity(owner,code,name,description)));
        if (objectives.findByCode(objectiveCode).isEmpty())
            objectives.save(new ObjectiveEntity(owner,topic.getId(),objectiveCode,objective,description));
        String seedKey = "m02-catalog-"+code;
        if (units.findBySeedKey(seedKey).isEmpty()) {
            var unit = new UnitEntity(owner,topic.getId(),name+" — Bắt đầu",description,1,order);
            unit.setSeedKey(seedKey); units.save(unit);
        }
    }
}
