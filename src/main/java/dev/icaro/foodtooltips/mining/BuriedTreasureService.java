package dev.icaro.foodtooltips.mining;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.mining.TreasureRarity;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import java.text.NumberFormat;
import java.util.HashSet;
import java.util.Locale;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.DoubleConsumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.ChatColor;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.entity.Display;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.persistence.PersistentDataContainer;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class BuriedTreasureService {
    private final Plugin plugin;
    private final GeneralSkillService skills;
    private final NamespacedKey coins = new NamespacedKey("foodtooltips", "coins");

    public BuriedTreasureService(GeneralSkillService s) {
        this(null, s);
    }

    public BuriedTreasureService(Plugin p, GeneralSkillService s) {
        this.plugin = p;
        this.skills = s;
    }

    public void tryFind(Player p, Location origin, DoubleConsumer xpReward) {
        TreasureRarity rarity;
        int roll = ThreadLocalRandom.current().nextInt(100000);
        TreasureRarity treasureRarity = roll < 1 ? TreasureRarity.LEGENDARY : (roll < 4 ? TreasureRarity.EPIC : (roll < 16 ? TreasureRarity.RARE : (roll < 66 ? TreasureRarity.UNCOMMON : (rarity = roll < 266 ? TreasureRarity.COMMON : null))));
        if (rarity == null) {
            return;
        }
        Reward reward = this.reward(rarity);
        if (this.plugin == null) {
            this.deliver(p, rarity, reward, xpReward);
            return;
        }
        this.animate(p, origin.clone().add(0.5, 0.55, 0.5), rarity, reward, xpReward);
    }

    public int found(Player p, TreasureRarity r) {
        return (Integer)p.getPersistentDataContainer().getOrDefault(this.countKey(r), PersistentDataType.INTEGER, 0);
    }

    private NamespacedKey countKey(TreasureRarity r) {
        return new NamespacedKey("foodtooltips", "treasure_" + r.name().toLowerCase(Locale.ROOT));
    }

    private void animate(final Player p, final Location start, final TreasureRarity rarity, final Reward reward, final DoubleConsumer xpReward) {
        final World world = start.getWorld();
        final ItemDisplay orb = (ItemDisplay)world.spawn(start, ItemDisplay.class, d -> {
            d.setItemStack(ItemStack.of((Material)this.orbMaterial(rarity)));
            d.setBillboard(Display.Billboard.CENTER);
            d.setGlowing(true);
            d.setGlowColorOverride(this.orbColor(rarity));
            d.setInvulnerable(true);
            d.setPersistent(false);
            d.setTeleportDuration(2);
        });
        final Particle.DustOptions dust = new Particle.DustOptions(this.orbColor(rarity), rarity.ordinal() >= 3 ? 1.45f : 1.05f);
        new BukkitRunnable(){
            int age;

            public void run() {
                if (!orb.isValid()) {
                    this.cancel();
                    return;
                }
                if (!p.isOnline() || p.isDead()) {
                    orb.remove();
                    this.cancel();
                    return;
                }
                ++this.age;
                Location current = orb.getLocation();
                if (this.age <= 20) {
                    double angle = (double)this.age * 0.45;
                    Location hover = start.clone().add(Math.cos(angle) * 0.18, Math.sin((double)this.age * 0.35) * 0.12, Math.sin(angle) * 0.18);
                    orb.teleport(hover);
                    world.spawnParticle(Particle.DUST, hover, 2, 0.08, 0.08, 0.08, 0.0, dust);
                    return;
                }
                Location target = p.getLocation().add(0.0, 1.15, 0.0);
                double distance = current.distance(target);
                if (distance < 0.38 || this.age >= 50) {
                    orb.remove();
                    BuriedTreasureService.this.deliver(p, rarity, reward, xpReward);
                    this.cancel();
                    return;
                }
                double speed = Math.min(0.85, 0.18 + (double)(this.age - 20) * 0.025);
                Location next = current.add(target.toVector().subtract(current.toVector()).normalize().multiply(Math.min(speed, distance)));
                orb.teleport(next);
                world.spawnParticle(Particle.DUST, next, 3, 0.08, 0.08, 0.08, 0.0, dust);
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }

    private void deliver(Player p, TreasureRarity r, Reward reward, DoubleConsumer xpReward) {
        this.skills.depositMineralDust(p, reward.dust);
        PersistentDataContainer data = p.getPersistentDataContainer();
        long balance = (Long)data.getOrDefault(this.coins, PersistentDataType.LONG, 0L) + reward.coins;
        data.set(this.coins, PersistentDataType.LONG, balance);
        NamespacedKey key = this.countKey(r);
        data.set(key, PersistentDataType.INTEGER, ((Integer)data.getOrDefault(key, PersistentDataType.INTEGER, 0) + 1));
        xpReward.accept(reward.xp);
        this.announce(p, r, reward);
        this.refreshCoins(p, balance);
    }

    private Reward reward(TreasureRarity r) {
        ThreadLocalRandom x = ThreadLocalRandom.current();
        return switch (r) {
            default -> throw new MatchException(null, null);
            case TreasureRarity.COMMON -> new Reward(x.nextLong(25L, 51L), x.nextLong(20L, 51L), 25.0);
            case TreasureRarity.UNCOMMON -> new Reward(x.nextLong(75L, 126L), x.nextLong(75L, 151L), 75.0);
            case TreasureRarity.RARE -> new Reward(x.nextLong(250L, 401L), x.nextLong(250L, 501L), 200.0);
            case TreasureRarity.EPIC -> new Reward(x.nextLong(750L, 1201L), x.nextLong(1000L, 2001L), 500.0);
            case TreasureRarity.LEGENDARY -> new Reward(x.nextLong(2500L, 4001L), x.nextLong(5000L, 10001L), 1500.0);
        };
    }

    private Material orbMaterial(TreasureRarity r) {
        return switch (r) {
            default -> throw new MatchException(null, null);
            case TreasureRarity.COMMON -> Material.IRON_NUGGET;
            case TreasureRarity.UNCOMMON -> Material.EMERALD;
            case TreasureRarity.RARE -> Material.DIAMOND;
            case TreasureRarity.EPIC -> Material.AMETHYST_SHARD;
            case TreasureRarity.LEGENDARY -> Material.NETHER_STAR;
        };
    }

    private Color orbColor(TreasureRarity r) {
        return switch (r) {
            default -> throw new MatchException(null, null);
            case TreasureRarity.COMMON -> Color.WHITE;
            case TreasureRarity.UNCOMMON -> Color.LIME;
            case TreasureRarity.RARE -> Color.AQUA;
            case TreasureRarity.EPIC -> Color.PURPLE;
            case TreasureRarity.LEGENDARY -> Color.ORANGE;
        };
    }

    private void announce(Player p, TreasureRarity r, Reward reward) {
        Language l = Language.of(p);
        p.sendMessage((Component)Component.text((String)"\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", (TextColor)NamedTextColor.DARK_GRAY));
        p.sendMessage((Component)Component.text((String)("\u2726 " + l.choose("TESOURO SOTERRADO!", "BURIED TREASURE!") + " \u2726"), (TextColor)r.color()));
        p.sendMessage((Component)Component.text((String)(l.choose("Raridade: ", "Rarity: ") + r.name(l == Language.PT)), (TextColor)r.color()));
        p.sendMessage((Component)Component.text((String)("\u2727 " + reward.dust + " " + l.choose("P\u00f3 Mineral", "Mineral Dust")), (TextColor)NamedTextColor.LIGHT_PURPLE));
        p.sendMessage((Component)Component.text((String)("\u26c3 " + reward.coins + " " + l.choose("moedas", "coins")), (TextColor)NamedTextColor.GOLD));
        p.sendMessage((Component)Component.text((String)("+" + Math.round(reward.xp) + " XP " + l.choose("de Minera\u00e7\u00e3o", "Mining XP")), (TextColor)NamedTextColor.AQUA));
        p.sendMessage((Component)Component.text((String)"\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501\u2501", (TextColor)NamedTextColor.DARK_GRAY));
        p.playSound(p.getLocation(), r.ordinal() >= 3 ? Sound.ENTITY_ENDER_DRAGON_GROWL : Sound.ENTITY_PLAYER_LEVELUP, 1.0f, 1.2f);
        p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0.0, 1.0, 0.0), 30, 0.5, 0.7, 0.5, 0.05);
    }

    private void refreshCoins(Player p, long balance) {
        Scoreboard board = p.getScoreboard();
        Objective objective = board.getObjective("rpg_sidebar");
        if (objective == null) {
            return;
        }
        for (String entry : new HashSet<>(board.getEntries())) {
            if (!entry.contains("\u26c3")) continue;
            board.resetScores(entry);
        }
        objective.getScore(String.valueOf(ChatColor.YELLOW) + NumberFormat.getIntegerInstance(Locale.US).format(balance) + " \u26c3").setScore(1);
    }

    private record Reward(long dust, long coins, double xp) {
    }
}

