package dev.icaro.foodtooltips.destroyer;

import dev.icaro.foodtooltips.i18n.Language;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
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
        if (p.isSneaking() && (e.getAction() == Action.LEFT_CLICK_BLOCK || e.getAction() == Action.LEFT_CLICK_AIR)) {
            // Shift + left-click undoes instead of breaking whatever block was clicked.
            e.setCancelled(true);
            int undone = this.hand.undo(p);
            if (undone <= 0) {
                p.sendActionBar(Component.text(l.choose("Nada pra desfazer.", "Nothing to undo."), NamedTextColor.RED));
            } else {
                p.sendActionBar(Component.text("+" + undone + " " + l.choose("blocos (desfeito)", "blocks (undone)"), NamedTextColor.GOLD));
            }
            return;
        }
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        // Holding the hand always means "destroy", never "open this chest/door/etc.".
        e.setCancelled(true);
        Block clicked = e.getClickedBlock();
        BlockFace face = e.getBlockFace();
        if (clicked == null || face == null) {
            return;
        }
        int cleared = this.hand.clear(p, clicked, face);
        if (cleared <= 0) {
            p.sendActionBar(Component.text(l.choose("Nada pra limpar aqui.", "Nothing to clear here."), NamedTextColor.RED));
        } else {
            p.sendActionBar(Component.text("-" + cleared + " " + l.choose("blocos", "blocks"), NamedTextColor.GREEN));
        }
    }

    @EventHandler
    public void quit(PlayerQuitEvent e) {
        this.hand.forget(e.getPlayer().getUniqueId());
    }
}
