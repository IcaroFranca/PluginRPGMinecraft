package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.stats.PlayerStats;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

/**
 * Runtime engine for the combat ability tree: rank storage/progression
 * (spending Blood Points to unlock and upgrade nodes) and every rank-scaled
 * gameplay effect, including the two menu-cast active abilities.
 */
public final class CombatAbilityService {
    public enum PurchaseResult {
        SUCCESS, ALREADY_MAX, PREREQUISITE_MISSING, INSUFFICIENT_VALOR
    }

    public enum CastResult {
        SUCCESS, LOCKED, ON_COOLDOWN, INSUFFICIENT_RESOURCE, NO_TARGET
    }

    private final Plugin plugin;
    private final PlayerStatsService stats;
    private final CombatValorService valor;
    private final long baseCost;
    private final long costPerTier;
    private final long costPerRank;
    private final long costPerRankPerTier;

    private final Map<UUID, Integer> bloodStreak = new HashMap<>();
    private final Map<UUID, Integer> attackCounter = new HashMap<>();
    private final Map<UUID, Long> arcaneSlashCooldowns = new HashMap<>();
    private final Map<UUID, Long> vitalTouchCooldowns = new HashMap<>();
    private final Set<UUID> magicDamageInFlight = new HashSet<>();

    public CombatAbilityService(Plugin p, PlayerStatsService stats, CombatValorService valor) {
        this.plugin = p;
        this.stats = stats;
        this.valor = valor;
        this.baseCost = Math.max(1L, p.getConfig().getLong("combat-tree.base-unlock-cost", 20L));
        this.costPerTier = Math.max(0L, p.getConfig().getLong("combat-tree.cost-per-tier", 15L));
        this.costPerRank = Math.max(0L, p.getConfig().getLong("combat-tree.cost-per-rank", 8L));
        this.costPerRankPerTier = Math.max(0L, p.getConfig().getLong("combat-tree.cost-per-rank-per-tier", 5L));
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
        long cost = this.nextRankCost(p, a);
        if (!this.valor.withdraw(p, cost)) {
            return PurchaseResult.INSUFFICIENT_VALOR;
        }
        p.getPersistentDataContainer().set(this.rankKey(a), PersistentDataType.INTEGER, current + 1);
        return PurchaseResult.SUCCESS;
    }

    // ---- Melee combat effects --------------------------------------------------

    public double outgoingMultiplier(Player p, LivingEntity target) {
        double multiplier = 1.0;
        if (this.enabled(p, CombatAbility.EXECUTIONER) && target.getHealth() <= maxHealth(target) * 0.25) {
            multiplier *= CombatTreeMath.executionerMultiplier(this.rank(p, CombatAbility.EXECUTIONER));
        }
        if (this.enabled(p, CombatAbility.BLOOD_LUST) && this.bloodStreak(p) >= CombatTreeMath.bloodLustThreshold(this.rank(p, CombatAbility.BLOOD_LUST))) {
            double bonus = CombatTreeMath.bloodLustBonus(this.rank(p, CombatAbility.BLOOD_LUST));
            if (this.enabled(p, CombatAbility.COMBAT_MASTERY)) {
                bonus += CombatTreeMath.masteryBloodLustBonus(this.rank(p, CombatAbility.COMBAT_MASTERY));
            }
            multiplier *= 1.0 + bonus;
        }
        if (this.enabled(p, CombatAbility.BERSERKER) && p.getHealth() <= maxHealth(p) * 0.3) {
            multiplier *= CombatTreeMath.berserkerMultiplier(this.rank(p, CombatAbility.BERSERKER));
        }
        AttributeInstance armor = target.getAttribute(Attribute.ARMOR);
        if (this.enabled(p, CombatAbility.ARMOR_PIERCER) && armor != null && armor.getValue() > 0.0) {
            multiplier *= CombatTreeMath.armorPiercerMultiplier(this.rank(p, CombatAbility.ARMOR_PIERCER));
        }
        if (this.enabled(p, CombatAbility.APEX_WARRIOR)) {
            multiplier *= 1.0 + CombatTreeMath.apexFinalDamageBonus(this.rank(p, CombatAbility.APEX_WARRIOR));
        }
        return multiplier;
    }

