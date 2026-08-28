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

    public static double executionerMultiplier(int rank, int maxRank) {
        return lerp(1.08, 1.25, rank, maxRank);
    }

    public static double berserkerMultiplier(int rank, int maxRank) {
        return lerp(1.10, 1.30, rank, maxRank);
    }

    public static double armorPiercerMultiplier(int rank, int maxRank) {
        return lerp(1.08, 1.25, rank, maxRank);
    }

    public static double criticalMasteryMultiplier(int rank, int maxRank) {
        return lerp(1.55, 2.20, rank, maxRank);
    }

    // ---- Sustain ----------------------------------------------------------------

    public static double vampirismHeal(int rank, int maxRank) {
        return lerp(0.5, 4.0, rank, maxRank);
    }

    public static double bloodLustBonus(int rank, int maxRank) {
        return lerp(0.06, 0.20, rank, maxRank);
    }

    public static int bloodLustThreshold(int rank, int maxRank) {
        return lerpInt(10, 4, rank, maxRank, 4);
    }

    public static double treasureHunterBonus(int rank, int maxRank) {
        return lerp(0.10, 0.40, rank, maxRank);
    }

    public static double undyingWillReduction(int rank, int maxRank) {
        return lerp(0.02, 0.15, rank, maxRank);
    }

    /** Flat max-Vitality bonus granted by Undying Will alongside its damage reduction. */
    public static double undyingWillVitalityBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(5.0, 40.0, rank, maxRank);
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
        return Math.round(lerp(30.0, 3.0, rank, maxRank) * 1000.0);
    }

    /** Flat Swing Range bonus granted by Sword Throw alongside its damage/cooldown. */
    public static double swordThrowSwingRangeBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(0.2, 2.0, rank, maxRank);
    }

    public static int huntersInstinctDurationTicks(int rank, int maxRank) {
        return lerpInt(60, 200, rank, maxRank, 60);
    }

    public static double cleaveSplashFraction(int rank, int maxRank) {
        return lerp(0.10, 0.45, rank, maxRank);
    }

    public static int cleaveMaxTargets(int rank, int maxRank) {
        return lerpInt(2, 8, rank, maxRank, 2);
    }

    /** Flat Ferocity bonus granted by Cleave alongside its splash damage — thematically apt since Ferocity is extra-hit chance. */
    public static double cleaveFerocityBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(5.0, 40.0, rank, maxRank);
    }

    public static int relentlessInterval(int rank, int maxRank) {
        return lerpInt(6, 2, rank, maxRank, 2);
    }

    // ---- Synergy / capstone ------------------------------------------------------

    public static double masteryBloodLustBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(0.03, 0.15, rank, maxRank);
    }

    public static long masteryCooldownReductionMillis(int rank, int maxRank) {
        return rank <= 0 ? 0L : Math.round(lerp(3.0, 10.0, rank, maxRank) * 1000.0);
    }

    /** Flat Strength bonus granted by Combat Mastery alongside its other bonuses. */
    public static double combatMasteryStrengthBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(3.0, 30.0, rank, maxRank);
    }

    public static double apexFinalDamageBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(0.05, 0.20, rank, maxRank);
    }

    public static long apexCooldownFloorMillis(int rank, int maxRank) {
        return rank <= 0 ? Long.MAX_VALUE : Math.round(lerp(8.0, 2.0, rank, maxRank) * 1000.0);
    }

    /** Percentage Ability Damage bonus granted by Apex Warrior, the capstone's broad late-game payoff. */
    public static double apexAbilityDamageBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(3.0, 25.0, rank, maxRank);
    }

    /** Combines the sword-throw base cooldown with the Mastery/Apex Warrior modifiers (each with its own max rank). */
    public static long swordThrowCooldownMillis(int swordThrowRank, int swordThrowMaxRank,
                                                 int masteryRank, int masteryMaxRank,
                                                 int apexRank, int apexMaxRank) {
        long cooldown = swordThrowBaseCooldownMillis(swordThrowRank, swordThrowMaxRank)
                - masteryCooldownReductionMillis(masteryRank, masteryMaxRank);
        if (apexRank > 0) {
            cooldown = Math.min(cooldown, apexCooldownFloorMillis(apexRank, apexMaxRank));
        }
        return Math.max(MIN_COOLDOWN_MILLIS, cooldown);
    }

    public static double arcaneSlashBaseDamage(int rank, int maxRank) {
        return lerp(4.0, 20.0, rank, maxRank);
    }

    public static double arcaneSlashDamage(int rank, int maxRank, double intelligence, double abilityDamage) {
        return (arcaneSlashBaseDamage(rank, maxRank) + intelligence * 0.35) * (1.0 + abilityDamage / 100.0);
    }

    public static double arcaneSlashManaCost(int rank, int maxRank) {
        return lerp(25.0, 8.0, rank, maxRank);
    }

    public static long arcaneSlashCooldownMillis(int rank, int maxRank) {
        return Math.round(lerp(14.0, 4.0, rank, maxRank) * 1000.0);
    }

    /** Flat Intelligence bonus granted by Arcane Slash alongside its own damage/cost/cooldown. */
    public static double arcaneSlashIntelligenceBonus(int rank, int maxRank) {
        return rank <= 0 ? 0.0 : lerp(5.0, 40.0, rank, maxRank);
    }

    public static double vitalTouchBaseHeal(int rank, int maxRank) {
        return lerp(2.0, 10.0, rank, maxRank);
    }

    /** Base heal (self-portion, unaffected by Mending: that stat only boosts healing applied to others). */
    public static double vitalTouchHeal(int rank, int maxRank, double intelligence, double abilityDamage) {
        return (vitalTouchBaseHeal(rank, maxRank) + intelligence * 0.2) * (1.0 + abilityDamage / 100.0);
    }

    /** Applies the Mending stat to a heal amount landing on someone other than the caster. */
    public static double applyMending(double baseHeal, double mending) {
        return baseHeal * (1.0 + mending / 100.0);
    }

    public static double vitalTouchVitalityCost(int rank, int maxRank) {
        return lerp(35.0, 15.0, rank, maxRank);
    }

    public static long vitalTouchCooldownMillis(int rank, int maxRank) {
        return Math.round(lerp(20.0, 6.0, rank, maxRank) * 1000.0);
    }
}
