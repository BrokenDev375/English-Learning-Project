package vn.edu.ptit.english.service;
import vn.edu.ptit.english.dto.WeeklyLeaderboardViews.Board;
import vn.edu.ptit.english.security.UserPrincipal;

public interface WeeklyLeaderboardService {
    Board read(int page,int size,UserPrincipal actor);
}
