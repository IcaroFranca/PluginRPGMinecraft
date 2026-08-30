package dev.icaro.foodtooltips.item;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

/**
 * Universal item rarity ({@link ItemTier}) for every item in the plugin, not just
 * combat gear. Every {@link Material} resolves to a tier — a curated table for
 * equipment (by tool/armor material family) and for a set of notable items, a
 * curated "junk" set for plain naturally-occurring blocks (bottom {@link
 * ItemTier#E}), and Common ({@link ItemTier#D}) as the default for everything
 * else, so nothing is left untagged. Server owners can override any single
 * Material's tier via {@code item-tiers} in config.yml without recompiling.
 *
 * <p>Tooltip rewriting follows the same one-shot pattern as {@code
 * ArmorDefenseService#applyDefenseTooltip}: idempotent via a PDC marker on the
 * item's own {@link ItemMeta}, so each item is only rewritten once. Equipment
 * (tools/weapons/armor) gets a bold "TIER {X} {KIND}" line appended at the very
 * end of its lore (e.g. "TIER C PICKAXE"); everything else gets just the bare
 * "TIER {X}" label, matching the reference tooltips (a fully-decorated item vs.
 * a plain block like Dirt showing only its tier).
 */
public final class ItemTierService {
    private final NamespacedKey tierKey;
    private final NamespacedKey forcedTierKey;
    private final Map<Material, ItemTier> overrides = new EnumMap<>(Material.class);

    public ItemTierService(Plugin plugin) {
        this.tierKey = new NamespacedKey(plugin, "item_tier_applied");
        this.forcedTierKey = new NamespacedKey(plugin, "item_tier_forced");
        var section = plugin.getConfig().getConfigurationSection("item-tiers");
        if (section != null) {
            for (String key : section.getKeys(false)) {
                try {
                    Material m = Material.valueOf(key.toUpperCase(java.util.Locale.ROOT));
                    ItemTier t = ItemTier.valueOf(section.getString(key, "D").toUpperCase(java.util.Locale.ROOT));
                    this.overrides.put(m, t);
                } catch (IllegalArgumentException ignored) {
                    // Unknown Material or tier name in config - skip rather than crash startup.
                }
            }
        }
    }

    // ----- Tier lookup ----------------------------------------------------

    /**
     * Pins a specific item (not the whole Material - see {@code item-tiers} in
     * config.yml for that) to a tier, regardless of what {@link #tierOf} would
     * otherwise say. For one-off special items built entirely in code (like the
     * Builder's Wand, a plain Stick that would otherwise fall into Tier E's junk
     * bucket) - call this on the item's own {@link ItemMeta} while building it,
     * before it ever reaches {@link #applyItemTiers}.
     */
    public void forceTier(ItemMeta meta, ItemTier tier) {
        meta.getPersistentDataContainer().set(this.forcedTierKey, PersistentDataType.STRING, tier.name());
    }

    private ItemTier tierOf(ItemMeta meta, Material m) {
        String forced = meta.getPersistentDataContainer().get(this.forcedTierKey, PersistentDataType.STRING);
        if (forced != null) {
            try {
                return ItemTier.valueOf(forced);
            } catch (IllegalArgumentException ignored) {
                // Corrupted/foreign PDC value - fall through to the normal Material-based lookup.
            }
        }
        return this.tierOf(m);
    }

    public ItemTier tierOf(Material m) {
        ItemTier override = this.overrides.get(m);
        if (override != null) {
            return override;
        }
        String kind = kindOf(m);
        if (kind != null) {
            return equipmentTier(m, kind);
        }
        if (S_ITEMS.contains(m)) return ItemTier.S;
        if (A_ITEMS.contains(m)) return ItemTier.A;
        if (B_ITEMS.contains(m)) return ItemTier.B;
        if (C_ITEMS.contains(m)) return ItemTier.C;
        if (JUNK_ITEMS.contains(m)) return ItemTier.E;
        return ItemTier.D;
    }

    private static ItemTier equipmentTier(Material m, String kind) {
        // Standalone weapons - no "{FAMILY}_{KIND}" material name to read a family from.
        switch (m) {
            case TRIDENT, MACE -> { return ItemTier.A; }
            case CROSSBOW, SHIELD -> { return ItemTier.B; }
            case BOW, FISHING_ROD -> { return ItemTier.C; }
            default -> { /* fall through to family-prefix lookup below */ }
        }
        String name = m.name();
        String family = name.endsWith("_" + kind) ? name.substring(0, name.length() - kind.length() - 1) : name;
        return switch (family) {
            case "NETHERITE" -> ItemTier.S;
            case "DIAMOND" -> ItemTier.A;
            case "IRON", "GOLDEN", "GOLD", "COPPER", "CHAINMAIL" -> ItemTier.B;
            case "STONE" -> ItemTier.C;
            case "WOODEN", "WOOD", "LEATHER", "TURTLE" -> ItemTier.D;
            default -> ItemTier.D;
        };
    }

