package vn.edu.ptit.english.service;
import java.util.List;
import vn.edu.ptit.english.dto.AttemptViews.Result;
import vn.edu.ptit.english.dto.GamificationViews.LeagueAward;
import vn.edu.ptit.english.dto.LeagueViews.*;
import vn.edu.ptit.english.security.UserPrincipal;

public interface LeagueService {
    Overview overview(UserPrincipal actor);
    RequestReceiptService.Result join(String requestId,UserPrincipal actor);
    Board leaderboard(int page,int size,UserPrincipal actor);
    History history(int page,int size,UserPrincipal actor);
    void lockSources(List<Result> sources);
    LeagueAward award(long user,Result result,int xp);
}
