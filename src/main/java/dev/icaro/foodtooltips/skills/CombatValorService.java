package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.bestiary.BestiaryEntry;
import java.text.NumberFormat;
import java.util.Locale;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Manages Blood Points, the currency spent in the combat ability tree to
 * unlock and upgrade abilities. Deliberately separate from {@link
 * dev.icaro.foodtooltips.economy.EconomyService}'s coins: Blood Points only
 * ever come from combat and only ever buy tree ranks.
 */
public final class CombatValorService {
    private final NamespacedKey key;
    private final double perKillMultiplier;
    private final long perLevel;

    public CombatValorService(Plugin plugin) {
        this.key = new NamespacedKey("foodtooltips", "combat_valor");
        this.perKillMultiplier = Math.max(0.0, plugin.getConfig().getDouble("combat-tree.valor-per-kill-multiplier", 0.3));
        this.perLevel = Math.max(0L, plugin.getConfig().getLong("combat-tree.valor-per-level", 3L));
    }

    public long balance(Player p) {
        return p.getPersistentDataContainer().getOrDefault(this.key, PersistentDataType.LONG, 0L);
    }

    public void deposit(Player p, long amount) {
        if (amount <= 0L) {
            return;
        }
        long current = this.balance(p);
        long next;
        try {
            next = Math.addExact(current, amount);
        } catch (ArithmeticException ex) {
            next = Long.MAX_VALUE;
        }
        p.getPersistentDataContainer().set(this.key, PersistentDataType.LONG, next);
    }

    public boolean withdraw(Player p, long amount) {
        if (amount < 0L) {
            return false;
        }
        long current = this.balance(p);
        if (current < amount) {
            return false;
        }
        p.getPersistentDataContainer().set(this.key, PersistentDataType.LONG, current - amount);
        return true;
    }

    public void setBalance(Player p, long amount) {
        p.getPersistentDataContainer().set(this.key, PersistentDataType.LONG, Math.max(0L, amount));
    }

    /**
     * Blood Points dropped by a hostile mob kill. Mirrors whatever the
     * Bestiary shows for that mob ({@link #catalogValor(BestiaryEntry)}) so
     * the number the player sees in the Bestiary is exactly what they get;
     * mobs without a Bestiary entry fall back to a health-based estimate.
     */
    public long mobValor(LivingEntity mob) {
        return BestiaryCatalog.find(mob.getType()).map(this::catalogValor).orElseGet(() -> this.fallbackValor(mob));
    }

    /** Blood Points a Bestiary entry awards on kill — shown in the Bestiary UI. */
    public long catalogValor(BestiaryEntry entry) {
        return Math.max(0, entry.awardedCombatXp());
    }

    private long fallbackValor(LivingEntity mob) {
        double hp = 10.0;
        Attribute maxHealth = Attribute.MAX_HEALTH;
        if (mob.getAttribute(maxHealth) != null) {
            hp = mob.getAttribute(maxHealth).getValue();
        }
        return Math.max(1L, Math.round(Math.sqrt(hp) * this.perKillMultiplier));
    }

    /** Bonus Blood Points awarded when the Combat skill gains levels. */
    public long levelUpValor(int levelsGained) {
        if (levelsGained <= 0) {
            return 0L;
        }
        return this.perLevel * levelsGained;
    }

    public String format(long amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }
}
