package dev.icaro.foodtooltips.combat;

import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.combat.MobVisualService;
import dev.icaro.foodtooltips.economy.EconomyService;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalSkill;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatAbility;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import dev.icaro.foodtooltips.skills.SkillProgressBarService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

public final class CombatListener
implements Listener {
    private final Plugin plugin;
    private final CombatSkillService combat;
    private final MobVisualService visuals;
    private final BestiaryProgressService bestiary;
    private final SkillProgressBarService progressBar;
    private final CombatAbilityService abilities;
    private final EconomyService economy;
    private final GlobalLevelService global;
    private final Map<UUID, Long> secondWind = new HashMap<UUID, Long>();
    private final double critMultiplier;
    private final double hpXp;
    private final double levelXp;

    public CombatListener(Plugin p, CombatSkillService c, MobVisualService v, BestiaryProgressService b, SkillProgressBarService bar, CombatAbilityService abilityService, EconomyService economyService, GlobalLevelService global) {
        this.plugin = p;
        this.combat = c;
        this.visuals = v;
        this.bestiary = b;
        this.progressBar = bar;
        this.abilities = abilityService;
        this.economy = economyService;
        this.global = global;
        this.critMultiplier = p.getConfig().getDouble("combat.critical-damage-multiplier", 1.5);
        this.hpXp = p.getConfig().getDouble("combat.hostile-xp-health-multiplier", 2.0);
        this.levelXp = p.getConfig().getDouble("combat.hostile-xp-level-multiplier", 3.0);
    }

    @EventHandler
    public void spawn(CreatureSpawnEvent e) {
        Bukkit.getScheduler().runTask(this.plugin, () -> this.visuals.track(e.getEntity()));
    }

    @EventHandler
    public void chunk(ChunkLoadEvent e) {
        for (Entity x : e.getChunk().getEntities()) {
            if (!(x instanceof LivingEntity)) continue;
            LivingEntity l = (LivingEntity)x;
            this.visuals.track(l);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void damage(EntityDamageByEntityEvent e) {
        Entity entity;
        Player p = this.attacker(e.getDamager());
        if (p == null || !((entity = e.getEntity()) instanceof LivingEntity)) {
            return;
        }
        LivingEntity target = (LivingEntity)entity;
        if (target instanceof Player) {
            e.setDamage(e.getDamage() * this.global.strengthMultiplier(p));
            return;
        }
        int level = this.combat.progress(p).level();
        boolean skillCritical = ThreadLocalRandom.current().nextDouble(100.0) < this.combat.critChance(level);
        boolean vanillaCritical = e.getDamager() == p && p.getFallDistance() > 0.0f && !p.isOnGround() && !p.isInWater() && !p.isClimbing() && !p.isSprinting() && p.getVehicle() == null;
        boolean critical = skillCritical || vanillaCritical || this.abilities.guaranteedCritical(p);
        double mobBonus = 1.0 + this.bestiary.damageBonus(p, target.getType());
        double damage = e.getDamage() * this.combat.damageMultiplier(level) * mobBonus * this.abilities.outgoingMultiplier(p, target) * this.global.strengthMultiplier(p) * (critical ? (this.abilities.enabled(p, CombatAbility.CRITICAL_MASTERY) ? 1.75 : this.critMultiplier) : 1.0);
        e.setDamage(damage);
        this.visuals.track(target);
        this.visuals.damageNumber(target, e.getFinalDamage(), critical);
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.visuals.update(target);
            if (this.abilities.enabled(p, CombatAbility.CLEAVE)) {
                int hits = 0;
                for (Entity nearby : target.getNearbyEntities(3.0, 2.0, 3.0)) {
                    Enemy enemy;
                    if (!(nearby instanceof Enemy) || (enemy = (Enemy)nearby) == target) continue;
                    enemy.damage(e.getFinalDamage() * 0.2);
                    this.visuals.damageNumber((LivingEntity)enemy, e.getFinalDamage() * 0.2, false);
                    if (++hits < 3) continue;
                    break;
                }
            }
        });
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void death(EntityDeathEvent e) {
        Player p = e.getEntity().getKiller();
        if (p == null) {
            return;
        }
        BestiaryCatalog.find(e.getEntityType()).ifPresent(entry -> {
            BestiaryProgressService.MilestoneUpdate update = this.bestiary.recordKill(p, e.getEntityType());
            this.applyLootBonus(p, e);
            if (update.unlocked()) {
                long reward = this.global.creditMilestones(p, "bestiary", this.bestiary.totalMilestones(p), GlobalXpSource.BESTIARY_MILESTONE);
                this.milestoneMessage(p, e.getEntityType(), update.after(), reward);
            }
        });
        if (e.getEntity() instanceof Enemy) {
            int coins = this.economy.mobCoins(p, e.getEntity());
            this.economy.deposit(p, coins);
            this.abilities.hostileKill(p);
            AttributeInstance a = e.getEntity().getAttribute(Attribute.MAX_HEALTH);
            double hp = a == null ? e.getEntity().getHealth() : a.getValue();
            double fallback = Math.max(1L, Math.round(Math.max(5.0, hp * this.hpXp + (double)this.visuals.level(e.getEntity()) * this.levelXp) / 10.0));
            double xp = BestiaryCatalog.find(e.getEntityType()).map(entry -> entry.awardedCombatXp()).orElse(fallback);
            int oldLevel = this.combat.progress(p).level();
            int levels = this.combat.addXp(p, xp);
            int newLevel = this.combat.progress(p).level();
            this.progressBar.showCombat(p, xp, this.combat.progress(p), this.combat.maxLevel());
            if (levels > 0) {
                long reward = this.global.creditSkillLevels(p, GlobalSkill.COMBAT, oldLevel, newLevel);
                this.levelUpMessage(p, oldLevel, newLevel, reward);
            }
        }
        if (this.abilities.enabled(p, CombatAbility.TELEKINESIS)) {
            this.collectDrops(p, e);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void secondWind(EntityDamageEvent e) {
        Player p;
        Entity entity = e.getEntity();
        if (!(entity instanceof Player) || !this.abilities.enabled(p = (Player)entity, CombatAbility.SECOND_WIND) || e.getFinalDamage() < p.getHealth()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.secondWind.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        this.secondWind.put(p.getUniqueId(), now + 180000L);
        e.setCancelled(true);
        p.setHealth(Math.max(1.0, p.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.3));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2));
        p.sendMessage((Component)Component.text((String)("\u2726 " + Language.of(p).choose("SEGUNDO F\u00d4LEGO!", "SECOND WIND!") + " \u2726"), (TextColor)NamedTextColor.AQUA));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void playerHit(EntityDamageByEntityEvent e) {
        Entity entity = e.getEntity();
        if (entity instanceof Player) {
            Player p = (Player)entity;
            if (this.hostile(e.getDamager())) {
                this.abilities.hostileHit(p);
            }
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.combat.applyAttackSpeed(e.getPlayer());
        this.bestiary.applyBonusHealth(e.getPlayer());
        this.visuals.track((LivingEntity)e.getPlayer());
        this.economy.updateBoard(e.getPlayer());
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.progressBar.remove(e.getPlayer());
        this.abilities.clear(e.getPlayer());
        this.secondWind.remove(e.getPlayer().getUniqueId());
        this.economy.clearBoard(e.getPlayer());
    }

    private void applyLootBonus(Player p, EntityDeathEvent e) {
        double bonus = this.bestiary.lootBonus(p, e.getEntityType());
        if (bonus <= 0.0) {
            return;
        }
        ArrayList originals = new ArrayList(e.getDrops());
        int guaranteed = (int)Math.floor(bonus);
        int chance = (int)Math.round((bonus - (double)guaranteed) * 100.0);
        for (ItemStack original : originals) {
            for (int i = 0; i < guaranteed; ++i) {
                e.getDrops().add(original.clone());
            }
            if (ThreadLocalRandom.current().nextInt(100) >= chance) continue;
            e.getDrops().add(original.clone());
        }
    }

    private void collectDrops(Player p, EntityDeathEvent e) {
        for (ItemStack drop : new ArrayList(e.getDrops())) {
            for (ItemStack overflow : p.getInventory().addItem(new ItemStack[]{drop}).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
        }
        e.getDrops().clear();
        int xp = e.getDroppedExp();
        if (xp > 0) {
            p.giveExp(xp, true);
            e.setDroppedExp(0);
        }
    }

    private boolean hostile(Entity damager) {
        if (damager instanceof Enemy) {
            return true;
        }
        if (damager instanceof Projectile) {
            Projectile projectile = (Projectile)damager;
            return projectile.getShooter() instanceof Enemy;
        }
        return false;
    }

    private void milestoneMessage(Player p, EntityType type, int milestone, long globalXp) {
        Language l = Language.of(p);
        p.sendMessage((Component)Component.text((String)"\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", (TextColor)NamedTextColor.DARK_GRAY));
        p.sendMessage((Component)Component.text((String)("\u2726 " + l.choose("MILESTONE DO BESTI\u00c1RIO!", "BESTIARY MILESTONE!") + " \u2726"), (TextColor)NamedTextColor.GOLD));
        p.sendMessage((Component)Component.text((String)(type.key().value().replace('_', ' ') + " \u2022 Milestone " + milestone), (TextColor)NamedTextColor.YELLOW));
        p.sendMessage((Component)Component.text((String)this.bestiary.reward(milestone, l == Language.PT), (TextColor)NamedTextColor.GREEN));
        p.sendMessage((Component)Component.text((String)("+" + globalXp + " " + l.choose("XP de N\u00edvel Global", "Global Level XP")), (TextColor)NamedTextColor.AQUA));
        if (this.bestiary.totalMilestones(p) % 10 == 0) {
            p.sendMessage((Component)Component.text((String)("\u2764 " + l.choose("B\u00f4nus global: +2 HP m\u00e1ximo", "Global bonus: +2 max HP")), (TextColor)NamedTextColor.RED));
        }
        p.sendMessage((Component)Component.text((String)"\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", (TextColor)NamedTextColor.DARK_GRAY));
    }

    private void levelUpMessage(Player p, int oldLevel, int newLevel, long globalXp) {
        Language l = Language.of(p);
        p.sendMessage((Component)Component.text((String)"\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", (TextColor)NamedTextColor.DARK_GRAY));
        p.sendMessage((Component)Component.text((String)("\u2726 " + l.choose("COMBAT SUBIU DE N\u00cdVEL!", "COMBAT LEVEL UP!") + " \u2726"), (TextColor)NamedTextColor.GOLD));
        p.sendMessage((Component)Component.text((String)(oldLevel + " \u2192 " + newLevel), (TextColor)NamedTextColor.GREEN));
        p.sendMessage((Component)Component.text((String)("+" + (double)(newLevel - oldLevel) * 0.5 + "% Crit Chance \u2022 +" + (newLevel - oldLevel) * 4 + "% " + l.choose("Dano", "Damage")), (TextColor)NamedTextColor.AQUA));
        p.sendMessage((Component)Component.text((String)("+" + globalXp + " " + l.choose("XP de N\u00edvel Global", "Global Level XP")), (TextColor)NamedTextColor.AQUA));
        for (CombatAbility ability : CombatAbility.values()) {
            if (ability.level() <= oldLevel || ability.level() > newLevel) continue;
            p.sendMessage((Component)Component.text((String)(l.choose("DESBLOQUEADO: ", "UNLOCKED: ") + ability.name(l == Language.PT)), (TextColor)NamedTextColor.LIGHT_PURPLE));
        }
        p.sendMessage((Component)Component.text((String)"\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", (TextColor)NamedTextColor.DARK_GRAY));
    }

    private Player attacker(Entity e) {
        Projectile projectile;
        ProjectileSource s;
        if (e instanceof Player) {
            Player p = (Player)e;
            return p;
        }
        if (e instanceof Projectile && (s = (projectile = (Projectile)e).getShooter()) instanceof Player) {
            Player p = (Player)s;
            return p;
        }
        return null;
    }
}

