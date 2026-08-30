package dev.icaro.foodtooltips.destroyer;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.item.ItemTier;
import dev.icaro.foodtooltips.item.ItemTierService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.GameMode;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.block.Block;
import org.bukkit.block.BlockFace;
import org.bukkit.block.data.BlockData;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * The Destroyer's Hand: the Builder's Wand's mirror image. Right-click a placed block
 * to clear it and every contiguous block of the same {@link Material} beyond it, along
 * the line (horizontal face) or column (top/bottom face) implied by the clicked face -
 * the exact same directional convention {@link dev.icaro.foodtooltips.builder.BuilderWandService}
 * uses for placing, just consuming existing blocks instead.
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
    /** One player's last clear: the exact blocks removed (with their original BlockData, to undo), the Material they were, and whether clearing them paid the player (so undo knows whether to take it back). */
    private record LastAction(List<Block> cleared, List<BlockData> data, Material material, boolean gaveItems) {
    }

    private final NamespacedKey handKey;
    private final int maxLength;
    private final ItemTierService tiers;
    private final Map<UUID, LastAction> lastAction = new HashMap<>();

    public DestroyerHandService(Plugin plugin, ItemTierService tiers) {
        this.handKey = new NamespacedKey(plugin, "destroyer_hand");
        this.maxLength = Math.max(1, plugin.getConfig().getInt("destroyer-hand.max-length", 64));
        this.tiers = tiers;
    }

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.BONE);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.line(l.choose("Mão do Destruidor", "Destroyer's Hand"), NamedTextColor.RED)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                this.line(l.choose("Clique direito num bloco pra limpar", "Right-click a block to clear"), NamedTextColor.GRAY),
                this.line(l.choose("toda a linha ou coluna dele.", "its whole line or column."), NamedTextColor.GRAY),
                this.line(l.choose("Shift + clique esquerdo desfaz a última ação.", "Shift + left-click undoes the last action."), NamedTextColor.GRAY),
                Component.empty(),
                this.line(l.choose("Criativo: não devolve nada.", "Creative: doesn't give anything back."), NamedTextColor.DARK_GRAY),
                this.line(l.choose("Sobrevivência: devolve os blocos.", "Survival: gives the blocks back."), NamedTextColor.DARK_GRAY)));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(this.handKey, PersistentDataType.BYTE, (byte) 1);
        // A one-of-a-kind admin tool, not a bone - pin it to Tier S same as the wand.
        this.tiers.forceTier(meta, ItemTier.S);
        item.setItemMeta(meta);
        return item;
    }

    /** Drops the remembered last action for a player (call on quit - no point holding Block references for an offline player). */
    public void forget(UUID playerId) {
        this.lastAction.remove(playerId);
    }

    public boolean isHand(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.handKey, PersistentDataType.BYTE);
    }

    /**
     * Clears {@code clicked} and every contiguous block of the same Material beyond it
     * along {@code face}'s axis, until it hits a different block (air included) or the
     * configured max length. Returns how many blocks were actually cleared.
     */
    public int clear(Player p, Block clicked, BlockFace face) {
        Material material = clicked.getType();
        if (!material.isBlock() || material.isAir()) {
            return 0;
        }
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
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
        if (!clearedBlocks.isEmpty()) {
            if (!creative) {
                this.give(p, material, clearedBlocks.size());
            }
            this.lastAction.put(p.getUniqueId(), new LastAction(clearedBlocks, savedData, material, !creative));
        }
        return clearedBlocks.size();
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
