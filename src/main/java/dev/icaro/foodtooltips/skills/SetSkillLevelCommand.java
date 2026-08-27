package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalSkill;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.SkillType;
import java.text.Normalizer;
import java.util.ArrayList;
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

public final class SetSkillLevelCommand
implements TabExecutor {
    private static final List<String> SKILLS = List.of("combat", "farming", "fishing", "mining", "foraging", "enchanting", "alchemy");
    private static final List<String> LEVELS = List.of("0", "1", "3", "30", "50", "100", "200");
    private final CombatSkillService combat;
    private final GeneralSkillService general;
    private final GlobalLevelService global;

    public SetSkillLevelCommand(CombatSkillService combat, GeneralSkillService general, GlobalLevelService global) {
        this.combat = combat;
        this.general = general;
        this.global = global;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        int level;
        String levelArg;
        String skillArg;
        Player target;
        Language language;
        if (sender instanceof Player) {
            Player p = (Player)sender;
            v0 = Language.of(p);
        } else {
            v0 = language = Language.EN;
        }
        if (args.length == 2 && sender instanceof Player) {
            Player player;
            target = player = (Player)sender;
            skillArg = args[0];
            levelArg = args[1];
        } else if (args.length == 3) {
            target = Bukkit.getPlayerExact((String)args[0]);
            if (target == null) {
                target = Bukkit.getPlayer((String)args[0]);
            }
            if (target == null) {
                this.message(sender, language.choose("Jogador n\u00e3o encontrado ou offline.", "Player not found or offline."), NamedTextColor.RED);
                return true;
            }
            skillArg = args[1];
            levelArg = args[2];
        } else {
            this.usage(sender, language);
            return true;
        }
        Parsed skill = this.parse(skillArg);
        if (skill == null) {
            this.message(sender, language.choose("Skill inv\u00e1lida. Use combate, agricultura, pesca, minera\u00e7\u00e3o, coleta, encantamento ou alquimia.", "Invalid skill. Use combat, farming, fishing, mining, foraging, enchanting or alchemy."), NamedTextColor.RED);
            return true;
        }
        try {
            level = Integer.parseInt(levelArg);
            if (level < 0 || level > 200) {
                throw new NumberFormatException();
            }
        }
        catch (NumberFormatException ex) {
            this.message(sender, language.choose("O n\u00edvel precisa ser um n\u00famero entre 0 e 200.", "Level must be a number between 0 and 200."), NamedTextColor.RED);
            return true;
        }
        if (skill.combat) {
            this.combat.setLevel(target, level);
        } else {
            this.general.setLevel(target, skill.type, level);
        }
        this.global.administrativeSkillLevel(target, skill.combat ? GlobalSkill.COMBAT : GlobalSkill.of(skill.type), level);
        this.message(sender, language.choose("N\u00edvel definido: ", "Level set: ") + this.name(skill, language) + " \u2022 " + target.getName() + " \u2022 " + level, NamedTextColor.GREEN);
        if (sender != target) {
            Language targetLanguage = Language.of(target);
            this.message((CommandSender)target, targetLanguage.choose("Seu n\u00edvel de ", "Your ") + this.name(skill, targetLanguage) + targetLanguage.choose(" foi definido para ", " level was set to ") + level + ".", NamedTextColor.GOLD);
        }
        return true;
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length == 1) {
            ArrayList<String> values = new ArrayList<String>();
            if (sender instanceof Player) {
                values.addAll(SKILLS);
            }
            values.addAll(Bukkit.getOnlinePlayers().stream().map(Player::getName).toList());
            return this.filter(values, args[0]);
        }
        if (args.length == 2) {
            return this.parse(args[0]) != null ? this.filter(LEVELS, args[1]) : this.filter(SKILLS, args[1]);
        }
        if (args.length == 3) {
            return this.filter(LEVELS, args[2]);
        }
        return List.of();
    }

    private void usage(CommandSender sender, Language language) {
        String usage = sender instanceof Player ? "/setskilllevel <skill> <0-200> " + language.choose("ou ", "or ") + "/setskilllevel <player> <skill> <0-200>" : "/setskilllevel <player> <skill> <0-200>";
        this.message(sender, language.choose("Uso: ", "Usage: ") + usage, NamedTextColor.RED);
    }

    private List<String> filter(List<String> values, String prefix) {
        String lower = prefix.toLowerCase(Locale.ROOT);
        return values.stream().filter(value -> value.toLowerCase(Locale.ROOT).startsWith(lower)).toList();
    }

    private void message(CommandSender sender, String text, NamedTextColor color) {
        sender.sendMessage((Component)Component.text((String)text, (TextColor)color));
    }

    private Parsed parse(String raw) {
        String value;
        return switch (value = Normalizer.normalize(raw, Normalizer.Form.NFD).replaceAll("\\p{M}", "").toLowerCase(Locale.ROOT)) {
            case "combat", "combate" -> new Parsed(true, null);
            case "farming", "agricultura" -> new Parsed(false, SkillType.FARMING);
            case "fishing", "pesca" -> new Parsed(false, SkillType.FISHING);
            case "mining", "mineracao" -> new Parsed(false, SkillType.MINING);
            case "foraging", "coleta" -> new Parsed(false, SkillType.FORAGING);
            case "enchanting", "encantamento" -> new Parsed(false, SkillType.ENCHANTING);
            case "alchemy", "alquimia" -> new Parsed(false, SkillType.ALCHEMY);
            default -> null;
        };
    }

    private String name(Parsed parsed, Language language) {
        return parsed.combat ? language.choose("Combate", "Combat") : parsed.type.name(language == Language.PT);
    }

    private record Parsed(boolean combat, SkillType type) {
    }
}

