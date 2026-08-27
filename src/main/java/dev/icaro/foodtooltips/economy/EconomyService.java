package dev.icaro.foodtooltips.economy;

import dev.icaro.foodtooltips.bestiary.BestiaryCatalog;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import java.text.NumberFormat;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.scoreboard.Criteria;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public final class EconomyService {
    private final NamespacedKey coinsKey;
    private final CombatAbilityService abilities;
    private final Map<UUID, Scoreboard> boards = new HashMap<UUID, Scoreboard>();

    public EconomyService(Plugin p, CombatAbilityService a) {
        this.coinsKey = new NamespacedKey("foodtooltips", "coins");
        this.abilities = a;
    }

    public long balance(Player p) {
        return (Long)p.getPersistentDataContainer().getOrDefault(this.coinsKey, PersistentDataType.LONG, (Object)0L);
    }

    public void deposit(Player p, long amount) {
        if (amount > 0L) {
            p.getPersistentDataContainer().set(this.coinsKey, PersistentDataType.LONG, (Object)Math.addExact(this.balance(p), amount));
        }
        this.updateBoard(p);
    }

    public boolean withdraw(Player p, long amount) {
        long balance = this.balance(p);
        if (amount < 0L || balance < amount) {
            return false;
        }
        p.getPersistentDataContainer().set(this.coinsKey, PersistentDataType.LONG, (Object)(balance - amount));
        this.updateBoard(p);
        return true;
    }

    public void setBalance(Player p, long amount) {
        p.getPersistentDataContainer().set(this.coinsKey, PersistentDataType.LONG, (Object)Math.max(0L, amount));
        this.updateBoard(p);
    }

    public int mobCoins(Player p, LivingEntity mob) {
        double hp = Optional.ofNullable(mob.getAttribute(Attribute.MAX_HEALTH)).map(x -> x.getValue()).orElse(10.0);
        int fallback = Math.max(1, (int)Math.round(Math.sqrt(hp) * 1.5));
        int base = BestiaryCatalog.find(mob.getType()).map(e -> this.catalogCoins(e.type(), e.awardedCombatXp())).orElse(fallback);
        double bonus = this.abilities.treasureHunterBonus(p);
        if (bonus > 0.0) {
            base = Math.max(1, (int)Math.round((double)base * (1.0 + bonus)));
        }
        return base;
    }

    public int catalogCoins(EntityType type, int combatXp) {
        return switch (type) {
            case EntityType.WARDEN -> 500;
            case EntityType.WITHER -> 1500;
            case EntityType.ENDER_DRAGON -> 3000;
            case EntityType.ELDER_GUARDIAN -> 150;
            case EntityType.RAVAGER -> 75;
            default -> combatXp <= 0 ? 0 : Math.max(1, combatXp * 2);
        };
    }

    public void updateBoard(Player p) {
        Objective old;
        Scoreboard board = this.boards.computeIfAbsent(p.getUniqueId(), id -> Bukkit.getScoreboardManager().getNewScoreboard());
        if (p.getScoreboard() != board) {
            p.setScoreboard(board);
        }
        if ((old = board.getObjective("rpg_sidebar")) != null) {
            old.unregister();
        }
        Objective objective = board.registerNewObjective("rpg_sidebar", Criteria.DUMMY, (Component)Component.text((String)"\u2726 NexusRPG", (TextColor)NamedTextColor.GOLD));
        objective.setDisplaySlot(DisplaySlot.SIDEBAR);
        objective.getScore(" ").setScore(3);
        objective.getScore(String.valueOf(ChatColor.GOLD) + Language.of(p).choose("Moedas", "Coins")).setScore(2);
        objective.getScore(String.valueOf(ChatColor.YELLOW) + this.format(this.balance(p)) + " \u26c3").setScore(1);
    }

    public void clearBoard(Player p) {
        this.boards.remove(p.getUniqueId());
    }

    public String format(long amount) {
        return NumberFormat.getIntegerInstance(Locale.US).format(amount);
    }
}

