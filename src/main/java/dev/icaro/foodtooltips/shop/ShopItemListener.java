package dev.icaro.foodtooltips.shop;

import dev.icaro.foodtooltips.protect.ProtectionService;
import dev.icaro.foodtooltips.shop.ShopItem;
import dev.icaro.foodtooltips.shop.ShopService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Color;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.block.Block;
import org.bukkit.entity.AbstractArrow;
import org.bukkit.entity.ArmorStand;
import org.bukkit.entity.Arrow;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Mob;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.entity.Snowball;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.CreatureSpawnEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.event.entity.EntityShootBowEvent;
import org.bukkit.event.entity.ProjectileHitEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.event.player.PlayerItemConsumeEvent;
import org.bukkit.event.player.PlayerMoveEvent;
import org.bukkit.inventory.EquipmentSlot;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.Damageable;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.metadata.FixedMetadataValue;
import org.bukkit.metadata.MetadataValue;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.projectiles.ProjectileSource;

public final class ShopItemListener
implements Listener {
    private final Plugin plugin;
    private final ShopService shop;
    private final ProtectionService protection;
    private final NamespacedKey coated;
    private final NamespacedKey solarFire;
    private final Map<String, Long> cooldowns = new HashMap<String, Long>();
    private final Map<String, Long> godMode = new HashMap<String, Long>();

    public ShopItemListener(Plugin p, ShopService s, ProtectionService protection) {
        this.plugin = p;
        this.shop = s;
        this.protection = protection;
        this.coated = new NamespacedKey("foodtooltips", "wither_coated");
        this.solarFire = new NamespacedKey("foodtooltips", "solar_fire");
        Bukkit.getScheduler().runTaskTimer(p, this::solarTick, 20L, 20L);
        Bukkit.getScheduler().runTask(p, this::removeOrphanBeaconMarkers);
    }

    private void removeOrphanBeaconMarkers() {
        for (World world : Bukkit.getWorlds()) {
            for (ArmorStand marker : world.getEntitiesByClass(ArmorStand.class)) {
                if (!marker.getScoreboardTags().contains("rpg_safe_beacon") || marker.getLocation().getBlock().getType() == Material.BEACON) continue;
                marker.remove();
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void use(PlayerInteractEvent e) {
        if (e.getHand() != EquipmentSlot.HAND && e.getHand() != EquipmentSlot.OFF_HAND) {
            return;
        }
        ShopItem type = this.shop.type(e.getItem());
        if (type == null || e.getAction() != Action.RIGHT_CLICK_AIR && e.getAction() != Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        Player p = e.getPlayer();
        switch (type) {
            case SUPREME_FOOD: {
                e.setCancelled(true);
                if (!this.cooldown(p, "food", 30000L)) break;
                p.setFoodLevel(20);
                p.setSaturation(20.0f);
                p.sendActionBar((Component)Component.text((String)"Comida Suprema utilizada", (TextColor)NamedTextColor.GOLD));
                break;
            }
            case MACHINE_BOW: {
                e.setCancelled(true);
                if (!this.cooldown(p, "machine", 5000L)) break;
                this.burst(p, e.getItem());
                break;
            }
            case FLAMETHROWER: {
                e.setCancelled(true);
                if (!this.cooldown(p, "flame", 8000L)) break;
                for (LivingEntity target : this.lineTargets(p, 12.0, 1.5)) {
                    target.setFireTicks(100);
                    target.damage(6.0, (Entity)p);
                }
                break;
            }
            case BOMB: 
            case LIGHTNING_PRISON: 
            case BLEEDING_DAGGER: 
            case MADNESS_POTION: {
                e.setCancelled(true);
                this.throwPayload(p, e.getItem(), type);
                break;
            }
            case METEOR: {
                e.setCancelled(true);
                if (!this.cooldown(p, "meteor", 60000L)) break;
                this.throwPayload(p, e.getItem(), type);
                break;
            }
            case GOD_POTION: {
                e.setCancelled(true);
                if (!this.cooldown(p, "god", 120000L)) break;
                this.consume(e.getItem());
                this.godMode.put(p.getUniqueId().toString(), System.currentTimeMillis() + 10000L);
                for (PotionEffectType effect : List.of(PotionEffectType.SPEED, PotionEffectType.STRENGTH, PotionEffectType.REGENERATION, PotionEffectType.RESISTANCE, PotionEffectType.FIRE_RESISTANCE, PotionEffectType.HASTE)) {
                    p.addPotionEffect(new PotionEffect(effect, 600, 1));
                }
                break;
            }
            case WITHER_COATING: {
                e.setCancelled(true);
                ItemStack sword = p.getInventory().getItemInMainHand();
                if (!sword.getType().name().endsWith("_SWORD")) break;
                sword.editMeta(m -> m.getPersistentDataContainer().set(this.coated, PersistentDataType.BYTE, 1));
                this.consume(e.getItem());
                p.sendMessage((Component)Component.text((String)"Wither aplicado permanentemente \u00e0 espada.", (TextColor)NamedTextColor.DARK_PURPLE));
                break;
            }
            case PORTAL: {
                break;
            }
            case EXCAVATOR: {
                Block clicked = e.getClickedBlock();
                if (clicked == null) break;
                e.setCancelled(true);
                if (this.protection.canBuild(p, clicked)) {
                    this.consume(e.getItem());
                    this.excavate(clicked);
                    break;
                }
                p.sendActionBar((Component)Component.text((String)"\u00c1rea protegida.", (TextColor)NamedTextColor.RED));
                break;
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void preventVanillaFood(PlayerItemConsumeEvent e) {
        ShopItem type = this.shop.type(e.getItem());
        if (type == ShopItem.SUPREME_FOOD || type == ShopItem.GOD_POTION) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void place(BlockPlaceEvent e) {
        ShopItem type = this.shop.type(e.getItemInHand());
        if (type != ShopItem.SAFE_BEACON) {
            return;
        }
        ArmorStand marker = (ArmorStand)e.getBlock().getWorld().spawn(e.getBlock().getLocation().add(0.5, 0.2, 0.5), ArmorStand.class, a -> {
            a.setInvisible(true);
            a.setMarker(true);
            a.setPersistent(true);
            a.addScoreboardTag("rpg_safe_beacon");
        });
    }

    @EventHandler(ignoreCancelled=true)
    public void breakBeacon(BlockBreakEvent e) {
        if (e.getBlock().getType() != Material.BEACON) {
            return;
        }
        for (ArmorStand marker : e.getBlock().getLocation().add(0.5, 0.2, 0.5).getNearbyEntitiesByType(ArmorStand.class, 1.0)) {
            if (!marker.getScoreboardTags().contains("rpg_safe_beacon")) continue;
            marker.remove();
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void hostileSpawn(CreatureSpawnEvent e) {
        if (!(e.getEntity() instanceof Enemy)) {
            return;
        }
        for (ArmorStand marker : e.getLocation().getNearbyEntitiesByType(ArmorStand.class, 16.0)) {
            if (!marker.getScoreboardTags().contains("rpg_safe_beacon")) continue;
            if (marker.getLocation().getBlock().getType() == Material.BEACON) {
                e.setCancelled(true);
                return;
            }
            marker.remove();
        }
    }

    @EventHandler(priority=EventPriority.LOWEST, ignoreCancelled=true)
    public void protectiveMovement(EntityDamageEvent e) {
        Entity entity = e.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player p = (Player)entity;
        if (this.godMode.getOrDefault(p.getUniqueId().toString(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && this.shop.type(p.getInventory().getBoots()) == ShopItem.HEAVY_BOOTS) {
            e.setCancelled(true);
            double damage = e.getDamage() + Optional.ofNullable(p.getAttribute(Attribute.ATTACK_DAMAGE)).map(a -> a.getValue()).orElse(1.0);
            for (Entity nearby : p.getNearbyEntities(4.0, 3.0, 4.0)) {
                LivingEntity living;
                if (!(nearby instanceof LivingEntity) || (living = (LivingEntity)nearby) == p) continue;
                living.damage(damage, (Entity)p);
            }
            p.getWorld().createExplosion(p.getLocation(), 0.0f, false, false);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void damage(EntityDamageEvent e) {
        Entity entity = e.getEntity();
        if (!(entity instanceof Player)) {
            return;
        }
        Player p = (Player)entity;
        if (this.godMode.getOrDefault(p.getUniqueId().toString(), 0L) > System.currentTimeMillis()) {
            e.setCancelled(true);
            return;
        }
        if (e.getCause() == EntityDamageEvent.DamageCause.FALL && this.shop.type(p.getInventory().getBoots()) == ShopItem.HEAVY_BOOTS) {
            e.setCancelled(true);
            double damage = e.getDamage() + Optional.ofNullable(p.getAttribute(Attribute.ATTACK_DAMAGE)).map(a -> a.getValue()).orElse(1.0);
            for (Entity nearby : p.getNearbyEntities(4.0, 3.0, 4.0)) {
                LivingEntity living;
                if (!(nearby instanceof LivingEntity) || (living = (LivingEntity)nearby) == p) continue;
                living.damage(damage, (Entity)p);
            }
            p.getWorld().createExplosion(p.getLocation(), 0.0f, false, false);
            return;
        }
        if (e.getFinalDamage() >= p.getHealth() && this.shop.type(p.getInventory().getChestplate()) == ShopItem.IMMORTAL_CHEST) {
            e.setCancelled(true);
            p.getInventory().setChestplate(null);
            p.setHealth(Math.max(1.0, p.getAttribute(Attribute.MAX_HEALTH).getValue() * 0.5));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 200, 2));
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0.0, 1.0, 0.0), 80);
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void melee(EntityDamageByEntityEvent e) {
        Player p;
        Entity entity;
        block7: {
            block6: {
                entity = e.getDamager();
                if (!(entity instanceof Player)) break block6;
                p = (Player)entity;
                entity = e.getEntity();
                if (entity instanceof LivingEntity) break block7;
            }
            return;
        }
        LivingEntity target = (LivingEntity)entity;
        ItemStack weapon = p.getInventory().getItemInMainHand();
        if (this.shop.type(weapon) == ShopItem.LIFESTEAL_DAGGER) {
            if (p.getLocation().distance(target.getLocation()) > 2.2) {
                e.setCancelled(true);
                return;
            }
            Bukkit.getScheduler().runTask(this.plugin, () -> p.setHealth(Math.min(p.getAttribute(Attribute.MAX_HEALTH).getValue(), p.getHealth() + e.getFinalDamage())));
        }
        if (weapon.hasItemMeta() && weapon.getItemMeta().getPersistentDataContainer().has(this.coated, PersistentDataType.BYTE)) {
            target.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, 100, 1));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void move(PlayerMoveEvent e) {
        Player p = e.getPlayer();
        if (p.getFallDistance() > 3.0f && this.shop.type(p.getInventory().getBoots()) == ShopItem.FLYING_BOOTS && this.cooldown(p, "glide", 20000L)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING, 100, 0));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void shoot(EntityShootBowEvent e) {
        Entity entity;
        if (!(e.getEntity() instanceof Player) || !((entity = e.getProjectile()) instanceof AbstractArrow)) {
            return;
        }
        AbstractArrow arrow = (AbstractArrow)entity;
        ShopItem ammo = this.shop.type(e.getConsumable());
        if (ammo == ShopItem.ANCESTRAL_ARROW || ammo == ShopItem.SOLAR_ARROW) {
            arrow.setMetadata("rpg_arrow", (MetadataValue)new FixedMetadataValue(this.plugin, ammo.name()));
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void projectile(ProjectileHitEvent e) {
        Projectile projectile = e.getEntity();
        if (projectile.hasMetadata("rpg_payload")) {
            Mob mob;
            LivingEntity target;
            Entity entity;
            Player p;
            Player owner;
            ShopItem type = ShopItem.valueOf(((MetadataValue)projectile.getMetadata("rpg_payload").getFirst()).asString());
            Location at = projectile.getLocation();
            ProjectileSource projectileSource = projectile.getShooter();
            Player player = owner = projectileSource instanceof Player ? (p = (Player)projectileSource) : null;
            if (type == ShopItem.METEOR && owner != null && this.protection.canBuild(owner, at.getBlock())) {
                this.meteorAt(owner, at);
            } else if (type == ShopItem.BOMB) {
                at.getWorld().createExplosion(at, 3.5f, false, true, (Entity)owner);
            } else if (type == ShopItem.LIGHTNING_PRISON) {
                ArrayList<Enemy> enemies = new ArrayList<>(at.getNearbyEntitiesByType(Enemy.class, 8.0));
                for (int i = 0; i < 5; ++i) {
                    Location strike = i < enemies.size() ? enemies.get(i).getLocation() : at.clone().add((Math.random() - 0.5) * 10.0, 0.0, (Math.random() - 0.5) * 10.0);
                    at.getWorld().strikeLightning(strike);
                }
            } else if (type == ShopItem.BLEEDING_DAGGER && (entity = e.getHitEntity()) instanceof LivingEntity && (target = (LivingEntity)entity).getType() != EntityType.SKELETON && target.getType() != EntityType.WITHER_SKELETON) {
                target.addPotionEffect(new PotionEffect(PotionEffectType.POISON, 160, 2));
            } else if (type == ShopItem.MADNESS_POTION && (entity = e.getHitEntity()) instanceof Mob && !this.boss((LivingEntity)(mob = (Mob)entity))) {
                mob.getNearbyEntities(12.0, 8.0, 12.0).stream().filter(x -> x instanceof Enemy && x != mob).findFirst().ifPresent(x -> mob.setTarget((LivingEntity)x));
            }
            projectile.remove();
        }
        if (projectile.hasMetadata("rpg_arrow") && e.getHitEntity() instanceof LivingEntity target) {
            ProjectileSource projectileSource;
            ShopItem type = ShopItem.valueOf(((MetadataValue)projectile.getMetadata("rpg_arrow").getFirst()).asString());
            if (type == ShopItem.ANCESTRAL_ARROW && !this.boss(target) && (projectileSource = projectile.getShooter()) instanceof Player) {
                Player p = (Player)projectileSource;
                target.damage(10.0, (Entity)p);
            }
            if (type == ShopItem.SOLAR_ARROW) {
                target.getPersistentDataContainer().set(this.solarFire, PersistentDataType.BYTE, 1);
                target.setFireTicks(40);
                target.getWorld().spawnParticle(Particle.DUST, target.getLocation().add(0.0, 1.0, 0.0), 30, 0.5, 0.5, 0.5, new Particle.DustOptions(Color.BLACK, 1.5f));
            }
        }
    }

    @EventHandler(priority=EventPriority.MONITOR)
    public void clearSolarFire(EntityDeathEvent e) {
        LivingEntity entity = e.getEntity();
        entity.getPersistentDataContainer().remove(this.solarFire);
        entity.setFireTicks(0);
        entity.setVisualFire(false);
    }

    private void burst(Player p, ItemStack bow) {
        for (int i = 0; i < 6; ++i) {
            Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
                if (!p.isOnline()) {
                    return;
                }
                Arrow arrow = (Arrow)p.launchProjectile(Arrow.class);
                arrow.setVelocity(p.getEyeLocation().getDirection().multiply(3));
                this.damage(bow, 3);
            }, (long)i * 2L);
        }
    }

    private void throwPayload(Player p, ItemStack item, ShopItem type) {
        this.consume(item);
        Snowball ball = (Snowball)p.launchProjectile(Snowball.class);
        ball.setItem(ItemStack.of((Material)type.icon()));
        ball.setVelocity(p.getEyeLocation().getDirection().multiply(1.6));
        ball.setMetadata("rpg_payload", (MetadataValue)new FixedMetadataValue(this.plugin, type.name()));
    }

    private List<LivingEntity> lineTargets(Player p, double range, double radius) {
        ArrayList<LivingEntity> found = new ArrayList<LivingEntity>();
        int i = 1;
        while ((double)i <= range) {
            Location point = p.getEyeLocation().add(p.getEyeLocation().getDirection().multiply(i));
            p.getWorld().spawnParticle(Particle.FLAME, point, 4, 0.2, 0.2, 0.2, 0.0);
            for (LivingEntity living : point.getNearbyLivingEntities(radius)) {
                if (living == p || found.contains(living)) continue;
                found.add(living);
            }
            ++i;
        }
        return found;
    }

    private void meteorAt(Player p, Location center) {
        p.sendMessage((Component)Component.text((String)"\u2604 Meteoro a caminho!", (TextColor)NamedTextColor.RED));
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> {
            center.getWorld().createExplosion(center, 7.0f, true, true, (Entity)p);
            for (int x = -5; x <= 5; ++x) {
                for (int z = -5; z <= 5; ++z) {
                    if (x * x + z * z > 25) continue;
                    Block b = center.clone().add((double)x, -1.0, (double)z).getBlock();
                    b.setType((x + z) % 5 == 0 ? Material.LAVA : ((x + z) % 2 == 0 ? Material.MAGMA_BLOCK : Material.NETHERRACK));
                }
            }
        }, 60L);
    }

    private void excavate(Block origin) {
        World w = origin.getWorld();
        for (int y = origin.getY(); y >= Math.max(w.getMinHeight(), origin.getY() - 89); --y) {
            for (int x = -1; x <= 1; ++x) {
                for (int z = -1; z <= 1; ++z) {
                    Block b = w.getBlockAt(origin.getX() + x, y, origin.getZ() + z);
                    if (b.getType() == Material.BEDROCK) continue;
                    b.setType(Material.AIR, false);
                }
            }
        }
    }

    private void consume(ItemStack item) {
        item.setAmount(item.getAmount() - 1);
    }

    private void damage(ItemStack item, int amount) {
        ItemMeta itemMeta = item.getItemMeta();
        if (!(itemMeta instanceof Damageable)) {
            return;
        }
        Damageable d = (Damageable)itemMeta;
        int next = d.getDamage() + amount;
        if (next >= item.getType().getMaxDurability()) {
            item.setAmount(0);
            return;
        }
        d.setDamage(next);
        item.setItemMeta((ItemMeta)d);
    }

    private boolean cooldown(Player p, String id, long millis) {
        long ready;
        String key = String.valueOf(p.getUniqueId()) + ":" + id;
        long now = System.currentTimeMillis();
        if (now < (ready = this.cooldowns.getOrDefault(key, 0L).longValue())) {
            p.sendActionBar((Component)Component.text((String)String.format(Locale.US, "Recarga: %.1fs", (double)(ready - now) / 1000.0), (TextColor)NamedTextColor.RED));
            return false;
        }
        this.cooldowns.put(key, now + millis);
        return true;
    }

    private boolean boss(LivingEntity e) {
        return e.getType() == EntityType.WARDEN || e.getType() == EntityType.WITHER || e.getType() == EntityType.ENDER_DRAGON;
    }

    private void solarTick() {
        for (World world : Bukkit.getWorlds()) {
            for (LivingEntity living : world.getLivingEntities()) {
                if (!living.getPersistentDataContainer().has(this.solarFire, PersistentDataType.BYTE)) continue;
                living.setFireTicks(40);
                living.getWorld().spawnParticle(Particle.DUST, living.getLocation().add(0.0, living.getHeight() * 0.6, 0.0), 6, 0.3, 0.4, 0.3, new Particle.DustOptions(Color.BLACK, 1.2f));
            }
        }
    }
}

