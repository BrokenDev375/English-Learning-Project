package vn.edu.ptit.english.service;

import com.fasterxml.jackson.databind.JsonNode;
import java.util.function.Supplier;
import vn.edu.ptit.english.security.UserPrincipal;

public interface RequestReceiptService {
    record Result(int status, JsonNode body, boolean replayed) {}
    Result execute(UserPrincipal actor, String requestId, String operation, Object payload, int status, Supplier<?> action);
}
