package dev.icaro.foodtooltips.skills;

/**
 * Pure numeric formulas backing the combat ability tree: Valor cost curve and
 * every per-rank ability scaling. Deliberately free of Bukkit types so this
 * class (and its formulas) can be exercised by a plain JUnit-free {@code
 * javac}/{@code java} run, independent of the Paper API.
 */
public final class CombatTreeMath {
    /** Absolute floor applied after every cooldown-reducing effect stacks. */
    public static final long MIN_COOLDOWN_MILLIS = 4000L;

    private CombatTreeMath() {
    }

    // ---- Valor cost curve ------------------------------------------------

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

    // ---- Ferocity ----------------------------------------------------------

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

    // ---- Existing abilities, now rank-scaled --------------------------------

    public static double executionerMultiplier(int rank) {
        return 1.0 + (0.10 + 0.02 * (rank - 1));
    }

    public static double berserkerMultiplier(int rank) {
        return 1.0 + (0.12 + 0.02 * (rank - 1));
    }

    public static double armorPiercerMultiplier(int rank) {
        return 1.0 + (0.10 + 0.02 * (rank - 1));
    }

    public static double criticalMasteryMultiplier(int rank) {
        return 1.5 + (0.15 + 0.05 * (rank - 1));
    }

    public static double vampirismHeal(int rank) {
        return 1.0 + 0.25 * (rank - 1);
    }

    public static double bloodLustBonus(int rank) {
        return 0.08 + 0.02 * rank;
    }

    public static int bloodLustThreshold(int rank) {
        return Math.max(4, 10 - (rank - 1));
    }

    public static double undyingWillReduction(int rank) {
        return 0.02 + 0.01 * (rank - 1);
    }

    public static double soulHarvestHeal(int rank) {
        return 1.0 + 0.5 * (rank - 1);
    }

    public static long secondWindCooldownMillis(int rank) {
        return (long) (180 - 15 * (rank - 1)) * 1000L;
    }

    public static double secondWindHealFraction(int rank) {
        return 0.30 + 0.025 * (rank - 1);
    }

    public static long swordThrowBaseCooldownMillis(int rank) {
        return (long) (30 - 4 * (rank - 1)) * 1000L;
    }

    public static double swordThrowDamageFraction(int rank) {
        return 0.50 + 0.05 * (rank - 1);
    }

    public static int huntersInstinctDurationTicks(int rank) {
        return 100 + 20 * (rank - 1);
    }

    public static double cleaveSplashFraction(int rank) {
        return 0.15 + 0.05 * rank;
    }

    public static int cleaveMaxTargets(int rank) {
        return 2 + rank;
    }

    public static int relentlessInterval(int rank) {
        return Math.max(2, 4 - (rank - 1) / 2);
    }

    public static double treasureHunterBonus(int rank) {
        return 0.15 + 0.05 * rank;
    }

    // ---- New passives --------------------------------------------------------

    public static double ruthlessStrikesCritBonus(int rank) {
        return 1.0 * rank;
    }

    // ---- Synergy / capstone nodes --------------------------------------------

    public static double masteryBloodLustBonus(int rank) {
        return rank <= 0 ? 0.0 : 0.05 + 0.025 * (rank - 1);
    }

    public static long masteryCooldownReductionMillis(int rank) {
        return rank <= 0 ? 0L : (long) (5 + 2 * (rank - 1)) * 1000L;
    }

    public static double apexFinalDamageBonus(int rank) {
        return rank <= 0 ? 0.0 : 0.08 + 0.02 * (rank - 1);
    }

    public static long apexCooldownFloorMillis(int rank) {
        return rank <= 0 ? Long.MAX_VALUE : Math.max(6, 10 - 2 * (rank - 1)) * 1000L;
    }

    /** Combines the sword-throw base cooldown with the Mastery/Apex Warrior modifiers. */
    public static long swordThrowCooldownMillis(int swordThrowRank, int masteryRank, int apexRank) {
        long cooldown = swordThrowBaseCooldownMillis(swordThrowRank) - masteryCooldownReductionMillis(masteryRank);
        if (apexRank > 0) {
            cooldown = Math.min(cooldown, apexCooldownFloorMillis(apexRank));
        }
        return Math.max(MIN_COOLDOWN_MILLIS, cooldown);
    }

    // ---- New actives -----------------------------------------------------------

    public static double arcaneSlashDamage(int rank, double intelligence, double abilityDamage) {
        return (6.0 + intelligence * 0.35) * (1.0 + abilityDamage / 100.0) * (1.0 + 0.15 * (rank - 1));
    }

    public static double arcaneSlashManaCost(int rank) {
        return Math.max(10.0, 20.0 - 2.0 * (rank - 1));
    }

    public static long arcaneSlashCooldownMillis(int rank) {
        return Math.max(6, 12 - (rank - 1)) * 1000L;
    }

    /** Base heal (self-portion, unaffected by Mending: that stat only boosts healing applied to others). */
    public static double vitalTouchHeal(int rank, double intelligence, double abilityDamage) {
        return (4.0 + intelligence * 0.2) * (1.0 + abilityDamage / 100.0) * (1.0 + 0.2 * (rank - 1));
    }

    /** Applies the Mending stat to a heal amount landing on someone other than the caster. */
    public static double applyMending(double baseHeal, double mending) {
        return baseHeal * (1.0 + mending / 100.0);
    }

    public static double vitalTouchVitalityCost(int rank) {
        return Math.max(15.0, 30.0 - 2.0 * (rank - 1));
    }

    public static long vitalTouchCooldownMillis(int rank) {
        return Math.max(8, 16 - (rank - 1)) * 1000L;
    }
}
