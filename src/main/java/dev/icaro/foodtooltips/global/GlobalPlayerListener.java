package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

public final class GlobalPlayerListener
implements Listener {
    private final GlobalLevelService global;

    public GlobalPlayerListener(GlobalLevelService global) {
        this.global = global;
    }

    @EventHandler(priority=EventPriority.LOWEST)
    public void join(PlayerJoinEvent e) {
        this.global.migrate(e.getPlayer());
    }
}

