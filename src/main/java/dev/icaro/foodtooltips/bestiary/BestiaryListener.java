package dev.icaro.foodtooltips.bestiary;

import dev.icaro.foodtooltips.bestiary.BestiaryMenuService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class BestiaryListener
implements Listener {
    private final BestiaryMenuService menus;

    public BestiaryListener(BestiaryMenuService menus) {
        this.menus = menus;
    }

    @EventHandler(ignoreCancelled=true)
    public void click(InventoryClickEvent event) {
        Player player;
        HumanEntity humanEntity = event.getWhoClicked();
        if (humanEntity instanceof Player && this.menus.viewing(player = (Player)humanEntity)) {
            event.setCancelled(true);
            this.menus.click(player, event.getRawSlot());
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void drag(InventoryDragEvent event) {
        Player player;
        HumanEntity humanEntity = event.getWhoClicked();
        if (humanEntity instanceof Player && this.menus.viewing(player = (Player)humanEntity)) {
            event.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent event) {
        HumanEntity humanEntity = event.getPlayer();
        if (humanEntity instanceof Player) {
            Player player = (Player)humanEntity;
            this.menus.close(player);
        }
    }
}

