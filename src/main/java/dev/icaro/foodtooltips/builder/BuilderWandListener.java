package dev.icaro.foodtooltips.builder;

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
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;

public final class BuilderWandListener implements Listener {
    private final BuilderWandService wand;

    public BuilderWandListener(BuilderWandService wand) {
        this.wand = wand;
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void use(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getHand() != EquipmentSlot.HAND) {
            return;
        }
        ItemStack hand = e.getItem();
        if (!this.wand.isWand(hand)) {
            return;
        }
        // Holding the wand always means "build", never "open this chest/door/etc.".
        e.setCancelled(true);
        Block clicked = e.getClickedBlock();
        BlockFace face = e.getBlockFace();
        if (clicked == null || face == null) {
            return;
        }
        Player p = e.getPlayer();
        int placed = this.wand.extend(p, clicked, face);
        Language l = Language.of(p);
        if (placed <= 0) {
            p.sendActionBar(Component.text(l.choose("Nada pra estender aqui.", "Nothing to extend here."), NamedTextColor.RED));
        } else {
            p.sendActionBar(Component.text("+" + placed + " " + l.choose("blocos", "blocks"), NamedTextColor.GREEN));
        }
    }
}
