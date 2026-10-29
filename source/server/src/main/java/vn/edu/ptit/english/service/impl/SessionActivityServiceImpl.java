package vn.edu.ptit.english.service.impl;

import com.fasterxml.jackson.databind.JsonNode;
import java.nio.charset.StandardCharsets;
import java.util.*;
import org.springframework.stereotype.Service;
import org.springframework.http.HttpStatus;
import vn.edu.ptit.english.exception.ApiException;
import vn.edu.ptit.english.security.*;
import vn.edu.ptit.english.service.*;

@Service
public class SessionActivityServiceImpl implements SessionActivityService {
    private final SessionActivityRegistry registry;
    private final AccountGuard guard;
    private final ConnectionService connections;
    private static final Set<String> ACTIVITIES=Set.of("HOME","LEARN","REVIEW","RESULT","CATALOG","PROFILE","REWARDS","LEADERBOARD","AUTHORING","ADMIN_USERS","STATISTICS","OTHER");
    public SessionActivityServiceImpl(SessionActivityRegistry registry,AccountGuard guard,ConnectionService connections) {
        this.registry=registry;this.guard=guard;this.connections=connections;
    }
    public SessionActivityRegistry.State state(String sessionId,UserPrincipal actor) {guard.requireCurrent(actor);return registry.state(sessionId);}
    public SessionActivityRegistry.State report(String sessionId,JsonNode n,UserPrincipal actor) {
        guard.requireCurrent(actor);
        if(n==null || !n.isObject() || n.toString().getBytes(StandardCharsets.UTF_8).length>1024)throw invalid();
        var fields=Set.of("tabId","seq","activity","visibility","interaction","attemptId");
        n.fieldNames().forEachRemaining(k->{if(!fields.contains(k))throw invalid();});
        if(!n.path("tabId").isTextual() || !n.path("tabId").asText().matches("[0-9a-f]{8}(-[0-9a-f]{4}){3}-[0-9a-f]{12}")
            || !n.path("seq").isIntegralNumber() || !n.path("seq").canConvertToLong() || n.path("seq").asLong()<0 || n.path("seq").asLong()>9007199254740991L
            || !n.path("activity").isTextual() || !ACTIVITIES.contains(n.path("activity").asText())
            || !n.path("visibility").isTextual() || !Set.of("VISIBLE","HIDDEN").contains(n.path("visibility").asText()) || !n.path("interaction").isBoolean())throw invalid();
        Long attemptId=null;
        if(n.has("attemptId") && !n.get("attemptId").isNull()) {
            var id=n.get("attemptId");
            if(!id.isIntegralNumber() || !id.canConvertToLong() || id.asLong()<1)throw invalid();
            if(!connections.canSubscribe(id.asLong(),actor))throw new ApiException(HttpStatus.NOT_FOUND,"RESOURCE_NOT_FOUND","Không tìm thấy lượt làm.");
            attemptId=id.asLong();
        }
        return registry.report(sessionId,n.get("tabId").asText(),n.get("seq").asLong(),
            new SessionActivityRegistry.Context(n.get("activity").asText(),attemptId,n.get("visibility").asText()),n.get("interaction").asBoolean());
    }
    private static ApiException invalid(){return new ApiException(HttpStatus.BAD_REQUEST,"INVALID_ACTIVITY","Dữ liệu hoạt động không hợp lệ.");}
}
