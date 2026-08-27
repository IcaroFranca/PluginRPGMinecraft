package dev.icaro.foodtooltips.skills;

/**
 * Pure numeric formulas backing the combat ability tree: the Blood Points
 * cost curve (scaled by both rank and tree tier) and every per-rank ability
 * scaling. Deliberately free of Bukkit types so this class (and its
 * formulas) can be exercised by a plain {@code javac}/{@code java} run,
 * independent of the Paper API.
 *
 * <p>Every ability now spans ranks 1..10. Most formulas are a straight
 * linear interpolation between a rank-1 value and a rank-10 value via
 * {@link #lerp(double, double, int, int)} — that's the "at rank 1 you get
 * X, at max rank you get Y" shape the tree is designed around.
 */
public final class CombatTreeMath {
    /** Absolute safety floor applied after every cooldown-reducing effect stacks. */
    public static final long MIN_COOLDOWN_MILLIS = 1000L;
    public static final int MAX_RANK = 10;

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

    public static double ruthlessStrikesCritBonus(int rank) {
        return lerp(1.0, 10.0, rank, MAX_RANK);
    }

    public static double executionerMultiplier(int rank) {
        return lerp(1.08, 1.25, rank, MAX_RANK);
    }

    public static double berserkerMultiplier(int rank) {
        return lerp(1.10, 1.30, rank, MAX_RANK);
    }

    public static double armorPiercerMultiplier(int rank) {
        return lerp(1.08, 1.25, rank, MAX_RANK);
    }

    public static double criticalMasteryMultiplier(int rank) {
        return lerp(1.55, 2.20, rank, MAX_RANK);
    }

    // ---- Sustain ----------------------------------------------------------------

    public static double vampirismHeal(int rank) {
        return lerp(0.5, 4.0, rank, MAX_RANK);
    }

    public static double bloodLustBonus(int rank) {
        return lerp(0.06, 0.20, rank, MAX_RANK);
    }

    public static int bloodLustThreshold(int rank) {
        return lerpInt(10, 4, rank, MAX_RANK, 4);
    }

    public static double treasureHunterBonus(int rank) {
        return lerp(0.10, 0.40, rank, MAX_RANK);
    }

    public static double undyingWillReduction(int rank) {
        return lerp(0.02, 0.15, rank, MAX_RANK);
    }

    public static double soulHarvestHeal(int rank) {
        return lerp(0.5, 4.0, rank, MAX_RANK);
    }

    public static long secondWindCooldownMillis(int rank) {
        return Math.round(lerp(240.0, 90.0, rank, MAX_RANK) * 1000.0);
    }

    public static double secondWindHealFraction(int rank) {
        return lerp(0.25, 0.50, rank, MAX_RANK);
    }

    // ---- Utility / precision --------------------------------------------------

    /** Blocks of nearby loose drops swept into the player's inventory on a Telekinesis kill. */
    public static double telekinesisMagnetRadius(int rank) {
        return lerp(1.0, 4.0, rank, MAX_RANK);
    }

    public static double swordThrowDamageFraction(int rank) {
        return lerp(0.10, 0.50, rank, MAX_RANK);
    }

    public static long swordThrowBaseCooldownMillis(int rank) {
        return Math.round(lerp(30.0, 3.0, rank, MAX_RANK) * 1000.0);
    }

    public static int huntersInstinctDurationTicks(int rank) {
        return lerpInt(60, 200, rank, MAX_RANK, 60);
    }

    public static double cleaveSplashFraction(int rank) {
        return lerp(0.10, 0.45, rank, MAX_RANK);
    }

    public static int cleaveMaxTargets(int rank) {
        return lerpInt(2, 8, rank, MAX_RANK, 2);
    }

    public static int relentlessInterval(int rank) {
        return lerpInt(6, 2, rank, MAX_RANK, 2);
    }

    // ---- Synergy / capstone ------------------------------------------------------

    public static double masteryBloodLustBonus(int rank) {
        return rank <= 0 ? 0.0 : lerp(0.03, 0.15, rank, MAX_RANK);
    }

    public static long masteryCooldownReductionMillis(int rank) {
        return rank <= 0 ? 0L : Math.round(lerp(3.0, 10.0, rank, MAX_RANK) * 1000.0);
    }

    public static double apexFinalDamageBonus(int rank) {
        return rank <= 0 ? 0.0 : lerp(0.05, 0.20, rank, MAX_RANK);
    }

    public static long apexCooldownFloorMillis(int rank) {
        return rank <= 0 ? Long.MAX_VALUE : Math.round(lerp(8.0, 2.0, rank, MAX_RANK) * 1000.0);
    }

    /** Combines the sword-throw base cooldown with the Mastery/Apex Warrior modifiers. */
    public static long swordThrowCooldownMillis(int swordThrowRank, int masteryRank, int apexRank) {
        long cooldown = swordThrowBaseCooldownMillis(swordThrowRank) - masteryCooldownReductionMillis(masteryRank);
        if (apexRank > 0) {
            cooldown = Math.min(cooldown, apexCooldownFloorMillis(apexRank));
        }
        return Math.max(MIN_COOLDOWN_MILLIS, cooldown);
    }

    public static double arcaneSlashBaseDamage(int rank) {
        return lerp(4.0, 20.0, rank, MAX_RANK);
    }

    public static double arcaneSlashDamage(int rank, double intelligence, double abilityDamage) {
        return (arcaneSlashBaseDamage(rank) + intelligence * 0.35) * (1.0 + abilityDamage / 100.0);
    }

    public static double arcaneSlashManaCost(int rank) {
        return lerp(25.0, 8.0, rank, MAX_RANK);
    }

    public static long arcaneSlashCooldownMillis(int rank) {
        return Math.round(lerp(14.0, 4.0, rank, MAX_RANK) * 1000.0);
    }

    public static double vitalTouchBaseHeal(int rank) {
        return lerp(2.0, 10.0, rank, MAX_RANK);
    }

    /** Base heal (self-portion, unaffected by Mending: that stat only boosts healing applied to others). */
    public static double vitalTouchHeal(int rank, double intelligence, double abilityDamage) {
        return (vitalTouchBaseHeal(rank) + intelligence * 0.2) * (1.0 + abilityDamage / 100.0);
    }

    /** Applies the Mending stat to a heal amount landing on someone other than the caster. */
    public static double applyMending(double baseHeal, double mending) {
        return baseHeal * (1.0 + mending / 100.0);
    }

    public static double vitalTouchVitalityCost(int rank) {
        return lerp(35.0, 15.0, rank, MAX_RANK);
    }

    public static long vitalTouchCooldownMillis(int rank) {
        return Math.round(lerp(20.0, 6.0, rank, MAX_RANK) * 1000.0);
    }
}
