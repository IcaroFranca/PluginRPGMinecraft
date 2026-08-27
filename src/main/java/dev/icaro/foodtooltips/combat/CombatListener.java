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
import dev.icaro.foodtooltips.skills.CombatTreeMath;
import dev.icaro.foodtooltips.skills.CombatValorService;
import dev.icaro.foodtooltips.skills.SkillProgressBarService;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Item;
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

public final class CombatListener implements Listener {
    private final Plugin plugin;
    private final CombatSkillService combat;
    private final MobVisualService visuals;
    private final BestiaryProgressService bestiary;
    private final SkillProgressBarService progressBar;
    private final CombatAbilityService abilities;
    private final EconomyService economy;
    private final GlobalLevelService global;
    private final PlayerStatsService stats;
    private final CombatValorService valor;
    private final Map<UUID, Long> secondWind = new HashMap<>();
    private final double critMultiplier;
    private final double hpXp;
    private final double levelXp;

    public CombatListener(Plugin p, CombatSkillService c, MobVisualService v, BestiaryProgressService b, SkillProgressBarService bar,
                           CombatAbilityService abilityService, EconomyService economyService, GlobalLevelService global,
                           PlayerStatsService stats, CombatValorService valor) {
        this.plugin = p;
        this.combat = c;
        this.visuals = v;
        this.bestiary = b;
        this.progressBar = bar;
        this.abilities = abilityService;
        this.economy = economyService;
        this.global = global;
        this.stats = stats;
        this.valor = valor;
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
            if (x instanceof LivingEntity l) {
                this.visuals.track(l);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void damage(EntityDamageByEntityEvent e) {
        Player p = this.attacker(e.getDamager());
        if (p == null || !(e.getEntity() instanceof LivingEntity target)) {
            return;
        }
        if (this.abilities.isMagicDamageInFlight(p)) {
            // Magic damage (Arcane Slash) bypasses melee multipliers entirely; just show feedback.
            if (!(target instanceof Player)) {
                this.visuals.track(target);
                this.visuals.damageNumber(target, e.getFinalDamage(), false);
            }
            return;
        }
        if (target instanceof Player) {
            e.setDamage(e.getDamage() * this.global.strengthMultiplier(p));
            return;
        }
        int level = this.combat.progress(p).level();
        double critChance = this.combat.critChance(level) + this.abilities.critChanceBonus(p);
        boolean skillCritical = ThreadLocalRandom.current().nextDouble(100.0) < critChance;
        boolean vanillaCritical = e.getDamager() == p && p.getFallDistance() > 0.0f && !p.isOnGround() && !p.isInWater() && !p.isClimbing() && !p.isSprinting() && p.getVehicle() == null;
        boolean critical = skillCritical || vanillaCritical || this.abilities.guaranteedCritical(p);
        double mobBonus = 1.0 + this.bestiary.damageBonus(p, target.getType());
        double damage = e.getDamage() * this.combat.damageMultiplier(level) * mobBonus * this.abilities.outgoingMultiplier(p, target)
                * this.global.strengthMultiplier(p) * (critical ? this.abilities.criticalMultiplier(p, this.critMultiplier) : 1.0);
        e.setDamage(damage);
        this.visuals.track(target);
        this.visuals.damageNumber(target, e.getFinalDamage(), critical);
        int extraHits = CombatTreeMath.extraHits(this.stats.stats(p).ferocity(), ThreadLocalRandom.current().nextDouble(100.0));
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.visuals.update(target);
            if (extraHits > 0 && target.isValid() && !target.isDead()) {
                double extraDamage = e.getFinalDamage();
                for (int i = 0; i < extraHits && !target.isDead(); i++) {
                    double newHealth = Math.max(0.0, target.getHealth() - extraDamage);
                    target.setHealth(newHealth);
                    this.visuals.damageNumber(target, extraDamage, false);
                }
            }
            if (this.abilities.enabled(p, CombatAbility.CLEAVE)) {
                double fraction = this.abilities.cleaveSplashFraction(p);
                int maxTargets = this.abilities.cleaveMaxTargets(p);
                double splash = e.getFinalDamage() * fraction;
                int hits = 0;
                for (Entity nearby : target.getNearbyEntities(3.0, 2.0, 3.0)) {
                    if (!(nearby instanceof Enemy enemy) || nearby == target) {
                        continue;
                    }
                    enemy.damage(splash, p);
                    this.visuals.damageNumber(enemy, splash, false);
                    if (++hits >= maxTargets) {
                        break;
                    }
                }
            }
        });
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
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
            long valorEarned = this.valor.mobValor(e.getEntity());
            this.valor.deposit(p, valorEarned);
            this.abilities.hostileKill(p);
            AttributeInstance a = e.getEntity().getAttribute(Attribute.MAX_HEALTH);
            double hp = a == null ? e.getEntity().getHealth() : a.getValue();
            double fallback = Math.max(1L, Math.round(Math.max(5.0, hp * this.hpXp + this.visuals.level(e.getEntity()) * this.levelXp) / 10.0));
            double xp = BestiaryCatalog.find(e.getEntityType()).map(entry -> (double) entry.awardedCombatXp()).orElse(fallback);
            int oldLevel = this.combat.progress(p).level();
            int levels = this.combat.addXp(p, xp);
            int newLevel = this.combat.progress(p).level();
            this.progressBar.showCombat(p, xp, this.combat.progress(p), this.combat.maxLevel());
            if (levels > 0) {
                long reward = this.global.creditSkillLevels(p, GlobalSkill.COMBAT, oldLevel, newLevel);
                long bonusValor = this.valor.levelUpValor(levels);
                if (bonusValor > 0L) {
                    this.valor.deposit(p, bonusValor);
                }
                this.levelUpMessage(p, oldLevel, newLevel, reward, bonusValor);
            }
        }
        if (this.abilities.enabled(p, CombatAbility.TELEKINESIS)) {
            this.collectDrops(p, e);
            double radius = this.abilities.telekinesisMagnetRadius(p);
            if (radius > 0.0) {
                this.sweepNearbyDrops(p, e.getEntity().getLocation(), radius);
            }
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void undyingWill(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p)) {
            return;
        }
        double reduction = this.abilities.undyingWillReduction(p);
        if (reduction <= 0.0) {
            return;
        }
        AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr == null ? 20.0 : maxHealthAttr.getValue();
        if (p.getHealth() > maxHealth * 0.5) {
            return;
        }
        e.setDamage(e.getDamage() * (1.0 - reduction));
    }

    @EventHandler(priority = EventPriority.HIGHEST, ignoreCancelled = true)
    public void secondWind(EntityDamageEvent e) {
        if (!(e.getEntity() instanceof Player p) || !this.abilities.enabled(p, CombatAbility.SECOND_WIND) || e.getFinalDamage() < p.getHealth()) {
            return;
        }
        long now = System.currentTimeMillis();
        if (this.secondWind.getOrDefault(p.getUniqueId(), 0L) > now) {
            return;
        }
        this.secondWind.put(p.getUniqueId(), now + this.abilities.secondWindCooldownMillis(p));
        e.setCancelled(true);
        double healFraction = this.abilities.secondWindHealFraction(p);
        AttributeInstance maxHealthAttr = p.getAttribute(Attribute.MAX_HEALTH);
        double maxHealth = maxHealthAttr == null ? 20.0 : maxHealthAttr.getValue();
        p.setHealth(Math.max(1.0, maxHealth * healFraction));
        p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 2));
        p.sendMessage(Component.text("✦ " + Language.of(p).choose("SEGUNDO FÔLEGO!", "SECOND WIND!") + " ✦", NamedTextColor.AQUA));
    }

    @EventHandler(priority = EventPriority.MONITOR, ignoreCancelled = true)
    public void playerHit(EntityDamageByEntityEvent e) {
        if (e.getEntity() instanceof Player p && this.hostile(e.getDamager())) {
            this.abilities.hostileHit(p);
        }
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.combat.applyAttackSpeed(e.getPlayer());
        this.stats.applySwingRange(e.getPlayer());
        this.bestiary.applyBonusHealth(e.getPlayer());
        this.visuals.track(e.getPlayer());
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
        ArrayList<ItemStack> originals = new ArrayList<>(e.getDrops());
        int guaranteed = (int) Math.floor(bonus);
        int chance = (int) Math.round((bonus - (double) guaranteed) * 100.0);
        for (ItemStack original : originals) {
            for (int i = 0; i < guaranteed; ++i) {
                e.getDrops().add(original.clone());
            }
            if (ThreadLocalRandom.current().nextInt(100) < chance) {
                e.getDrops().add(original.clone());
            }
        }
    }

    private void collectDrops(Player p, EntityDeathEvent e) {
        for (ItemStack drop : new ArrayList<>(e.getDrops())) {
            for (ItemStack overflow : p.getInventory().addItem(drop).values()) {
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

    /** Telekinesis: sweeps loose dropped items (not just the kill's own drops) within radius into the player's inventory. */
    private void sweepNearbyDrops(Player p, Location center, double radius) {
        if (center.getWorld() == null) {
            return;
        }
        for (Entity nearby : center.getWorld().getNearbyEntities(center, radius, radius, radius)) {
            if (!(nearby instanceof Item item) || !item.isValid()) {
                continue;
            }
            ItemStack stack = item.getItemStack();
            for (ItemStack overflow : p.getInventory().addItem(stack).values()) {
                p.getWorld().dropItemNaturally(p.getLocation(), overflow);
            }
            item.remove();
        }
    }

    private boolean hostile(Entity damager) {
        if (damager instanceof Enemy) {
            return true;
        }
        if (damager instanceof Projectile projectile) {
            return projectile.getShooter() instanceof Enemy;
        }
        return false;
    }

    private void milestoneMessage(Player p, EntityType type, int milestone, long globalXp) {
        Language l = Language.of(p);
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("✦ " + l.choose("MILESTONE DO BESTIÁRIO!", "BESTIARY MILESTONE!") + " ✦", NamedTextColor.GOLD));
        p.sendMessage(Component.text(type.key().value().replace('_', ' ') + " • Milestone " + milestone, NamedTextColor.YELLOW));
        p.sendMessage(Component.text(this.bestiary.reward(milestone, l == Language.PT), NamedTextColor.GREEN));
        p.sendMessage(Component.text("+" + globalXp + " " + l.choose("XP de Nível Global", "Global Level XP"), NamedTextColor.AQUA));
        if (this.bestiary.totalMilestones(p) % 10 == 0) {
            p.sendMessage(Component.text("❤ " + l.choose("Bônus global: +2 HP máximo", "Global bonus: +2 max HP"), NamedTextColor.RED));
        }
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private void levelUpMessage(Player p, int oldLevel, int newLevel, long globalXp, long bonusValor) {
        Language l = Language.of(p);
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
        p.sendMessage(Component.text("✦ " + l.choose("COMBATE SUBIU DE NÍVEL!", "COMBAT LEVEL UP!") + " ✦", NamedTextColor.GOLD));
        p.sendMessage(Component.text(oldLevel + " → " + newLevel, NamedTextColor.GREEN));
        p.sendMessage(Component.text("+" + (double) (newLevel - oldLevel) * 0.5 + "% Crit Chance • +" + (newLevel - oldLevel) * 4 + "% " + l.choose("Dano", "Damage"), NamedTextColor.AQUA));
        p.sendMessage(Component.text("+" + globalXp + " " + l.choose("XP de Nível Global", "Global Level XP"), NamedTextColor.AQUA));
        if (bonusValor > 0L) {
            p.sendMessage(Component.text("🩸 +" + this.valor.format(bonusValor) + " " + l.choose("Pontos de Sangue", "Blood Points"), NamedTextColor.DARK_RED));
        }
        p.sendMessage(Component.text("━━━━━━━━━━━━━━━━━━━━━━━━", NamedTextColor.DARK_GRAY));
    }

    private Player attacker(Entity e) {
        if (e instanceof Player p) {
            return p;
        }
        if (e instanceof Projectile projectile && projectile.getShooter() instanceof Player p) {
            return p;
        }
        return null;
    }
}
