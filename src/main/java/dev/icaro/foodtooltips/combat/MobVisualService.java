package dev.icaro.foodtooltips.combat;

import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Random;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.TranslatableComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Animals;
import org.bukkit.entity.Display;
import org.bukkit.entity.Enemy;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Golem;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.entity.WaterMob;
import org.bukkit.plugin.Plugin;
import org.bukkit.util.Transformation;
import org.joml.Quaternionf;
import org.joml.Vector3f;

public final class MobVisualService {
    private static final String TAG = "foodtooltips_mob_label";
    private static final Pattern LEGACY = Pattern.compile("^\\[Lv\\.?\\s*\\d+].*\\d+/\\d+\u2764$");
    private final Plugin plugin;
    private final Map<UUID, TextDisplay> labels = new HashMap<UUID, TextDisplay>();
    private final Map<UUID, Long> renderState = new HashMap<UUID, Long>();
    private final Map<UUID, Set<UUID>> shownTo = new HashMap<UUID, Set<UUID>>();
    private final Random random = new Random();
    private final float labelRange;
    private final float damageRange;
    private final double maxDistanceSquared;

    public MobVisualService(Plugin p) {
        this.plugin = p;
        this.labelRange = (float)p.getConfig().getDouble("mob-visuals.label-view-range", 0.12);
        this.damageRange = (float)p.getConfig().getDouble("mob-visuals.damage-view-range", 0.25);
        double max = p.getConfig().getDouble("mob-visuals.label-max-distance-blocks", 10.0);
        this.maxDistanceSquared = max * max;
        this.removeOrphans();
    }

    public void track(LivingEntity entity) {
        if (!this.supported(entity) || this.labels.containsKey(entity.getUniqueId())) {
            return;
        }
        this.clearLegacyName(entity);
        TextDisplay d = (TextDisplay)entity.getWorld().spawn(entity.getLocation(), TextDisplay.class, x -> {
            x.addScoreboardTag(TAG);
            x.setPersistent(false);
            x.setGravity(false);
            x.setInvulnerable(true);
            x.setBillboard(Display.Billboard.CENTER);
            x.setSeeThrough(true);
            x.setShadowed(true);
            x.setViewRange(this.labelRange);
            x.setTeleportDuration(0);
            x.setInterpolationDuration(0);
            x.setTransformation(new Transformation(new Vector3f(0.0f, 0.55f, 0.0f), new Quaternionf(), new Vector3f(1.0f, 1.0f, 1.0f), new Quaternionf()));
            x.setVisibleByDefault(false);
        });
        entity.addPassenger((Entity)d);
        this.labels.put(entity.getUniqueId(), d);
        this.update(entity);
    }

    private boolean supported(LivingEntity e) {
        return e instanceof Enemy || e instanceof Player || e instanceof Animals || e instanceof WaterMob || e instanceof Golem;
    }

    public void tick() {
        Iterator<Map.Entry<UUID, TextDisplay>> it = this.labels.entrySet().iterator();
        while (it.hasNext()) {
            LivingEntity living;
            Map.Entry<UUID, TextDisplay> e = it.next();
            UUID id = e.getKey();
            TextDisplay d = e.getValue();
            Entity raw = Bukkit.getEntity((UUID)id);
            if (!(raw instanceof LivingEntity) || !(living = (LivingEntity)raw).isValid() || living.isDead() || !d.isValid()) {
                if (d.isValid()) {
                    d.remove();
                }
                it.remove();
                this.renderState.remove(id);
                this.shownTo.remove(id);
                continue;
            }
            if (!living.getPassengers().contains(d)) {
                living.addPassenger((Entity)d);
            }
            this.update(living);
            Set previous = this.shownTo.getOrDefault(id, Set.of());
            HashSet<UUID> nowVisible = new HashSet<UUID>();
            for (Player viewer : Bukkit.getOnlinePlayers()) {
                boolean visible;
                boolean bl = visible = viewer != living && viewer.getWorld() == living.getWorld() && viewer.getLocation().distanceSquared(living.getLocation()) <= this.maxDistanceSquared;
                if (visible) {
                    nowVisible.add(viewer.getUniqueId());
                    if (previous.contains(viewer.getUniqueId())) continue;
                    viewer.showEntity(this.plugin, (Entity)d);
                    continue;
                }
                if (!previous.contains(viewer.getUniqueId())) continue;
                viewer.hideEntity(this.plugin, (Entity)d);
            }
            this.shownTo.put(id, nowVisible);
        }
    }

