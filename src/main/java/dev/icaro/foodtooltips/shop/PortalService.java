package dev.icaro.foodtooltips.shop;

import dev.icaro.foodtooltips.shop.ShopItem;
import dev.icaro.foodtooltips.shop.ShopService;
import io.papermc.paper.event.player.AsyncChatEvent;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Display;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.entity.TextDisplay;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.Action;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.player.PlayerInteractEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.plugin.Plugin;

public final class PortalService
implements Listener {
    private static final String TAG = "rpg_portal_hologram";
    private static final NamedTextColor[] COLORS = new NamedTextColor[]{NamedTextColor.WHITE, NamedTextColor.RED, NamedTextColor.GOLD, NamedTextColor.YELLOW, NamedTextColor.GREEN, NamedTextColor.AQUA, NamedTextColor.BLUE, NamedTextColor.LIGHT_PURPLE};
    private static final Material[] DYES = new Material[]{Material.WHITE_DYE, Material.RED_DYE, Material.ORANGE_DYE, Material.YELLOW_DYE, Material.LIME_DYE, Material.LIGHT_BLUE_DYE, Material.BLUE_DYE, Material.MAGENTA_DYE};
    private final Plugin plugin;
    private final ShopService shop;
    private final File file;
    private final Map<UUID, Portal> portals = new LinkedHashMap<UUID, Portal>();
    private final Map<String, UUID> locations = new HashMap<String, UUID>();
    private final Map<UUID, View> views = new HashMap<UUID, View>();
    private final Map<UUID, UUID> renaming = new HashMap<UUID, UUID>();
    private final Map<UUID, Long> cooldowns = new HashMap<UUID, Long>();

    public PortalService(Plugin p, ShopService s) {
        this.plugin = p;
        this.shop = s;
        this.file = new File(p.getDataFolder(), "portals.yml");
        this.removeHologramOrphans();
        this.load();
        Bukkit.getScheduler().runTaskTimer(p, this::tick, 10L, 10L);
    }

    @EventHandler(priority=EventPriority.HIGHEST)
    public void interact(PlayerInteractEvent e) {
        if (e.getAction() != Action.RIGHT_CLICK_BLOCK || e.getClickedBlock() == null) {
            return;
        }
        Player p = e.getPlayer();
        Portal existing = this.at(e.getClickedBlock().getLocation());
        if (existing != null) {
            e.setCancelled(true);
            if (!existing.owner.equals(p.getUniqueId())) {
                p.sendMessage((Component)Component.text((String)"Somente o dono pode configurar este portal.", (TextColor)NamedTextColor.RED));
                return;
            }
            this.openMain(p, existing);
            return;
        }
        if (this.shop.type(e.getItem()) != ShopItem.PORTAL) {
            return;
        }
        e.setCancelled(true);
        Block target = e.getClickedBlock().getRelative(e.getBlockFace());
        if (!target.isEmpty()) {
            p.sendMessage((Component)Component.text((String)"N\u00e3o h\u00e1 espa\u00e7o para instalar o portal.", (TextColor)NamedTextColor.RED));
            return;
        }
        target.setType(Material.RESPAWN_ANCHOR);
        Portal portal = new Portal(UUID.randomUUID(), p.getUniqueId(), target.getLocation(), "Portal " + (this.owned(p).size() + 1), NamedTextColor.LIGHT_PURPLE, null);
        this.portals.put(portal.id, portal);
        this.locations.put(this.key(portal.location), portal.id);
        this.spawnHologram(portal);
        this.consume(e.getItem());
        this.save();
        this.openMain(p, portal);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void breakPortal(BlockBreakEvent e) {
        Portal portal = this.at(e.getBlock().getLocation());
        if (portal == null) {
            return;
        }
        this.remove(portal, true);
        e.getPlayer().sendMessage((Component)Component.text((String)"Portal removido. O link e o teleporte foram apagados.", (TextColor)NamedTextColor.RED));
    }

    @EventHandler(ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        HumanEntity humanEntity = e.getWhoClicked();
        if (!(humanEntity instanceof Player)) {
            return;
        }
        Player p = (Player)humanEntity;
        View view = this.views.get(p.getUniqueId());
        if (view == null) {
            return;
        }
        e.setCancelled(true);
        Portal portal = this.portals.get(view.portal);
        if (portal == null) {
            p.closeInventory();
            return;
        }
        int slot = e.getRawSlot();
        if (view.type == ViewType.MAIN) {
            if (slot == 11) {
                this.renaming.put(p.getUniqueId(), portal.id);
                p.closeInventory();
                p.sendMessage((Component)Component.text((String)"Digite o novo nome do portal no chat. Use 'cancelar' para sair.", (TextColor)NamedTextColor.YELLOW));
            } else if (slot == 13) {
                this.openColors(p, portal);
            } else if (slot == 15) {
                this.openLinks(p, portal);
            } else if (slot == 22) {
                p.closeInventory();
            }
        } else if (view.type == ViewType.COLORS) {
            if (slot >= 10 && slot < 10 + COLORS.length) {
                portal.color = COLORS[slot - 10];
                this.updateHologram(portal);
                this.save();
                this.openMain(p, portal);
            } else if (slot == 22) {
                this.openMain(p, portal);
            }
        } else if (view.type == ViewType.LINKS) {
            UUID selected = view.buttons.get(slot);
            if (selected != null) {
                Portal other = this.portals.get(selected);
                if (other != null) {
                    this.link(portal, other);
                }
                this.openMain(p, portal);
            } else if (slot == 49) {
                this.openMain(p, portal);
            }
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        this.views.remove(e.getPlayer().getUniqueId());
    }

    @EventHandler
    public void rename(AsyncChatEvent e) {
        UUID portalId = this.renaming.remove(e.getPlayer().getUniqueId());
        if (portalId == null) {
            return;
        }
        e.setCancelled(true);
        String value = PlainTextComponentSerializer.plainText().serialize(e.message()).trim();
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            Portal portal = this.portals.get(portalId);
            if (portal == null) {
                return;
            }
            if (value.equalsIgnoreCase("cancelar") || value.equalsIgnoreCase("cancel")) {
                this.openMain(e.getPlayer(), portal);
                return;
            }
            portal.name = value.isBlank() ? "Portal" : value.substring(0, Math.min(32, value.length()));
            this.updateHologram(portal);
            this.save();
            this.openMain(e.getPlayer(), portal);
        });
    }

    private void openMain(Player p, Portal portal) {
        Inventory inv = Bukkit.createInventory(null, (int)27, (String)("Portal \u2022 " + portal.name));
        this.fill(inv);
        inv.setItem(11, this.item(Material.NAME_TAG, "Renomear", List.of(Component.text((String)portal.name, (TextColor)portal.color), Component.text((String)"Clique e digite o nome no chat.", (TextColor)NamedTextColor.GRAY))));
        inv.setItem(13, this.item(Material.MAGENTA_DYE, "Cor do nome", List.of(Component.text((String)"Cor atual", (TextColor)portal.color), Component.text((String)"Clique para escolher.", (TextColor)NamedTextColor.YELLOW))));
        String linked = portal.link == null ? "Nenhum" : Optional.ofNullable(this.portals.get(portal.link)).map(x -> x.name).orElse("Inv\u00e1lido");
        inv.setItem(15, this.item(Material.ENDER_PEARL, "Vincular portal", List.of(Component.text((String)("Link atual: " + linked), (TextColor)NamedTextColor.AQUA), Component.text((String)"Clique para selecionar outro portal.", (TextColor)NamedTextColor.YELLOW))));
        inv.setItem(22, this.item(Material.BARRIER, "Fechar", List.of()));
        p.openInventory(inv);
        this.views.put(p.getUniqueId(), new View(ViewType.MAIN, portal.id, Map.of()));
    }

    private void openColors(Player p, Portal portal) {
        Inventory inv = Bukkit.createInventory(null, (int)27, (String)("Cor \u2022 " + portal.name));
        this.fill(inv);
        for (int i = 0; i < COLORS.length; ++i) {
            inv.setItem(10 + i, this.item(DYES[i], "Cor", List.of(Component.text((String)portal.name, (TextColor)COLORS[i]))));
        }
        inv.setItem(22, this.item(Material.ARROW, "Voltar", List.of()));
        p.openInventory(inv);
        this.views.put(p.getUniqueId(), new View(ViewType.COLORS, portal.id, Map.of()));
    }

    private void openLinks(Player p, Portal portal) {
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)("Vincular \u2022 " + portal.name));
        this.fill(inv);
        HashMap<Integer, UUID> buttons = new HashMap<Integer, UUID>();
        int slot = 10;
        for (Portal other : this.owned(p)) {
            if (other.id.equals(portal.id)) continue;
            if (slot % 9 == 8) {
                slot += 2;
            }
            if (slot >= 45) break;
            inv.setItem(slot, this.item(Material.RESPAWN_ANCHOR, other.name, List.of(Component.text((String)"Clique para criar v\u00ednculo de ida e volta.", (TextColor)NamedTextColor.YELLOW))));
            buttons.put(slot++, other.id);
        }
        inv.setItem(49, this.item(Material.ARROW, "Voltar", List.of()));
        p.openInventory(inv);
        this.views.put(p.getUniqueId(), new View(ViewType.LINKS, portal.id, buttons));
    }

    private void link(Portal a, Portal b) {
        this.unlink(a);
        this.unlink(b);
        a.link = b.id;
        b.link = a.id;
        this.save();
        this.updateHologram(a);
        this.updateHologram(b);
    }

    private void unlink(Portal portal) {
        if (portal.link == null) {
            return;
        }
        Portal old = this.portals.get(portal.link);
        if (old != null && portal.id.equals(old.link)) {
            old.link = null;
            this.updateHologram(old);
        }
        portal.link = null;
    }

    private void tick() {
        for (Portal portal : new ArrayList<Portal>(this.portals.values())) {
            Portal destination;
            if (portal.location.getBlock().getType() != Material.RESPAWN_ANCHOR) {
                this.remove(portal, false);
                continue;
            }
            if (portal.link == null || (destination = this.portals.get(portal.link)) == null) continue;
            Location trigger = portal.location.clone().add(0.5, 1.0, 0.5);
            for (Player player : trigger.getNearbyPlayers(1.2)) {
                long now = System.currentTimeMillis();
                if (this.cooldowns.getOrDefault(player.getUniqueId(), 0L) > now) continue;
                this.cooldowns.put(player.getUniqueId(), now + 2500L);
                player.teleport(destination.location.clone().add(0.5, 1.1, 0.5));
            }
        }
    }

    private void remove(Portal portal, boolean breakBlock) {
        this.unlink(portal);
        this.portals.remove(portal.id);
        this.locations.remove(this.key(portal.location));
        if (portal.hologram != null && portal.hologram.isValid()) {
            portal.hologram.remove();
        }
        if (breakBlock && portal.location.getBlock().getType() == Material.RESPAWN_ANCHOR) {
            portal.location.getBlock().setType(Material.AIR);
        }
        this.save();
    }

    private void spawnHologram(Portal portal) {
        TextDisplay display;
        portal.hologram = display = (TextDisplay)portal.location.getWorld().spawn(portal.location.clone().add(0.5, 1.65, 0.5), TextDisplay.class, d -> {
            d.addScoreboardTag(TAG);
            d.setPersistent(false);
            d.setBillboard(Display.Billboard.CENTER);
            d.setSeeThrough(true);
            d.setShadowed(true);
            d.setViewRange(0.25f);
        });
        this.updateHologram(portal);
    }

    private void updateHologram(Portal p) {
        if (p.hologram == null || !p.hologram.isValid()) {
            this.spawnHologram(p);
        } else {
            p.hologram.text((Component)Component.text((String)p.name, (TextColor)p.color));
        }
    }

    private Portal at(Location location) {
        UUID id = this.locations.get(this.key(location));
        return id == null ? null : this.portals.get(id);
    }

    private List<Portal> owned(Player p) {
        return this.portals.values().stream().filter(x -> x.owner.equals(p.getUniqueId())).toList();
    }

    private String key(Location l) {
        return String.valueOf(l.getWorld().getUID()) + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private void load() {
        YamlConfiguration y = YamlConfiguration.loadConfiguration((File)this.file);
        for (String raw : y.getKeys(false)) {
            try {
                UUID id = UUID.fromString(raw);
                UUID owner = UUID.fromString(y.getString(raw + ".owner"));
                UUID worldId = UUID.fromString(y.getString(raw + ".world"));
                World world = Bukkit.getWorld((UUID)worldId);
                if (world == null) continue;
                Location loc = new Location(world, (double)y.getInt(raw + ".x"), (double)y.getInt(raw + ".y"), (double)y.getInt(raw + ".z"));
                NamedTextColor color = (NamedTextColor)NamedTextColor.NAMES.value((Object)y.getString(raw + ".color", "light_purple"));
                String linked = y.getString(raw + ".link");
                Portal portal = new Portal(id, owner, loc, y.getString(raw + ".name", "Portal"), color == null ? NamedTextColor.LIGHT_PURPLE : color, linked == null ? null : UUID.fromString(linked));
                this.portals.put(id, portal);
                this.locations.put(this.key(loc), id);
                this.spawnHologram(portal);
            }
            catch (Exception exception) {}
        }
    }

    private void save() {
        YamlConfiguration y = new YamlConfiguration();
        for (Portal p : this.portals.values()) {
            String base = p.id.toString();
            y.set(base + ".owner", (Object)p.owner.toString());
            y.set(base + ".world", (Object)p.location.getWorld().getUID().toString());
            y.set(base + ".x", (Object)p.location.getBlockX());
            y.set(base + ".y", (Object)p.location.getBlockY());
            y.set(base + ".z", (Object)p.location.getBlockZ());
            y.set(base + ".name", (Object)p.name);
            y.set(base + ".color", (Object)p.color.toString());
            y.set(base + ".link", p.link == null ? null : p.link.toString());
        }
        try {
            y.save(this.file);
        }
        catch (IOException ex) {
            this.plugin.getLogger().warning("Could not save portals.yml: " + ex.getMessage());
        }
    }

    private void removeHologramOrphans() {
        for (World w : Bukkit.getWorlds()) {
            for (TextDisplay display : w.getEntitiesByClass(TextDisplay.class)) {
                if (!display.getScoreboardTags().contains(TAG)) continue;
                display.remove();
            }
        }
    }

    private void consume(ItemStack item) {
        item.setAmount(item.getAmount() - 1);
    }

    private void fill(Inventory inv) {
        ItemStack pane = this.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); ++i) {
            inv.setItem(i, pane);
        }
    }

    private ItemStack item(Material material, String name, List<Component> lore) {
        ItemStack stack = ItemStack.of((Material)material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text((String)name, (TextColor)NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        stack.setItemMeta(meta);
        return stack;
    }

    private static final class Portal {
        final UUID id;
        final UUID owner;
        final Location location;
        String name;
        NamedTextColor color;
        UUID link;
        TextDisplay hologram;

        Portal(UUID id, UUID owner, Location location, String name, NamedTextColor color, UUID link) {
            this.id = id;
            this.owner = owner;
            this.location = location;
            this.name = name;
            this.color = color;
            this.link = link;
        }
    }

    private record View(ViewType type, UUID portal, Map<Integer, UUID> buttons) {
    }

    private static enum ViewType {
        MAIN,
        COLORS,
        LINKS;

    }
}

