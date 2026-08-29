package dev.icaro.foodtooltips;

import dev.icaro.foodtooltips.bestiary.BestiaryListener;
import dev.icaro.foodtooltips.bestiary.BestiaryMenuService;
import dev.icaro.foodtooltips.bestiary.BestiaryProgressService;
import dev.icaro.foodtooltips.combat.CombatListener;
import dev.icaro.foodtooltips.combat.MobVisualService;
import dev.icaro.foodtooltips.economy.EconomyService;
import dev.icaro.foodtooltips.food.FoodTooltipListener;
import dev.icaro.foodtooltips.food.FoodTooltipService;
import dev.icaro.foodtooltips.global.GlobalLevelCommand;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalPlayerListener;
import dev.icaro.foodtooltips.global.GlobalPresentationService;
import dev.icaro.foodtooltips.global.LevelBadgeRenderer;
import dev.icaro.foodtooltips.global.LevelColorCommand;
import dev.icaro.foodtooltips.global.LevelColorMenuService;
import dev.icaro.foodtooltips.global.LevelColorService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTierListener;
import dev.icaro.foodtooltips.item.ItemTierService;
import dev.icaro.foodtooltips.mining.GemService;
import dev.icaro.foodtooltips.mining.MiningMenuListener;
import dev.icaro.foodtooltips.mining.MiningMenuService;
import dev.icaro.foodtooltips.skills.ArmorDefenseListener;
import dev.icaro.foodtooltips.skills.ArmorDefenseService;
import dev.icaro.foodtooltips.skills.BackpackListener;
import dev.icaro.foodtooltips.skills.BackpackService;
import dev.icaro.foodtooltips.skills.BedrockSwordThrowListener;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import dev.icaro.foodtooltips.skills.CombatTreeListener;
import dev.icaro.foodtooltips.skills.CombatTreeMenuService;
import dev.icaro.foodtooltips.skills.CombatValorService;
import dev.icaro.foodtooltips.skills.GeneralSkillListener;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.SetSkillLevelCommand;
import dev.icaro.foodtooltips.skills.SkillProgressBarService;
import dev.icaro.foodtooltips.skills.SkillsListener;
import dev.icaro.foodtooltips.skills.SkillsMenuService;
import dev.icaro.foodtooltips.skills.SwordThrowListener;
import dev.icaro.foodtooltips.stats.PlayerStats;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import dev.icaro.foodtooltips.stats.StatsHudService;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.World;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.Listener;
import org.bukkit.plugin.Plugin;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

