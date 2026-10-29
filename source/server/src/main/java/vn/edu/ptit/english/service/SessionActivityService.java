package vn.edu.ptit.english.service;
import com.fasterxml.jackson.databind.JsonNode;
import vn.edu.ptit.english.security.*;
public interface SessionActivityService {
    SessionActivityRegistry.State state(String sessionId,UserPrincipal actor);
    SessionActivityRegistry.State report(String sessionId,JsonNode input,UserPrincipal actor);
}
