package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.global.GlobalLevelRules;
import dev.icaro.foodtooltips.global.GlobalLevelSnapshot;
import dev.icaro.foodtooltips.global.GlobalSkill;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.SkillType;
import java.util.Locale;
import java.util.function.Consumer;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class GlobalLevelService {
    public static final int MIGRATION_VERSION = 1;
    private final Plugin plugin;
    private final CombatSkillService combat;
    private final GeneralSkillService general;
    private final BestiaryProgressService bestiary;
    private final NamespacedKey xpKey;
    private final NamespacedKey migrationKey;
    private final NamespacedKey healthKey;
    private final long xpPerLevel;
    private final long milestoneXp;
    private final double hpPerLevel;
    private final double strengthPercent;
    private final int levelsPerStrength;
    private final int strengthPerGroup;
    private final long[] skillRewards;
    private final int telekinesisLevel;
    private final double telekinesisRadiusBlocks;
    private Consumer<Player> changeListener = p -> {};

    public GlobalLevelService(Plugin plugin, CombatSkillService combat, GeneralSkillService general, BestiaryProgressService bestiary) {
        this.plugin = plugin;
        this.combat = combat;
        this.general = general;
        this.bestiary = bestiary;
        this.xpKey = this.key("global_xp");
        this.migrationKey = this.key("global_migration_version");
        this.healthKey = this.key("global_level_health");
        this.xpPerLevel = Math.max(1L, plugin.getConfig().getLong("global-level.xp-per-level", 100L));
        this.milestoneXp = Math.max(0L, plugin.getConfig().getLong("global-level.milestone-xp", 4L));
        this.hpPerLevel = Math.max(0.0, plugin.getConfig().getDouble("global-level.hp-per-level", 5.0));
        this.levelsPerStrength = Math.max(1, plugin.getConfig().getInt("global-level.levels-per-strength", 5));
        this.strengthPerGroup = Math.max(0, plugin.getConfig().getInt("global-level.strength-per-global-level-group", 1));
        this.strengthPercent = Math.max(0.0, plugin.getConfig().getDouble("global-level.strength-damage-percent-per-point", 1.0));
        this.skillRewards = new long[]{this.reward("level-1-10", 5L), this.reward("level-11-25", 10L), this.reward("level-26-50", 20L), this.reward("level-51-60", 30L), this.reward("level-61-100", 40L), this.reward("level-101-150", 50L), this.reward("level-151-200", 60L)};
        this.telekinesisLevel = Math.max(1, plugin.getConfig().getInt("global-level.telekinesis-level", 3));
        this.telekinesisRadiusBlocks = Math.max(0.0, plugin.getConfig().getDouble("global-level.telekinesis-radius", 3.0));
    }

    /** Global Level required to unlock Telekinesis, the universal "drops come to you" perk. */
    public int telekinesisRequiredLevel() {
        return this.telekinesisLevel;
    }

    public boolean telekinesisUnlocked(Player p) {
        return this.snapshot(p).level() >= this.telekinesisLevel;
    }

    /** Blocks swept for nearby loose drops once Telekinesis is unlocked; 0 if not yet unlocked. */
    public double telekinesisRadius(Player p) {
        return this.telekinesisUnlocked(p) ? this.telekinesisRadiusBlocks : 0.0;
    }

    public void onChange(Consumer<Player> listener) {
        this.changeListener = listener == null ? p -> {} : listener;
    }

    public GlobalLevelSnapshot snapshot(Player p) {
        long total = this.totalXp(p);
        long level = GlobalLevelRules.level(total, this.xpPerLevel);
        return new GlobalLevelSnapshot(total, level, GlobalLevelRules.progress(total, this.xpPerLevel), this.xpPerLevel, (double)level * this.hpPerLevel, level / (long)this.levelsPerStrength * (long)this.strengthPerGroup);
    }

    public long totalXp(Player p) {
        return (Long)p.getPersistentDataContainer().getOrDefault(this.xpKey, PersistentDataType.LONG, 0L);
    }

    public double strengthMultiplier(Player p) {
        return 1.0 + (double)this.snapshot(p).strength() * this.strengthPercent / 100.0;
    }

    public long addGlobalXp(Player p, long amount, GlobalXpSource source) {
        long after;
        if (amount <= 0L) {
            return 0L;
        }
        long before = this.totalXp(p);
        try {
            after = Math.addExact(before, amount);
        }
        catch (ArithmeticException ex) {
            after = Long.MAX_VALUE;
        }
        this.setRaw(p, after);
        after = this.totalXp(p);
        this.finishChange(p, before, after, source, true);
        return after - before;
    }

    public void setGlobalXp(Player p, long amount, GlobalXpSource source) {
        long before = this.totalXp(p);
        this.setRaw(p, Math.max(0L, amount));
        this.finishChange(p, before, this.totalXp(p), source, true);
    }

    public void removeGlobalXp(Player p, long amount, GlobalXpSource source) {
        if (amount < 0L) {
            throw new IllegalArgumentException("negative amount");
        }
        this.setGlobalXp(p, Math.max(0L, this.totalXp(p) - Math.min(this.totalXp(p), amount)), source);
    }

    public long creditSkillLevels(Player p, GlobalSkill skill, int oldLevel, int newLevel) {
        int checkpoint = this.skillCheckpoint(p, skill);
        int first = Math.max(oldLevel + 1, checkpoint + 1);
        long amount = newLevel >= first ? GlobalLevelRules.skillReward(first, newLevel, this.skillRewards) : 0L;
        this.setSkillCheckpoint(p, skill, Math.max(checkpoint, newLevel));
        if (amount > 0L) {
            this.addGlobalXp(p, amount, GlobalXpSource.SKILL);
        }
        return amount;
    }

    public void administrativeSkillLevel(Player p, GlobalSkill skill, int level) {
        this.setSkillCheckpoint(p, skill, Math.max(this.skillCheckpoint(p, skill), level));
    }

    public long creditMilestones(Player p, String category, int current, GlobalXpSource source) {
        int checkpoint = this.milestoneCheckpoint(p, category);
        int fresh = Math.max(0, current - checkpoint);
        this.setMilestoneCheckpoint(p, category, Math.max(checkpoint, current));
        long amount = Math.multiplyExact((long)fresh, this.milestoneXp);
        if (amount > 0L) {
            this.addGlobalXp(p, amount, source);
        }
        return amount;
    }

    public long migrate(Player p) {
        if ((Integer)p.getPersistentDataContainer().getOrDefault(this.migrationKey, PersistentDataType.INTEGER, 0) >= 1) {
            this.reconcile(p);
            this.applyHealth(p);
            return 0L;
        }
        long amount = 0L;
        int combatLevel = this.combat.progress(p).level();
        amount = Math.addExact(amount, GlobalLevelRules.skillReward(1, combatLevel, this.skillRewards));
        this.setSkillCheckpoint(p, GlobalSkill.COMBAT, combatLevel);
        for (SkillType type : SkillType.values()) {
            int level = this.general.progress(p, type).level();
            amount = Math.addExact(amount, GlobalLevelRules.skillReward(1, level, this.skillRewards));
            this.setSkillCheckpoint(p, GlobalSkill.of(type), level);
        }
        int bestiaryCount = this.bestiary.totalMilestones(p);
        int miningCount = this.general.totalMiningMilestones(p);
        this.setMilestoneCheckpoint(p, "bestiary", bestiaryCount);
        this.setMilestoneCheckpoint(p, "mining", miningCount);
        amount = Math.addExact(amount, Math.multiplyExact((long)bestiaryCount + (long)miningCount, this.milestoneXp));
        long before = this.totalXp(p);
        if (amount > 0L) {
            this.setRaw(p, this.safeAdd(before, amount));
        }
        p.getPersistentDataContainer().set(this.migrationKey, PersistentDataType.INTEGER, 1);
        this.finishChange(p, before, this.totalXp(p), GlobalXpSource.MIGRATION, false);
        if (amount > 0L) {
            Language l = Language.of(p);
            p.sendMessage((Component)Component.text((String)("\u2726 " + l.choose("Migra\u00e7\u00e3o do N\u00edvel Global conclu\u00edda: +", "Global Level migration complete: +") + amount + " XP \u2022 " + l.choose("N\u00edvel ", "Level ") + this.snapshot(p).level()), (TextColor)NamedTextColor.GOLD));
        }
        return amount;
    }

    public void reconcile(Player p) {
        this.creditSkillLevels(p, GlobalSkill.COMBAT, this.skillCheckpoint(p, GlobalSkill.COMBAT), this.combat.progress(p).level());
        for (SkillType type : SkillType.values()) {
            GlobalSkill skill = GlobalSkill.of(type);
            this.creditSkillLevels(p, skill, this.skillCheckpoint(p, skill), this.general.progress(p, type).level());
        }
        this.creditMilestones(p, "bestiary", this.bestiary.totalMilestones(p), GlobalXpSource.BESTIARY_MILESTONE);
        this.creditMilestones(p, "mining", this.general.totalMiningMilestones(p), GlobalXpSource.MINING_MILESTONE);
    }

    public void applyHealth(Player p) {
        double bonus;
        AttributeInstance attribute = p.getAttribute(Attribute.MAX_HEALTH);
        if (attribute == null) {
            return;
        }
        AttributeModifier old = attribute.getModifier(Key.key((String)this.healthKey.getNamespace(), (String)this.healthKey.getKey()));
        if (old != null) {
            attribute.removeModifier(old);
        }
        if ((bonus = this.snapshot(p).bonusHealth()) > 0.0) {
            attribute.addTransientModifier(new AttributeModifier(this.healthKey, bonus, AttributeModifier.Operation.ADD_NUMBER));
        }
        if (p.getHealth() > attribute.getValue()) {
            p.setHealth(attribute.getValue());
        }
    }

    private void finishChange(Player p, long beforeXp, long afterXp, GlobalXpSource source, boolean announce) {
        this.applyHealth(p);
        this.changeListener.accept(p);
        long before = GlobalLevelRules.level(beforeXp, this.xpPerLevel);
        long after = GlobalLevelRules.level(afterXp, this.xpPerLevel);
        if (announce && after > before) {
            this.levelUpMessage(p, before, after);
        }
    }

    private void levelUpMessage(Player p, long before, long after) {
        Language l = Language.of(p);
        long strengthBefore = before / (long)this.levelsPerStrength * (long)this.strengthPerGroup;
        long strengthAfter = after / (long)this.levelsPerStrength * (long)this.strengthPerGroup;
        p.sendMessage((Component)Component.text((String)"\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", (TextColor)NamedTextColor.DARK_GRAY));
        p.sendMessage((Component)Component.text((String)("\u2726 " + l.choose("N\u00cdVEL GLOBAL AUMENTOU!", "GLOBAL LEVEL UP!") + " \u2726"), (TextColor)NamedTextColor.GOLD));
        p.sendMessage((Component)Component.text((String)(before + " \u2192 " + after), (TextColor)NamedTextColor.GREEN));
        p.sendMessage((Component)Component.text((String)("+" + Math.round((double)(after - before) * this.hpPerLevel) + " " + l.choose("HP m\u00e1ximo", "max HP")), (TextColor)NamedTextColor.RED));
        if (strengthAfter > strengthBefore) {
            p.sendMessage((Component)Component.text((String)("+" + (strengthAfter - strengthBefore) + " Strength"), (TextColor)NamedTextColor.AQUA));
        }
        p.sendMessage((Component)Component.text((String)"\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", (TextColor)NamedTextColor.DARK_GRAY));
    }

    private int skillCheckpoint(Player p, GlobalSkill s) {
        return (Integer)p.getPersistentDataContainer().getOrDefault(this.key("global_skill_" + s.name().toLowerCase(Locale.ROOT)), PersistentDataType.INTEGER, 0);
    }

    private void setSkillCheckpoint(Player p, GlobalSkill s, int level) {
        p.getPersistentDataContainer().set(this.key("global_skill_" + s.name().toLowerCase(Locale.ROOT)), PersistentDataType.INTEGER, Math.max(0, level));
    }

    private int milestoneCheckpoint(Player p, String category) {
        return (Integer)p.getPersistentDataContainer().getOrDefault(this.key("global_milestone_" + category.toLowerCase(Locale.ROOT)), PersistentDataType.INTEGER, 0);
    }

    private void setMilestoneCheckpoint(Player p, String category, int value) {
        p.getPersistentDataContainer().set(this.key("global_milestone_" + category.toLowerCase(Locale.ROOT)), PersistentDataType.INTEGER, Math.max(0, value));
    }

    private void setRaw(Player p, long amount) {
        p.getPersistentDataContainer().set(this.xpKey, PersistentDataType.LONG, Math.max(0L, amount));
    }

    private long safeAdd(long a, long b) {
        try {
            return Math.addExact(a, b);
        }
        catch (ArithmeticException ex) {
            return Long.MAX_VALUE;
        }
    }

    private long reward(String range, long fallback) {
        return Math.max(0L, this.plugin.getConfig().getLong("global-level.skill-xp." + range, fallback));
    }

    private NamespacedKey key(String value) {
        return new NamespacedKey("foodtooltips", value);
    }
}

