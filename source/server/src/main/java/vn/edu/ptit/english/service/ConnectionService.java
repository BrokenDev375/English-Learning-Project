package vn.edu.ptit.english.service;

import vn.edu.ptit.english.dto.ConnectionViews.Presence;
import vn.edu.ptit.english.security.UserPrincipal;

public interface ConnectionService {
    Presence presence(UserPrincipal actor);
    boolean canSubscribe(long attemptId, UserPrincipal actor);
}
