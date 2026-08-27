package dev.icaro.foodtooltips.skills;

import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class CombatTreeListener implements Listener {
    private final CombatTreeMenuService tree;

    public CombatTreeListener(CombatTreeMenuService tree) {
        this.tree = tree;
    }

    @EventHandler(ignoreCancelled = true)
    public void click(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.tree.viewing(p)) {
            e.setCancelled(true);
            this.tree.handleClick(p, e.getRawSlot(), e.getClick());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void drag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.tree.viewing(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player p) {
            this.tree.close(p);
        }
    }
}
