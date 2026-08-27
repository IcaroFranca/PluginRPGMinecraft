package dev.icaro.foodtooltips.bestiary;

import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import net.kyori.adventure.key.Key;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class BestiaryProgressService {
    private static final int[] STEP_KILLS = new int[]{20, 40, 80, 120, 200, 300, 500, 750, 1000, 1500, 2000, 3000, 5000, 7500, 10000};
    private static final int[] BOSS_STEP_KILLS = new int[]{5, 10, 10, 10, 15};
    private final Plugin plugin;
    private final NamespacedKey healthKey;

    public BestiaryProgressService(Plugin plugin) {
        this.plugin = plugin;
        this.healthKey = new NamespacedKey("foodtooltips", "bestiary_health");
    }

    public int kills(Player player, EntityType type) {
        return (Integer)player.getPersistentDataContainer().getOrDefault(this.killKey(type), PersistentDataType.INTEGER, 0);
    }

    public MilestoneUpdate recordKill(Player player, EntityType type) {
        int beforeKills = this.kills(player, type);
        int beforeMilestones = this.achieved(type, beforeKills);
        int afterKills = this.isBoss(type) ? Math.min(50, beforeKills + 1) : beforeKills + 1;
        player.getPersistentDataContainer().set(this.killKey(type), PersistentDataType.INTEGER, afterKills);
        int afterMilestones = this.achieved(type, afterKills);
        this.applyBonusHealth(player);
        return new MilestoneUpdate(afterKills, beforeMilestones, afterMilestones);
    }

    public int achieved(Player player, EntityType type) {
        return this.achieved(type, this.kills(player, type));
    }

    public int achieved(EntityType type, int kills) {
        int count = 0;
        int consumed = 0;
        for (int step : this.steps(type)) {
            if (kills < consumed + step) break;
            consumed += step;
            ++count;
        }
        return count;
    }

    public int totalMilestones(Player player) {
        return BestiaryCatalog.entries().stream().mapToInt(entry -> this.achieved(player, entry.type())).sum();
    }

    public int bonusHealth(Player player) {
        return this.totalMilestones(player) / 10 * 2;
    }

    public double damageBonus(Player player, EntityType type) {
        return (double)this.bonusPoints(this.achieved(player, type), true) / 100.0;
    }

    public double lootBonus(Player player, EntityType type) {
        return (double)this.bonusPoints(this.achieved(player, type), false) / 100.0;
    }

    public int maxMilestones(EntityType type) {
        return this.steps(type).length;
    }

    public int nextStepKills(EntityType type, int achieved) {
        int[] steps = this.steps(type);
        if (achieved < steps.length) {
            return steps[achieved];
        }
        if (this.isBoss(type)) {
            return 0;
        }
        return STEP_KILLS[STEP_KILLS.length - 1] + (achieved - STEP_KILLS.length + 1) * 5000;
    }

    public int startOfStep(EntityType type, int achieved) {
        int i;
        int[] steps = this.steps(type);
        int total = 0;
        for (i = 0; i < Math.min(achieved, steps.length); ++i) {
            total += steps[i];
        }
        if (achieved > STEP_KILLS.length) {
            for (i = STEP_KILLS.length; i < achieved; ++i) {
                total += this.nextStepKills(type, i);
            }
        }
        return total;
    }

    public String reward(int milestone, boolean pt) {
        boolean damage;
        int amount = this.rewardAmount(milestone);
        boolean bl = damage = milestone % 2 == 1;
        if (damage) {
            return "+" + amount + "% " + (pt ? "de dano contra este mob" : "damage against this mob");
        }
        return "+" + amount + "% " + (pt ? "de multiplicador de loot" : "loot multiplier");
    }

    public void applyBonusHealth(Player player) {
        int bonus;
        AttributeInstance attribute = player.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        AttributeModifier old = attribute.getModifier(Key.key((String)this.healthKey.getNamespace(), (String)this.healthKey.getKey()));
        if (old != null) {
            attribute.removeModifier(old);
        }
        if ((bonus = this.bonusHealth(player)) > 0) {
            attribute.addTransientModifier(new AttributeModifier(this.healthKey, (double)bonus, AttributeModifier.Operation.ADD_NUMBER));
        }
        player.setHealthScaled(true);
        player.setHealthScale(20.0);
        if (player.getHealth() > attribute.getValue()) {
            player.setHealth(attribute.getValue());
        }
    }

    private int bonusPoints(int milestones, boolean damage) {
        int points = 0;
        for (int i = 1; i <= milestones; ++i) {
            if (i % 2 == 1 != damage) continue;
            points += this.rewardAmount(i);
        }
        return points;
    }

    private int rewardAmount(int milestone) {
        return 1 + (milestone - 1) / 4;
    }

    private boolean isBoss(EntityType type) {
        return type == EntityType.WARDEN || type == EntityType.WITHER || type == EntityType.ENDER_DRAGON;
    }

    private int[] steps(EntityType type) {
        return this.isBoss(type) ? BOSS_STEP_KILLS : STEP_KILLS;
    }

    private NamespacedKey killKey(EntityType type) {
        return new NamespacedKey("foodtooltips", "kills_" + type.key().value());
    }

    public record MilestoneUpdate(int kills, int before, int after) {
        public boolean unlocked() {
            return this.after > this.before;
        }
    }
}