    /** Vanilla item-type word to append after the tier for equipment (e.g. "PICKAXE"), or null for plain items. */
    private static String kindOf(Material m) {
        String n = m.name();
        if (n.endsWith("_SWORD")) return "SWORD";
        if (n.endsWith("_PICKAXE")) return "PICKAXE";
        if (n.endsWith("_AXE")) return "AXE";
        if (n.endsWith("_SHOVEL")) return "SHOVEL";
        if (n.endsWith("_HOE")) return "HOE";
        if (n.endsWith("_HELMET")) return "HELMET";
        if (n.endsWith("_CHESTPLATE")) return "CHESTPLATE";
        if (n.endsWith("_LEGGINGS")) return "LEGGINGS";
        if (n.endsWith("_BOOTS")) return "BOOTS";
        return switch (m) {
            case BOW -> "BOW";
            case CROSSBOW -> "CROSSBOW";
            case TRIDENT -> "TRIDENT";
            case SHIELD -> "SHIELD";
            case MACE -> "MACE";
            case FISHING_ROD -> "FISHING ROD";
            default -> null;
        };
    }

    private static final Set<Material> S_ITEMS = EnumSet.of(
            Material.NETHERITE_INGOT, Material.NETHERITE_BLOCK, Material.NETHERITE_SCRAP,
            Material.ANCIENT_DEBRIS, Material.NETHER_STAR, Material.DRAGON_EGG,
            Material.DRAGON_HEAD, Material.ELYTRA, Material.TOTEM_OF_UNDYING,
            Material.ENCHANTED_GOLDEN_APPLE, Material.BEACON, Material.WITHER_SKELETON_SKULL,
            Material.HEART_OF_THE_SEA);

    private static final Set<Material> A_ITEMS = EnumSet.of(
            Material.DIAMOND, Material.DIAMOND_BLOCK, Material.DIAMOND_ORE, Material.DEEPSLATE_DIAMOND_ORE,
            Material.EMERALD, Material.EMERALD_BLOCK, Material.EMERALD_ORE, Material.DEEPSLATE_EMERALD_ORE,
            Material.GOLDEN_APPLE, Material.ENCHANTED_BOOK, Material.EXPERIENCE_BOTTLE,
            Material.SHULKER_SHELL, Material.CONDUIT, Material.NAUTILUS_SHELL);

    private static final Set<Material> B_ITEMS = EnumSet.of(
            Material.IRON_INGOT, Material.IRON_BLOCK, Material.IRON_ORE, Material.DEEPSLATE_IRON_ORE,
            Material.GOLD_INGOT, Material.GOLD_BLOCK, Material.GOLD_ORE, Material.DEEPSLATE_GOLD_ORE,
            Material.COPPER_INGOT, Material.COPPER_BLOCK, Material.COPPER_ORE, Material.DEEPSLATE_COPPER_ORE,
            Material.LAPIS_LAZULI, Material.LAPIS_BLOCK, Material.LAPIS_ORE, Material.DEEPSLATE_LAPIS_ORE,
            Material.REDSTONE, Material.REDSTONE_BLOCK, Material.REDSTONE_ORE, Material.DEEPSLATE_REDSTONE_ORE,
            Material.AMETHYST_SHARD, Material.AMETHYST_BLOCK, Material.QUARTZ, Material.QUARTZ_BLOCK,
            Material.OBSIDIAN, Material.CRYING_OBSIDIAN, Material.RESPAWN_ANCHOR,
            Material.BLAZE_ROD, Material.BLAZE_POWDER, Material.GHAST_TEAR,
            Material.ENDER_PEARL, Material.ENDER_EYE, Material.SADDLE, Material.NAME_TAG);

    private static final Set<Material> C_ITEMS = EnumSet.of(
            Material.COAL, Material.COAL_BLOCK, Material.COAL_ORE, Material.DEEPSLATE_COAL_ORE,
            Material.RAW_IRON, Material.RAW_GOLD, Material.RAW_COPPER,
            Material.RAW_IRON_BLOCK, Material.RAW_GOLD_BLOCK, Material.RAW_COPPER_BLOCK,
            Material.STRING, Material.LEATHER, Material.BONE, Material.GUNPOWDER,
            Material.SLIME_BALL, Material.MAGMA_CREAM, Material.SPIDER_EYE);

