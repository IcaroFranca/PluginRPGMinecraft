package dev.icaro.foodtooltips.protect;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldedit.world.World;
import com.sk89q.worldguard.LocalPlayer;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.bukkit.WorldGuardPlugin;
import com.sk89q.worldguard.protection.flags.Flags;
import com.sk89q.worldguard.protection.flags.StateFlag;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;

public final class ProtectionService {
    private final boolean worldGuard = Bukkit.getPluginManager().getPlugin("WorldGuard") != null;
    private final boolean griefPrevention = Bukkit.getPluginManager().getPlugin("GriefPrevention") != null;

    public boolean canBuild(Player player, Block block) {
        return this.canBuild(player, block.getLocation());
    }

    public boolean canBuild(Player player, Location location) {
        if (this.worldGuard) {
            try {
                if (!WorldGuardHook.canBuild(player, location)) {
                    return false;
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        if (this.griefPrevention) {
            try {
                if (!GriefPreventionHook.canBuild(player, location)) {
                    return false;
                }
            }
            catch (Throwable throwable) {
                // empty catch block
            }
        }
        return true;
    }

    private static final class WorldGuardHook {
        private WorldGuardHook() {
        }

        static boolean canBuild(Player player, Location location) {
            LocalPlayer local = WorldGuardPlugin.inst().wrapPlayer(player);
            World world = BukkitAdapter.adapt((org.bukkit.World)location.getWorld());
            if (WorldGuard.getInstance().getPlatform().getSessionManager().hasBypass(local, world)) {
                return true;
            }
            RegionQuery query = WorldGuard.getInstance().getPlatform().getRegionContainer().createQuery();
            return query.testState(BukkitAdapter.adapt((Location)location), local, new StateFlag[]{Flags.BUILD});
        }
    }

    private static final class GriefPreventionHook {
        private GriefPreventionHook() {
        }

        static boolean canBuild(Player player, Location location) throws Exception {
            Object reason;
            Object gp = Class.forName("me.ryanhamshire.GriefPrevention.GriefPrevention").getField("instance").get(null);
            try {
                reason = gp.getClass().getMethod("allowBuild", Player.class, Location.class, Material.class).invoke(gp, player, location, location.getBlock().getType());
            }
            catch (NoSuchMethodException legacy) {
                reason = gp.getClass().getMethod("allowBuild", Player.class, Location.class).invoke(gp, player, location);
            }
            return reason == null;
        }
    }
}

