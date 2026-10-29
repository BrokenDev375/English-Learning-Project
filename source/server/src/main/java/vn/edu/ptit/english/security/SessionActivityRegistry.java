package vn.edu.ptit.english.security;

import jakarta.servlet.http.HttpSession;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Consumer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import vn.edu.ptit.english.exception.ApiException;

/** Ephemeral state for a single backend. No activity is inferred from HTTP access or heartbeat. */
@Component
public class SessionActivityRegistry {
    public record State(String sessionGeneration, Instant serverTime, Instant lastInteractionAt,
                        Instant idleExpiresAt, Instant warningAt) {}
    public record Context(String activity, Long attemptId, String visibility) {}
    /** Internal projection only; raw IDs must never be serialized by controllers. */
    public record TabSnapshot(String id, String tabRef, Instant lastInteractionAt, Instant lastSeenAt, Context context) {}
    public record SessionSnapshot(String id, String sessionRef, long userId, Instant loginAt,
        Instant lastInteractionAt, Instant idleExpiresAt, List<TabSnapshot> tabs) {}
    private static final class Tab {
        long seq, seen;
        Long last;
        final String ref = UUID.randomUUID().toString();
        Context context;
        Tab(long now, Context context) { seen = now; this.context = context; }
    }
    private static final class Entry {
        final HttpSession session;
        final long userId;
        final String generation = UUID.randomUUID().toString();
        final String ref = UUID.randomUUID().toString();
        final long loginAt;
        final Map<String, Tab> tabs = new HashMap<>();
        long last, window; int count;
        boolean expired;
        Entry(HttpSession session, long userId, long now) { this.session=session; this.userId=userId; last=now; window=now; loginAt=now; }
    }
    private final Map<String, Entry> entries = new ConcurrentHashMap<>();
    private final Map<String, Long> expiredIds = new ConcurrentHashMap<>();
    private final String salt = UUID.randomUUID().toString();
    private final Clock clock;
    private final long timeout, warning;
    private volatile Consumer<String> closing = id -> {};
    private volatile Runnable changed = () -> {};
    public SessionActivityRegistry(Clock clock,
        @Value("${app.session.idle-timeout-seconds:300}") long seconds,
        @Value("${app.session.idle-warning-seconds:30}") long warningSeconds) {
        if (seconds < 2 || seconds > 86400 || warningSeconds < 1 || warningSeconds >= seconds)
            throw new IllegalArgumentException("Invalid idle session configuration");
        this.clock=clock; timeout=seconds*1000; warning=warningSeconds*1000;
    }
    public void onClosing(Consumer<String> closing) { this.closing=closing; }
    public void onChanged(Runnable changed) { this.changed=changed; }
    /** Heartbeat keeps existing tab metadata live, never its interaction deadline or sequence. */
    public void socketSeen(String sessionId,String tabId) {
        if(tabId==null)return;
        var e=entries.get(sessionId);if(e==null)return;
        synchronized(e){
            long now=clock.millis();var tab=e.tabs.get(tabId);
            if(!e.expired && now-e.last<timeout && tab!=null && now-tab.seen<75000)tab.seen=now;
        }
    }
    @org.springframework.beans.factory.annotation.Autowired
    public void validateSweep(@Value("${app.session.idle-sweep-ms:5000}") long millis) {
        if(millis<100 || millis>60000)throw new IllegalArgumentException("Invalid idle sweep configuration");
    }
    public void register(HttpSession session, long userId) {
        entries.put(session.getId(),new Entry(session,userId,clock.millis()));
        changed.run();
    }
    public void remove(String id) {
        var e=entries.remove(id);
        if(e!=null) synchronized(e) { e.expired=true; }
        if(e!=null)changed.run();
    }
    public boolean idleReason(String id) {
        if(id==null)return false;
        var until=expiredIds.get(hash(id)); return until!=null && until>clock.millis();
    }
    public State state(String id) { return access(id,null,0,null,false); }
    public State report(String id, String tabId, long seq, Context context, boolean interaction) {
        return access(id,tabId,seq,context,interaction);
    }
    private State access(String id, String tabId, long seq, Context context, boolean interaction) {
        var e=id==null?null:entries.get(id);
        if(e==null) throw unauthorized(id);
        boolean expire=false, notify=false;
        State result=null;
        synchronized(e) {
            long now=clock.millis();
            if(e.expired)throw unauthorized(id);
            if(now-e.last>=timeout) { e.expired=true; expire=true; }
            else {
                if(tabId!=null) {
                    if(now-e.window>=60000) {e.window=now;e.count=0;}
                    if(++e.count>120)throw error(HttpStatus.TOO_MANY_REQUESTS,"ACTIVITY_RATE_LIMIT");
                    var tab=e.tabs.get(tabId);
                    if(seq==0) {
                        if(interaction)throw error(HttpStatus.BAD_REQUEST,"INVALID_ACTIVITY");
                        if(tab==null) {
                            long live=e.tabs.values().stream().filter(t->now-t.seen<75000).count();
                            if(live>=16 || e.tabs.size()>=256)throw error(HttpStatus.TOO_MANY_REQUESTS,"TAB_LIMIT");
                            e.tabs.put(tabId,new Tab(now,context));
                            notify=true;
                        }
                    } else {
                        if(tab==null || now-tab.seen>=75000)throw error(HttpStatus.CONFLICT,"TAB_EXPIRED");
                        if(seq>tab.seq) {
                            notify=interaction || !Objects.equals(tab.context,context);
                            tab.seq=seq;tab.seen=now;tab.context=context;
                            if(interaction){e.last=now;tab.last=now;}
                        }
                    }
                }
                result=new State(e.generation,Instant.ofEpochMilli(now),Instant.ofEpochMilli(e.last),
                    Instant.ofEpochMilli(e.last+timeout),Instant.ofEpochMilli(e.last+timeout-warning));
            }
        }
        if(result!=null){if(notify)changed.run();return result;}
        if(expire)finishExpiry(id,e);
        throw unauthorized(id);
    }
    public boolean current(String id) {
        try { state(id); return true; } catch(ApiException ex) {return false;}
    }
    private void finishExpiry(String id, Entry e) {
        if(expiredIds.size()>=10000) {
            expiredIds.entrySet().removeIf(x->x.getValue()<=clock.millis());
            if(expiredIds.size()>=10000) expiredIds.keySet().stream().findFirst().ifPresent(expiredIds::remove);
        }
        expiredIds.put(hash(id),clock.millis()+300000);
        entries.remove(id,e);
        changed.run();
        try { closing.accept(id); }
        finally {try{e.session.invalidate();}catch(IllegalStateException ignored){}}
    }
    @Scheduled(fixedDelayString="${app.session.idle-sweep-ms:5000}")
    public void sweep() {
        entries.forEach((id,e)->current(id));
        expiredIds.entrySet().removeIf(x->x.getValue()<=clock.millis());
    }
    public ApiException unauthorized(String id) {
        return new ApiException(HttpStatus.UNAUTHORIZED,idleReason(id)?"SESSION_IDLE_TIMEOUT":"UNAUTHENTICATED",
            idleReason(id)?"Phiên làm việc đã hết hạn do không tương tác. Vui lòng đăng nhập lại.":"Vui lòng đăng nhập lại.");
    }
    /** Does not touch, expire or renew any session. Cleanup remains the idle worker's responsibility. */
    public List<SessionSnapshot> snapshot() {
        long now=clock.millis();
        var result=new ArrayList<SessionSnapshot>();
        entries.forEach((id,e)->{
            synchronized(e){
                if(e.expired || now-e.last>=timeout)return;
                var tabs=e.tabs.entrySet().stream().filter(t->now-t.getValue().seen<75000).map(t->{
                    var v=t.getValue();return new TabSnapshot(t.getKey(),v.ref,v.last==null?null:Instant.ofEpochMilli(v.last),
                        Instant.ofEpochMilli(v.seen),v.context);
                }).sorted(Comparator.comparing(TabSnapshot::tabRef)).toList();
                result.add(new SessionSnapshot(id,e.ref,e.userId,Instant.ofEpochMilli(e.loginAt),
                    Instant.ofEpochMilli(e.last),Instant.ofEpochMilli(e.last+timeout),tabs));
            }
        });
        return List.copyOf(result);
    }
    private static ApiException error(HttpStatus status,String code) {return new ApiException(status,code,"Chưa cập nhật được hoạt động phiên.");}
    private String hash(String id) {
        try{return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest((salt+id).getBytes(StandardCharsets.UTF_8)));}
        catch(java.security.NoSuchAlgorithmException ex){throw new IllegalStateException(ex);}
    }
}
