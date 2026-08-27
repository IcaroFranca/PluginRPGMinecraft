package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.skills.SkillsMenuService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;

public final class SkillsListener
implements Listener {
    private final SkillsMenuService menus;

    public SkillsListener(SkillsMenuService m) {
        this.menus = m;
    }

    @EventHandler(ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player && this.menus.viewing(p = (Player)humanEntity)) {
            e.setCancelled(true);
            this.menus.handleClick(p, e.getRawSlot());
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void drag(InventoryDragEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player && this.menus.viewing(p = (Player)humanEntity)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player) {
            Player p = (Player)humanEntity;
            this.menus.close(p);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void shortcut(PlayerSwapHandItemsEvent e) {
        if (e.getPlayer().isSneaking()) {
            e.setCancelled(true);
            this.menus.openMain(e.getPlayer());
        }
    }
}

