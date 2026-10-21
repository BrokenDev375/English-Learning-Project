package vn.edu.ptit.english.dto;

import java.time.Instant;

public final class ConnectionViews {
    private ConnectionViews() {}
    public record Presence(boolean online, int activeConnectionCount, Instant measuredAt) {}
}