    /** Flat crit-chance percentage points contributed by Ruthless Strikes. */
    public double critChanceBonus(Player p) {
        return this.enabled(p, CombatAbility.RUTHLESS_STRIKES) ? CombatTreeMath.ruthlessStrikesCritBonus(this.rank(p, CombatAbility.RUTHLESS_STRIKES)) : 0.0;
    }

    public double criticalMultiplier(Player p, double defaultMultiplier) {
        return this.enabled(p, CombatAbility.CRITICAL_MASTERY) ? CombatTreeMath.criticalMasteryMultiplier(this.rank(p, CombatAbility.CRITICAL_MASTERY)) : defaultMultiplier;
    }

    private static double maxHealth(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? Math.max(1.0, e.getHealth()) : a.getValue();
    }

    public void hostileKill(Player p) {
        if (this.enabled(p, CombatAbility.HUNTERS_INSTINCT)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, CombatTreeMath.huntersInstinctDurationTicks(this.rank(p, CombatAbility.HUNTERS_INSTINCT)), 0));
        }
        if (this.enabled(p, CombatAbility.BLOOD_LUST)) {
            int threshold = CombatTreeMath.bloodLustThreshold(this.rank(p, CombatAbility.BLOOD_LUST));
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
        if (this.enabled(p, CombatAbility.VAMPIRISM)) {
            heal += CombatTreeMath.vampirismHeal(this.rank(p, CombatAbility.VAMPIRISM));
        }
        if (this.enabled(p, CombatAbility.SOUL_HARVEST)) {
            heal += CombatTreeMath.soulHarvestHeal(this.rank(p, CombatAbility.SOUL_HARVEST));
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

    public boolean guaranteedCritical(Player p) {
        if (!this.enabled(p, CombatAbility.RELENTLESS)) {
            return false;
        }
        int interval = CombatTreeMath.relentlessInterval(this.rank(p, CombatAbility.RELENTLESS));
        int count = this.attackCounter.merge(p.getUniqueId(), 1, (a, b) -> a >= interval ? 1 : a + b);
        return count == interval;
    }

    public int bloodStreak(Player p) {
        return this.bloodStreak.getOrDefault(p.getUniqueId(), 0);
    }

    public void clear(Player p) {
        this.bloodStreak.remove(p.getUniqueId());
        this.attackCounter.remove(p.getUniqueId());
        this.arcaneSlashCooldowns.remove(p.getUniqueId());
        this.vitalTouchCooldowns.remove(p.getUniqueId());
    }

    /** Combined Sword Throw cooldown after Combat Mastery / Apex Warrior reductions, floored at {@link CombatTreeMath#MIN_COOLDOWN_MILLIS}. */
    public long swordThrowCooldownMillis(Player p) {
        int swordThrowRank = this.rank(p, CombatAbility.SWORD_THROW);
        int masteryRank = this.enabled(p, CombatAbility.COMBAT_MASTERY) ? this.rank(p, CombatAbility.COMBAT_MASTERY) : 0;
        int apexRank = this.enabled(p, CombatAbility.APEX_WARRIOR) ? this.rank(p, CombatAbility.APEX_WARRIOR) : 0;
        return CombatTreeMath.swordThrowCooldownMillis(swordThrowRank, masteryRank, apexRank);
    }

    public double swordThrowDamageFraction(Player p) {
        return CombatTreeMath.swordThrowDamageFraction(this.rank(p, CombatAbility.SWORD_THROW));
    }

    /** Whether the currently-in-progress hostile damage event was caused by a magic-damage ability cast (Arcane Slash). */
    public boolean isMagicDamageInFlight(Player p) {
        return this.magicDamageInFlight.contains(p.getUniqueId());
    }

    // ---- Second Wind (used by CombatListener) -----------------------------------

    public long secondWindCooldownMillis(Player p) {
        return CombatTreeMath.secondWindCooldownMillis(this.rank(p, CombatAbility.SECOND_WIND));
    }

    public double secondWindHealFraction(Player p) {
        return CombatTreeMath.secondWindHealFraction(this.rank(p, CombatAbility.SECOND_WIND));
    }

    public double cleaveSplashFraction(Player p) {
        return CombatTreeMath.cleaveSplashFraction(this.rank(p, CombatAbility.CLEAVE));
    }

    public int cleaveMaxTargets(Player p) {
        return CombatTreeMath.cleaveMaxTargets(this.rank(p, CombatAbility.CLEAVE));
    }

    public double treasureHunterBonus(Player p) {
        return this.enabled(p, CombatAbility.TREASURE_HUNTER) ? CombatTreeMath.treasureHunterBonus(this.rank(p, CombatAbility.TREASURE_HUNTER)) : 0.0;
    }

    public double undyingWillReduction(Player p) {
        return this.enabled(p, CombatAbility.UNDYING_WILL) ? CombatTreeMath.undyingWillReduction(this.rank(p, CombatAbility.UNDYING_WILL)) : 0.0;
    }

    // ---- New active abilities (menu-cast) ---------------------------------------

    public CastResult castArcaneSlash(Player p) {
        if (!this.unlocked(p, CombatAbility.ARCANE_SLASH)) {
            return CastResult.LOCKED;
        }
        long now = System.currentTimeMillis();
        long ready = this.arcaneSlashCooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < ready) {
            return CastResult.ON_COOLDOWN;
        }
        int rank = this.rank(p, CombatAbility.ARCANE_SLASH);
        double manaCost = CombatTreeMath.arcaneSlashManaCost(rank);
        PlayerStats s = this.stats.stats(p);
        if (s.mana() < manaCost) {
            return CastResult.INSUFFICIENT_RESOURCE;
        }
        org.bukkit.entity.Entity targetEntity = p.getTargetEntity(10);
        if (!(targetEntity instanceof LivingEntity target) || target == p || target.isDead()) {
            return CastResult.NO_TARGET;
        }
        this.stats.withdrawMana(p, manaCost);
        this.arcaneSlashCooldowns.put(p.getUniqueId(), now + CombatTreeMath.arcaneSlashCooldownMillis(rank));
        double damage = CombatTreeMath.arcaneSlashDamage(rank, s.intelligence(), s.abilityDamage());
        this.magicDamageInFlight.add(p.getUniqueId());
        try {
            target.damage(damage, p);
        } finally {
            this.magicDamageInFlight.remove(p.getUniqueId());
        }
        return CastResult.SUCCESS;
    }

    public CastResult castVitalTouch(Player p) {
        if (!this.unlocked(p, CombatAbility.VITAL_TOUCH)) {
            return CastResult.LOCKED;
        }
        long now = System.currentTimeMillis();
        long ready = this.vitalTouchCooldowns.getOrDefault(p.getUniqueId(), 0L);
        if (now < ready) {
            return CastResult.ON_COOLDOWN;
        }
        int rank = this.rank(p, CombatAbility.VITAL_TOUCH);
        double vitalityCost = CombatTreeMath.vitalTouchVitalityCost(rank);
        PlayerStats s = this.stats.stats(p);
        if (s.vitality() < vitalityCost) {
            return CastResult.INSUFFICIENT_RESOURCE;
        }
        this.stats.withdrawVitality(p, vitalityCost);
        this.vitalTouchCooldowns.put(p.getUniqueId(), now + CombatTreeMath.vitalTouchCooldownMillis(rank));
        double baseHeal = CombatTreeMath.vitalTouchHeal(rank, s.intelligence(), s.abilityDamage());
        this.stats.regenHealth(p, baseHeal);
        for (org.bukkit.entity.Entity nearby : p.getNearbyEntities(6.0, 4.0, 6.0)) {
            if (nearby instanceof Player ally && !ally.getUniqueId().equals(p.getUniqueId())) {
                this.stats.regenHealth(ally, CombatTreeMath.applyMending(baseHeal, s.mending()));
            }
        }
        return CastResult.SUCCESS;
    }

    public long arcaneSlashCooldownRemainingMillis(Player p) {
        return Math.max(0L, this.arcaneSlashCooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis());
    }

    public long vitalTouchCooldownRemainingMillis(Player p) {
        return Math.max(0L, this.vitalTouchCooldowns.getOrDefault(p.getUniqueId(), 0L) - System.currentTimeMillis());
    }

    // ---- Flavor text -----------------------------------------------------------

    public String description(CombatAbility a, boolean pt) {
        return switch (a) {
            case RUTHLESS_STRIKES -> pt ? "+1% de chance crítica por nível." : "+1% crit chance per level.";
            case VAMPIRISM -> pt ? "Recupera HP ao matar um mob hostil, escala por nível." : "Recover HP after killing a hostile mob, scales per level.";
            case SWORD_THROW -> pt ? "F arremessa a espada; dano e recarga melhoram por nível." : "F throws your sword; damage and cooldown improve per level.";
            case EXECUTIONER -> pt ? "Dano bônus contra alvos abaixo de 25% HP, escala por nível." : "Bonus damage against targets below 25% HP, scales per level.";
            case BLOOD_LUST -> pt ? "Após uma sequência de abates sem ser atingido: dano bônus." : "After a kill streak without being hit: bonus damage.";
            case TREASURE_HUNTER -> pt ? "Mais moedas de mobs, escala por nível." : "More coins from mobs, scales per level.";
            case HUNTERS_INSTINCT -> pt ? "Velocidade após um abate hostil, duração escala por nível." : "Speed after a hostile kill, duration scales per level.";
            case BERSERKER -> pt ? "Dano bônus quando estiver abaixo de 30% HP, escala por nível." : "Bonus damage while below 30% HP, scales per level.";
            case UNDYING_WILL -> pt ? "Reduz dano recebido abaixo de 50% HP, escala por nível." : "Reduces damage taken below 50% HP, scales per level.";
            case VITAL_TOUCH -> pt ? "Clique direito na árvore: cura você e aliados próximos." : "Right-click in the tree: heals you and nearby allies.";
            case COMBAT_MASTERY -> pt ? "Fortalece Sede de Sangue e reduz ainda mais a recarga do Arremesso." : "Strengthens Blood Lust and further reduces Sword Throw's cooldown.";
            case ARCANE_SLASH -> pt ? "Clique direito na árvore: dano mágico direto no alvo (ignora armadura)." : "Right-click in the tree: direct magic damage to your target (bypasses armor).";
            case CLEAVE -> pt ? "Parte do dano atinge inimigos próximos, alcance escala por nível." : "Part of the damage hits nearby enemies, reach scales per level.";
            case ARMOR_PIERCER -> pt ? "Dano bônus contra alvos com armadura, escala por nível." : "Bonus damage against armored targets, scales per level.";
            case SOUL_HARVEST -> pt ? "Cura adicional por abate hostil, escala por nível." : "Additional heal per hostile kill, scales per level.";
            case CRITICAL_MASTERY -> pt ? "Aumenta o multiplicador de dano crítico, escala por nível." : "Increases the critical damage multiplier, scales per level.";
            case SECOND_WIND -> pt ? "Evita um golpe fatal; recarga e cura escalam por nível." : "Prevents a fatal hit; cooldown and heal scale per level.";
            case RELENTLESS -> pt ? "A cada N ataques, um crítico garantido; N encolhe por nível." : "Every N attacks, a guaranteed critical; N shrinks per level.";
            case APEX_WARRIOR -> pt ? "Dano final bônus e piso de recarga menor para o Arremesso." : "Bonus final damage and a lower cooldown floor for Sword Throw.";
        };
    }

    private NamespacedKey key(CombatAbility a) {
        return new NamespacedKey("foodtooltips", "ability_" + a.name().toLowerCase(Locale.ROOT));
    }

    private NamespacedKey rankKey(CombatAbility a) {
        return new NamespacedKey("foodtooltips", "ability_rank_" + a.name().toLowerCase(Locale.ROOT));
    }
}
