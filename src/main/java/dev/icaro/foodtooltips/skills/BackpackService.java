package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.mining.MiningCatalog;
import dev.icaro.foodtooltips.skills.BackpackType;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.SkillType;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.plugin.Plugin;

public final class BackpackService {
    private static final String BROWN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODM1MWU1MDU5ODk4MzhlMjcyODdlN2FmYmM3Zjk3ZTc5NmNhYjVmMzU5OGE3NjE2MGMxMzFjOTQwZDBjNSJ9fX0=";
    private static final String BLACK = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvMTUwNjBlZjU3ZmY0Y2UwYTg1NDgyZWQwNDU0YzdmZTAzOGFhNDU1ZDNjMjY3NTM0ZTI5ZWRjODE0MWM1YjVjZSJ9fX0=";
    private static final String GREEN = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTc4NjY4MDVmODc1MzM5ZWZiOTRlNjY3ODM3YzE5M2YyYTFiY2VkZWM4YjQxYmFiYmJmMGJiN2E3YzhmNjE0OCJ9fX0=";
    private static final String BLUE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvODIyNGIyYzczOTFlYjViZmNiMjc4NDMxZDVjODI3Y2IyNjM0OTUyNmM3YmM1MzViMWU5NWY2ZGY5ZjNmZGYzIn19fQ==";
    private static final String MINING_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTk3M2ZmYWMyOGVmYzMzNGVmYWFjZjYxZmVjNTcyMmNmZjBjOTg1OTUxZTVkMjBhNjIyOWNkMTU0YjdlMTljIn19fQ==";
    private static final String ENCHANTING_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZTJjNjJkODc2NjNjNTg4YWNiZDdjYWZiNWVlZjZhYTNjODg2YWVhZmU3YWJhYzJiOGE1MjcyYzM4OThhNWRhYyJ9fX0=";
    private static final String ALCHEMY_TEXTURE = "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZDBkM2MzMDRmZjAxOGZjZTY1MDk5ZWFlZDlkNjhhNGM0OTAxNGFlNzA2MTc2ZjhlZTE4NzcyYTdiMzYyZjU4NSJ9fX0=";
    private final Plugin plugin;
    private final CombatSkillService combat;
    private final GeneralSkillService general;
    private final Map<UUID, View> views = new HashMap<UUID, View>();

    public BackpackService(Plugin p, CombatSkillService c, GeneralSkillService g) {
        this.plugin = p;
        this.combat = c;
        this.general = g;
    }

