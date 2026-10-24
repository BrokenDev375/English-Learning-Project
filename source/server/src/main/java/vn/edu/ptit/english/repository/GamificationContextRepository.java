package vn.edu.ptit.english.repository;

import java.util.List;
import vn.edu.ptit.english.entity.*;

public interface GamificationContextRepository {
    GamificationPolicyEntity policy();
    void save(ReviewRewardContextEntity context);
    List<ReviewRewardContextEntity> contexts(long attemptId, long userId);
}
