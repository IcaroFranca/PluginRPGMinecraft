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
import org.bukkit.event.player.PlayerAnimationEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class DestroyerHandListener implements Listener {
    private final DestroyerHandService hand;

    public DestroyerHandListener(DestroyerHandService hand) {
        this.hand = hand;
    }

    /**
     * Left-click (undo/open menu) rides {@link PlayerAnimationEvent} - the plain arm-swing
     * animation - instead of {@code PlayerInteractEvent}'s {@code LEFT_CLICK_AIR}. Bukkit's
     * air-click interact event is throttled/best-effort in a way block clicks aren't, so a
     * swing with nothing in reach doesn't reliably reach {@link #use}. The animation event
     * has no such caveat: it fires for every left-click, air or not.
     */
    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void swing(PlayerAnimationEvent e) {
        Player p = e.getPlayer();
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.hand.isHand(held)) {
            return;
        }
        Language l = Language.of(p);
        if (p.isSneaking()) {
            int undone = this.hand.undo(p);
            if (undone <= 0) {
                p.sendActionBar(Component.text(l.choose("Nada pra desfazer.", "Nothing to undo."), NamedTextColor.RED));
            } else {
                p.sendActionBar(Component.text("+" + undone + " " + l.choose("blocos (desfeito)", "blocks (undone)"), NamedTextColor.GOLD));
            }
        } else {
            this.hand.openModeMenu(p, held);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !this.hand.isHand(e.getItem())) {
            return;
        }
        Player p = e.getPlayer();
        Language l = Language.of(p);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK) {
            // The actual undo/menu action already ran off swing() above - this only
            // stops the left-click from also mining the block underneath the cursor.
            e.setCancelled(true);
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
