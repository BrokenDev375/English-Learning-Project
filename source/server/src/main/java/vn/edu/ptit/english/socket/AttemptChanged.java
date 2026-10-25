package vn.edu.ptit.english.socket;

/** Invalidation only. The committed HTTP snapshot remains authoritative. */
public record AttemptChanged(long userId, long attemptId, long revision) {}
