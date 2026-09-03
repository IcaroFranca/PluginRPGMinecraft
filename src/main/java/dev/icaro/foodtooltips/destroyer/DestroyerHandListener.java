package dev.icaro.foodtooltips.destroyer;

import dev.icaro.foodtooltips.i18n.Language;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class DestroyerHandListener implements Listener {
    private final DestroyerHandService hand;

    public DestroyerHandListener(DestroyerHandService hand) {
        this.hand = hand;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !this.hand.isHand(e.getItem())) {
            return;
        }
        Player p = e.getPlayer();
        Language l = Language.of(p);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_AIR) {
            // Holding the hand always means "destroy", never "open this chest/door/etc." or
            // "mine this block" - left-click always gets cancelled here.
            e.setCancelled(true);
            if (p.isSneaking()) {
                int undone = this.hand.undo(p);
                if (undone <= 0) {
                    p.sendActionBar(Component.text(l.choose("Nada pra desfazer.", "Nothing to undo."), NamedTextColor.RED));
                } else {
                    p.sendActionBar(Component.text("+" + undone + " " + l.choose("blocos (desfeito)", "blocks (undone)"), NamedTextColor.GOLD));
                }
            } else {
                this.hand.openModeMenu(p, e.getItem());
            }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        e.setCancelled(true);
        Block clicked = e.getClickedBlock();
        BlockFace face = e.getBlockFace();
        if (clicked == null || face == null) {
            return;
        }
        int cleared = this.hand.clear(p, clicked, face, e.getItem());
        if (cleared <= 0) {
            p.sendActionBar(Component.text(l.choose("Nada pra limpar aqui.", "Nothing to clear here."), NamedTextColor.RED));
        } else {
            p.sendActionBar(Component.text("-" + cleared + " " + l.choose("blocos", "blocks"), NamedTextColor.GREEN));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void menuClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.hand.viewingMenu(p)) {
            e.setCancelled(true);
            this.hand.handleMenuClick(p, e.getRawSlot(), e.getClick());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void menuDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.hand.viewingMenu(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void menuClose(InventoryCloseEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player p) {
            this.hand.closeMenu(p);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.hand.forget(e.getPlayer().getUniqueId());
    }
}
