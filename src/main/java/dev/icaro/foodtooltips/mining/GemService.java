package dev.icaro.foodtooltips.mining;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.mining.GemType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Queue;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Chunk;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockExplodeEvent;
import org.bukkit.event.block.BlockPistonExtendEvent;
import org.bukkit.event.block.BlockPistonRetractEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.entity.EntityExplodeEvent;
import org.bukkit.event.world.ChunkLoadEvent;
import org.bukkit.event.world.ChunkPopulateEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class GemService
implements Listener {
    private static final String RUBY = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDE1OWIwMzI0M2JlMThhMTRmM2VhZTc2M2M0NTY1Yzc4ZjFmMzM5YTg3NDJkMjZmZGU1NDFiZTU5YjdkZTA3In19fQ==";
    private static final String SAPPHIRE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDhmOWYzY2VkNzE2Yzc5MGQ1MjQ1ZDRjZDllMmI3NjZhNTU3NjU0MmE4OGQ1YjE0NGFlMWQ3YjA0MjYwMzc4YSJ9fX0=";
    private static final String EMERALD = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTBkZDIwZDI5ZDc3NmI3OWU0ODUyMDBhYmVkY2M2ZDk0YzExYmQ0Y2E1YjE0OWM2MGE0MDQxYzQ2NjhiYjZhOCJ9fX0=";
    private static final String AMETHYST = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzZhOWU0ODFmMDMxMWRjYjU0M2VlZjllNjQ3YzkyYWY5Zjk0MjE0MjAyMDU3YTcyMGIzZDliYzhkY2NhMDlmZCJ9fX0=";
    private static final String AMBER = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTUwOWMyMzZjZjg5OGZlNjJjN2I1NDQ2ODE4MzM4MTYwY2I5NTU2MzQ4NmQzZTI5YWZmZmRmODg3N2MwOGJmNCJ9fX0=";
    private static final String ONYX = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTg4YmNlNDk3Y2ZhNWY2MTE4MzlmNmRhMjFjOTVkMzRlM2U3MjNjMmNjNGMzYzMxOWI1NjI3NzNkMTIxNiJ9fX0=";
    private final NamespacedKey dataKey;
    private final NamespacedKey itemKey;
    private final NamespacedKey generationKey;
    private final Map<UUID, Inventory> menus = new HashMap<UUID, Inventory>();
    private final Queue<Chunk> queue = new ArrayDeque<Chunk>();
    private final Set<String> queued = new HashSet<String>();

    public GemService(Plugin p) {
        this.dataKey = new NamespacedKey(p, "natural_gems");
        this.itemKey = new NamespacedKey(p, "gem_type");
        this.generationKey = new NamespacedKey(p, "gem_generation_v3");
        Bukkit.getScheduler().runTask(p, () -> Bukkit.getWorlds().stream().filter(w -> w.getEnvironment() == World.Environment.NORMAL).forEach(w -> Arrays.stream(w.getLoadedChunks()).forEach(this::enqueue)));
        Bukkit.getScheduler().runTaskTimer(p, () -> {
            Chunk chunk = this.queue.poll();
            if (chunk == null) {
                return;
            }
            this.queued.remove(this.chunkKey(chunk));
            if (chunk.isLoaded()) {
                this.generate(chunk);
            }
        }, 1L, 1L);
    }

    @EventHandler
    public void populate(ChunkPopulateEvent e) {
        this.enqueue(e.getChunk());
    }

    @EventHandler
    public void load(ChunkLoadEvent e) {
        this.enqueue(e.getChunk());
    }

    private void enqueue(Chunk chunk) {
        if (chunk.getWorld().getEnvironment() != World.Environment.NORMAL || chunk.getPersistentDataContainer().has(this.generationKey, PersistentDataType.BYTE)) {
            return;
        }
        if (this.queued.add(this.chunkKey(chunk))) {
            this.queue.add(chunk);
        }
    }

    private String chunkKey(Chunk c) {
        return String.valueOf(c.getWorld().getUID()) + ":" + c.getX() + ":" + c.getZ();
    }

    private void generate(Chunk c) {
        if (c.getPersistentDataContainer().has(this.generationKey, PersistentDataType.BYTE)) {
            return;
        }
        ThreadLocalRandom r = ThreadLocalRandom.current();
        ArrayList<String> records = new ArrayList<>();
        String previous = (String)c.getPersistentDataContainer().get(this.dataKey, PersistentDataType.STRING);
        if (previous != null && !previous.isBlank()) {
            records.addAll(Arrays.asList(previous.split(";")));
        }
        int veins = 1 + (r.nextInt(100) < 15 ? 1 : 0);
        for (int i = 0; i < veins; ++i) {
            int x = 0;
            int y = 0;
            int z = 0;
            boolean found = false;
            for (int attempt = 0; attempt < 24; ++attempt) {
                x = r.nextInt(16);
                if (c.getBlock(x, y = r.nextInt(-60, -7), z = r.nextInt(16)).getType() != Material.DEEPSLATE) continue;
                found = true;
                break;
            }
            if (!found) continue;
            GemType type = this.choose(r);
            int size = r.nextInt(1, 4);
            for (int n = 0; n < size; ++n) {
                int bz;
                int by;
                int bx = Math.max(0, Math.min(15, x + r.nextInt(-1, 2)));
                Block block = c.getBlock(bx, by = Math.max(-63, Math.min(-8, y + r.nextInt(-1, 2))), bz = Math.max(0, Math.min(15, z + r.nextInt(-1, 2))));
                if (block.getType() != Material.DEEPSLATE) continue;
                block.setType(type.block(), false);
                records.add(bx + "," + by + "," + bz + "," + type.name());
            }
        }
        c.getPersistentDataContainer().set(this.dataKey, PersistentDataType.STRING, String.join((CharSequence)";", records));
        c.getPersistentDataContainer().set(this.generationKey, PersistentDataType.BYTE, (byte) 1);
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void breakGem(BlockBreakEvent e) {
        Chunk c = e.getBlock().getChunk();
        String raw = (String)c.getPersistentDataContainer().get(this.dataKey, PersistentDataType.STRING);
        if (raw == null) {
            return;
        }
        String prefix = Math.floorMod(e.getBlock().getX(), 16) + "," + e.getBlock().getY() + "," + Math.floorMod(e.getBlock().getZ(), 16) + ",";
        GemType found = null;
        ArrayList<String> keep = new ArrayList<String>();
        for (String record : raw.split(";")) {
            if (record.startsWith(prefix)) {
                try {
                    found = GemType.valueOf(record.substring(prefix.length()));
                }
                catch (Exception exception) {}
                continue;
            }
            if (record.isBlank()) continue;
            keep.add(record);
        }
        if (found == null) {
            return;
        }
        e.setDropItems(false);
        c.getPersistentDataContainer().set(this.dataKey, PersistentDataType.STRING, String.join((CharSequence)";", keep));
        ItemStack gem = this.create(found, Language.of(e.getPlayer()));
        for (ItemStack overflow : e.getPlayer().getInventory().addItem(new ItemStack[]{gem}).values()) {
            e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), overflow);
        }
        e.getPlayer().sendActionBar((Component)Component.text((String)("\u2726 " + found.name(Language.of(e.getPlayer()) == Language.PT) + "!"), (TextColor)NamedTextColor.LIGHT_PURPLE));
        e.getBlock().getWorld().spawnParticle(Particle.END_ROD, e.getBlock().getLocation().add(0.5, 0.5, 0.5), 16, 0.3, 0.3, 0.3, 0.03);
    }

    @EventHandler(ignoreCancelled=true)
    public void pistonExtend(BlockPistonExtendEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> this.gemAt((Block)b) != null)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void pistonRetract(BlockPistonRetractEvent e) {
        if (e.getBlocks().stream().anyMatch(b -> this.gemAt((Block)b) != null)) {
            e.setCancelled(true);
        }
    }

    @EventHandler(ignoreCancelled=true)
    public void entityExplode(EntityExplodeEvent e) {
        e.blockList().removeIf(b -> this.gemAt((Block)b) != null);
    }

    @EventHandler(ignoreCancelled=true)
    public void blockExplode(BlockExplodeEvent e) {
        e.blockList().removeIf(b -> this.gemAt((Block)b) != null);
    }

    private GemType gemAt(Block block) {
        if (!block.getType().name().endsWith("_STAINED_GLASS")) {
            return null;
        }
        String raw = (String)block.getChunk().getPersistentDataContainer().get(this.dataKey, PersistentDataType.STRING);
        if (raw == null || raw.isBlank()) {
            return null;
        }
        String prefix = Math.floorMod(block.getX(), 16) + "," + block.getY() + "," + Math.floorMod(block.getZ(), 16) + ",";
        for (String record : raw.split(";")) {
            if (!record.startsWith(prefix)) continue;
            try {
                return GemType.valueOf(record.substring(prefix.length()));
            }
            catch (IllegalArgumentException ignored) {
                return null;
            }
        }
        return null;
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void preventGemPlacement(BlockPlaceEvent e) {
        ItemStack held = e.getItemInHand();
        if (!held.hasItemMeta() || !held.getItemMeta().getPersistentDataContainer().has(this.itemKey, PersistentDataType.STRING)) {
            return;
        }
        e.setCancelled(true);
        e.getPlayer().sendActionBar((Component)Component.text((String)Language.of(e.getPlayer()).choose("Gemas n\u00e3o podem ser posicionadas.", "Gems cannot be placed."), (TextColor)NamedTextColor.RED));
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)l.choose("Gemas", "Gems"));
        ItemStack fill = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; ++i) {
            inv.setItem(i, fill);
        }
        int[] slots = new int[]{10, 11, 12, 13, 14, 15, 16, 19, 20, 21, 22, 23, 24, 25, 30, 32};
        GemType[] values = GemType.values();
        for (int i = 0; i < values.length; ++i) {
            GemType g = values[i];
            inv.setItem(slots[i], this.create(g, l, List.of(this.line(l.choose("Bloco natural: ", "Natural block: "), NamedTextColor.GRAY).append((Component)Component.translatable((String)g.block().translationKey())), this.line(l.choose("Camadas: Y -60 a -8", "Layers: Y -60 to -8"), NamedTextColor.AQUA), this.line(l.choose("Atributo de encaixe: ", "Socket attribute: ") + g.attribute(l == Language.PT), NamedTextColor.GREEN))));
        }
        inv.setItem(49, this.item(Material.ARROW, l.choose("Voltar ao Comp\u00eandio", "Back to Compendium"), List.of()));
        p.openInventory(inv);
        this.menus.put(p.getUniqueId(), inv);
    }

    public boolean viewing(Player p) {
        return this.menus.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        this.menus.remove(p.getUniqueId());
    }

    public ItemStack create(GemType type, Language l) {
        return this.create(type, l, List.of(this.line(l.choose("Gema lapid\u00e1vel", "Socketable gemstone"), NamedTextColor.GRAY), this.line(l.choose("Atributo: ", "Attribute: ") + type.attribute(l == Language.PT), NamedTextColor.GREEN)));
    }

    private ItemStack create(GemType type, Language l, List<Component> lore) {
        ItemStack i = this.item(Material.PLAYER_HEAD, type.name(l == Language.PT), lore);
        SkullMeta m = (SkullMeta)i.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile((UUID)UUID.randomUUID());
        profile.setProperty(new ProfileProperty("textures", this.faithfulTexture(type)));
        m.setPlayerProfile(profile);
        m.getPersistentDataContainer().set(this.itemKey, PersistentDataType.STRING, type.name());
        i.setItemMeta((ItemMeta)m);
        return i;
    }

    private String faithfulTexture(GemType type) {
        return switch (type) {
            case GemType.TOURMALINE -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDUwMjNhY2YyNTIzMzNjZWZjNjY4ZDg5YzEwYzE2MDA4NDc5NjBjMWNmN2QzZTdiYzE3NTAzNWU4MzcxY2ExNSJ9fX0=";
            case GemType.SMOKY_QUARTZ -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODMyMGIzZDQzYTRlY2EzMTZiM2MwZmJlZTU0N2FjZTJjNjBlZTcyM2NiZjMxYjgzZjQ5ZjhkNDM1NDgxMTdjNSJ9fX0=";
            case GemType.TIGER_EYE -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGU4YmJmYjcyOWZmOTE3Zjk1MGVjYTdkNzUzMzE3YzFjNzA0ZDAyNmVhMTAyNWVmMzFhZWFlNWE0MGIzYzg4In19fQ==";
            default -> this.texture(type);
        };
    }

    private GemType choose(ThreadLocalRandom r) {
        int total = Arrays.stream(GemType.values()).mapToInt(GemType::weight).sum();
        int roll = r.nextInt(total);
        for (GemType type : GemType.values()) {
            if ((roll -= type.weight()) >= 0) continue;
            return type;
        }
        return GemType.OPAL;
    }

    private String texture(GemType t) {
        return switch (t) {
            default -> throw new MatchException(null, null);
            case GemType.OPAL -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMWQ5ZDJiNWEwNjQxZjNhN2Q3YWFhYmNhYmYyOTRkNmVlMGY5ZDY2MjE2M2Y1MTYyNmFiODIzNTA5NGMyM2NlYyJ9fX0=";
            case GemType.AMBER -> AMBER;
            case GemType.TOURMALINE -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWM2NDlhMGJiMjY1OWQ4ZjAzNzBjOWRhMWQwMGIzMmI1NmQwOTg5Nzg4ZWJmZGY5ODQwMmNjYmYzZTk2OGE3NSJ9fX0=";
            case GemType.AQUAMARINE -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNWE1OGEzOGY3N2I1ZjBmMzYxMGIxNjRkYTI5ZTkwMTY2MWI0NjI4YTQ4MmQ1ZjcwMmI3MTA1NGY2NjVlMzUyYyJ9fX0=";
            case GemType.TOPAZ -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYTMzZTkyYjA2NTcxOTZjYmExODZjZTc0ZjIwMzhiOTNiZThlNDI1MWFlMmNhYTNlYmY1MWNkZWYwM2E3NDM4NCJ9fX0=";
            case GemType.PERIDOT -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTRjZGZiODhjZjhjMzY1NjMwNDI1NGU0ZDY3YTBhYjAyZjZkMjdjNzlmMWIxZTQwNGRjM2E3Y2U0ZWIyZGUwMCJ9fX0=";
            case GemType.ROSE_QUARTZ -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzUzNjJmOTBmZjI5Y2NkNDBmNDIzNWEwNWRkOTFkZjA5MWNmNDNmM2EyZjcxNDE0ZGZiYmNhNmU1ZDM2MzRkOSJ9fX0=";
            case GemType.SMOKY_QUARTZ -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvYzhlMmZhZjI1ZmI0MDAxYjRlZDQyYzhiNDUwOGJiODA2ZWUzY2Y3YTRiMTNmMzgyMGVlMTM0ZWJmODJjMTI4YyJ9fX0=";
            case GemType.MOONSTONE -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvOWQ5M2VkOTJiMWE5YTI0YWQwNzJhY2Y2ZThmY2NmY2VkZTFiOGMyNzEzNmEyYjQyNTc2ZWU4NWRmN2RjMTE0YiJ9fX0=";
            case GemType.TURQUOISE -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNTRkYTU4NWY5NDE0NTNhZDYyOGVjMDk3MTBmNmU0NTU1NGE5ZjUyZWZhNWIwNWVkYTI1MzgzMDQ1MGM1YmY5OCJ9fX0=";
            case GemType.AMETHYST -> AMETHYST;
            case GemType.SAPPHIRE -> SAPPHIRE;
            case GemType.TIGER_EYE -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDUwMjNhY2YyNTIzMzNjZWZjNjY4ZDg5YzEwYzE2MDA4NDc5NjBjMWNmN2QzZTdiYzE3NTAzNWU4MzcxY2ExNSJ9fX0=";
            case GemType.JADE -> EMERALD;
            case GemType.RUBY -> RUBY;
            case GemType.ONYX -> ONYX;
        };
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text((String)s, (TextColor)c);
    }

    private ItemStack item(Material mat, String name, List<Component> lore) {
        ItemStack i = ItemStack.of((Material)mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(Component.text((String)name, (TextColor)NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        i.setItemMeta(m);
        return i;
    }
}

