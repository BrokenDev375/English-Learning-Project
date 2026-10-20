package vn.edu.ptit.english.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Supplier;
import vn.edu.ptit.english.security.UserPrincipal;

public interface RequestReceiptService {
    record Result(int status, JsonNode body, boolean replayed) {}
    /** Status and body chosen by the action while holding the actor lock. */
    record Reply(int status, Object body) {}
    Result execute(UserPrincipal actor, String requestId, String operation, Object payload, int status, Supplier<?> action);
    Result execute(UserPrincipal actor, String requestId, String operation, Object payload, Supplier<Reply> action);
}