public final class FoodTooltipsPlugin
extends JavaPlugin {
    private MobVisualService visuals;
    private SkillProgressBarService progressBar;
    private BackpackService backpacks;

    public void onEnable() {
        this.saveDefaultConfig();
        if (this.getConfig().getInt("combat.max-level", 50) < 200) {
            this.getConfig().set("combat.max-level", 200);
            this.saveConfig();
        }
        PlayerStatsService stats = new PlayerStatsService((Plugin)this);
        CombatSkillService combat = new CombatSkillService((Plugin)this);
        GeneralSkillService general = new GeneralSkillService();
        CombatValorService valor = new CombatValorService((Plugin)this);
        ArmorDefenseService armor = new ArmorDefenseService();
        ItemTierService tiers = new ItemTierService((Plugin)this);
        CombatAbilityService abilities = new CombatAbilityService((Plugin)this, combat, stats, valor);
        stats.abilities(abilities);
        EconomyService economy = new EconomyService((Plugin)this, abilities);
        BestiaryProgressService bestiaryProgress = new BestiaryProgressService((Plugin)this);
        GemService gems = new GemService((Plugin)this);
        MiningMenuService mining = new MiningMenuService(gems);
        GlobalLevelService global = new GlobalLevelService((Plugin)this, combat, general, bestiaryProgress);
        stats.global(global);
        SkillsMenuService menus = new SkillsMenuService(combat, general, stats, abilities, mining, global, armor);
        this.backpacks = new BackpackService((Plugin)this, combat, general, abilities);
        menus.backpacks(this.backpacks);
        BestiaryMenuService bestiary = new BestiaryMenuService(bestiaryProgress, economy, valor);
        this.progressBar = new SkillProgressBarService((Plugin)this);
        this.visuals = new MobVisualService((Plugin)this);
        LevelColorService levelColors = new LevelColorService((Plugin)this, global);
        LevelBadgeRenderer badgeRenderer = new LevelBadgeRenderer(this.getConfig().getInt("global-level.badge-animation-smoothness", 4));
        GlobalPresentationService presentation = new GlobalPresentationService((Plugin)this, global, levelColors, badgeRenderer);
        LevelColorMenuService levelColorMenu = new LevelColorMenuService(global, levelColors, badgeRenderer, presentation, menus::openMain);
        menus.levelColors(levelColorMenu);
        CombatTreeMenuService treeMenu = new CombatTreeMenuService(combat, abilities, valor, menus::openMain);
        menus.tree(treeMenu);
        global.onChange(p -> {
            presentation.refresh((Player)p);
            presentation.refreshAll();
        });
        levelColors.onChange(p -> presentation.refreshAll());
        PluginManager pm = this.getServer().getPluginManager();
        pm.registerEvents((Listener)new GlobalPlayerListener(global), (Plugin)this);
        pm.registerEvents((Listener)presentation, (Plugin)this);
        pm.registerEvents((Listener)levelColorMenu, (Plugin)this);
        pm.registerEvents((Listener)new SkillsListener(menus), (Plugin)this);
        pm.registerEvents((Listener)new CombatTreeListener(treeMenu), (Plugin)this);
        pm.registerEvents((Listener)new BackpackListener(this.backpacks, menus), (Plugin)this);
        pm.registerEvents((Listener)new GeneralSkillListener((Plugin)this, general, this.progressBar, global), (Plugin)this);
        pm.registerEvents((Listener)gems, (Plugin)this);
        pm.registerEvents((Listener)new MiningMenuListener(mining, menus, gems), (Plugin)this);
        pm.registerEvents((Listener)new BestiaryListener(bestiary), (Plugin)this);
        CombatListener combatListener = new CombatListener((Plugin)this, combat, this.visuals, bestiaryProgress, this.progressBar, abilities, economy, global, stats, valor, armor);
        pm.registerEvents((Listener)combatListener, (Plugin)this);
        pm.registerEvents((Listener)new ArmorDefenseListener(armor), (Plugin)this);
        pm.registerEvents((Listener)new ItemTierListener(tiers), (Plugin)this);
        SwordThrowListener swordThrow = new SwordThrowListener((Plugin)this, abilities);
        pm.registerEvents((Listener)swordThrow, (Plugin)this);
        pm.registerEvents((Listener)new BedrockSwordThrowListener(swordThrow), (Plugin)this);
        FoodTooltipListener foodListener = new FoodTooltipListener((Plugin)this, new FoodTooltipService());
        pm.registerEvents((Listener)foodListener, (Plugin)this);
        SetSkillLevelCommand setSkill = new SetSkillLevelCommand(combat, general, global);
        this.getCommand("setskilllevel").setExecutor((CommandExecutor)setSkill);
        this.getCommand("setskilllevel").setTabCompleter((TabCompleter)setSkill);
        GlobalLevelCommand globalCommand = new GlobalLevelCommand(global);
        this.getCommand("nivelglobal").setExecutor((CommandExecutor)globalCommand);
        this.getCommand("nivelglobal").setTabCompleter((TabCompleter)globalCommand);
        this.getCommand("globalxp").setExecutor((CommandExecutor)globalCommand);
        this.getCommand("globalxp").setTabCompleter((TabCompleter)globalCommand);
        LevelColorCommand levelColorCommand = new LevelColorCommand(levelColors, levelColorMenu);
        this.getCommand("levelcolor").setExecutor((CommandExecutor)levelColorCommand);
        this.getCommand("levelcolor").setTabCompleter((TabCompleter)levelColorCommand);
        this.getCommand("coins").setExecutor((s, c, l, a) -> {
            if (a.length == 0 && s instanceof Player) {
                Player p = (Player)s;
                s.sendMessage((Component)Component.text((String)(this.text(s, "Saldo: ", "Balance: ") + economy.format(economy.balance(p)) + " \u26c3"), (TextColor)NamedTextColor.GOLD));
                return true;
            }
            if (a.length != 3 || !s.hasPermission("foodtooltips.admin")) {
                s.sendMessage((Component)Component.text((String)(this.text(s, "Uso: ", "Usage: ") + "/coins <player> <set|give> <amount>"), (TextColor)NamedTextColor.RED));
                return true;
            }
            Player target = Bukkit.getPlayerExact((String)a[0]);
            if (target == null) {
                s.sendMessage((Component)Component.text((String)this.text(s, "Jogador n\u00e3o encontrado.", "Player not found."), (TextColor)NamedTextColor.RED));
                return true;
            }
            try {
                long amount = Long.parseLong(a[2]);
                if (a[1].equalsIgnoreCase("set")) {
                    economy.setBalance(target, amount);
                } else if (a[1].equalsIgnoreCase("give")) {
                    economy.deposit(target, amount);
                } else {
                    throw new IllegalArgumentException();
                }
                s.sendMessage((Component)Component.text((String)(this.text(s, "Saldo de ", "Balance of ") + target.getName() + ": " + economy.format(economy.balance(target)) + " \u26c3"), (TextColor)NamedTextColor.GREEN));
            }
            catch (IllegalArgumentException ex) {
                s.sendMessage((Component)Component.text((String)(this.text(s, "Uso: ", "Usage: ") + "/coins <player> <set|give> <amount>"), (TextColor)NamedTextColor.RED));
            }
            return true;
        });
        this.getCommand("skills").setExecutor((s, c, l, a) -> {
            if (s instanceof Player) {
                Player p = (Player)s;
                menus.openMain(p);
            } else {
                s.sendMessage((Component)Component.text((String)"Only players."));
            }
            return true;
        });
        this.getCommand("bestiary").setExecutor((s, c, l, a) -> {
            if (s instanceof Player) {
                Player p = (Player)s;
                bestiary.openCategories(p);
            } else {
                s.sendMessage((Component)Component.text((String)"Only players."));
            }
            return true;
        });
        this.getCommand("rpgstats").setExecutor((s, c, l, a) -> {
            if (s instanceof Player) {
                Player p = (Player)s;
                PlayerStats x = stats.stats(p);
                s.sendMessage((Component)Component.text((String)(this.text(s, "Vida ", "Health ") + Math.round(x.health()) + "/" + Math.round(x.maxHealth()) + ", Mana " + Math.round(x.mana()) + "/" + Math.round(x.maxMana()) + ", Strength " + x.strength() + ", " + this.text(s, "milestones do besti\u00e1rio ", "bestiary milestones ") + bestiaryProgress.totalMilestones(p))));
            }
            return true;
        });
        StatsHudService hud = new StatsHudService(this.getConfig().getString("hud.spacing", "     "));
        long ticks = Math.max(1L, this.getConfig().getLong("hud.update-ticks", 5L));
        double manaRegen = this.getConfig().getDouble("stats.mana-regeneration-per-second", 2.0) * (double)ticks / 20.0;
        double vitalityRegen = this.getConfig().getDouble("stats.vitality-regeneration-per-second", 4.0) * (double)ticks / 20.0;
        double naturalHealthRegenPerSecond = this.getConfig().getDouble("stats.natural-health-regen-per-second", 0.5);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, () -> this.getServer().getOnlinePlayers().forEach(p -> {
            stats.regen((Player)p, manaRegen);
            stats.regenVitality((Player)p, vitalityRegen);
            double healthRegenMultiplier = stats.stats((Player)p).healthRegen() / 100.0;
            stats.regenHealth((Player)p, naturalHealthRegenPerSecond * healthRegenMultiplier * (double)ticks / 20.0);
            armor.neutralizeVanillaArmor((Player)p);
            armor.applyDefenseTooltip((Player)p);
            tiers.applyItemTiers((Player)p);
            hud.show((Player)p, stats.stats((Player)p), armor.defense((Player)p));
        }), 1L, ticks);
        this.getServer().getScheduler().runTaskTimer((Plugin)this, this.visuals::tick, 1L, Math.max(1L, this.getConfig().getLong("mob-visuals.update-ticks", 3L)));
        for (World w : this.getServer().getWorlds()) {
            for (LivingEntity e : w.getLivingEntities()) {
                combatListener.scaleMobHealth(e);
                armor.neutralizeVanillaArmor(e);
                this.visuals.track(e);
            }
        }
        this.getServer().getOnlinePlayers().forEach(p -> {
            stats.applyBaseHealth((Player)p);
            combat.applyAttackSpeed((Player)p);
            stats.applySwingRange((Player)p);
            armor.neutralizeVanillaArmor((Player)p);
            armor.applyDefenseTooltip((Player)p);
            tiers.applyItemTiers((Player)p);
            bestiaryProgress.applyBonusHealth((Player)p);
            global.migrate((Player)p);
            foodListener.refresh((Player)p);
            this.visuals.track((LivingEntity)p);
            economy.updateBoard((Player)p);
        });
        Bukkit.getScheduler().runTask((Plugin)this, presentation::refreshAll);
    }

    public void onDisable() {
        if (this.visuals != null) {
            this.visuals.shutdown();
        }
        if (this.progressBar != null) {
            this.progressBar.shutdown();
        }
        if (this.backpacks != null) {
            this.backpacks.shutdown();
        }
    }

    private String text(CommandSender sender, String pt, String en) {
        String string;
        if (sender instanceof Player) {
            Player p = (Player)sender;
            string = Language.of(p).choose(pt, en);
        } else {
            string = en;
        }
        return string;
    }
}

