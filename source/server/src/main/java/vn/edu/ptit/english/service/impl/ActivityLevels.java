package vn.edu.ptit.english.service.impl;

/** Activity levels use earned XP; calculating a level never awards or rewrites XP. */
public final class ActivityLevels {
    private ActivityLevels() {}
    public static final long MAX_XP = 9007199254740991L;
    public record Level(long value, long floorXp, long nextLevelAtXp) {}

    public static Level fixed(long totalXp, long step) {
        validate(totalXp);
        if (step <= 0) throw new IllegalArgumentException("Level step must be positive");
        long floor = totalXp / step * step;
        return new Level(totalXp / step + 1, floor, Math.min(MAX_XP, floor + step));
    }

    public static Level progressive(long totalXp) {
        validate(totalXp);
        long low = 1, high = 2;
        while (threshold(high) <= totalXp) high *= 2;
        while (low + 1 < high) {
            long middle = low + (high - low) / 2;
            if (threshold(middle) <= totalXp) low = middle;
            else high = middle;
        }
        return new Level(low, threshold(low), Math.min(MAX_XP, threshold(low + 1)));
    }

    // Cost from level L to L+1 is 10*L. Threshold at level L is 5*L*(L-1).
    private static long threshold(long level) { return 5 * level * (level - 1); }
    private static void validate(long totalXp) {
        if (totalXp < 0 || totalXp > MAX_XP) throw new IllegalArgumentException("XP outside supported range");
    }
}
