package dev.icaro.foodtooltips.skills;

/**
 * Pure numeric formulas backing the combat ability tree: the Blood Points
 * cost curve (scaled by both rank and tree tier) and every per-rank ability
 * scaling. Deliberately free of Bukkit types so this class (and its
 * formulas) can be exercised by a plain {@code javac}/{@code java} run,
 * independent of the Paper API.
 *
 * <p>Every ability spans ranks 1..maxRank, where {@code maxRank} is that
 * ability's own value from {@link CombatTreeNode} (at least 10, but some
 * abilities go further — 12, 15, 20, up to the capstone's 25 — to be a
 * longer grind without changing how strong the ability ends up at max rank).
 * Every formula below takes {@code maxRank} explicitly rather than
 * assuming a single fixed cap, and is a straight linear interpolation
 * between a rank-1 value and a rank-{@code maxRank} value via
 * {@link #lerp(double, double, int, int)}.
 */
public final class CombatTreeMath {
    /** Absolute safety floor applied after every cooldown-reducing effect stacks. */
    public static final long MIN_COOLDOWN_MILLIS = 1000L;
    /** Absolute safety floor for any ability's Mana cost, however low rank/scaling pushes it. */
    public static final int MIN_MANA_COST = 5;

    private CombatTreeMath() {
    }

    // ---- Shared interpolation --------------------------------------------

    /** Value at the given rank, linearly interpolated from {@code start} (rank 1) to {@code end} (rank maxRank). */
    public static double lerp(double start, double end, int rank, int maxRank) {
        if (maxRank <= 1) {
            return end;
        }
        double t = (double) (Math.max(1, Math.min(maxRank, rank)) - 1) / (double) (maxRank - 1);
        return start + (end - start) * t;
    }

    /** Same as {@link #lerp}, rounded to the nearest int and clamped to at least {@code min}. */
    public static int lerpInt(double start, double end, int rank, int maxRank, int min) {
        return Math.max(min, (int) Math.round(lerp(start, end, rank, maxRank)));
    }

    // ---- Blood Points cost curve --------------------------------------------

    /** Base Blood Points cost for rank 1 of a node at the given tree tier (tier 1 = root). */
    public static long tierBaseCost(long baseCost, long costPerTier, int tier) {
        return baseCost + costPerTier * (long) (Math.max(1, tier) - 1);
    }

    /** Extra Blood Points cost per additional rank for a node at the given tree tier. */
    public static long tierCostPerRank(long costPerRank, long costPerRankPerTier, int tier) {
        return costPerRank + costPerRankPerTier * (long) (Math.max(1, tier) - 1);
    }

    public static long rankCost(long baseCost, long costPerRank, int rank) {
        if (rank < 1) {
            throw new IllegalArgumentException("rank must be >= 1, got " + rank);
        }
        return baseCost + costPerRank * (long) (rank - 1);
    }

    public static long totalCost(long baseCost, long costPerRank, int rank) {
        long total = 0L;
        for (int r = 1; r <= rank; r++) {
            total += rankCost(baseCost, costPerRank, r);
        }
        return total;
    }

    public static boolean canAfford(long balance, long cost) {
        return cost >= 0L && balance >= cost;
    }

    // ---- Ferocity ------------------------------------------------------------

    /**
     * @param ferocity   the player's ferocity stat (>= 0)
     * @param roll0to100 a uniform random draw in [0, 100)
     * @return how many extra hits should land alongside the original hit
     */
    public static int extraHits(double ferocity, double roll0to100) {
        double clamped = Math.max(0.0, ferocity);
        int guaranteed = (int) (clamped / 100.0);
        double remainder = clamped % 100.0;
        return guaranteed + (roll0to100 < remainder ? 1 : 0);
    }

    // ---- Fury -----------------------------------------------------------------

    public static double ruthlessStrikesCritBonus(int rank, int maxRank) {
        return lerp(1.0, 10.0, rank, maxRank);
    }

    public static double berserkerMultiplier(int rank, int maxRank) {
        return lerp(1.10, 1.30, rank, maxRank);
    }

    public static double criticalMasteryMultiplier(int rank, int maxRank) {
        return lerp(1.55, 2.20, rank, maxRank);
    }

    // ---- Sustain ----------------------------------------------------------------

    public static double bloodLustBonus(int rank, int maxRank) {
        return lerp(0.06, 0.20, rank, maxRank);
    }

    public static int bloodLustThreshold(int rank, int maxRank) {
        return lerpInt(10, 4, rank, maxRank, 4);
    }

    public static double soulHarvestHeal(int rank, int maxRank) {
        return lerp(0.5, 4.0, rank, maxRank);
    }

    /** Percentage Health Regen bonus granted by Soul Harvest alongside its per-kill heal. */
    public static double soulHarvestHealthRegenBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(5.0, 40.0, rank, maxRank);
    }

    public static long secondWindCooldownMillis(int rank, int maxRank) {
        return Math.round(lerp(240.0, 90.0, rank, maxRank) * 1000.0);
    }

    public static double secondWindHealFraction(int rank, int maxRank) {
        return lerp(0.25, 0.50, rank, maxRank);
    }

    /** Percentage Mending bonus granted by Second Wind alongside its emergency save. */
    public static double secondWindMendingBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(5.0, 40.0, rank, maxRank);
    }

    // ---- Utility / precision --------------------------------------------------

    public static double swordThrowDamageFraction(int rank, int maxRank) {
        return lerp(0.10, 0.50, rank, maxRank);
    }

    public static long swordThrowBaseCooldownMillis(int rank, int maxRank) {
        return Math.round(lerp(22.0, 2.0, rank, maxRank) * 1000.0);
    }

    /**
     * Mana cost of one throw, scaling from 35 (rank 1) down to 15 (max rank) - mastering
     * the ability makes it cheaper to spam, on top of the cooldown already dropping.
     * Paired with the cooldown cut above: throwing more often is now gated by Mana
     * (a real resource with a regen rate) instead of purely by a long cooldown timer.
     */
    public static int swordThrowManaCost(int rank, int maxRank) {
        return lerpInt(35.0, 15.0, rank, maxRank, MIN_MANA_COST);
    }

    /** Flat Swing Range bonus granted by Sword Throw alongside its damage/cooldown. */
    public static double swordThrowSwingRangeBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(0.2, 2.0, rank, maxRank);
    }
}
