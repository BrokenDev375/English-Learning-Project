package vn.edu.ptit.english.service.impl;

import java.time.*;
import java.util.*;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import vn.edu.ptit.english.dto.*;
import vn.edu.ptit.english.dto.AdminSessionViews.*;
import vn.edu.ptit.english.enums.Role;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.repository.AdminSessionRepository;
import vn.edu.ptit.english.repository.AdminSessionRepository.AttemptContext;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.security.SessionActivityRegistry.*;
import vn.edu.ptit.english.service.AdminSessionService;

@Service
@Transactional(readOnly=true)
public class AdminSessionServiceImpl implements AdminSessionService {
    public static final String BASIS="LOCAL_BACKEND_LIVE_SOCKET";
    private final SessionActivityRegistry registry;
    private final LiveConnections live;
    private final AdminSessionRepository repository;
    private final AccountGuard guard;
    private final Clock clock;
    public AdminSessionServiceImpl(SessionActivityRegistry registry,LiveConnections live,AdminSessionRepository repository,AccountGuard guard,Clock clock){
        this.registry=registry;this.live=live;this.repository=repository;this.guard=guard;this.clock=clock;
    }
    private void authorize(UserPrincipal actor){
        guard.requireCurrent(actor);
        if(actor.role()!=Role.ADMIN)throw new ApiException(HttpStatus.FORBIDDEN,"FORBIDDEN","Chỉ quản trị viên được giám sát phiên.");
    }
    private record Row(SessionSnapshot source, Session session, List<Tab> tabs) {}
    private List<Row> rows(List<SessionSnapshot> sessions,List<LiveConnections.SocketSnapshot> sockets,Instant now){
        var ids=sessions.stream().flatMap(s->s.tabs().stream()).map(t->t.context().attemptId()).filter(Objects::nonNull).collect(Collectors.toSet());
        var contexts=repository.attempts(ids).stream().collect(Collectors.toMap(AttemptContext::attemptId,c->c));
        return sessions.stream().map(s->{
            var connected=sockets.stream().filter(w->w.userId()==s.userId() && w.sessionId().equals(s.id())).toList();
            var tabs=s.tabs().stream().map(t->new Tab(t.tabRef(),connected.stream().anyMatch(w->t.id().equals(w.tabId())),t.context().visibility(),
                t.lastInteractionAt(),t.lastSeenAt(),context(s.userId(),t.context(),contexts))).toList();
            return new Row(s,new Session(s.sessionRef(),s.loginAt(),s.lastInteractionAt(),s.idleExpiresAt(),
                interaction(s.lastInteractionAt(),now),!connected.isEmpty(),connected.size(),tabs.size()),tabs);
        }).toList();
    }
    private AdminSessionViews.Context context(long user,SessionActivityRegistry.Context c,Map<Long,AttemptContext> contexts){
        if(c.attemptId()==null)return new AdminSessionViews.Context(c.activity(),null,null,null);
        var a=contexts.get(c.attemptId());
        if(a==null || a.userId()!=user)return new AdminSessionViews.Context("OTHER",null,null,null);
        String activity=c.activity().equals("RESULT") && a.state().equals("SUBMITTED")?"RESULT":a.mode().equals("REVIEW")?"REVIEW":"LEARN";
        return new AdminSessionViews.Context(activity,a.attemptId(),a.lessonTitle(),a.roundNumber());
    }
    private String interaction(Instant last,Instant now){return Duration.between(last,now).toMillis()>=60000?"IDLE":"ACTIVE";}
    private Account account(User user,List<Row> rows,Instant now){
        var onlineTabs=rows.stream().flatMap(r->r.tabs().stream()).filter(Tab::connected).toList();
        var visible=onlineTabs.stream().filter(t->t.visibility().equals("VISIBLE") && t.lastInteractionAt()!=null).toList();
        var primary=visible.isEmpty()?onlineTabs.stream().max(Comparator.comparing(Tab::lastSeenAt).thenComparing(Tab::tabRef)).orElse(null)
            :visible.stream().max(Comparator.comparing(Tab::lastInteractionAt).thenComparing(Tab::tabRef)).orElseThrow();
        Instant last=rows.stream().map(r->r.session().lastInteractionAt()).max(Instant::compareTo).orElseThrow();
        Instant deadline=rows.stream().map(r->r.session().idleExpiresAt()).max(Instant::compareTo).orElseThrow();
        var sessions=rows.stream().map(Row::session).sorted(Comparator.comparing(Session::sessionRef)).toList();
        return new Account(user,primary==null?new AdminSessionViews.Context("OTHER",null,null,null):primary.context(),primary==null?"UNKNOWN":primary.visibility(),
            interaction(last,now),last,deadline,sessions.size(),onlineTabs.size(),sessions.stream().mapToLong(Session::openConnections).sum(),sessions);
    }
    public Page list(AdminSessionQuery query,UserPrincipal actor){
        authorize(actor);Instant now=clock.instant();
        var sessions=registry.snapshot();var sockets=live.monitoringSnapshot();
        var onlineIds=sockets.stream().map(LiveConnections.SocketSnapshot::userId).collect(Collectors.toSet());
        var grouped=rows(sessions.stream().filter(s->onlineIds.contains(s.userId())).toList(),sockets,now).stream().collect(Collectors.groupingBy(r->r.source().userId()));
        String q=query.q().toLowerCase(Locale.ROOT);
        var accounts=repository.users(grouped.keySet()).stream().map(u->account(u,grouped.get(u.userId()),now))
            .filter(a->a.openConnections()>0 && (query.role()==null || a.user().role()==query.role())
                && (q.isEmpty() || a.user().fullName().toLowerCase(Locale.ROOT).contains(q) || a.user().email().toLowerCase(Locale.ROOT).contains(q))
                && (query.activity()==null || a.primaryContext().activity().equals(query.activity())))
            .sorted(Comparator.comparingLong(a->a.user().userId())).toList();
        var activities=new TreeMap<String,Long>();AdminSessionQuery.ACTIVITIES.forEach(k->activities.put(k,0L));
        accounts.forEach(a->activities.merge(a.primaryContext().activity(),1L,Long::sum));
        var summary=new Summary(accounts.size(),accounts.stream().mapToLong(Account::validSessionCount).sum(),accounts.stream().mapToLong(Account::tabCount).sum(),
            accounts.stream().mapToLong(Account::openConnections).sum(),Collections.unmodifiableMap(activities),accounts.stream().filter(a->a.interactionState().equals("IDLE")).count());
        int start=(int)Math.min((long)query.page()*query.size(),accounts.size()),end=Math.min(start+query.size(),accounts.size());
        return new Page(now,BASIS,summary,accounts.subList(start,end),accounts.size(),query.page(),query.size());
    }
    public Detail detail(String ref,UserPrincipal actor){
        authorize(actor);
        var source=registry.snapshot().stream().filter(s->s.sessionRef().equals(ref)).findFirst().orElseThrow(AdminSessionServiceImpl::missing);
        var user=repository.users(Set.of(source.userId())).stream().findFirst().orElseThrow(AdminSessionServiceImpl::missing);
        Instant now=clock.instant();var row=rows(List.of(source),live.monitoringSnapshot(),now).get(0);
        return new Detail(now,BASIS,user,row.session(),row.tabs());
    }
    private static ApiException missing(){return new ApiException(HttpStatus.NOT_FOUND,"SESSION_NOT_FOUND","Phiên đã kết thúc hoặc không tồn tại.");}
}
