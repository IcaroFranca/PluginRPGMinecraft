package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Runtime engine for the combat ability tree: rank storage/progression
 * (spending Blood Points to unlock and upgrade nodes) and every rank-scaled
 * gameplay effect. Also owns the 6 Combat Backpack capacity nodes (see
 * {@link CombatAbility#BACKPACK_1} and friends) — {@link BackpackService}
 * reads their unlock state instead of the Combat skill's level directly.
 *
 * <p>Of the stats shown on the "Combat Stats" screen, Crit Chance (Ruthless
 * Strikes), Crit Damage (Critical Mastery), Swing Range (Sword Throw),
 * Health Regen and Mending (Soul Harvest / Second Wind) have a tree-driven
 * bonus source here — each rides along an existing, thematically-fitting
 * ability rather than needing a dedicated node of its own.
 */
public final class CombatAbilityService {
    public enum PurchaseResult {
        SUCCESS, ALREADY_MAX, PREREQUISITE_MISSING, LEVEL_TOO_LOW, INSUFFICIENT_VALOR
    }

    private final Plugin plugin;
    private final CombatSkillService combat;
    private final PlayerStatsService stats;
    private final CombatValorService valor;
    private final long baseCost;
    private final long costPerTier;
    private final long costPerRank;
    private final long costPerRankPerTier;
    private final double baseCritMultiplier;
    private final List<Integer> tierLevelRequirements;

    private final Map<UUID, Integer> bloodStreak = new HashMap<>();
    private final Set<UUID> abilityDamageInFlight = new HashSet<>();

    public CombatAbilityService(Plugin p, CombatSkillService combat, PlayerStatsService stats, CombatValorService valor) {
        this.plugin = p;
        this.combat = combat;
        this.stats = stats;
        this.valor = valor;
        this.baseCost = Math.max(1L, p.getConfig().getLong("combat-tree.base-unlock-cost", 20L));
        this.costPerTier = Math.max(0L, p.getConfig().getLong("combat-tree.cost-per-tier", 15L));
        this.costPerRank = Math.max(0L, p.getConfig().getLong("combat-tree.cost-per-rank", 8L));
        this.costPerRankPerTier = Math.max(0L, p.getConfig().getLong("combat-tree.cost-per-rank-per-tier", 5L));
        this.baseCritMultiplier = p.getConfig().getDouble("combat.critical-damage-multiplier", 1.5);
        List<Integer> requirements = p.getConfig().getIntegerList("combat-tree.tier-level-requirements");
        this.tierLevelRequirements = requirements.isEmpty() ? List.of(0, 15, 35, 60, 90, 130) : requirements;
    }

    /**
     * Minimum Combat level required to unlock a node at the given tree tier (tier 1 =
     * root). Reads {@code combat-tree.tier-level-requirements} (index 0 = tier 1);
     * tiers past the configured list extrapolate using the last step between two
     * configured values, clamped to the Combat skill's max level.
     */
    public int levelRequirement(int tier) {
        List<Integer> req = this.tierLevelRequirements;
        int index = Math.max(0, tier - 1);
        if (index < req.size()) {
            return req.get(index);
        }
        int lastIndex = req.size() - 1;
        int last = req.get(lastIndex);
        int step = lastIndex > 0 ? Math.max(0, last - req.get(lastIndex - 1)) : 0;
        int extra = index - lastIndex;
        return Math.min(this.combat.maxLevel(), last + step * extra);
    }

    /** Effective critical-damage multiplier (base config value, or Critical Mastery's if unlocked), as a raw multiplier (1.5 = +50%). */
    public double criticalDamageMultiplier(Player p) {
        return this.criticalMultiplier(p, this.baseCritMultiplier);
    }

    // ---- Rank / unlock state -------------------------------------------------

    public int rank(Player p, CombatAbility a) {
        return p.getPersistentDataContainer().getOrDefault(this.rankKey(a), PersistentDataType.INTEGER, 0);
    }

    public int maxRank(CombatAbility a) {
        return CombatTreeNode.of(a).maxRank();
    }

    public boolean unlocked(Player p, CombatAbility a) {
        return this.rank(p, a) > 0;
    }

    public boolean enabled(Player p, CombatAbility a) {
        return this.unlocked(p, a) && p.getPersistentDataContainer().getOrDefault(this.key(a), PersistentDataType.BYTE, (byte) 1) == 1;
    }

    public boolean toggle(Player p, CombatAbility a) {
        if (!this.unlocked(p, a)) {
            return false;
        }
        boolean next = !this.enabled(p, a);
        p.getPersistentDataContainer().set(this.key(a), PersistentDataType.BYTE, (byte) (next ? 1 : 0));
        return next;
    }

    public boolean prerequisitesMet(Player p, CombatAbility a) {
        for (CombatAbility prereq : CombatTreeNode.of(a).prerequisites()) {
            if (!this.unlocked(p, prereq)) {
                return false;
            }
        }
        return true;
    }

    /** Blood Points cost of the *next* rank, or -1 if the node is already maxed. Scales with both rank and tree tier. */
    public long nextRankCost(Player p, CombatAbility a) {
        int current = this.rank(p, a);
        int max = this.maxRank(a);
        if (current >= max) {
            return -1L;
        }
        int tier = CombatTreeNode.of(a).tier();
        long tierBase = CombatTreeMath.tierBaseCost(this.baseCost, this.costPerTier, tier);
        long tierPerRank = CombatTreeMath.tierCostPerRank(this.costPerRank, this.costPerRankPerTier, tier);
        return CombatTreeMath.rankCost(tierBase, tierPerRank, current + 1);
    }

    public PurchaseResult purchaseRank(Player p, CombatAbility a) {
        int current = this.rank(p, a);
        int max = this.maxRank(a);
        if (current >= max) {
            return PurchaseResult.ALREADY_MAX;
        }
        if (!this.prerequisitesMet(p, a)) {
            return PurchaseResult.PREREQUISITE_MISSING;
        }
        if (this.combat.progress(p).level() < this.levelRequirement(CombatTreeNode.of(a).tier())) {
            return PurchaseResult.LEVEL_TOO_LOW;
        }
        long cost = this.nextRankCost(p, a);
        if (!this.valor.withdraw(p, cost)) {
            return PurchaseResult.INSUFFICIENT_VALOR;
        }
        p.getPersistentDataContainer().set(this.rankKey(a), PersistentDataType.INTEGER, current + 1);
        return PurchaseResult.SUCCESS;
    }

    /**
     * Resets every combat tree node to rank 0 and refunds the full Blood Points cost
     * paid for every rank purchased (same tier-scaled formula {@link #nextRankCost}
     * uses), so a rebalance (or just changing your mind) never leaves points stranded
     * in an old build. Also clears each ability's enabled/disabled toggle back to its
     * default. Returns the amount refunded (0 if nothing was unlocked).
     */
    public long resetTree(Player p) {
        long refund = 0L;
        for (CombatAbility a : CombatAbility.values()) {
            int rank = this.rank(p, a);
            if (rank <= 0) {
                continue;
            }
            int tier = CombatTreeNode.of(a).tier();
            long tierBase = CombatTreeMath.tierBaseCost(this.baseCost, this.costPerTier, tier);
            long tierPerRank = CombatTreeMath.tierCostPerRank(this.costPerRank, this.costPerRankPerTier, tier);
            refund += CombatTreeMath.totalCost(tierBase, tierPerRank, rank);
            p.getPersistentDataContainer().set(this.rankKey(a), PersistentDataType.INTEGER, 0);
            p.getPersistentDataContainer().remove(this.key(a));
        }
        if (refund > 0L) {
            this.valor.deposit(p, refund);
        }
        return refund;
    }

    // ---- Melee combat effects --------------------------------------------------

    public double outgoingMultiplier(Player p) {
        double multiplier = 1.0;
        if (this.enabled(p, CombatAbility.BLOOD_LUST) && this.bloodStreak(p) >= CombatTreeMath.bloodLustThreshold(this.rank(p, CombatAbility.BLOOD_LUST), this.maxRank(CombatAbility.BLOOD_LUST))) {
            double bonus = CombatTreeMath.bloodLustBonus(this.rank(p, CombatAbility.BLOOD_LUST), this.maxRank(CombatAbility.BLOOD_LUST));
            multiplier *= 1.0 + bonus;
        }
        // Berserker's threshold: below 10% HP (was 30%) - a true last-stand bonus, not a
        // "spend most of the fight at reduced HP" one.
        if (this.enabled(p, CombatAbility.BERSERKER) && p.getHealth() <= maxHealth(p) * 0.1) {
            multiplier *= CombatTreeMath.berserkerMultiplier(this.rank(p, CombatAbility.BERSERKER), this.maxRank(CombatAbility.BERSERKER));
        }
        return multiplier;
    }

    /** Flat crit-chance percentage points contributed by Ruthless Strikes. */
    public double critChanceBonus(Player p) {
        return this.enabled(p, CombatAbility.RUTHLESS_STRIKES) ? CombatTreeMath.ruthlessStrikesCritBonus(this.rank(p, CombatAbility.RUTHLESS_STRIKES), this.maxRank(CombatAbility.RUTHLESS_STRIKES)) : 0.0;
    }

    public double criticalMultiplier(Player p, double defaultMultiplier) {
        return this.enabled(p, CombatAbility.CRITICAL_MASTERY) ? CombatTreeMath.criticalMasteryMultiplier(this.rank(p, CombatAbility.CRITICAL_MASTERY), this.maxRank(CombatAbility.CRITICAL_MASTERY)) : defaultMultiplier;
    }

    private static double maxHealth(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? Math.max(1.0, e.getHealth()) : a.getValue();
    }

    public void hostileKill(Player p) {
        if (this.enabled(p, CombatAbility.BLOOD_LUST)) {
            int threshold = CombatTreeMath.bloodLustThreshold(this.rank(p, CombatAbility.BLOOD_LUST), this.maxRank(CombatAbility.BLOOD_LUST));
            int before = this.bloodStreak.getOrDefault(p.getUniqueId(), 0);
            int after = Math.min(threshold, before + 1);
            this.bloodStreak.put(p.getUniqueId(), after);
            if (before < threshold && after == threshold) {
                p.sendMessage(Component.text("✦ " + Language.of(p).choose(
                        "SEDE DE SANGUE ATIVADA! Dano bônus até receber um golpe.",
                        "BLOOD LUST ACTIVATED! Bonus damage until you are hit.") + " ✦", NamedTextColor.RED));
            }
        }
        double heal = 0.0;
        if (this.enabled(p, CombatAbility.SOUL_HARVEST)) {
            heal += CombatTreeMath.soulHarvestHeal(this.rank(p, CombatAbility.SOUL_HARVEST), this.maxRank(CombatAbility.SOUL_HARVEST));
        }
        if (heal > 0.0) {
            this.stats.regenHealth(p, heal);
        }
    }

    public void hostileHit(Player p) {
        if (this.bloodStreak.remove(p.getUniqueId()) != null && this.enabled(p, CombatAbility.BLOOD_LUST)) {
            p.sendActionBar(Component.text(Language.of(p).choose("Sede de Sangue reiniciada", "Blood Lust reset"), NamedTextColor.DARK_RED));
        }
    }

    public int bloodStreak(Player p) {
        return this.bloodStreak.getOrDefault(p.getUniqueId(), 0);
    }

    public void clear(Player p) {
        this.bloodStreak.remove(p.getUniqueId());
    }

    /** Sword Throw's own cooldown, floored at {@link CombatTreeMath#MIN_COOLDOWN_MILLIS}. */
    public long swordThrowCooldownMillis(Player p) {
        return Math.max(CombatTreeMath.MIN_COOLDOWN_MILLIS,
                CombatTreeMath.swordThrowBaseCooldownMillis(this.rank(p, CombatAbility.SWORD_THROW), this.maxRank(CombatAbility.SWORD_THROW)));
    }

    public double swordThrowDamageFraction(Player p) {
        return CombatTreeMath.swordThrowDamageFraction(this.rank(p, CombatAbility.SWORD_THROW), this.maxRank(CombatAbility.SWORD_THROW));
    }

    /**
     * Whether the currently-in-progress hostile damage event was caused by a special
     * ability dealing its own pre-computed damage (Sword Throw) rather than a normal
     * melee swing — see {@link #dealAbilityDamage}. {@link
     * dev.icaro.foodtooltips.combat.CombatListener#damage} checks this to skip the
     * melee multiplier stack (level/crit/strength/mob bonus) and Ferocity's extra hits,
     * so Sword Throw only ever lands on the one target it actually hit.
     */
    public boolean isAbilityDamageInFlight(Player p) {
        return this.abilityDamageInFlight.contains(p.getUniqueId());
    }

    /**
     * Deals damage from a special (non-melee) ability the same safe way: flagged via
     * {@link #isAbilityDamageInFlight} for the duration of the call so {@code
     * CombatListener#damage} bypasses melee multipliers/Ferocity for it.
     */
    public void dealAbilityDamage(Player p, LivingEntity target, double amount) {
        this.abilityDamageInFlight.add(p.getUniqueId());
        try {
            target.damage(amount, p);
        } finally {
            this.abilityDamageInFlight.remove(p.getUniqueId());
        }
    }

    // ---- Second Wind (used by CombatListener) -----------------------------------

    public long secondWindCooldownMillis(Player p) {
        return CombatTreeMath.secondWindCooldownMillis(this.rank(p, CombatAbility.SECOND_WIND), this.maxRank(CombatAbility.SECOND_WIND));
    }

    public double secondWindHealFraction(Player p) {
        return CombatTreeMath.secondWindHealFraction(this.rank(p, CombatAbility.SECOND_WIND), this.maxRank(CombatAbility.SECOND_WIND));
    }

    // ---- Stat-tree bonuses (feed PlayerStatsService) -----------------------------

    /** Flat Swing Range bonus from Sword Throw. */
    public double swingRangeBonus(Player p) {
        return this.enabled(p, CombatAbility.SWORD_THROW) ? CombatTreeMath.swordThrowSwingRangeBonus(this.rank(p, CombatAbility.SWORD_THROW), this.maxRank(CombatAbility.SWORD_THROW)) : 0.0;
    }

    /** Percentage Health Regen bonus from Soul Harvest. */
    public double healthRegenBonus(Player p) {
        return this.enabled(p, CombatAbility.SOUL_HARVEST) ? CombatTreeMath.soulHarvestHealthRegenBonus(this.rank(p, CombatAbility.SOUL_HARVEST), this.maxRank(CombatAbility.SOUL_HARVEST)) : 0.0;
    }

    /** Percentage Mending bonus from Second Wind. */
    public double mendingBonus(Player p) {
        return this.enabled(p, CombatAbility.SECOND_WIND) ? CombatTreeMath.secondWindMendingBonus(this.rank(p, CombatAbility.SECOND_WIND), this.maxRank(CombatAbility.SECOND_WIND)) : 0.0;
    }

    // ---- Combat Backpack capacity (feed BackpackService) -------------------------

    private static final CombatAbility[] BACKPACK_CHAIN = {
            CombatAbility.BACKPACK_1, CombatAbility.BACKPACK_2, CombatAbility.BACKPACK_3,
            CombatAbility.BACKPACK_4, CombatAbility.BACKPACK_5, CombatAbility.BACKPACK_6,
    };

    /**
     * How many of the 6 Combat Backpack nodes are unlocked, 0-6 — the chain's
     * prerequisites keep this contiguous from BACKPACK_1, so it's safe to stop at the
     * first gap. Deliberately keyed on {@link #unlocked}, not {@link #enabled}: unlike
     * every other passive, toggling a backpack node off would shrink the bag's visible
     * size, stranding whatever the player already stored past the new smaller capacity.
     */
    public int backpackRank(Player p) {
        int count = 0;
        for (CombatAbility a : BACKPACK_CHAIN) {
            if (!this.unlocked(p, a)) {
                break;
            }
            count++;
        }
        return count;
    }

    /** "9 slots" / "18 slots" / ... for the tree tooltip preview - mirrors {@code BackpackService#size}'s capacity table. */
    private static String backpackCapacityLabel(CombatAbility a) {
        int slots = switch (a) {
            case BACKPACK_1 -> 9;
            case BACKPACK_2 -> 18;
            case BACKPACK_3 -> 27;
            case BACKPACK_4 -> 36;
            case BACKPACK_5 -> 45;
            case BACKPACK_6 -> 54;
            default -> 0;
        };
        return slots + " slots";
    }

    // ---- Numeric stat preview (tree tooltip) ------------------------------------

    /** One "current level → next level" numeric readout row for the tree tooltip. */
    public record StatPreview(String label, String current, String next) {
    }

    /**
     * Numeric current/next-level stat readout for the tree tooltip: what this ability
     * actually does right now, and how much it improves at the next level. When
     * {@code rank} is 0 (not yet unlocked), "current" previews level 1 instead — useful
     * for deciding whether to unlock it. {@code next} in the returned rows is null once
     * the ability is at max level.
     */
    public List<StatPreview> statPreview(CombatAbility a, int rank, boolean pt) {
        int max = this.maxRank(a);
        int cur = Math.max(1, Math.min(max, rank <= 0 ? 1 : rank));
        int next = Math.min(max, cur + 1);
        boolean hasNext = rank < max;
        List<StatPreview> out = new ArrayList<>();
        switch (a) {
            case RUTHLESS_STRIKES -> out.add(this.pointsPlus(pt ? "Chance Crítica" : "Crit Chance", CombatTreeMath::ruthlessStrikesCritBonus, cur, next, hasNext, max));
            case SWORD_THROW -> {
                out.add(this.pctAbs(pt ? "Dano" : "Damage", CombatTreeMath::swordThrowDamageFraction, cur, next, hasNext, max));
                out.add(this.seconds(pt ? "Recarga" : "Cooldown", CombatTreeMath::swordThrowBaseCooldownMillis, cur, next, hasNext, max));
                out.add(this.flat(pt ? "Alcance de Ataque" : "Swing Range", CombatTreeMath::swordThrowSwingRangeBonus, cur, next, hasNext, max, ""));
            }
            case BLOOD_LUST -> {
                out.add(this.pctPlus(pt ? "Dano bônus" : "Damage bonus", CombatTreeMath::bloodLustBonus, cur, next, hasNext, max));
                out.add(this.integer(pt ? "Abates p/ ativar" : "Kills to trigger", CombatTreeMath::bloodLustThreshold, cur, next, hasNext, max, ""));
            }
            case BERSERKER -> out.add(this.multPct(pt ? "Dano bônus" : "Damage bonus", CombatTreeMath::berserkerMultiplier, cur, next, hasNext, max));
            case SOUL_HARVEST -> {
                out.add(this.flat(pt ? "Cura por abate" : "Heal per kill", CombatTreeMath::soulHarvestHeal, cur, next, hasNext, max, " HP"));
                out.add(this.pctPlus(pt ? "Regen. de Vida" : "Health Regen", CombatTreeMath::soulHarvestHealthRegenBonus, cur, next, hasNext, max));
            }
            case CRITICAL_MASTERY -> out.add(this.multAbs(pt ? "Multiplicador crítico" : "Critical multiplier", CombatTreeMath::criticalMasteryMultiplier, cur, next, hasNext, max));
            case SECOND_WIND -> {
                out.add(this.seconds(pt ? "Recarga" : "Cooldown", CombatTreeMath::secondWindCooldownMillis, cur, next, hasNext, max));
                out.add(this.pctAbs(pt ? "Cura ao ativar" : "Heal on trigger", CombatTreeMath::secondWindHealFraction, cur, next, hasNext, max));
                out.add(this.pctPlus(pt ? "Mending" : "Mending", CombatTreeMath::secondWindMendingBonus, cur, next, hasNext, max));
            }
            case BACKPACK_1, BACKPACK_2, BACKPACK_3, BACKPACK_4, BACKPACK_5, BACKPACK_6 ->
                    out.add(new StatPreview(pt ? "Capacidade" : "Capacity", backpackCapacityLabel(a), null));
        }
        return out;
    }

    private interface RankFn {
        double applyAsDouble(int rank, int maxRank);
    }

    private interface RankLongFn {
        long applyAsLong(int rank, int maxRank);
    }

    private interface RankIntFn {
        int applyAsInt(int rank, int maxRank);
    }

    /** "+X.X%" for a fraction bonus stat (0.10 → "+10.0%"). */
    private StatPreview pctPlus(String label, RankFn fn, int cur, int next, boolean hasNext, int max) {
        String c = String.format(Locale.US, "+%.1f%%", fn.applyAsDouble(cur, max) * 100.0);
        String n = hasNext ? String.format(Locale.US, "+%.1f%%", fn.applyAsDouble(next, max) * 100.0) : null;
        return new StatPreview(label, c, n);
    }

    /** "+X.X%" for a stat that is already expressed in percentage points (1.0 → "+1.0%"), not a 0-1 fraction. */
    private StatPreview pointsPlus(String label, RankFn fn, int cur, int next, boolean hasNext, int max) {
        String c = String.format(Locale.US, "+%.1f%%", fn.applyAsDouble(cur, max));
        String n = hasNext ? String.format(Locale.US, "+%.1f%%", fn.applyAsDouble(next, max)) : null;
        return new StatPreview(label, c, n);
    }

    /** "X.X%" for a fraction stat that is itself the whole value, not a bonus (e.g. Sword Throw's damage). */
    private StatPreview pctAbs(String label, RankFn fn, int cur, int next, boolean hasNext, int max) {
        String c = String.format(Locale.US, "%.1f%%", fn.applyAsDouble(cur, max) * 100.0);
        String n = hasNext ? String.format(Locale.US, "%.1f%%", fn.applyAsDouble(next, max) * 100.0) : null;
        return new StatPreview(label, c, n);
    }

    /** "+X%" derived from a multiplier (1.25 → "+25%"), for multipliers that represent a damage bonus. */
    private StatPreview multPct(String label, RankFn fn, int cur, int next, boolean hasNext, int max) {
        String c = String.format(Locale.US, "+%.0f%%", (fn.applyAsDouble(cur, max) - 1.0) * 100.0);
        String n = hasNext ? String.format(Locale.US, "+%.0f%%", (fn.applyAsDouble(next, max) - 1.0) * 100.0) : null;
        return new StatPreview(label, c, n);
    }

    /** "×X.XX" for a multiplier that replaces a base value outright (e.g. Critical Mastery). */
    private StatPreview multAbs(String label, RankFn fn, int cur, int next, boolean hasNext, int max) {
        String c = String.format(Locale.US, "×%.2f", fn.applyAsDouble(cur, max));
        String n = hasNext ? String.format(Locale.US, "×%.2f", fn.applyAsDouble(next, max)) : null;
        return new StatPreview(label, c, n);
    }

    /** A raw number with an optional unit suffix (e.g. "2.3 HP"). */
    private StatPreview flat(String label, RankFn fn, int cur, int next, boolean hasNext, int max, String unit) {
        String c = String.format(Locale.US, "%.1f%s", fn.applyAsDouble(cur, max), unit);
        String n = hasNext ? String.format(Locale.US, "%.1f%s", fn.applyAsDouble(next, max), unit) : null;
        return new StatPreview(label, c, n);
    }

    /** A millis-returning formula, shown in seconds. */
    private StatPreview seconds(String label, RankLongFn fn, int cur, int next, boolean hasNext, int max) {
        String c = String.format(Locale.US, "%.1fs", fn.applyAsLong(cur, max) / 1000.0);
        String n = hasNext ? String.format(Locale.US, "%.1fs", fn.applyAsLong(next, max) / 1000.0) : null;
        return new StatPreview(label, c, n);
    }

    /** A plain integer with an optional unit suffix. */
    private StatPreview integer(String label, RankIntFn fn, int cur, int next, boolean hasNext, int max, String unit) {
        String c = fn.applyAsInt(cur, max) + unit;
        String n = hasNext ? fn.applyAsInt(next, max) + unit : null;
        return new StatPreview(label, c, n);
    }

    // ---- Flavor text -----------------------------------------------------------

    public String description(CombatAbility a, boolean pt) {
        return switch (a) {
            case RUTHLESS_STRIKES -> pt ? "+1% de chance crítica por nível." : "+1% crit chance per level.";
            case SWORD_THROW -> pt ? "F arremessa a espada; dano, recarga e alcance de ataque melhoram por nível." : "F throws your sword; damage, cooldown and swing range improve per level.";
            case BLOOD_LUST -> pt ? "Após uma sequência de abates sem ser atingido: dano bônus." : "After a kill streak without being hit: bonus damage.";
            case BERSERKER -> pt ? "Dano bônus quando estiver abaixo de 10% HP, escala por nível." : "Bonus damage while below 10% HP, scales per level.";
            case SOUL_HARVEST -> pt ? "Cura adicional por abate hostil e aumenta Regen. de Vida, escala por nível." : "Additional heal per hostile kill and raises Health Regen, scales per level.";
            case CRITICAL_MASTERY -> pt ? "Aumenta o multiplicador de dano crítico, escala por nível." : "Increases the critical damage multiplier, scales per level.";
            case SECOND_WIND -> pt ? "Evita um golpe fatal e aumenta Mending; recarga e cura escalam por nível." : "Prevents a fatal hit and raises Mending; cooldown and heal scale per level.";
            case BACKPACK_1, BACKPACK_2, BACKPACK_3, BACKPACK_4, BACKPACK_5, BACKPACK_6 ->
                    pt ? "Aumenta a capacidade da Mochila de Combate." : "Increases the Combat Backpack's capacity.";
        };
    }

    private NamespacedKey key(CombatAbility a) {
        return new NamespacedKey("foodtooltips", "ability_" + a.name().toLowerCase(Locale.ROOT));
    }

    private NamespacedKey rankKey(CombatAbility a) {
        return new NamespacedKey("foodtooltips", "ability_rank_" + a.name().toLowerCase(Locale.ROOT));
    }
}