    public void openMenu(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)l.choose("Mochilas", "Backpacks"));
        this.fill(inv);
        int[] slots = new int[]{19, 20, 21, 22, 23, 24, 25};
        BackpackType[] all = BackpackType.values();
        for (int i = 0; i < all.length; ++i) {
            BackpackType type = all[i];
            int level = this.level(p, type);
            int size = this.size(level);
            List<Component> lore = List.of(this.line(l.choose("N\u00edvel da skill: ", "Skill level: ") + level, NamedTextColor.GRAY), this.line((String)(size == 0 ? l.choose("Desbloqueia no n\u00edvel 1", "Unlocks at level 1") : l.choose("Capacidade: ", "Capacity: ") + size + "/54"), size == 0 ? NamedTextColor.RED : NamedTextColor.GREEN), this.line(l.choose("Progress\u00e3o: 1, 10, 20, 30, 40 e 50", "Progression: 1, 10, 20, 30, 40, and 50"), NamedTextColor.DARK_GRAY), Component.empty(), this.line(size == 0 ? l.choose("BLOQUEADA", "LOCKED") : l.choose("Clique para abrir!", "Click to open!"), size == 0 ? NamedTextColor.RED : NamedTextColor.YELLOW));
            inv.setItem(slots[i], this.head(type, type.name(l == Language.PT) + " \u2022 " + l.choose("Mochila", "Backpack"), lore));
        }
        inv.setItem(49, this.item(Material.ARROW, l.choose("Voltar", "Back"), List.of()));
        p.openInventory(inv);
        this.views.put(p.getUniqueId(), new View(null, 0, 0, 0, inv));
    }

    public void openBag(Player p, BackpackType type) {
        this.openBag(p, type, 0);
    }

    private void openBag(Player p, BackpackType type, int page) {
        int capacity = this.size(this.level(p, type));
        if (capacity == 0) {
            return;
        }
        page = capacity > 45 ? Math.max(0, Math.min(1, page)) : 0;
        int stored = page == 0 ? Math.min(45, capacity) : capacity - 45;
        int inventorySize = stored + 9;
        int offset = page * 45;
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)inventorySize, (String)(type.name(l == Language.PT) + " \u2022 " + l.choose("Mochila", "Backpack") + " " + (page + 1) + "/" + (capacity > 45 ? 2 : 1)));
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = stored; i < inventorySize; ++i) {
            inv.setItem(i, filler);
        }
        inv.setItem(inventorySize - 5, this.item(Material.ARROW, l.choose("Voltar", "Back"), List.of()));
        if (page > 0) {
            inv.setItem(inventorySize - 7, this.item(Material.ARROW, l.choose("P\u00e1gina anterior", "Previous page"), List.of()));
        }
        if (capacity > 45 && page == 0) {
            inv.setItem(inventorySize - 3, this.item(Material.ARROW, l.choose("Pr\u00f3xima p\u00e1gina", "Next page"), List.of()));
        }
        this.load(p.getUniqueId(), type, inv, offset, stored);
        p.openInventory(inv);
        this.views.put(p.getUniqueId(), new View(type, page, stored, offset, inv));
    }

    public boolean clickMenu(Player p, int slot) {
        if (this.current(p) != null) {
            return false;
        }
        int index = slot - 19;
        if (index >= 0 && index < 7) {
            this.openBag(p, BackpackType.values()[index]);
            return true;
        }
        return false;
    }

    public boolean handleControl(Player p, int slot) {
        View v = this.views.get(p.getUniqueId());
        if (v == null || v.type == null) {
            return false;
        }
        int size = v.inventory.getSize();
        if (slot == size - 5) {
            this.openMenu(p);
            return true;
        }
        if (slot == size - 7 && v.page > 0) {
            this.openBag(p, v.type, v.page - 1);
            return true;
        }
        if (slot == size - 3 && v.page == 0 && this.size(this.level(p, v.type)) > 45) {
            this.openBag(p, v.type, 1);
            return true;
        }
        return false;
    }

    public boolean storageSlot(Player p, int raw) {
        View v = this.views.get(p.getUniqueId());
        return v != null && v.type != null && raw >= 0 && raw < v.stored;
    }

    public boolean viewing(Player p) {
        return this.views.containsKey(p.getUniqueId());
    }

    public BackpackType current(Player p) {
        View v = this.views.get(p.getUniqueId());
        return v == null ? null : v.type;
    }

    public void close(Player p) {
        this.save(p.getUniqueId(), this.views.remove(p.getUniqueId()));
    }

    public void shutdown() {
        this.views.forEach(this::save);
        this.views.clear();
    }

    public boolean accepts(BackpackType type, Material m) {
        String n = m.name();
        return switch (type) {
            default -> throw new MatchException(null, null);
            case BackpackType.MINING -> {
                if (MiningCatalog.find(m).isPresent() || MiningCatalog.entries().stream().anyMatch(e -> e.drop() == m) || n.contains("STONE") || n.equals("COBBLESTONE") || n.equals("TUFF") || n.equals("CALCITE")) {
                    yield true;
                }
                yield false;
            }
            case BackpackType.FORAGING -> {
                if (n.endsWith("_LOG") || n.endsWith("_WOOD") || n.endsWith("_STEM") || n.endsWith("_HYPHAE") || n.endsWith("_SAPLING") || n.endsWith("_LEAVES")) {
                    yield true;
                }
                yield false;
            }
            case BackpackType.FARMING -> {
                if (n.contains("WHEAT") || n.contains("SEEDS") || Set.of(Material.CARROT, Material.POTATO, Material.BEETROOT, Material.NETHER_WART, Material.COCOA_BEANS, Material.SUGAR_CANE, Material.MELON_SLICE, Material.PUMPKIN, Material.SWEET_BERRIES).contains(m)) {
                    yield true;
                }
                yield false;
            }
            case BackpackType.FISHING -> {
                if (n.contains("COD") || n.contains("SALMON") || n.contains("FISH") || n.contains("NAUTILUS") || m == Material.INK_SAC || m == Material.PRISMARINE_SHARD || m == Material.PRISMARINE_CRYSTALS) {
                    yield true;
                }
                yield false;
            }
            case BackpackType.ENCHANTING -> {
                if (m == Material.BOOK || m == Material.ENCHANTED_BOOK || m == Material.LAPIS_LAZULI || m == Material.EXPERIENCE_BOTTLE) {
                    yield true;
                }
                yield false;
            }
            case BackpackType.ALCHEMY -> {
                if (n.contains("POTION") || n.contains("BLAZE") || n.contains("FERMENTED") || n.contains("SPIDER_EYE") || Set.of(Material.NETHER_WART, Material.GHAST_TEAR, Material.MAGMA_CREAM, Material.RABBIT_FOOT, Material.GLISTERING_MELON_SLICE, Material.SUGAR, Material.REDSTONE, Material.GLOWSTONE_DUST, Material.GUNPOWDER, Material.DRAGON_BREATH).contains(m)) {
                    yield true;
                }
                yield false;
            }
            case BackpackType.COMBAT -> n.contains("ROTTEN") || n.contains("BONE") || n.contains("STRING") || n.contains("GUNPOWDER") || n.contains("ENDER_PEARL") || n.contains("SLIME") || n.contains("PHANTOM") || n.contains("BLAZE") || n.contains("GHAST") || n.contains("MAGMA") || n.contains("SHULKER") || n.contains("WITHER") || n.contains("ARROW");
        };
    }

    private int level(Player p, BackpackType t) {
        return t == BackpackType.COMBAT ? this.combat.progress(p).level() : this.general.progress(p, SkillType.valueOf(t.name())).level();
    }

    private int size(int level) {
        if (level < 1) {
            return 0;
        }
        if (level < 10) {
            return 9;
        }
        if (level < 20) {
            return 18;
        }
        if (level < 30) {
            return 27;
        }
        if (level < 40) {
            return 36;
        }
        if (level < 50) {
            return 45;
        }
        return 54;
    }

    private File file(UUID id, BackpackType t) {
        File dir = new File(this.plugin.getDataFolder(), "backpacks");
        if (!dir.exists()) {
            dir.mkdirs();
        }
        return new File(dir, String.valueOf(id) + "-" + t.name().toLowerCase(Locale.ROOT) + ".yml");
    }

    private void load(UUID id, BackpackType t, Inventory inv, int offset, int stored) {
        YamlConfiguration y = YamlConfiguration.loadConfiguration((File)this.file(id, t));
        for (int i = 0; i < stored; ++i) {
            inv.setItem(i, y.getItemStack("items." + (offset + i)));
        }
    }

    private void save(UUID id, View v) {
        if (id == null || v == null || v.type == null) {
            return;
        }
        YamlConfiguration y = YamlConfiguration.loadConfiguration((File)this.file(id, v.type));
        for (int i = 0; i < v.stored; ++i) {
            y.set("items." + (v.offset + i), (Object)v.inventory.getItem(i));
        }
        try {
            y.save(this.file(id, v.type));
        }
        catch (IOException e) {
            this.plugin.getLogger().severe("Could not save backpack " + String.valueOf(id) + ": " + e.getMessage());
        }
    }

    public ItemStack menuIcon(String name, List<Component> lore) {
        return this.head(BackpackType.COMBAT, name, lore);
    }

    private ItemStack head(BackpackType type, String name, List<Component> lore) {
        ItemStack i = this.item(Material.PLAYER_HEAD, name, lore);
        SkullMeta m = (SkullMeta)i.getItemMeta();
        PlayerProfile profile = Bukkit.createProfile((UUID)UUID.randomUUID());
        String texture = switch (type) {
            default -> throw new MatchException(null, null);
            case BackpackType.COMBAT -> BLACK;
            case BackpackType.FARMING -> GREEN;
            case BackpackType.FISHING -> BLUE;
            case BackpackType.MINING -> MINING_TEXTURE;
            case BackpackType.FORAGING -> BROWN;
            case BackpackType.ENCHANTING -> ENCHANTING_TEXTURE;
            case BackpackType.ALCHEMY -> ALCHEMY_TEXTURE;
        };
        profile.setProperty(new ProfileProperty("textures", texture));
        m.setPlayerProfile(profile);
        i.setItemMeta((ItemMeta)m);
        return i;
    }

    private void fill(Inventory inv) {
        ItemStack f = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); ++i) {
            inv.setItem(i, f);
        }
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text((String)s, (TextColor)c);
    }

    private ItemStack item(Material m, String n, List<Component> lore) {
        ItemStack i = ItemStack.of((Material)m);
        ItemMeta meta = i.getItemMeta();
        meta.displayName(Component.text((String)n, (TextColor)NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        i.setItemMeta(meta);
        return i;
    }

    private record View(BackpackType type, int page, int stored, int offset, Inventory inventory) {
    }
}

