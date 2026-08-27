package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.skills.CombatProgress;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class CombatSkillService {
    private final NamespacedKey levelKey = new NamespacedKey("foodtooltips", "combat_level");
    private final NamespacedKey xpKey = new NamespacedKey("foodtooltips", "combat_xp");
    private final NamespacedKey speedKey = new NamespacedKey("foodtooltips", "combat_attack_speed");
    private final int max;
    private final double xpBase;
    private final double xpExp;
    private final double critPer;
    private final double damagePer;
    private final double s0;
    private final double s25;
    private final double s50;

    public CombatSkillService(Plugin p) {
        this.max = Math.max(200, p.getConfig().getInt("combat.max-level", 200));
        this.xpBase = p.getConfig().getDouble("combat.xp-base", 100.0);
        this.xpExp = p.getConfig().getDouble("combat.xp-exponent", 1.65);
        this.critPer = p.getConfig().getDouble("combat.crit-chance-per-level", 0.5);
        this.damagePer = p.getConfig().getDouble("combat.damage-percent-per-level", 4.0);
        this.s0 = p.getConfig().getDouble("combat.attack-speed-level-0", 4.0);
        this.s25 = p.getConfig().getDouble("combat.attack-speed-level-25", 8.0);
        this.s50 = p.getConfig().getDouble("combat.attack-speed-level-50", 20.0);
    }

    public CombatProgress progress(Player p) {
        PersistentDataContainer d = p.getPersistentDataContainer();
        int l = (Integer)d.getOrDefault(this.levelKey, PersistentDataType.INTEGER, (Object)0);
        double x = (Double)d.getOrDefault(this.xpKey, PersistentDataType.DOUBLE, (Object)0.0);
        return new CombatProgress(l, x, l >= this.max ? 0.0 : this.required(l + 1));
    }

    public int addXp(Player p, double amount) {
        double x;
        PersistentDataContainer d = p.getPersistentDataContainer();
        CombatProgress old = this.progress(p);
        int l = old.level();
        for (x = old.xp() + Math.max(0.0, amount); l < this.max && x >= this.required(l + 1); x -= this.required(++l)) {
        }
        if (l >= this.max) {
            x = 0.0;
        }
        d.set(this.levelKey, PersistentDataType.INTEGER, (Object)l);
        d.set(this.xpKey, PersistentDataType.DOUBLE, (Object)x);
        this.applyAttackSpeed(p);
        return l - old.level();
    }

    public void setLevel(Player p, int level) {
        int value = Math.max(0, Math.min(this.max, level));
        PersistentDataContainer d = p.getPersistentDataContainer();
        d.set(this.levelKey, PersistentDataType.INTEGER, (Object)value);
        d.set(this.xpKey, PersistentDataType.DOUBLE, (Object)0.0);
        this.applyAttackSpeed(p);
    }

    public double required(int level) {
        return Math.round(this.xpBase * Math.pow(Math.max(1, level), this.xpExp) + 50.0);
    }

    public double critChance(int level) {
        return (double)level * this.critPer;
    }

    public double damageMultiplier(int level) {
        return 1.0 + (double)level * this.damagePer / 100.0;
    }

    public double attackSpeed(int level) {
        int capped = Math.min(50, Math.max(0, level));
        if (capped <= 25) {
            return this.s0 + (this.s25 - this.s0) * ((double)capped / 25.0);
        }
        return this.s25 + (this.s50 - this.s25) * ((double)(capped - 25) / 25.0);
    }

    public void applyAttackSpeed(Player p) {
        double amount;
        AttributeInstance a = p.getAttribute(Attribute.ATTACK_SPEED);
        if (a == null) {
            return;
        }
        AttributeModifier old = a.getModifier(Key.key((String)this.speedKey.getNamespace(), (String)this.speedKey.getKey()));
        if (old != null) {
            a.removeModifier(old);
        }
        if (Math.abs(amount = this.attackSpeed(this.progress(p).level()) - this.s0) > 1.0E-4) {
            a.addTransientModifier(new AttributeModifier(this.speedKey, amount, AttributeModifier.Operation.ADD_NUMBER));
        }
    }

    public int maxLevel() {
        return this.max;
    }
}

