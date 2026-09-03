package dev.icaro.foodtooltips.builder;

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

public final class BuilderWandListener implements Listener {
    private final BuilderWandService wand;

    public BuilderWandListener(BuilderWandService wand) {
        this.wand = wand;
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
        if (!this.wand.isWand(held)) {
            return;
        }
        Language l = Language.of(p);
        if (p.isSneaking()) {
            int undone = this.wand.undo(p);
            if (undone <= 0) {
                p.sendActionBar(Component.text(l.choose("Nada pra desfazer.", "Nothing to undo."), NamedTextColor.RED));
            } else {
                p.sendActionBar(Component.text("-" + undone + " " + l.choose("blocos (desfeito)", "blocks (undone)"), NamedTextColor.GOLD));
            }
        } else {
            this.wand.openModeMenu(p, held);
        }
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !this.wand.isWand(e.getItem())) {
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
        int placed = this.wand.extend(p, clicked, face, e.getItem());
        if (placed <= 0) {
            p.sendActionBar(Component.text(l.choose("Nada pra estender aqui.", "Nothing to extend here."), NamedTextColor.RED));
        } else {
            p.sendActionBar(Component.text("+" + placed + " " + l.choose("blocos", "blocks"), NamedTextColor.GREEN));
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void menuClick(InventoryClickEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.wand.viewingMenu(p)) {
            e.setCancelled(true);
            this.wand.handleMenuClick(p, e.getRawSlot(), e.getClick());
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void menuDrag(InventoryDragEvent e) {
        if (e.getWhoClicked() instanceof Player p && this.wand.viewingMenu(p)) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void menuClose(InventoryCloseEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player p) {
            this.wand.closeMenu(p);
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.wand.forget(e.getPlayer().getUniqueId());
    }
}
