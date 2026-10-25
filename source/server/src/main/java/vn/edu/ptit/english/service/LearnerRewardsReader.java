package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.StatisticsViews.Rewards;
import vn.edu.ptit.english.security.UserPrincipal;

/** M05 may provide a bean for committed reward/league data. M07 never awards XP. */
public interface LearnerRewardsReader {
    Rewards read(UserPrincipal actor);
}
