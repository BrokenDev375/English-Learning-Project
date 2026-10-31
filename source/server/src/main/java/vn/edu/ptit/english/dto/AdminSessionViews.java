package vn.edu.ptit.english.dto;

import java.time.Instant;
import java.util.*;
import vn.edu.ptit.english.enums.*;

public final class AdminSessionViews {
    private AdminSessionViews() {}
    public record User(long userId, String fullName, String email, Role role, AccountStatus accountStatus) {}
    public record Context(String activity, Long attemptId, String lessonTitle, Integer roundNumber) {}
    public record Tab(String tabRef, boolean connected, String visibility, Instant lastInteractionAt,
        Instant lastSeenAt, Context context) {}
    public record Session(String sessionRef, Instant loginAt, Instant lastInteractionAt, Instant idleExpiresAt,
        String interactionState, boolean connected, long openConnections, int tabCount) {}
    public record Account(User user, Context primaryContext, String visibility, String interactionState,
        Instant lastInteractionAt, Instant idleExpiresAt, int validSessionCount, int tabCount,
        long openConnections, List<Session> sessions) {}
    public record Summary(long connectedAccounts, long validSessions, long tabs, long openConnections,
        Map<String,Long> activities, long idleAccounts) {}
    public record Page(Instant serverTime, String basis, Summary summary, List<Account> items,
        long totalElements, int page, int size) {}
    public record Detail(Instant serverTime, String basis, User user, Session session, List<Tab> tabs) {}
}