    private static final Set<Material> JUNK_ITEMS = EnumSet.of(
            Material.DIRT, Material.COARSE_DIRT, Material.GRASS_BLOCK, Material.PODZOL, Material.MYCELIUM,
            Material.SAND, Material.RED_SAND, Material.GRAVEL, Material.CLAY,
            Material.STONE, Material.COBBLESTONE, Material.COBBLED_DEEPSLATE, Material.DEEPSLATE,
            Material.NETHERRACK, Material.BASALT, Material.TUFF, Material.ANDESITE, Material.DIORITE, Material.GRANITE,
            Material.ROTTEN_FLESH, Material.STICK, Material.OAK_SAPLING, Material.SPRUCE_SAPLING,
            Material.BIRCH_SAPLING, Material.JUNGLE_SAPLING, Material.ACACIA_SAPLING, Material.DARK_OAK_SAPLING,
            Material.WHEAT_SEEDS, Material.POISONOUS_POTATO, Material.FLINT);

    // ----- Tooltip rewrite --------------------------------------------------

    /** Applies the tier tooltip to every item in the player's inventory (storage, armor and offhand). */
    public void applyItemTiers(Player p) {
        Language l = Language.of(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack updated = this.tooltip(storage[i], l);
            if (updated != null) {
                storage[i] = updated;
                changed = true;
            }
        }
        // A freshly-received item (bought, mined, looted, given...) only gets its tier
        // tooltip applied here, one tick after it lands in the inventory - for that one
        // tick its lore/name doesn't match an already-tagged stack of the same item, so
        // the game can't merge them and they end up as two separate slots even once both
        // are tagged identically. Re-coalescing every tick, right after tagging, heals
        // that split (and any other stray fragmentation) instead of leaving it stuck.
        if (this.coalesce(storage)) {
            changed = true;
        }
        if (changed) {
            inv.setStorageContents(storage);
        }
        ItemStack[] armor = inv.getArmorContents();
        for (int i = 0; i < armor.length; i++) {
            ItemStack updated = this.tooltip(armor[i], l);
            if (updated != null) {
                armor[i] = updated;
            }
        }
        inv.setArmorContents(armor);
        ItemStack offhand = this.tooltip(inv.getItemInOffHand(), l);
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
    }

    /** Returns the mutated item if it needed rewriting, or null if it's not taggable or was already done. */
    private ItemStack tooltip(ItemStack item, Language l) {
        if (item == null || item.isEmpty() || !item.getType().isItem()) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getPersistentDataContainer().has(this.tierKey, PersistentDataType.BYTE)) {
            return null;
        }
        ItemTier tier = this.tierOf(meta, item.getType());
        String kind = kindOf(item.getType());
        String text = kind == null ? tier.label() : tier.label() + " " + kind;
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        if (!lore.isEmpty()) {
            lore.add(Component.empty());
        }
        lore.add(Component.text(text, tier.color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        // Tooltip box border is a fixed vanilla texture (can't be recolored per-item without a
        // custom resource pack via the tooltip_style component) - so instead the item's own
        // name is recolored/bolded to match its tier, the closest at-a-glance signal without one.
        Component base = meta.hasDisplayName() ? meta.displayName() : Component.translatable(item.getType().translationKey());
        meta.displayName(base.color(tier.color())
                .decoration(TextDecoration.BOLD, true)
                .decoration(TextDecoration.ITALIC, false));
        meta.getPersistentDataContainer().set(this.tierKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Merges any same-item stacks in {@code storage} into as few slots as possible, respecting max stack size. Returns true if anything moved. */
    private boolean coalesce(ItemStack[] storage) {
        boolean changed = false;
        for (int i = 0; i < storage.length; i++) {
            ItemStack into = storage[i];
            if (into == null || into.isEmpty() || into.getAmount() >= into.getMaxStackSize()) {
                continue;
            }
            for (int j = i + 1; j < storage.length; j++) {
                ItemStack from = storage[j];
                if (from == null || from.isEmpty() || !into.isSimilar(from)) {
                    continue;
                }
                int move = Math.min(into.getMaxStackSize() - into.getAmount(), from.getAmount());
                if (move <= 0) {
                    continue;
                }
                into.setAmount(into.getAmount() + move);
                from.setAmount(from.getAmount() - move);
                if (from.getAmount() <= 0) {
                    storage[j] = null;
                }
                changed = true;
                if (into.getAmount() >= into.getMaxStackSize()) {
                    break;
                }
            }
        }
        return changed;
    }
}
