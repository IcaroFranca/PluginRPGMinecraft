package dev.icaro.foodtooltips.destroyer;

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
import org.bukkit.util.Vector;

/**
 * The Destroyer's Hand: the Builder's Wand's mirror image. Right-click a placed block
 * to clear it and, depending on {@link FillMode}, either every contiguous block of the
 * same {@link Material} along the line/column implied by the clicked face, or the
 * whole connected wall/floor area on that plane - the exact same directional
 * convention {@link dev.icaro.foodtooltips.builder.BuilderWandService} uses for
 * placing, just consuming existing blocks instead.
 *
 * <p>Doesn't touch real drop tables (no tool/enchantment/loot-table lookups) - like
 * the wand's own placing side, this is a raw material-for-material trade: Creative
 * clears for free (matches Creative's "delete for free" rule), Survival hands the
 * player back one item of that Material per block cleared.
 *
 * <p>Admin-only for now, same as the wand - reachable only via {@code
 * FoodTooltipsPlugin}'s {@code /destroyerhand} executor.
 */
public final class DestroyerHandService {
    /** Whether a right-click clears only along the clicked face's line/column, or floods the whole connected area on that face. Stored per-item (see {@link #modeKey}), not per-player. */
    public enum FillMode {
        LINE, FACE
    }

    /** One player's last clear: the exact blocks removed (with their original BlockData, to undo), the Material they were, and whether clearing them paid the player (so undo knows whether to take it back). */
    private record LastAction(List<Block> cleared, List<BlockData> data, Material material, boolean gaveItems) {
    }

    // 3-row grid (27 slots); all 3 controls centered on the middle row (9-17).
    private static final int MODE_LINE_SLOT = 11;
    private static final int MODE_FACE_SLOT = 13;
    private static final int RANGE_SLOT = 15;

    private final NamespacedKey handKey;
    private final NamespacedKey modeKey;
    private final NamespacedKey rangeKey;
    private final int maxLength;
    private final ItemTierService tiers;
    private final Map<UUID, LastAction> lastAction = new HashMap<>();
    private final Set<UUID> viewingMenu = new HashSet<>();

