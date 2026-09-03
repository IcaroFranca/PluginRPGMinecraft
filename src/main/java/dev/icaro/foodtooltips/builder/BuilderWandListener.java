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
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerQuitEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class BuilderWandListener implements Listener {
    private final BuilderWandService wand;

    public BuilderWandListener(BuilderWandService wand) {
        this.wand = wand;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND || !this.wand.isWand(e.getItem())) {
            return;
        }
        Player p = e.getPlayer();
        Language l = Language.of(p);
        if (e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_AIR) {
            // Holding the wand always means "build", never "open this chest/door/etc." or
            // "mine this block" - left-click always gets cancelled here.
            e.setCancelled(true);
            if (p.isSneaking()) {
                int undone = this.wand.undo(p);
                if (undone <= 0) {
                    p.sendActionBar(Component.text(l.choose("Nada pra desfazer.", "Nothing to undo."), NamedTextColor.RED));
                } else {
                    p.sendActionBar(Component.text("-" + undone + " " + l.choose("blocos (desfeito)", "blocks (undone)"), NamedTextColor.GOLD));
                }
            } else {
                this.wand.openModeMenu(p, e.getItem());
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
            this.wand.handleMenuClick(p, e.getRawSlot());
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
