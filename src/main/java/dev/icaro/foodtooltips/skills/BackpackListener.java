package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.skills.BackpackService;
import dev.icaro.foodtooltips.skills.BackpackType;
import dev.icaro.foodtooltips.skills.SkillsMenuService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.ItemStack;

public final class BackpackListener
implements Listener {
    private final BackpackService bags;
    private final SkillsMenuService skills;

    public BackpackListener(BackpackService b, SkillsMenuService s) {
        this.bags = b;
        this.skills = s;
    }

    @EventHandler(ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (!(humanEntity instanceof Player) || !this.bags.viewing(p = (Player)humanEntity)) {
            return;
        }
        BackpackType type = this.bags.current(p);
        if (type == null) {
            e.setCancelled(true);
            if (e.getRawSlot() == 49) {
                this.bags.close(p);
                this.skills.openMain(p);
            } else {
                this.bags.clickMenu(p, e.getRawSlot());
            }
            return;
        }
        int top = e.getView().getTopInventory().getSize();
        if (e.getRawSlot() < top && !this.bags.storageSlot(p, e.getRawSlot())) {
            e.setCancelled(true);
            this.bags.handleControl(p, e.getRawSlot());
            return;
        }
        if (e.getRawSlot() < top) {
            ItemStack incoming = switch (e.getAction()) {
                case HOTBAR_SWAP, HOTBAR_MOVE_AND_READD -> p.getInventory().getItem(e.getHotbarButton());
                default -> e.getCursor();
            };
            if (incoming != null && !incoming.getType().isAir() && !this.bags.accepts(type, incoming.getType())) {
                e.setCancelled(true);
            }
        } else if (e.isShiftClick() && e.getCurrentItem() != null && !this.bags.accepts(type, e.getCurrentItem().getType())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void drag(InventoryDragEvent e) {
        boolean dropsRejectedItem;
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (!(humanEntity instanceof Player) || !this.bags.viewing(p = (Player)humanEntity)) {
            return;
        }
        BackpackType type = this.bags.current(p);
        if (type == null) {
            e.setCancelled(true);
            return;
        }
        int top = e.getView().getTopInventory().getSize();
        boolean touchesControlSlot = e.getRawSlots().stream().anyMatch(s -> s < top && !this.bags.storageSlot(p, (int)s));
        boolean bl = dropsRejectedItem = e.getRawSlots().stream().anyMatch(s -> s < top) && !this.bags.accepts(type, e.getOldCursor().getType());
        if (touchesControlSlot || dropsRejectedItem) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        Player p;
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player && this.bags.viewing(p = (Player)humanEntity)) {
            this.bags.close(p);
        }
    }
}

