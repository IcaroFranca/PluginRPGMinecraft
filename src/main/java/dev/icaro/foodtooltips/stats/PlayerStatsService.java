package dev.icaro.foodtooltips.stats;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.Registry;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Tracks every non-vanilla combat stat. Base values come from config; the
 * combat ability tree layers additional bonuses on top for every stat here
 * except {@link PlayerStats#trueDefense()} (deliberately tree-independent —
 * see {@link CombatAbilityService}'s class doc for which ability grants
 * which bonus). {@link #abilities(CombatAbilityService)} is wired in after
 * construction (the two services depend on each other) exactly like
 * {@link #global(GlobalLevelService)} already is.
 */
public final class PlayerStatsService {
    private final NamespacedKey mana;
    private final NamespacedKey maxMana;
    private final NamespacedKey vitality;
    private final NamespacedKey maxVitality;
    private final NamespacedKey swingRangeKey;
    private final double base;
    private final double baseHealth;
    private final double baseVitality;
    private final double ferocity;
    private final double ferocityCap;
    private final double swingRange;
    private final double swingRangeCap;
    private final double intelligence;
    private final double abilityDamage;
    private final double healthRegen;
    private final double mending;
    private final double trueDefense;
    private GlobalLevelService global;
    private CombatAbilityService abilities;

    private static boolean attributeResolved;
    private static Attribute entityInteractionRangeAttribute;

    public PlayerStatsService(Plugin p) {
        this.base = p.getConfig().getDouble("stats.base-mana", 100.0);
        this.baseHealth = Math.max(1.0, p.getConfig().getDouble("stats.base-health", 100.0));
        this.baseVitality = p.getConfig().getDouble("stats.base-vitality", 100.0);
        this.ferocityCap = p.getConfig().getDouble("stats.ferocity-cap", 500.0);
        this.ferocity = clamp(p.getConfig().getDouble("stats.base-ferocity", 0.0), 0.0, this.ferocityCap);
        this.swingRangeCap = p.getConfig().getDouble("stats.swing-range-cap", 15.0);
        this.swingRange = clamp(p.getConfig().getDouble("stats.base-swing-range", 3.0), 0.0, this.swingRangeCap);
        this.intelligence = Math.max(0.0, p.getConfig().getDouble("stats.base-intelligence", 0.0));
        this.abilityDamage = Math.max(0.0, p.getConfig().getDouble("stats.base-ability-damage", 0.0));
        this.healthRegen = Math.max(0.0, p.getConfig().getDouble("stats.base-health-regen", 100.0));
        this.mending = Math.max(0.0, p.getConfig().getDouble("stats.base-mending", 100.0));
        this.trueDefense = Math.max(0.0, p.getConfig().getDouble("stats.base-true-defense", 0.0));
        this.mana = new NamespacedKey("foodtooltips", "stat_mana");
        this.maxMana = new NamespacedKey("foodtooltips", "stat_max_mana");
        this.vitality = new NamespacedKey("foodtooltips", "stat_vitality");
        this.maxVitality = new NamespacedKey("foodtooltips", "stat_max_vitality");
        this.swingRangeKey = new NamespacedKey("foodtooltips", "stat_swing_range");
    }

    public void global(GlobalLevelService global) {
        this.global = global;
    }

    public void abilities(CombatAbilityService abilities) {
        this.abilities = abilities;
    }

    public void init(Player p) {
        PersistentDataContainer d = p.getPersistentDataContainer();
        if (!d.has(this.maxMana, PersistentDataType.DOUBLE)) {
            d.set(this.maxMana, PersistentDataType.DOUBLE, this.base);
        }
        if (!d.has(this.mana, PersistentDataType.DOUBLE)) {
            d.set(this.mana, PersistentDataType.DOUBLE, this.base);
        }
        if (!d.has(this.maxVitality, PersistentDataType.DOUBLE)) {
            d.set(this.maxVitality, PersistentDataType.DOUBLE, this.baseVitality);
        }
        if (!d.has(this.vitality, PersistentDataType.DOUBLE)) {
            d.set(this.vitality, PersistentDataType.DOUBLE, this.baseVitality);
        }
    }

    private double get(Player p, NamespacedKey k, double fallback) {
        this.init(p);
        return p.getPersistentDataContainer().getOrDefault(k, PersistentDataType.DOUBLE, fallback);
    }

    public PlayerStats stats(Player p) {
        double storedMaxMana = this.get(p, this.maxMana, this.base);
        double effectiveMaxMana = storedMaxMana + this.intelligence;
        double storedMana = this.get(p, this.mana, storedMaxMana);
        double storedMaxVitality = this.get(p, this.maxVitality, this.baseVitality);
        double storedVitality = this.get(p, this.vitality, storedMaxVitality);
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        long globalStrength = this.global == null ? 0L : this.global.snapshot(p).strength();

        double strengthBonus = this.abilities == null ? 0.0 : this.abilities.strengthBonus(p);
        double ferocityBonus = this.abilities == null ? 0.0 : this.abilities.ferocityBonus(p);
        double swingRangeBonus = this.abilities == null ? 0.0 : this.abilities.swingRangeBonus(p);
        double intelligenceBonus = this.abilities == null ? 0.0 : this.abilities.intelligenceBonus(p);
        double abilityDamageBonus = this.abilities == null ? 0.0 : this.abilities.abilityDamageBonus(p);
        double healthRegenBonus = this.abilities == null ? 0.0 : this.abilities.healthRegenBonus(p);
        double vitalityBonus = this.abilities == null ? 0.0 : this.abilities.vitalityBonus(p);
        double mendingBonus = this.abilities == null ? 0.0 : this.abilities.mendingBonus(p);

        double effectiveMaxVitality = storedMaxVitality + vitalityBonus;
        double effectiveIntelligence = this.intelligence + intelligenceBonus;

        return new PlayerStats(
                p.getHealth(),
                a == null ? 20.0 : a.getValue(),
                Math.min(effectiveMaxMana, storedMana),
                effectiveMaxMana,
                globalStrength + Math.round(strengthBonus),
                clamp(this.ferocity + ferocityBonus, 0.0, this.ferocityCap),
                clamp(this.swingRange + swingRangeBonus, 0.0, this.swingRangeCap),
                effectiveIntelligence,
                this.abilityDamage + abilityDamageBonus,
                this.healthRegen + healthRegenBonus,
                Math.min(effectiveMaxVitality, storedVitality),
                effectiveMaxVitality,
                this.mending + mendingBonus,
                this.trueDefense);
    }

    public void regen(Player p, double n) {
        this.setMana(p, this.stats(p).mana() + n);
    }

    public void regenVitality(Player p, double n) {
        this.setVitality(p, this.stats(p).vitality() + n);
    }

    public void regenHealth(Player p, double amount) {
        if (amount <= 0.0 || p.isDead()) {
            return;
        }
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? 20.0 : a.getValue();
        if (p.getHealth() < max) {
            p.setHealth(Math.min(max, p.getHealth() + amount));
        }
    }

    public void setMana(Player p, double n) {
        double m = this.get(p, this.maxMana, this.base) + this.intelligence;
        p.getPersistentDataContainer().set(this.mana, PersistentDataType.DOUBLE, clamp(n, 0.0, m));
    }

    public void setMaxMana(Player p, double n) {
        p.getPersistentDataContainer().set(this.maxMana, PersistentDataType.DOUBLE, Math.max(1.0, n));
        this.setMana(p, this.get(p, this.mana, n));
    }

    public boolean withdrawMana(Player p, double amount) {
        if (amount < 0.0) {
            return false;
        }
        double current = this.stats(p).mana();
        if (current < amount) {
            return false;
        }
        this.setMana(p, current - amount);
        return true;
    }

    public void setVitality(Player p, double n) {
        double m = this.get(p, this.maxVitality, this.baseVitality);
        p.getPersistentDataContainer().set(this.vitality, PersistentDataType.DOUBLE, clamp(n, 0.0, m));
    }

    public boolean withdrawVitality(Player p, double amount) {
        if (amount < 0.0) {
            return false;
        }
        double current = this.stats(p).vitality();
        if (current < amount) {
            return false;
        }
        this.setVitality(p, current - amount);
        return true;
    }

    /**
     * Sets the player's base Max Health (vanilla default is 20; this plugin's default
     * is {@code stats.base-health}, 100). Bonuses on top (Bestiary milestones, Global
     * Level's HP-per-level) are transient modifiers added separately, so they compose
     * correctly on top of whichever base is set here regardless of call order.
     */
    public void applyBaseHealth(Player p) {
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        if (a != null) {
            a.setBaseValue(this.baseHealth);
        }
    }

    /**
     * Applies the Swing Range stat to the vanilla melee/interaction-range
     * attribute, if this server version exposes it under the registry key
     * this method knows about. No-ops silently otherwise so an older/newer
     * server never fails to start over a cosmetic stat.
     */
    public void applySwingRange(Player p) {
        Attribute attribute = resolveEntityInteractionRangeAttribute();
        if (attribute == null) {
            return;
        }
        AttributeInstance instance = p.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier old = instance.getModifier(Key.key(this.swingRangeKey.getNamespace(), this.swingRangeKey.getKey()));
        if (old != null) {
            instance.removeModifier(old);
        }
        double delta = this.stats(p).swingRange() - instance.getBaseValue();
        if (Math.abs(delta) > 1.0E-4) {
            instance.addTransientModifier(new AttributeModifier(this.swingRangeKey, delta, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    private static Attribute resolveEntityInteractionRangeAttribute() {
        if (attributeResolved) {
            return entityInteractionRangeAttribute;
        }
        attributeResolved = true;
        for (String key : new String[]{"player.entity_interaction_range", "entity_interaction_range"}) {
            try {
                Attribute found = Registry.ATTRIBUTE.get(NamespacedKey.minecraft(key));
                if (found != null) {
                    entityInteractionRangeAttribute = found;
                    break;
                }
            } catch (RuntimeException ignored) {
                // Try the next candidate key; give up gracefully if none resolve.
            }
        }
        return entityInteractionRangeAttribute;
    }

    private static double clamp(double value, double min, double max) {
        return Math.max(min, Math.min(max, value));
    }
}
