package vn.edu.ptit.english.repository.impl;

import java.util.List;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;

@Repository
public class GamificationContextRepositoryImpl implements GamificationContextRepository {
    private final GamificationPolicyJpaRepository policies;
    private final ReviewRewardContextJpaRepository contexts;
    private final java.time.Clock clock;
    public GamificationContextRepositoryImpl(GamificationPolicyJpaRepository policies,ReviewRewardContextJpaRepository contexts,java.time.Clock clock) {
        this.clock=clock;
        this.policies=policies; this.contexts=contexts;
    }
    public GamificationPolicyEntity policy() { return policyAt(clock.instant()); }
    public GamificationPolicyEntity policyAt(java.time.Instant at) {
        var rows=policies.findAllByOrderByEffectiveAtDescVersionDesc();
        if(rows.isEmpty())throw new IllegalStateException("M05 policy is missing");
        return rows.stream().filter(p->!p.getEffectiveAt().isAfter(at)).findFirst().orElse(rows.get(rows.size()-1));
    }
    public void save(ReviewRewardContextEntity value) { contexts.saveAndFlush(value); }
    public List<ReviewRewardContextEntity> contexts(long attemptId,long userId) { return contexts.findByIdAttemptIdAndUserIdOrderByIdObjectiveId(attemptId,userId); }
}
