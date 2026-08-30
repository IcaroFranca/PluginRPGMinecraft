package dev.icaro.foodtooltips.builder;

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
 * The Builder's Wand: right-click a placed block to extend it into a full line
 * (horizontal face) or column (top/bottom face) of the same block, starting from the
 * block adjacent to the one clicked. In Creative it's free (matches Creative's own
 * "unlimited blocks" rule); in Survival it consumes one matching block from the
 * player's inventory per block placed and stops the moment it runs out.
 *
 * <p>For now the wand is admin-only and only reachable via a command (see
 * {@code FoodTooltipsPlugin}'s {@code /builderwand} executor) - no drop/craft source yet.
 */
public final class BuilderWandService {
    /** One player's last extension: the exact blocks it placed, and whether it paid for them (so undo knows whether to refund). */
    private record LastAction(List<Block> placed, Material material, boolean consumed) {
    }

    private final NamespacedKey wandKey;
    private final int maxLength;
    private final ItemTierService tiers;
    private final Map<UUID, LastAction> lastAction = new HashMap<>();

    public BuilderWandService(Plugin plugin, ItemTierService tiers) {
        this.wandKey = new NamespacedKey(plugin, "builder_wand");
        this.maxLength = Math.max(1, plugin.getConfig().getInt("builder-wand.max-length", 64));
        this.tiers = tiers;
    }

    public ItemStack create(Language l) {
        ItemStack item = new ItemStack(Material.STICK);
        ItemMeta meta = item.getItemMeta();
        meta.displayName(this.line(l.choose("Varinha do Construtor", "Builder's Wand"), NamedTextColor.AQUA)
                .decoration(TextDecoration.BOLD, true));
        meta.lore(List.of(
                this.line(l.choose("Clique direito num bloco pra estender", "Right-click a block to extend"), NamedTextColor.GRAY),
                this.line(l.choose("toda a linha ou coluna dele.", "its whole line or column."), NamedTextColor.GRAY),
                this.line(l.choose("Shift + clique esquerdo desfaz a última ação.", "Shift + left-click undoes the last action."), NamedTextColor.GRAY),
                Component.empty(),
                this.line(l.choose("Criativo: não gasta blocos.", "Creative: doesn't use blocks."), NamedTextColor.DARK_GRAY),
                this.line(l.choose("Sobrevivência: precisa ter os blocos.", "Survival: needs the blocks."), NamedTextColor.DARK_GRAY)));
        meta.setEnchantmentGlintOverride(true);
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ENCHANTS, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        meta.getPersistentDataContainer().set(this.wandKey, PersistentDataType.BYTE, (byte) 1);
        // A one-of-a-kind admin tool, not a stick - pin it to Tier S so ItemTierService's
        // periodic pass doesn't sort it into Tier E with every other plain Stick.
        this.tiers.forceTier(meta, ItemTier.S);
        item.setItemMeta(meta);
        return item;
    }

    /** Drops the remembered last action for a player (call on quit - no point holding Block references for an offline player). */
    public void forget(UUID playerId) {
        this.lastAction.remove(playerId);
    }

    public boolean isWand(ItemStack item) {
        if (item == null || item.getType().isAir()) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        return meta != null && meta.getPersistentDataContainer().has(this.wandKey, PersistentDataType.BYTE);
    }

    /**
     * Extends {@code clicked}'s block into the line/column implied by {@code face},
     * replacing air only, until it hits a non-air block, the configured max length, or
     * (Survival only) runs out of that material in {@code p}'s inventory. Returns how
     * many blocks were actually placed.
     */
    public int extend(Player p, Block clicked, BlockFace face) {
        Material material = clicked.getType();
        if (!material.isBlock() || !material.isSolid()) {
            return 0;
        }
        BlockData data = clicked.getBlockData();
        boolean creative = p.getGameMode() == GameMode.CREATIVE;
        List<Block> placedBlocks = new ArrayList<>();
        Block cursor = clicked.getRelative(face);
        for (int i = 0; i < this.maxLength; i++) {
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
        if (!placedBlocks.isEmpty()) {
            this.lastAction.put(p.getUniqueId(), new LastAction(placedBlocks, material, !creative));
        }
        return placedBlocks.size();
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
