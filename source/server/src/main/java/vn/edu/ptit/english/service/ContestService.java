package vn.edu.ptit.english.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.List;
import vn.edu.ptit.english.dto.ContestData.Command;
import vn.edu.ptit.english.security.UserPrincipal;
import vn.edu.ptit.english.service.RequestReceiptService.Result;

public interface ContestService {
    Object list(int page,int size,UserPrincipal actor);
    Object author(long id,UserPrincipal actor);
    Result create(Command input,UserPrincipal actor);
    Result configure(long id,String action,Command input,UserPrincipal actor);
    Object monitor(long id,String filter,int page,int size,UserPrincipal actor);
    Object events(long id,Long participantId,int page,int size,UserPrincipal actor);
    Result decide(long id,long participantId,String action,Command input,UserPrincipal actor);
    Object learner(long id,UserPrincipal actor);
    Result participate(long id,String action,Command input,UserPrincipal actor);
    Object heartbeat(long id,JsonNode input,UserPrincipal actor);
    Object board(long id,UserPrincipal actor);
    boolean canSubscribe(long id,UserPrincipal actor);
    List<Long> activeIds();
    void tick(long id);
}
