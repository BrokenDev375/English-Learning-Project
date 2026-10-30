package vn.edu.ptit.english.socket;

/** Invalidation only. Protected snapshots are fetched again after commit. */
public record ContestChanged(long contestId,Long userId) {
    public ContestChanged(long contestId){this(contestId,null);}
}
