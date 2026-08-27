package dev.icaro.foodtooltips.global;

public final class GlobalLevelRules {
    private GlobalLevelRules() {
    }

    public static long level(long totalXp, long xpPerLevel) {
        return Math.max(0L, totalXp) / Math.max(1L, xpPerLevel);
    }

    public static long progress(long totalXp, long xpPerLevel) {
        return Math.max(0L, totalXp) % Math.max(1L, xpPerLevel);
    }

    public static long skillReward(int level, long[] rewards) {
        if (level <= 0) {
            return 0L;
        }
        int index = level <= 10 ? 0 : (level <= 25 ? 1 : (level <= 50 ? 2 : (level <= 60 ? 3 : (level <= 100 ? 4 : (level <= 150 ? 5 : 6)))));
        return rewards[index];
    }

    public static long skillReward(int firstInclusive, int lastInclusive, long[] rewards) {
        long total = 0L;
        for (int level = Math.max(1, firstInclusive); level <= lastInclusive; ++level) {
            total = Math.addExact(total, GlobalLevelRules.skillReward(level, rewards));
        }
        return total;
    }
}

