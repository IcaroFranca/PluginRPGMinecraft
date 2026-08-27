package dev.icaro.foodtooltips.shop;

import dev.icaro.foodtooltips.shop.ShopService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class ShopMenuListener
implements Listener {
    private final ShopService shop;

    public ShopMenuListener(ShopService s) {
        this.shop = s;
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void click(InventoryClickEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (!(humanEntity instanceof Player) || !this.shop.viewing(p = (Player)humanEntity)) {
            return;
        }
        if (this.shop.isShopView(p)) {
            e.setCancelled(true);
            if (e.getRawSlot() >= 0 && e.getRawSlot() < e.getView().getTopInventory().getSize()) {
                this.shop.click(p, e.getRawSlot());
            }
        } else {
            this.shop.refreshSaleLoreNextTick(p, e.getView().getTopInventory());
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void drag(InventoryDragEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player && this.shop.isShopView(p = (Player)humanEntity)) {
            e.setCancelled(true);
        } else {
            Player p2;
            humanEntity = e.getWhoClicked();
            if (humanEntity instanceof Player && this.shop.viewing(p2 = (Player)humanEntity)) {
                this.shop.refreshSaleLoreNextTick(p2, e.getView().getTopInventory());
            }
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        Player p;
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player && this.shop.viewing(p = (Player)humanEntity)) {
            this.shop.close(p, e.getInventory());
        }
    }
}

