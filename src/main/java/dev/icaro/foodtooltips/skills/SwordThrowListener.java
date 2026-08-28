package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatAbility;
import dev.icaro.foodtooltips.skills.CombatAbilityService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.FluidCollisionMode;
import org.bukkit.Location;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Display;
import org.bukkit.entity.Entity;
import org.bukkit.entity.ItemDisplay;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerSwapHandItemsEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.util.RayTraceResult;
import org.bukkit.util.Transformation;
import org.bukkit.util.Vector;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class SwordThrowListener
implements Listener {
    private final Plugin plugin;
    private final CombatAbilityService abilities;
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();

    public SwordThrowListener(Plugin p, CombatAbilityService a) {
        this.plugin = p;
        this.abilities = a;
    }

    @EventHandler(priority=EventPriority.HIGH, ignoreCancelled=true)
    public void throwSword(PlayerSwapHandItemsEvent e) {
        Player p = e.getPlayer();
        if (p.isSneaking()) {
            return;
        }
        if (this.attemptThrow(p)) {
            e.setCancelled(true);
        }
    }

    public boolean attemptThrow(Player p) {
        long ready;
        ItemStack sword = p.getInventory().getItemInMainHand();
        if (!sword.getType().name().endsWith("_SWORD") || !this.abilities.enabled(p, CombatAbility.SWORD_THROW)) {
            return false;
        }
        long now = System.currentTimeMillis();
        if (now < (ready = this.cooldowns.getOrDefault(p.getUniqueId(), 0L).longValue())) {
            p.sendActionBar((Component)Component.text((String)(Language.of(p).choose("Arremesso em recarga: ", "Sword Throw cooldown: ") + String.format(Locale.US, "%.1fs", (double)(ready - now) / 1000.0)), (TextColor)NamedTextColor.RED));
            return true;
        }
        long cooldown = this.abilities.swordThrowCooldownMillis(p);
        this.cooldowns.put(p.getUniqueId(), now + cooldown);
        this.launch(p, sword);
        return true;
    }

    private void launch(final Player p, ItemStack sword) {
        final Location start = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(0.6));
        final Vector direction = p.getEyeLocation().getDirection().normalize();
        final ItemDisplay display = (ItemDisplay)p.getWorld().spawn(start, ItemDisplay.class, d -> {
            ItemStack visual = sword.clone();
            visual.setAmount(1);
            d.setItemStack(visual);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.FIXED);
            d.setViewRange(0.5f);
        });
        new BukkitRunnable(){
            int ticks;
            Location at = start.clone();

            public void run() {
                Entity entity2;
                if (!p.isOnline() || !display.isValid() || this.ticks++ >= 30) {
                    this.finish();
                    return;
                }
                RayTraceResult block = p.getWorld().rayTraceBlocks(this.at, direction, 1.0, FluidCollisionMode.NEVER, true);
                RayTraceResult hit = p.getWorld().rayTraceEntities(this.at, direction, 1.0, 0.65, entity -> entity instanceof LivingEntity && entity != p);
                if (hit != null && (entity2 = hit.getHitEntity()) instanceof LivingEntity) {
                    LivingEntity target = (LivingEntity)entity2;
                    AttributeInstance attack = p.getAttribute(Attribute.ATTACK_DAMAGE);
                    target.damage((attack == null ? 1.0 : attack.getValue()) * SwordThrowListener.this.abilities.swordThrowDamageFraction(p), (Entity)p);
                    this.finish();
                    return;
                }
                if (block != null) {
                    this.finish();
                    return;
                }
                this.at.add(direction);
                display.teleport(this.at);
                float angle = (float)((double)this.ticks * Math.PI / 3.0);
                display.setTransformation(new Transformation(new Vector3f(), new Quaternionf().rotateX(angle), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
            }

            private void finish() {
                if (display.isValid()) {
                    display.remove();
                }
                this.cancel();
            }
        }.runTaskTimer(this.plugin, 0L, 1L);
    }
}

