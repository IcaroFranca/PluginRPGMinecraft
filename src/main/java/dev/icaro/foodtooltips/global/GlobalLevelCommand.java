package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalLevelSnapshot;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import java.text.NumberFormat;
import java.util.List;
import java.util.Locale;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class GlobalLevelCommand
implements TabExecutor {
    private final GlobalLevelService global;

    public GlobalLevelCommand(GlobalLevelService global) {
        this.global = global;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (command.getName().equalsIgnoreCase("nivelglobal")) {
            if (!(sender instanceof Player)) {
                sender.sendMessage("Only players.");
                return true;
            }
            Player p = (Player)sender;
            this.show((CommandSender)p, p);
            return true;
        }
        return this.admin(sender, args);
    }

    private boolean admin(CommandSender sender, String[] args) {
        long amount;
        Language l;
        if (sender instanceof Player) {
            Player p = (Player)sender;
            v0 = Language.of(p);
        } else {
            v0 = l = Language.EN;
        }
        if (!sender.hasPermission("foodtooltips.admin")) {
            this.msg(sender, l.choose("Sem permiss\u00e3o.", "No permission."), NamedTextColor.RED);
            return true;
        }
        if (args.length < 2 || args.length > 3) {
            this.usage(sender, l);
            return true;
        }
        Player target = Bukkit.getPlayerExact((String)args[0]);
        if (target == null) {
            this.msg(sender, l.choose("O jogador precisa estar online.", "The player must be online."), NamedTextColor.RED);
            return true;
        }
        String action = args[1].toLowerCase(Locale.ROOT);
        if (action.equals("get")) {
            this.show(sender, target);
            return true;
        }
        if (args.length != 3) {
            this.usage(sender, l);
            return true;
        }
        try {
            amount = Long.parseLong(args[2]);
            if (amount < 0L) {
                throw new NumberFormatException();
            }
        }
        catch (NumberFormatException ex) {
            this.msg(sender, l.choose("A quantidade deve ser um inteiro n\u00e3o negativo.", "Amount must be a non-negative integer."), NamedTextColor.RED);
            return true;
        }
        try {
            switch (action) {
                case "give": {
                    this.global.addGlobalXp(target, amount, GlobalXpSource.ADMIN);
                    break;
                }
                case "set": {
                    this.global.setGlobalXp(target, amount, GlobalXpSource.ADMIN);
                    break;
                }
                case "remove": {
                    this.global.removeGlobalXp(target, amount, GlobalXpSource.ADMIN);
                    break;
                }
                default: {
                    this.usage(sender, l);
                    return true;
                }
            }
        }
        catch (ArithmeticException ex) {
            this.msg(sender, l.choose("A quantidade excede o limite permitido.", "Amount exceeds the allowed limit."), NamedTextColor.RED);
            return true;
        }
        this.show(sender, target);
        return true;
    }

    private void show(CommandSender viewer, Player target) {
        Language language;
        if (viewer instanceof Player) {
            Player p = (Player)viewer;
            language = Language.of(p);
        } else {
            language = Language.EN;
        }
        Language l = language;
        GlobalLevelSnapshot s = this.global.snapshot(target);
        this.msg(viewer, (String)(viewer == target ? "" : target.getName() + " \u2022 ") + l.choose("N\u00edvel Global ", "Global Level ") + s.level() + " \u2022 " + s.progress() + "/" + s.required() + " XP \u2022 " + l.choose("Total ", "Total ") + NumberFormat.getIntegerInstance(Locale.US).format(s.totalXp()) + " \u2022 +" + Math.round(s.bonusHealth()) + " HP \u2022 +" + s.strength() + " Strength", NamedTextColor.GOLD);
    }

    private void usage(CommandSender s, Language l) {
        this.msg(s, l.choose("Uso: ", "Usage: ") + "/globalxp <player> <get|give|set|remove> [amount]", NamedTextColor.RED);
    }

    private void msg(CommandSender s, String value, NamedTextColor color) {
        s.sendMessage((Component)Component.text((String)value, (TextColor)color));
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (command.getName().equalsIgnoreCase("nivelglobal")) {
            return List.of();
        }
        if (args.length == 1) {
            return Bukkit.getOnlinePlayers().stream().map(Player::getName).filter(x -> this.starts((String)x, args[0])).toList();
        }
        if (args.length == 2) {
            return List.of("get", "give", "set", "remove").stream().filter(x -> this.starts((String)x, args[1])).toList();
        }
        if (args.length == 3 && !args[1].equalsIgnoreCase("get")) {
            return List.of("0", "4", "100", "500", "1000").stream().filter(x -> x.startsWith(args[2])).toList();
        }
        return List.of();
    }

    private boolean starts(String a, String b) {
        return a.toLowerCase(Locale.ROOT).startsWith(b.toLowerCase(Locale.ROOT));
    }
}

