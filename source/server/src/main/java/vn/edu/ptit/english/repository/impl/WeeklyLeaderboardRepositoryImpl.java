package vn.edu.ptit.english.repository.impl;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import java.sql.Date;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import org.springframework.stereotype.Repository;
import vn.edu.ptit.english.repository.WeeklyLeaderboardRepository;

@Repository
public class WeeklyLeaderboardRepositoryImpl implements WeeklyLeaderboardRepository {
    @PersistenceContext private EntityManager em;
    private static final String RANKED = """
        WITH scores AS (
            SELECT user_id, SUM(awarded_xp) AS weekly_xp
            FROM gamification_daily_stats
            WHERE activity_date >= :start AND activity_date < :end
            GROUP BY user_id HAVING SUM(awarded_xp) > 0
        ), ranked AS (
            SELECT u.id, u.full_name, COALESCE(p.total_xp,0) AS total_xp, s.weekly_xp,
                   RANK() OVER (ORDER BY s.weekly_xp DESC) AS rank_value,
                   ROW_NUMBER() OVER (ORDER BY s.weekly_xp DESC,u.id) AS row_position
            FROM scores s JOIN users u ON u.id=s.user_id
            LEFT JOIN gamification_profiles p ON p.user_id=u.id
            WHERE u.role='STUDENT' AND u.status='ACTIVE'
        )
        """;
    private static final String COLUMNS="SELECT id,full_name,total_xp,weekly_xp,rank_value,row_position FROM ranked ";
    private Query query(String sql,LocalDate start,LocalDate end) {
        return em.createNativeQuery(RANKED+sql).setParameter("start",Date.valueOf(start)).setParameter("end",Date.valueOf(end));
    }
    private Score score(Object value) {
        var r=(Object[])value;
        return new Score(((Number)r[0]).longValue(),(String)r[1],((Number)r[2]).longValue(),
            ((Number)r[3]).longValue(),((Number)r[4]).longValue(),((Number)r[5]).longValue());
    }
    public List<Score> page(LocalDate start,LocalDate end,long offset,int size) {
        return query(COLUMNS+"WHERE row_position > :offset ORDER BY row_position LIMIT :size",start,end)
            .setParameter("offset",offset).setParameter("size",size).getResultStream().map(this::score).toList();
    }
    public Optional<Score> self(LocalDate start,LocalDate end,long userId) {
        return query(COLUMNS+"WHERE id=:user",start,end).setParameter("user",userId).getResultStream().map(this::score).findFirst();
    }
    public long count(LocalDate start,LocalDate end) {
        return ((Number)query("SELECT COUNT(*) FROM ranked",start,end).getSingleResult()).longValue();
    }
}
