package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.LevelColorCatalog;
import dev.icaro.foodtooltips.global.LevelColorTheme;
import java.util.function.Consumer;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class LevelColorService {
    private final GlobalLevelService global;
    private final NamespacedKey key;
    private Consumer<Player> listener = p -> {};

    public LevelColorService(Plugin plugin, GlobalLevelService global) {
        this.global = global;
        this.key = new NamespacedKey("foodtooltips", "level_color");
    }

    public void onChange(Consumer<Player> listener) {
        this.listener = listener == null ? p -> {} : listener;
    }

    public LevelColorTheme selected(Player p) {
        String id = (String)p.getPersistentDataContainer().getOrDefault(this.key, PersistentDataType.STRING, (Object)"white");
        return LevelColorCatalog.find(id).orElse(LevelColorCatalog.white());
    }

    public LevelColorTheme effective(Player p) {
        LevelColorTheme selected = this.selected(p);
        return this.unlocked(p, selected) ? selected : LevelColorCatalog.white();
    }

    public boolean unlocked(Player p, LevelColorTheme theme) {
        return theme.id().equals("white") || this.global.snapshot(p).level() >= (long)theme.requiredLevel();
    }

    public boolean select(Player p, LevelColorTheme theme) {
        if (!this.unlocked(p, theme)) {
            return false;
        }
        p.getPersistentDataContainer().set(this.key, PersistentDataType.STRING, (Object)theme.id());
        this.listener.accept(p);
        return true;
    }
}

