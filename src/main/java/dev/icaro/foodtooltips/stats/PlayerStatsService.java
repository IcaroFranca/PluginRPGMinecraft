package dev.icaro.foodtooltips.stats;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.stats.PlayerStats;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class PlayerStatsService {
    private final NamespacedKey mana;
    private final NamespacedKey maxMana;
    private final double base;
    private GlobalLevelService global;

    public PlayerStatsService(Plugin p) {
        this.base = p.getConfig().getDouble("stats.base-mana", 100.0);
        this.mana = new NamespacedKey("foodtooltips", "stat_mana");
        this.maxMana = new NamespacedKey("foodtooltips", "stat_max_mana");
    }

    public void global(GlobalLevelService global) {
        this.global = global;
    }

    public void init(Player p) {
        PersistentDataContainer d = p.getPersistentDataContainer();
        if (!d.has(this.maxMana, PersistentDataType.DOUBLE)) {
            d.set(this.maxMana, PersistentDataType.DOUBLE, (Object)this.base);
        }
        if (!d.has(this.mana, PersistentDataType.DOUBLE)) {
            d.set(this.mana, PersistentDataType.DOUBLE, (Object)this.base);
        }
    }

    private double get(Player p, NamespacedKey k, double f) {
        this.init(p);
        return (Double)p.getPersistentDataContainer().getOrDefault(k, PersistentDataType.DOUBLE, (Object)f);
    }

    public PlayerStats stats(Player p) {
        double mm = this.get(p, this.maxMana, this.base);
        AttributeInstance a = p.getAttribute(Attribute.MAX_HEALTH);
        long strength = this.global == null ? 0L : this.global.snapshot(p).strength();
        return new PlayerStats(p.getHealth(), a == null ? 20.0 : a.getValue(), Math.min(mm, this.get(p, this.mana, mm)), mm, strength, strength);
    }

    public void regen(Player p, double n) {
        PlayerStats s = this.stats(p);
        this.setMana(p, s.mana() + n);
    }

    public void setMana(Player p, double n) {
        double m = this.get(p, this.maxMana, this.base);
        p.getPersistentDataContainer().set(this.mana, PersistentDataType.DOUBLE, (Object)Math.max(0.0, Math.min(m, n)));
    }

    public void setMaxMana(Player p, double n) {
        p.getPersistentDataContainer().set(this.maxMana, PersistentDataType.DOUBLE, (Object)Math.max(1.0, n));
        this.setMana(p, this.get(p, this.mana, n));
    }
}

