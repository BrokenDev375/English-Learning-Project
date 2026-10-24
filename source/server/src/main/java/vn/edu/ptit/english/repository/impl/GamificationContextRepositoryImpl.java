package vn.edu.ptit.english.repository.impl;

import java.util.List;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.entity.*;
import vn.edu.ptit.english.repository.*;

@Repository
public class GamificationContextRepositoryImpl implements GamificationContextRepository {
    private final GamificationPolicyJpaRepository policies;
    private final ReviewRewardContextJpaRepository contexts;
    public GamificationContextRepositoryImpl(GamificationPolicyJpaRepository policies,ReviewRewardContextJpaRepository contexts) {
        this.policies=policies; this.contexts=contexts;
    }
    public GamificationPolicyEntity policy() { return policies.findById("m05-p0-v1").orElseThrow(()->new IllegalStateException("M05 policy is missing")); }
    public void save(ReviewRewardContextEntity value) { contexts.saveAndFlush(value); }
    public List<ReviewRewardContextEntity> contexts(long attemptId,long userId) { return contexts.findByIdAttemptIdAndUserIdOrderByIdObjectiveId(attemptId,userId); }
}
