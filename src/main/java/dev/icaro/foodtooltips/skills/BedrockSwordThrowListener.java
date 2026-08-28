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

    /**
     * Tries every detection path that doesn't require a hard compile-time dependency
     * on Geyser/Floodgate, since a server may run either, both, or neither jar:
     * 1) the Floodgate-UUID signature — Floodgate derives a player's UUID purely from
     *    their 64-bit Xbox Live XUID, always zeroing the upper 64 bits, so this works
     *    even when Floodgate's own API jar isn't on this server's classpath (e.g. it's
     *    only installed on a proxy) — this alone covers the overwhelmingly common setup;
     * 2) FloodgateApi#isFloodgatePlayer, if the backend Floodgate plugin is installed here;
     * 3) GeyserApi#connectionByUuid, if the backend Geyser-Spigot plugin is installed here
     *    (works even without Floodgate, e.g. offline/cracked Geyser setups).
     */
    private boolean isBedrock(Player p) {
        UUID id = p.getUniqueId();
        if (id.getMostSignificantBits() == 0L) {
            return true;
        }
        return this.isFloodgatePlayer(id) || this.isGeyserConnection(id);
    }

    private boolean isFloodgatePlayer(UUID id) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.floodgate.api.FloodgateApi");
            Object api = apiClass.getMethod("getInstance", new Class[0]).invoke(null, new Object[0]);
            Method method = apiClass.getMethod("isFloodgatePlayer", UUID.class);
            return Boolean.TRUE.equals(method.invoke(api, id));
        }
        catch (ReflectiveOperationException ignored) {
            return false;
        }
    }

    private boolean isGeyserConnection(UUID id) {
        try {
            Class<?> apiClass = Class.forName("org.geysermc.geyser.api.GeyserApi");
            Object api = apiClass.getMethod("api", new Class[0]).invoke(null, new Object[0]);
            Method method = apiClass.getMethod("connectionByUuid", UUID.class);
            return method.invoke(api, id) != null;
        }
        catch (ReflectiveOperationException ignored) {
            return false;
        }
    }
}