    public DestroyerHandService(Plugin plugin, ItemTierService tiers) {
        this.handKey = new NamespacedKey(plugin, "destroyer_hand");
        this.modeKey = new NamespacedKey(plugin, "destroyer_hand_mode");
        this.rangeKey = new NamespacedKey(plugin, "destroyer_hand_range");
        this.maxLength = Math.max(1, plugin.getConfig().getInt("destroyer-hand.max-length", 64));
        this.tiers = tiers;
    }

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.handKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(this.modeKey, PersistentDataType.STRING, FillMode.LINE.name());
        meta.getPersistentDataContainer().set(this.rangeKey, PersistentDataType.INTEGER, this.maxLength);
        // A one-of-a-kind admin tool, not a bone - pin it to Tier S same as the wand.
        this.tiers.forceTier(meta, ItemTier.S);
        item.setItemMeta(meta);
        this.refreshLore(item, l);
        return item;
    }

    /** Rewrites the hand's name/lore to reflect its current {@link FillMode} and range - called on creation and whenever either setting changes. */
    private void refreshLore(ItemStack item, Language l) {
        ItemMeta meta = item.getItemMeta();
        FillMode mode = this.mode(item);
        meta.displayName(this.line(l.choose("Mão do Destruidor", "Destroyer's Hand"), NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Clique direito num bloco pra limpar", "Right-click a block to clear"), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("(a direção depende do modo abaixo).", "(direction depends on the mode below)."), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Clique esquerdo abre o menu de configurações.", "Left-click opens the settings menu."), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Shift + clique esquerdo desfaz a última ação.", "Shift + left-click undoes the last action."), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(this.line(l.choose("Modo: ", "Mode: ") + (mode == FillMode.LINE
                        ? l.choose("Linha/Coluna", "Line/Column")
                        : l.choose("Face inteira (parede/chão)", "Whole face (wall/floor)")),
                NamedTextColor.YELLOW));
        lore.add(this.line(l.choose("Alcance: ", "Range: ") + this.rangeLabel(this.range(item), l), NamedTextColor.YELLOW));
        lore.add(this.line(l.choose("Criativo: não devolve nada.", "Creative: doesn't give anything back."), NamedTextColor.DARK_GRAY));
        lore.add(this.line(l.choose("Sobrevivência: devolve os blocos.", "Survival: gives the blocks back."), NamedTextColor.DARK_GRAY));
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

    public boolean isHand(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.handKey, PersistentDataType.BYTE);
    }

    /** The hand's current fill mode - {@link FillMode#LINE} if the item predates this setting or the tag is missing/corrupted. */
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
     * Sentinel {@link #range} value meaning "no cap at all" - the top preset in {@link
     * #rangePresets}, past the server's own max-length. Admin-only tool, so this is a deliberate
     * opt-in, not a safety hole. Deliberately a large finite number rather than {@code
     * Integer.MAX_VALUE} (mirrors {@link dev.icaro.foodtooltips.builder.BuilderWandService#UNLIMITED}):
     * a natural formation of contiguous same-Material blocks (a stone mountain, an ocean floor)
     * can run for a very long way, and true unbounded would spin the server thread clearing it
     * instead of just stopping at a still-generous limit. 10,000 blocks is effectively unlimited
     * for any real clear.
     */
    public static final int UNLIMITED = 10_000;

    /**
     * The hand's current per-item block limit: {@link #UNLIMITED} if that's what's picked in
     * the menu, otherwise clamped to [1, {@code destroyer-hand.max-length}] - a player can only
     * ever dial it down from the server's own cap (or explicitly opt out via Unlimited), never
     * accidentally end up past it from a stale stored value after an admin lowers the config.
     */
    public int range(ItemStack item) {
        ItemMeta meta = item.getItemMeta();
        Integer stored = meta == null ? null : meta.getPersistentDataContainer().get(this.rangeKey, PersistentDataType.INTEGER);
        if (stored == null) {
            return this.maxLength;
        }
        if (stored == UNLIMITED) {
            return UNLIMITED;
        }
        return Math.max(1, Math.min(this.maxLength, stored));
    }

    /** The selectable range presets shown in the mode menu: powers of two from 8 up to the configured max-length, plus {@link #UNLIMITED} as the last, top option. */
    private List<Integer> rangePresets() {
        List<Integer> presets = new ArrayList<>();
        int v = 8;
        while (v < this.maxLength) {
            presets.add(v);
            v *= 2;
        }
        presets.add(this.maxLength);
        presets.add(UNLIMITED);
        return presets;
    }

    // ---- Mode menu ---------------------------------------------------------

    /** Opens the small 1-row settings menu (fill mode + range) for {@code item} (the hand currently in the player's hand). */
    public void openModeMenu(Player p, ItemStack item) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 27, l.choose("Mão do Destruidor: Configurações", "Destroyer's Hand: Settings"));
        this.renderMenu(v, item, l);
        p.openInventory(v);
        this.viewingMenu.add(p.getUniqueId());
    }

    private void renderMenu(Inventory v, ItemStack item, Language l) {
        ItemStack filler = new ItemStack(Material.GRAY_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 27; i++) {
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
     * whatever hand {@code p} is currently holding, then re-renders the menu in place (it
     * never auto-closes, so mode and range can both be tweaked in the same session).
     */
    public void handleMenuClick(Player p, int slot, ClickType click) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.isHand(held)) {
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

    /** Steps the hand's range one preset up ({@code forward}) or down, clamped at the ends of {@link #rangePresets}. */
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
                ? l.choose("Topo/base = coluna vertical; lateral = ao longo da parede.", "Top/bottom = vertical column; side = along the wall.")
                : l.choose("Limpa toda a área conectada da parede ou chão.", "Clears the whole connected area of the wall or floor."), NamedTextColor.GRAY));
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

    /** {@code current} as shown to the player - the plain number, or "Ilimitado"/"Unlimited" for {@link #UNLIMITED}. */
    private String rangeLabel(int current, Language l) {
        return current == UNLIMITED ? l.choose("Ilimitado", "Unlimited") : current + l.choose(" blocos", " blocks");
    }

    private ItemStack rangeItem(int current, Language l) {
        ItemStack item = new ItemStack(current == UNLIMITED ? Material.ENDER_EYE : Material.SPYGLASS);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.line(l.choose("Alcance: ", "Range: ") + this.rangeLabel(current, l), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Clique esquerdo: aumenta", "Left-click: increase"), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Clique direito: diminui", "Right-click: decrease"), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(this.line(l.choose("Máximo do servidor: " + this.maxLength, "Server max: " + this.maxLength), NamedTextColor.DARK_GRAY));
        if (current == UNLIMITED) {
            lore.add(this.line(l.choose("Sem teto - cuidado em áreas muito grandes.", "No cap - be careful in very large areas."), NamedTextColor.RED));
        }
        meta.lore(lore);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        item.setItemMeta(meta);
        return item;
    }

    // ---- Clearing -------------------------------------------------------------

    /**
     * Clears {@code clicked} and, per {@code item}'s current {@link FillMode}, either
     * every contiguous block of the same Material along a line ({@link FillMode#LINE} -
     * vertically for a top/bottom click, or along the wall's own plane for a side click,
     * see {@link #lineDirection}), or the whole connected pocket of that Material on the
     * plane perpendicular to {@code face} ({@link FillMode#FACE}). Both stop at {@code
     * item}'s own {@link #range}. Returns how many blocks were actually cleared.
     */
    public int clear(Player p, Block clicked, BlockFace face, ItemStack item) {
        Material material = clicked.getType();
        if (!material.isBlock() || material.isAir()) {
            return 0;
        }
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        int limit = this.range(item);
        ClearResult result = this.mode(item) == FillMode.FACE
                ? this.clearFace(clicked, face, material, limit)
                : this.clearLine(clicked, lineDirection(p, clicked, face, material), material, limit);
        if (!result.cleared().isEmpty()) {
            if (!creative) {
                this.give(p, material, result.cleared().size());
            }
            this.lastAction.put(p.getUniqueId(), new LastAction(result.cleared(), result.data(), material, !creative));
        }
        return result.cleared().size();
    }

    private record ClearResult(List<Block> cleared, List<BlockData> data) {
    }

    /**
     * The direction a {@link FillMode#LINE} clear actually walks: unchanged ({@code
     * clickedFace} itself) for a top/bottom click - still a plain vertical column, as
     * always - but for a side click, clears along the wall's own plane (its face's
     * horizontal cross-axis) instead of drilling straight through it. A 1-block-thick
     * wall has nothing behind it to drill into, so the old "walk straight out from the
     * clicked face" convention only ever cleared a single block there.
     *
     * <p>Between the two in-plane candidates, whichever one actually continues with the
     * same Material wins - that's the real wall, not a guess. Only falls back to picking
     * whichever direction {@code p} is more turned toward when that's ambiguous (both
     * candidates match, e.g. clicked in the middle of a long wall) or moot (neither does,
     * e.g. a single isolated block - the clear only ever reaches 1 block either way).
     */
    private static BlockFace lineDirection(Player p, Block clicked, BlockFace clickedFace, Material material) {
        if (clickedFace == BlockFace.UP || clickedFace == BlockFace.DOWN) {
            return clickedFace;
        }
        BlockFace a;
        BlockFace b;
        if (clickedFace == BlockFace.EAST || clickedFace == BlockFace.WEST) {
            a = BlockFace.NORTH;
            b = BlockFace.SOUTH;
        } else {
            a = BlockFace.EAST;
            b = BlockFace.WEST;
        }
        boolean aMatches = clicked.getRelative(a).getType() == material;
        boolean bMatches = clicked.getRelative(b).getType() == material;
        if (aMatches != bMatches) {
            return aMatches ? a : b;
        }
        Vector look = p.getLocation().getDirection().setY(0.0);
        return look.dot(a.getDirection()) >= look.dot(b.getDirection()) ? a : b;
    }

    private ClearResult clearLine(Block clicked, BlockFace face, Material material, int limit) {
        List<Block> clearedBlocks = new ArrayList<>();
        List<BlockData> savedData = new ArrayList<>();
        Block cursor = clicked;
        for (int i = 0; i < limit; i++) {
            if (cursor.getType() != material) {
                break;
            }
            savedData.add(cursor.getBlockData());
            clearedBlocks.add(cursor);
            cursor.setType(Material.AIR);
            cursor = cursor.getRelative(face);
        }
        return new ClearResult(clearedBlocks, savedData);
    }

    /**
     * {@link FillMode#FACE} version of {@link #clearLine}: BFS flood-fill across the
     * plane perpendicular to {@code face}, starting at {@code clicked} itself, clearing
     * contiguous same-Material blocks only. Tracks visited coordinates via {@link Pos}
     * rather than {@code Block} itself - {@code Block#getRelative} returns a fresh
     * instance every call, and relying on its {@code equals}/{@code hashCode} for dedup
     * is a well-known Bukkit footgun; a plain coordinate record sidesteps it entirely.
     */
    private ClearResult clearFace(Block clicked, BlockFace face, Material material, int limit) {
        BlockFace[] axes = planeAxes(face);
        List<Block> clearedBlocks = new ArrayList<>();
        List<BlockData> savedData = new ArrayList<>();
        Set<Pos> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(clicked);
        visited.add(Pos.of(clicked));
        while (!queue.isEmpty() && clearedBlocks.size() < limit) {
            Block b = queue.poll();
            if (b.getType() != material) {
                continue;
            }
            savedData.add(b.getBlockData());
            clearedBlocks.add(b);
            b.setType(Material.AIR);
            for (BlockFace dir : axes) {
                Block next = b.getRelative(dir);
                if (visited.add(Pos.of(next))) {
                    queue.add(next);
                }
            }
        }
        return new ClearResult(clearedBlocks, savedData);
    }

    /** Plain block coordinates, used only as a reliable-by-value HashSet key for flood fills (see {@link #clearFace}). */
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
     * Shift + left-click's counterpart to {@link #clear}: puts the last cleared blocks
     * back exactly as they were (same {@link BlockData}, orientation included) and -
     * only if that clear actually paid the player (Survival) - takes back the same
     * count of that material from their inventory. Only remembers one action per
     * player, matching "undo the last thing I did" rather than a full undo stack.
     */
    public int undo(Player p) {
        LastAction action = this.lastAction.remove(p.getUniqueId());
        if (action == null) {
            return 0;
        }
        List<Block> cleared = action.cleared();
        List<BlockData> data = action.data();
        for (int i = 0; i < cleared.size(); i++) {
            cleared.get(i).setBlockData(data.get(i));
        }
        if (action.gaveItems()) {
            this.take(p, action.material(), cleared.size());
        }
        return cleared.size();
    }

    /** Hands {@code count} of {@code material} to the player, splitting into full stacks and dropping whatever doesn't fit in the inventory. */
    private void give(Player p, Material material, int count) {
        int max = material.getMaxStackSize();
        while (count > 0) {
            int amount = Math.min(max, count);
            count -= amount;
            for (ItemStack leftover : p.getInventory().addItem(new ItemStack(material, amount)).values()) {
                p.getWorld().dropItem(p.getLocation(), leftover);
            }
        }
    }

    /** Removes up to {@code count} of {@code material} from the player's main storage - best-effort, stops early if they no longer have that many. */
    private void take(Player p, Material material, int count) {
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length && count > 0; i++) {
            ItemStack stack = storage[i];
            if (stack == null || stack.getType() != material) {
                continue;
            }
            int remove = Math.min(count, stack.getAmount());
            stack.setAmount(stack.getAmount() - remove);
            inv.setItem(i, stack.getAmount() <= 0 ? null : stack);
            count -= remove;
        }
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text(s, c).decoration(TextDecoration.ITALIC, false);
    }
}
