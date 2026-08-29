package dev.icaro.foodtooltips.item;

import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;

/**
 * Applies the tier tooltip on join; the periodic HUD tick in {@code
 * FoodTooltipsPlugin} re-applies it every cycle afterwards (same
 * join+tick pattern {@code ArmorDefenseListener} uses for Defense), which
 * is enough to reach any item the player picks up, buys, mines or is given,
 * without needing a dedicated pickup/inventory-click hook.
 */
public final class ItemTierListener implements Listener {
    private final ItemTierService tiers;

    public ItemTierListener(ItemTierService tiers) {
        this.tiers = tiers;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.tiers.applyItemTiers(e.getPlayer());
    }
}
