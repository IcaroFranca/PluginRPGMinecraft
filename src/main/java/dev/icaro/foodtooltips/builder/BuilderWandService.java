package dev.icaro.foodtooltips.builder;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Builder's Wand: right-click a placed block to extend it into a full line
 * (horizontal face), column (top/bottom face), or - in {@link FillMode#FACE} - the
 * whole connected wall/floor area beyond it, starting from the block adjacent to the
 * one clicked. In Creative it's free (matches Creative's own "unlimited blocks" rule);
 * in Survival it consumes one matching block from the player's inventory per block
 * placed and stops the moment it runs out.
 *
 * <p>For now the wand is admin-only and only reachable via a command (see
 * {@code FoodTooltipsPlugin}'s {@code /builderwand} executor) - no drop/craft source yet.
 */
public final class BuilderWandService {
    /** Whether a right-click extends only along the clicked face's line/column, or floods the whole connected area on that face. Stored per-item (see {@link #modeKey}), not per-player - the wand itself remembers its own setting. */
    public enum FillMode {
        LINE, FACE
    }

    /** One player's last extension: the exact blocks it placed, and whether it paid for them (so undo knows whether to refund). */
    private record LastAction(List<Block> placed, Material material, boolean consumed) {
    }

    private static final int MODE_LINE_SLOT = 3;
    private static final int MODE_FACE_SLOT = 5;
    private static final int RANGE_SLOT = 7;

    private final NamespacedKey wandKey;
    private final NamespacedKey modeKey;
    private final NamespacedKey rangeKey;
    private final int maxLength;
    private final ItemTierService tiers;
    private final Map<UUID, LastAction> lastAction = new HashMap<>();
    private final Set<UUID> viewingMenu = new HashSet<>();

    public BuilderWandService(Plugin plugin, ItemTierService tiers) {
        this.wandKey = new NamespacedKey(plugin, "builder_wand");
        this.modeKey = new NamespacedKey(plugin, "builder_wand_mode");
        this.rangeKey = new NamespacedKey(plugin, "builder_wand_range");
        this.maxLength = Math.max(1, plugin.getConfig().getInt("builder-wand.max-length", 64));
        this.tiers = tiers;
    }

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.wandKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(this.modeKey, PersistentDataType.STRING, FillMode.LINE.name());
        meta.getPersistentDataContainer().set(this.rangeKey, PersistentDataType.INTEGER, this.maxLength);
        // A one-of-a-kind admin tool, not a stick - pin it to Tier S so ItemTierService's
        // periodic pass doesn't sort it into Tier E with every other plain Stick.
        this.tiers.forceTier(meta, ItemTier.S);
        item.setItemMeta(meta);
        this.refreshLore(item, l);
        return item;
    }

    /** Rewrites the wand's name/lore to reflect its current {@link FillMode} and range - called on creation and whenever either setting changes. */
    private void refreshLore(ItemStack item, Language l) {
        ItemMeta meta = item.getItemMeta();
        FillMode mode = this.mode(item);
        meta.displayName(this.line(l.choose("Varinha do Construtor", "Builder's Wand"), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Clique direito num bloco pra estender", "Right-click a block to extend"), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("na direção da face clicada.", "in the clicked face's direction."), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Clique esquerdo abre o menu de configurações.", "Left-click opens the settings menu."), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Shift + clique esquerdo desfaz a última ação.", "Shift + left-click undoes the last action."), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(this.line(l.choose("Modo: ", "Mode: ") + (mode == FillMode.LINE
                        ? l.choose("Linha/Coluna", "Line/Column")
                        : l.choose("Face inteira (parede/chão)", "Whole face (wall/floor)")),
                NamedTextColor.YELLOW));
        lore.add(this.line(l.choose("Alcance: ", "Range: ") + this.range(item) + l.choose(" blocos", " blocks"), NamedTextColor.YELLOW));
        lore.add(this.line(l.choose("Criativo: não gasta blocos.", "Creative: doesn't use blocks."), NamedTextColor.DARK_GRAY));
        lore.add(this.line(l.choose("Sobrevivência: precisa ter os blocos.", "Survival: needs the blocks."), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
    }

    /** Drops the remembered last action for a player (call on quit - no point holding Block references for an offline player). */
    public void forget(UUID playerId) {
        this.lastAction.remove(playerId);
        this.viewingMenu.remove(playerId);
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.wandKey, PersistentDataType.BYTE);
    }

    /** The wand's current fill mode - {@link FillMode#LINE} if the item predates this setting or the tag is missing/corrupted. */
    public FillMode mode(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        String raw = meta == null ? null : meta.getPersistentDataContainer().get(this.modeKey, PersistentDataType.STRING);
        if (raw == null) {
            return FillMode.LINE;
        }
        try {
            return FillMode.valueOf(raw);
        } catch (IllegalArgumentException ignored) {
            return FillMode.LINE;
        }
    }

    /**
     * The wand's current per-item block limit, clamped to [1, {@code builder-wand.max-length}] -
     * a player can only ever dial it down from the server's own cap, never past it, even if the
     * stored value is stale from before an admin lowered the config.
     */
    public int range(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Integer stored = meta == null ? null : meta.getPersistentDataContainer().get(this.rangeKey, PersistentDataType.INTEGER);
        if (stored == null) {
            return this.maxLength;
        }
        return Math.max(1, Math.min(this.maxLength, stored));
    }

    /** The selectable range presets shown in the mode menu: powers of two from 8 up to (and always including) the configured max-length. */
    private List<Integer> rangePresets() {
        List<Integer> presets = new ArrayList<>();
        int v = 8;
        while (v < this.maxLength) {
            presets.add(v);
            v *= 2;
        }
        presets.add(this.maxLength);
        return presets;
    }

    // ---- Mode menu ---------------------------------------------------------

    /** Opens the small 1-row settings menu (fill mode + range) for {@code item} (the wand currently in the player's hand). */
    public void openModeMenu(Player p, ItemStack item) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 9, l.choose("Varinha: Configurações", "Wand: Settings"));
        this.renderMenu(v, item, l);
        p.openInventory(v);
        this.viewingMenu.add(p.getUniqueId());
    }

    private void renderMenu(Inventory v, ItemStack item, Language l) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 9; i++) {
            v.setItem(i, filler);
        }
        FillMode current = this.mode(item);
        v.setItem(MODE_LINE_SLOT, this.modeOption(FillMode.LINE, current, l));
        v.setItem(MODE_FACE_SLOT, this.modeOption(FillMode.FACE, current, l));
        v.setItem(RANGE_SLOT, this.rangeItem(this.range(item), l));
    }

    public boolean viewingMenu(Player p) {
        return this.viewingMenu.contains(p.getUniqueId());
    }

    public void closeMenu(Player p) {
        this.viewingMenu.remove(p.getUniqueId());
    }

    /**
     * Handles a click inside the settings menu - applies the picked mode or range step to
     * whatever wand {@code p} is currently holding, then re-renders the menu in place (it
     * never auto-closes, so mode and range can both be tweaked in the same session).
     */
    public void handleMenuClick(Player p, int slot, ClickType click) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.isWand(held)) {
            return;
        }
        Language l = Language.of(p);
        if (slot == MODE_LINE_SLOT) {
            this.setMode(held, FillMode.LINE, l);
        } else if (slot == MODE_FACE_SLOT) {
            this.setMode(held, FillMode.FACE, l);
        } else if (slot == RANGE_SLOT) {
            this.cycleRange(held, click.isLeftClick(), l);
        } else {
            return;
        }
        this.renderMenu(p.getOpenInventory().getTopInventory(), held, l);
    }

    private void setMode(ItemStack item, FillMode mode, Language l) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.modeKey, PersistentDataType.STRING, mode.name());
        item.setItemMeta(meta);
        this.refreshLore(item, l);
    }

    /** Steps the wand's range one preset up ({@code forward}) or down, clamped at the ends of {@link #rangePresets}. */
    private void cycleRange(ItemStack item, boolean forward, Language l) {
        List<Integer> presets = this.rangePresets();
        int index = presets.indexOf(this.range(item));
        if (index < 0) {
            index = presets.size() - 1;
        }
        int next = forward ? Math.min(presets.size() - 1, index + 1) : Math.max(0, index - 1);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.rangeKey, PersistentDataType.INTEGER, presets.get(next));
        item.setItemMeta(meta);
        this.refreshLore(item, l);
    }

    private ItemStack modeOption(FillMode option, FillMode current, Language l) {
        boolean selected = option == current;
        Material material = option == FillMode.LINE ? Material.LIGHT_BLUE_STAINED_GLASS_PANE : Material.ORANGE_STAINED_GLASS_PANE;
        String label = option == FillMode.LINE
                ? l.choose("Linha/Coluna", "Line/Column")
                : l.choose("Face inteira (parede/chão)", "Whole face (wall/floor)");
        ItemStack item = new ItemStack(material);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.line((selected ? "✔ " : "") + label, selected ? NamedTextColor.GREEN : NamedTextColor.GRAY)
                .decoration(TextDecoration.BOLD, selected));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(option == FillMode.LINE
                ? l.choose("Estende/limpa só na direção da face clicada.", "Extends/clears only along the clicked face's direction.")
                : l.choose("Preenche/limpa toda a área conectada da parede ou chão.", "Fills/clears the whole connected area of the wall or floor."), NamedTextColor.GRAY));
        if (selected) {
            lore.add(Component.empty());
            lore.add(this.line(l.choose("Modo atual", "Current mode"), NamedTextColor.GREEN));
        }
        meta.lore(lore);
        if (selected) {
            meta.setEnchantmentGlintOverride(true);
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    private ItemStack rangeItem(int current, Language l) {
        ItemStack item = new ItemStack(Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.line(l.choose("Alcance: ", "Range: ") + current + l.choose(" blocos", " blocks"), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Clique esquerdo: aumenta", "Left-click: increase"), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Clique direito: diminui", "Right-click: decrease"), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(this.line(l.choose("Máximo do servidor: " + this.maxLength, "Server max: " + this.maxLength), NamedTextColor.DARK_GRAY));
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    // ---- Extending ----------------------------------------------------------

    /**
     * Extends {@code clicked}'s block starting from the block adjacent to it (in
     * {@code face}'s direction), replacing air only, per {@code item}'s current {@link
     * FillMode}: {@link FillMode#LINE} walks a straight line/column; {@link
     * FillMode#FACE} flood-fills the whole connected pocket of air on that plane. Both
     * stop at {@code item}'s own {@link #range}, or (Survival only) the moment {@code p}
     * runs out of that material. Returns how many blocks were actually placed.
     */
    public int extend(Player p, Block clicked, BlockFace face, ItemStack item) {
        Material material = clicked.getType();
        if (!material.isBlock() || !material.isSolid()) {
            return 0;
        }
        BlockData data = clicked.getBlockData();
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        int limit = this.range(item);
        List<Block> placedBlocks = this.mode(item) == FillMode.FACE
                ? this.extendFace(p, clicked, face, material, data, creative, limit)
                : this.extendLine(p, clicked, face, material, data, creative, limit);
        if (!placedBlocks.isEmpty()) {
            this.lastAction.put(p.getUniqueId(), new LastAction(placedBlocks, material, !creative));
        }
        return placedBlocks.size();
    }

    private List<Block> extendLine(Player p, Block clicked, BlockFace face, Material material, BlockData data, boolean creative, int limit) {
        List<Block> placedBlocks = new ArrayList<>();
        Block cursor = clicked.getRelative(face);
        for (int i = 0; i < limit; i++) {
            if (!cursor.getType().isAir()) {
                break;
            }
            if (!creative && !this.consume(p, material)) {
                break;
            }
            cursor.setBlockData(data);
            placedBlocks.add(cursor);
            cursor = cursor.getRelative(face);
        }
        return placedBlocks;
    }

    /**
     * {@link FillMode#FACE} version of {@link #extendLine}: BFS flood-fill across the
     * plane perpendicular to {@code face}, starting one block out from {@code clicked},
     * filling contiguous air only. Tracks visited coordinates via {@link Pos} rather
     * than {@code Block} itself - {@code Block#getRelative} returns a fresh instance
     * every call, and relying on its {@code equals}/{@code hashCode} for dedup is a
     * well-known Bukkit footgun; a plain coordinate record sidesteps it entirely.
     */
    private List<Block> extendFace(Player p, Block clicked, BlockFace face, Material material, BlockData data, boolean creative, int limit) {
        BlockFace[] axes = planeAxes(face);
        List<Block> placedBlocks = new ArrayList<>();
        Set<Pos> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        Block start = clicked.getRelative(face);
        queue.add(start);
        visited.add(Pos.of(start));
        while (!queue.isEmpty() && placedBlocks.size() < limit) {
            Block b = queue.poll();
            if (!b.getType().isAir()) {
                continue;
            }
            if (!creative && !this.consume(p, material)) {
                break;
            }
            b.setBlockData(data);
            placedBlocks.add(b);
            for (BlockFace dir : axes) {
                Block next = b.getRelative(dir);
                if (visited.add(Pos.of(next))) {
                    queue.add(next);
                }
            }
        }
        return placedBlocks;
    }

    /** Plain block coordinates, used only as a reliable-by-value HashSet key for flood fills (see {@link #extendFace}). */
    private record Pos(int x, int y, int z) {
        static Pos of(Block b) {
            return new Pos(b.getX(), b.getY(), b.getZ());
        }
    }

    /** The 4 {@link BlockFace}s spanning the plane perpendicular to {@code face} (e.g. UP/DOWN's plane is North/South/East/West - a floor or ceiling). */
    private static BlockFace[] planeAxes(BlockFace face) {
        return switch (face) {
            case UP, DOWN -> new BlockFace[]{BlockFace.NORTH, BlockFace.SOUTH, BlockFace.EAST, BlockFace.WEST};
            case EAST, WEST -> new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.NORTH, BlockFace.SOUTH};
            default -> new BlockFace[]{BlockFace.UP, BlockFace.DOWN, BlockFace.EAST, BlockFace.WEST};
        };
    }

    /**
     * Shift + left-click's counterpart to {@link #extend}: puts back air where the last
     * extension placed blocks, and - only if that extension actually paid for them
     * (Survival) - returns the same count of that material to the player's inventory,
     * dropping anything that doesn't fit. Only remembers one action per player, matching
     * "undo the last thing I did" rather than a full undo stack.
     */
    public int undo(Player p) {
        LastAction action = this.lastAction.remove(p.getUniqueId());
        if (action == null) {
            return 0;
        }
        for (Block b : action.placed()) {
            b.setType(Material.AIR);
        }
        if (action.consumed()) {
            this.giveBack(p, action.material(), action.placed().size());
        }
        return action.placed().size();
    }

    /** Hands back {@code count} of {@code material}, splitting into full stacks and dropping whatever doesn't fit in the inventory. */
    private void giveBack(Player p, Material material, int count) {
        int max = material.getMaxStackSize();
        while (count > 0) {
            int amount = Math.min(max, count);
            count -= amount;
            for (ItemStack leftover : p.getInventory().addItem(new ItemStack(material, amount)).values()) {
                p.getWorld().dropItem(p.getLocation(), leftover);
            }
        }
    }

    /** Removes one item of {@code material} from the player's main storage; returns false if none was found. */
    private boolean consume(Player p, Material material) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack stack = storage[i];
            if (stack != null && stack.getType() == material) {
                stack.setAmount(stack.getAmount() - 1);
                inv.setItem(i, stack.getAmount() <= 0 ? null : stack);
                return true;
            }
        }
        return false;
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
