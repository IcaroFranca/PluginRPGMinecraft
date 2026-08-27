package dev.icaro.foodtooltips.mining;

import dev.icaro.foodtooltips.mining.GemService;
import dev.icaro.foodtooltips.mining.MiningMenuService;
import dev.icaro.foodtooltips.skills.SkillType;
import dev.icaro.foodtooltips.skills.SkillsMenuService;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;

public final class MiningMenuListener
implements Listener {
    private final MiningMenuService menu;
    private final SkillsMenuService skills;
    private final GemService gems;

    public MiningMenuListener(MiningMenuService m, SkillsMenuService s, GemService g) {
        this.menu = m;
        this.skills = s;
        this.gems = g;
    }

    @EventHandler(ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (!(humanEntity instanceof Player) || !this.menu.viewing(p = (Player)humanEntity) && !this.gems.viewing(p)) {
            return;
        }
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (this.gems.viewing(p)) {
            if (slot == 49) {
                this.gems.close(p);
                this.menu.open(p);
            }
            return;
        }
        if (slot == 49) {
            if (this.menu.detail(p)) {
                this.menu.open(p);
            } else {
                this.menu.close(p);
                this.skills.openGeneral(p, SkillType.MINING, 0);
            }
        } else {
            this.menu.clickEntry(p, slot);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void drag(InventoryDragEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player && (this.menu.viewing(p = (Player)humanEntity) || this.gems.viewing(p))) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player) {
            Player p = (Player)humanEntity;
            this.menu.close(p);
        }
    }
}

