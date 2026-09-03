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
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

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

    private static final int MODE_LINE_SLOT = 3;
    private static final int MODE_FACE_SLOT = 5;

    private final NamespacedKey handKey;
    private final NamespacedKey modeKey;
    private final int maxLength;
    private final ItemTierService tiers;
    private final Map<UUID, LastAction> lastAction = new HashMap<>();
    private final Set<UUID> viewingMenu = new HashSet<>();

    public DestroyerHandService(Plugin plugin, ItemTierService tiers) {
        this.handKey = new NamespacedKey(plugin, "destroyer_hand");
        this.modeKey = new NamespacedKey(plugin, "destroyer_hand_mode");
        this.maxLength = Math.max(1, plugin.getConfig().getInt("destroyer-hand.max-length", 64));
        this.tiers = tiers;
    }

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.handKey, PersistentDataType.BYTE, (byte) 1);
        meta.getPersistentDataContainer().set(this.modeKey, PersistentDataType.STRING, FillMode.LINE.name());
        // A one-of-a-kind admin tool, not a bone - pin it to Tier S same as the wand.
        this.tiers.forceTier(meta, ItemTier.S);
        item.setItemMeta(meta);
        this.refreshLore(item, l);
        return item;
    }

    /** Rewrites the hand's name/lore to reflect its current {@link FillMode} - called on creation and whenever the mode changes. */
    private void refreshLore(ItemStack item, Language l) {
        ItemMeta meta = item.getItemMeta();
        FillMode mode = this.mode(item);
        meta.displayName(this.line(l.choose("Mão do Destruidor", "Destroyer's Hand"), NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true));
        List<Component> lore = new ArrayList<>();
        lore.add(this.line(l.choose("Clique direito num bloco pra limpar", "Right-click a block to clear"), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("na direção da face clicada.", "in the clicked face's direction."), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Clique esquerdo abre o menu de modo.", "Left-click opens the mode menu."), NamedTextColor.GRAY));
        lore.add(this.line(l.choose("Shift + clique esquerdo desfaz a última ação.", "Shift + left-click undoes the last action."), NamedTextColor.GRAY));
        lore.add(Component.empty());
        lore.add(this.line(l.choose("Modo: ", "Mode: ") + (mode == FillMode.LINE
                        ? l.choose("Linha/Coluna", "Line/Column")
                        : l.choose("Face inteira (parede/chão)", "Whole face (wall/floor)")),
                NamedTextColor.YELLOW));
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

    // ---- Mode menu ---------------------------------------------------------

    /** Opens the small 1-row menu letting the player pick between {@link FillMode#LINE} and {@link FillMode#FACE} for {@code item} (the hand currently in their hand). */
    public void openModeMenu(Player p, ItemStack item) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 9, l.choose("Mão do Destruidor: Modo", "Destroyer's Hand: Mode"));
        ItemStack filler = new ItemStack(Material.BLACK_STAINED_GLASS_PANE);
        ItemMeta fillerMeta = filler.getItemMeta();
        fillerMeta.displayName(Component.text(" "));
        filler.setItemMeta(fillerMeta);
        for (int i = 0; i < 9; i++) {
            v.setItem(i, filler);
        }
        FillMode current = this.mode(item);
        v.setItem(MODE_LINE_SLOT, this.modeOption(FillMode.LINE, current, l));
        v.setItem(MODE_FACE_SLOT, this.modeOption(FillMode.FACE, current, l));
        p.openInventory(v);
        this.viewingMenu.add(p.getUniqueId());
    }

    public boolean viewingMenu(Player p) {
        return this.viewingMenu.contains(p.getUniqueId());
    }

    public void closeMenu(Player p) {
        this.viewingMenu.remove(p.getUniqueId());
    }

    /** Handles a click inside the mode menu - applies the picked mode to whatever hand {@code p} is currently holding, then closes the menu. */
    public void handleMenuClick(Player p, int slot) {
        ItemStack held = p.getInventory().getItemInMainHand();
        if (!this.isHand(held)) {
            return;
        }
        if (slot == MODE_LINE_SLOT) {
            this.setMode(held, FillMode.LINE, Language.of(p));
        } else if (slot == MODE_FACE_SLOT) {
            this.setMode(held, FillMode.FACE, Language.of(p));
        } else {
            return;
        }
        p.closeInventory();
    }

    private void setMode(ItemStack item, FillMode mode, Language l) {
        ItemMeta meta = item.getItemMeta();
        meta.getPersistentDataContainer().set(this.modeKey, PersistentDataType.STRING, mode.name());
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
                ? l.choose("Limpa só na direção da face clicada.", "Clears only along the clicked face's direction.")
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

    // ---- Clearing -------------------------------------------------------------

    /**
     * Clears {@code clicked} and, per {@code item}'s current {@link FillMode}, either
     * every contiguous block of the same Material along {@code face}'s axis ({@link
     * FillMode#LINE}), or the whole connected pocket of that Material on the plane
     * perpendicular to {@code face} ({@link FillMode#FACE}). Both stop at the
     * configured max block count. Returns how many blocks were actually cleared.
     */
    public int clear(Player p, Block clicked, BlockFace face, ItemStack item) {
        Material material = clicked.getType();
        if (!material.isBlock() || material.isAir()) {
            return 0;
        }
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        ClearResult result = this.mode(item) == FillMode.FACE
                ? this.clearFace(clicked, face, material)
                : this.clearLine(clicked, face, material);
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

    private ClearResult clearLine(Block clicked, BlockFace face, Material material) {
        List<Block> clearedBlocks = new ArrayList<>();
        List<BlockData> savedData = new ArrayList<>();
        Block cursor = clicked;
        for (int i = 0; i < this.maxLength; i++) {
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

    /** {@link FillMode#FACE} version of {@link #clearLine}: BFS flood-fill across the plane perpendicular to {@code face}, starting at {@code clicked} itself, clearing contiguous same-Material blocks only. */
    private ClearResult clearFace(Block clicked, BlockFace face, Material material) {
        BlockFace[] axes = planeAxes(face);
        List<Block> clearedBlocks = new ArrayList<>();
        List<BlockData> savedData = new ArrayList<>();
        Set<Block> visited = new HashSet<>();
        Deque<Block> queue = new ArrayDeque<>();
        queue.add(clicked);
        visited.add(clicked);
        while (!queue.isEmpty() && clearedBlocks.size() < this.maxLength) {
            Block b = queue.poll();
            if (b.getType() != material) {
                continue;
            }
            savedData.add(b.getBlockData());
            clearedBlocks.add(b);
            b.setType(Material.AIR);
            for (BlockFace dir : axes) {
                Block next = b.getRelative(dir);
                if (visited.add(next)) {
                    queue.add(next);
                }
            }
        }
        return new ClearResult(clearedBlocks, savedData);
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
