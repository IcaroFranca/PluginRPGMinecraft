package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.skills.SwordThrowListener;
import java.lang.reflect.Method;
import java.util.UUID;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.EquipmentSlot;

public final class BedrockSwordThrowListener
implements Listener {
    private final SwordThrowListener throwsService;

    public BedrockSwordThrowListener(SwordThrowListener service) {
        this.throwsService = service;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void interact(PlayerInteractEvent e) {
        Player p = e.getPlayer();
        if (e.getHand() != EquipmentSlot.HAND || !p.isSneaking() || e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK || !this.isBedrock(p)) {
            return;
        }
        if (this.throwsService.attemptThrow(p)) {
            e.setCancelled(true);
        }
    }

    private boolean isBedrock(Player p) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            Method method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(method.invoke(api, p.getUniqueId()));
        }
        catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}

