package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.global.LevelColorCatalog;
import dev.icaro.foodtooltips.global.LevelColorMenuService;
import dev.icaro.foodtooltips.global.LevelColorService;
import dev.icaro.foodtooltips.global.LevelColorTheme;
import dev.icaro.foodtooltips.i18n.Language;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabExecutor;
import org.bukkit.entity.Player;

public final class LevelColorCommand
implements TabExecutor {
    private final LevelColorService colors;
    private final LevelColorMenuService menu;

    public LevelColorCommand(LevelColorService colors, LevelColorMenuService menu) {
        this.colors = colors;
        this.menu = menu;
    }

    public boolean onCommand(CommandSender sender, Command command, String label, String[] args) {
        if (!(sender instanceof Player)) {
            sender.sendMessage("Only players.");
            return true;
        }
        Player p = (Player)sender;
        if (args.length == 0) {
            this.menu.open(p);
            return true;
        }
        Language l = Language.of(p);
        if (args.length != 1) {
            this.usage(p, l);
            return true;
        }
        Optional<LevelColorTheme> found = LevelColorCatalog.find(args[0]);
        if (found.isEmpty()) {
            p.sendMessage((Component)Component.text((String)l.choose("Tema desconhecido.", "Unknown theme."), (TextColor)NamedTextColor.RED));
            return true;
        }
        LevelColorTheme theme = found.get();
        if (!this.colors.select(p, theme)) {
            p.sendMessage((Component)Component.text((String)(l.choose("Voc\u00ea precisa do N\u00edvel Global ", "You need Global Level ") + theme.requiredLevel() + l.choose(" para usar este tema.", " to use this theme.")), (TextColor)NamedTextColor.RED));
            return true;
        }
        p.sendMessage((Component)Component.text((String)(l.choose("Tema de n\u00edvel selecionado: ", "Level theme selected: ") + theme.name()), (TextColor)NamedTextColor.GREEN));
        return true;
    }

    private void usage(Player p, Language l) {
        p.sendMessage((Component)Component.text((String)(l.choose("Uso: ", "Usage: ") + "/levelcolor [id]"), (TextColor)NamedTextColor.RED));
    }

    public List<String> onTabComplete(CommandSender sender, Command command, String alias, String[] args) {
        if (args.length != 1) {
            return List.of();
        }
        String value = args[0].toLowerCase(Locale.ROOT);
        return LevelColorCatalog.themes().stream().map(LevelColorTheme::id).filter(x -> x.startsWith(value)).toList();
    }
}

