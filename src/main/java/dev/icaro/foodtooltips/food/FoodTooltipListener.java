package dev.icaro.foodtooltips.food;

import dev.icaro.foodtooltips.food.FoodTooltipService;
import dev.icaro.foodtooltips.i18n.Language;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryOpenEvent;
import org.bukkit.event.player.PlayerItemHeldEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerLocaleChangeEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;

public final class FoodTooltipListener
implements Listener {
    private final Plugin plugin;
    private final FoodTooltipService service;
    private final Set<UUID> scheduled = new HashSet<UUID>();

    public FoodTooltipListener(Plugin p, FoodTooltipService s) {
        this.plugin = p;
        this.service = s;
    }

    @EventHandler
    public void join(PlayerJoinEvent e) {
        this.later(e.getPlayer());
    }

    @EventHandler
    public void locale(PlayerLocaleChangeEvent e) {
        this.later(e.getPlayer());
    }

    @EventHandler
    public void open(InventoryOpenEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player) {
            Player p = (Player)humanEntity;
            this.later(p);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player) {
            Player p = (Player)humanEntity;
            this.later(p);
        }
    }

    @EventHandler
    public void held(PlayerItemHeldEvent e) {
        this.later(e.getPlayer());
    }

    private void later(Player p) {
        if (this.scheduled.add(p.getUniqueId())) {
            Bukkit.getScheduler().runTask(this.plugin, () -> {
                this.scheduled.remove(p.getUniqueId());
                this.refresh(p);
            });
        }
    }

    public void refresh(Player p) {
        Language l = Language.of(p);
        boolean changed = this.update((Inventory)p.getInventory(), l, p);
        if (changed |= this.update(p.getOpenInventory().getTopInventory(), l, p)) {
            p.updateInventory();
        }
    }

    private boolean update(Inventory inv, Language l, Player p) {
        boolean changed = false;
        for (ItemStack i : inv.getContents()) {
            if (i == null || i.isEmpty()) continue;
            changed |= this.service.update(i, l, p);
        }
        return changed;
    }
}