    public int level(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        double hp = a == null ? e.getHealth() : a.getValue();
        return Math.max(1, (int)Math.round(hp / 5.0));
    }

    public void update(LivingEntity e) {
        TextDisplay d = this.labels.get(e.getUniqueId());
        if (d == null) {
            return;
        }
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        double max = a == null ? e.getHealth() : a.getValue();
        boolean showName = !(e instanceof Player) && e.customName() == null;
        long hp = Math.max(0L, Math.round(e.getHealth()));
        long maxHp = Math.round(max);
        long key = (hp * 100000L + maxHp) * 2L + (long)(showName ? 1 : 0);
        Long last = this.renderState.get(e.getUniqueId());
        if (last != null && last == key) {
            return;
        }
        this.renderState.put(e.getUniqueId(), key);
        TextComponent prefix = e instanceof Enemy ? Component.text((String)("[Lv" + this.level(e) + "] "), (TextColor)NamedTextColor.GRAY) : Component.empty();
        TextComponent named = showName ? ((TranslatableComponent)Component.translatable((String)e.getType().translationKey()).color((TextColor)NamedTextColor.RED)).append((Component)Component.space()) : Component.empty();
        d.text(prefix.append((Component)named).append((Component)Component.text((String)(hp + "/" + maxHp), (TextColor)NamedTextColor.GREEN)).append((Component)Component.text((String)"\u2764", (TextColor)NamedTextColor.RED)));
    }

    public void damageNumber(LivingEntity e, double damage, boolean critical) {
        Location at = e.getLocation().add((this.random.nextDouble() - 0.5) * 0.8, e.getHeight() * 0.7 + 0.4, (this.random.nextDouble() - 0.5) * 0.8);
        String value = this.number(damage);
        Component text = critical ? this.criticalNumber(value) : Component.text((String)value, (TextColor)NamedTextColor.RED);
        TextDisplay d = (TextDisplay)e.getWorld().spawn(at, TextDisplay.class, x -> {
            x.text(text);
            x.setPersistent(false);
            x.setGravity(false);
            x.setInvulnerable(true);
            x.setBillboard(Display.Billboard.CENTER);
            x.setSeeThrough(true);
            x.setShadowed(true);
            x.setViewRange(this.damageRange);
        });
        Bukkit.getScheduler().runTaskLater(this.plugin, () -> ((TextDisplay)d).remove(), 24L);
    }

    private Component criticalNumber(String value) {
        TextColor[] colors = new TextColor[]{NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.GREEN, NamedTextColor.AQUA, NamedTextColor.LIGHT_PURPLE};
        Component out = Component.text((String)"\u2726 ", (TextColor)NamedTextColor.GOLD).decorate(TextDecoration.BOLD);
        for (int i = 0; i < value.length(); ++i) {
            out = out.append(Component.text((String)String.valueOf(value.charAt(i)), (TextColor)colors[i % colors.length]).decorate(TextDecoration.BOLD));
        }
        return out.append(Component.text((String)" \u2726", (TextColor)NamedTextColor.LIGHT_PURPLE).decorate(TextDecoration.BOLD));
    }

    private String number(double value) {
        return Long.toString(Math.round(Math.max(0.0, value)));
    }

    private String humanize(String key) {
        String v = key.substring(key.indexOf(58) + 1).replace('_', ' ');
        return Character.toUpperCase(v.charAt(0)) + v.substring(1);
    }

    private void clearLegacyName(LivingEntity e) {
        if (e instanceof Player || e.customName() == null) {
            return;
        }
        String plain = PlainTextComponentSerializer.plainText().serialize(e.customName());
        if (LEGACY.matcher(plain).matches()) {
            e.customName(null);
            e.setCustomNameVisible(false);
        }
    }

    private void removeOrphans() {
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay d : w.getEntitiesByClass(TextDisplay.class)) {
                if (!d.getScoreboardTags().contains(TAG)) continue;
                d.remove();
            }
        }
    }

    public void shutdown() {
        this.labels.values().forEach(Entity::remove);
        this.labels.clear();
        this.renderState.clear();
        this.shownTo.clear();
        this.removeOrphans();
    }
}

