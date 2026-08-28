package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.mining.MiningCatalog;
import dev.icaro.foodtooltips.mining.MiningEntry;
import dev.icaro.foodtooltips.skills.SkillProgress;
import dev.icaro.foodtooltips.skills.SkillType;
import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;

public final class GeneralSkillService {
    private static final int MAX_LEVEL = 200;
    private static final int[] MINING_MILESTONE_THRESHOLDS = {25, 100, 250, 500, 1000};

    public SkillProgress progress(Player p, SkillType type) {
        int level = (Integer)p.getPersistentDataContainer().getOrDefault(this.key(type, "level"), PersistentDataType.INTEGER, 0);
        double xp = (Double)p.getPersistentDataContainer().getOrDefault(this.key(type, "xp"), PersistentDataType.DOUBLE, 0.0);
        return new SkillProgress(level, xp, level >= 200 ? 0.0 : this.required(level + 1));
    }

    public int addXp(Player p, SkillType type, double amount) {
        double xp;
        double needed;
        SkillProgress before = this.progress(p, type);
        if (before.level() >= 200 || amount <= 0.0) {
            return 0;
        }
        int level = before.level();
        for (xp = before.xp() + amount; level < 200 && !(xp < (needed = this.required(level + 1))); xp -= needed, ++level) {
        }
        if (level >= 200) {
            xp = 0.0;
        }
        p.getPersistentDataContainer().set(this.key(type, "level"), PersistentDataType.INTEGER, level);
        p.getPersistentDataContainer().set(this.key(type, "xp"), PersistentDataType.DOUBLE, xp);
        return level - before.level();
    }

    public void setLevel(Player p, SkillType type, int level) {
        p.getPersistentDataContainer().set(this.key(type, "level"), PersistentDataType.INTEGER, Math.max(0, Math.min(200, level)));
        p.getPersistentDataContainer().set(this.key(type, "xp"), PersistentDataType.DOUBLE, 0.0);
    }

    public double required(int level) {
        return Math.max(50L, Math.round(50.0 * Math.pow(level, 1.55)));
    }

    public int maxLevel() {
        return 200;
    }

    public int fortune(Player player, SkillType type) {
        return switch (type) {
            case SkillType.MINING, SkillType.FARMING, SkillType.FORAGING -> this.progress(player, type).level() * 4;
            default -> 0;
        };
    }

    public int miningSpeed(Player player, Material tool) {
        return this.baseMiningSpeed(tool) + this.progress(player, SkillType.MINING).level();
    }

    public int baseMiningSpeed(Material tool) {
        return switch (tool) {
            case Material.WOODEN_PICKAXE -> 70;
            case Material.STONE_PICKAXE -> 100;
            case Material.IRON_PICKAXE -> 130;
            case Material.DIAMOND_PICKAXE -> 160;
            case Material.NETHERITE_PICKAXE -> 180;
            case Material.GOLDEN_PICKAXE -> 250;
            default -> tool.name().endsWith("_PICKAXE") ? 100 : 0;
        };
    }

    public MiningRecord recordMined(Player p, Material block) {
        int before = this.miningMilestones(p, block);
        NamespacedKey k = this.minedKey(block);
        int count = (Integer)p.getPersistentDataContainer().getOrDefault(k, PersistentDataType.INTEGER, 0) + 1;
        p.getPersistentDataContainer().set(k, PersistentDataType.INTEGER, count);
        MiningCatalog.find(block).ifPresent(e -> {
            if (ThreadLocalRandom.current().nextInt(100) < 20) {
                this.depositMineralDust(p, Math.max(1L, Math.round(e.skillXp() / 20.0)));
            }
        });
        int after = this.miningMilestones(p, block);
        if (after > before) {
            this.depositMineralDust(p, (long)after * 50L);
        }
        return new MiningRecord(count, this.recordCommission(p, block));
    }

    public long mineralDust(Player p) {
        return (Long)p.getPersistentDataContainer().getOrDefault(new NamespacedKey("foodtooltips", "mineral_dust"), PersistentDataType.LONG, 0L);
    }

    public void depositMineralDust(Player p, long amount) {
        if (amount > 0L) {
            p.getPersistentDataContainer().set(new NamespacedKey("foodtooltips", "mineral_dust"), PersistentDataType.LONG, Math.max(0L, this.mineralDust(p) + amount));
        }
    }

    public int mined(Player p, Material block) {
        return (Integer)p.getPersistentDataContainer().getOrDefault(this.minedKey(block), PersistentDataType.INTEGER, 0);
    }

    public int miningMilestones(Player p, Material block) {
        int count = this.mined(p, block);
        int done = 0;
        for (int n : MINING_MILESTONE_THRESHOLDS) {
            if (count < n) continue;
            ++done;
        }
        return done;
    }

    /** How many milestone tiers a single ore/block type can reach (used to size Global Level's max-achievable-level estimate). */
    public static int maxMiningMilestonesPerBlock() {
        return MINING_MILESTONE_THRESHOLDS.length;
    }

    public double carefulChance(Player p) {
        return Math.min(25.0, (double)this.progress(p, SkillType.MINING).level() * 0.05 + (double)this.totalMiningMilestones(p) * 0.5);
    }

    public int totalMiningMilestones(Player p) {
        int n = 0;
        for (MiningEntry e : MiningCatalog.entries()) {
            n += this.miningMilestones(p, e.block());
        }
        return n;
    }

    public Material commissionTarget(Player p) {
        List<MiningEntry> list = MiningCatalog.entries();
        int index = Math.floorMod(LocalDate.now().toEpochDay() + (long)p.getUniqueId().hashCode(), list.size());
        return list.get(index).block();
    }

    public int commissionProgress(Player p) {
        PersistentDataContainer d;
        String day = LocalDate.now().toString();
        if (!day.equals((d = p.getPersistentDataContainer()).get(this.key(SkillType.MINING, "commission_day"), PersistentDataType.STRING))) {
            return 0;
        }
        return (Integer)d.getOrDefault(this.key(SkillType.MINING, "commission_count"), PersistentDataType.INTEGER, 0);
    }

    public int commissionGoal(Player p) {
        return 100;
    }

    private boolean recordCommission(Player p, Material block) {
        int before;
        if (block != this.commissionTarget(p)) {
            return false;
        }
        PersistentDataContainer d = p.getPersistentDataContainer();
        NamespacedKey dayKey = this.key(SkillType.MINING, "commission_day");
        NamespacedKey countKey = this.key(SkillType.MINING, "commission_count");
        String day = LocalDate.now().toString();
        if (!day.equals(d.get(dayKey, PersistentDataType.STRING))) {
            d.set(dayKey, PersistentDataType.STRING, day);
            d.set(countKey, PersistentDataType.INTEGER, 0);
        }
        if ((before = ((Integer)d.getOrDefault(countKey, PersistentDataType.INTEGER, 0)).intValue()) >= this.commissionGoal(p)) {
            return false;
        }
        int after = before + 1;
        d.set(countKey, PersistentDataType.INTEGER, after);
        if (after == this.commissionGoal(p)) {
            this.depositMineralDust(p, 250L);
            return true;
        }
        return false;
    }

    private NamespacedKey minedKey(Material block) {
        return new NamespacedKey("foodtooltips", "mined_" + block.name().toLowerCase(Locale.ROOT));
    }

    private NamespacedKey key(SkillType type, String field) {
        return new NamespacedKey("foodtooltips", type.name().toLowerCase(Locale.ROOT) + "_" + field);
    }

    public record MiningRecord(int count, boolean commissionCompleted) {
    }
}

