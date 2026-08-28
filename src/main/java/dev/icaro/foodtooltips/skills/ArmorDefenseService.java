package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.List;
import net.kyori.adventure.key.Key;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.inventory.EntityEquipment;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.persistence.PersistentDataType;

/**
 * Defense now comes entirely from a fixed per-piece table below, not from
 * vanilla armor points/toughness — for players and mobs alike. {@link
 * #neutralizeVanillaArmor(LivingEntity)} cancels the {@link
 * Attribute#ARMOR}/{@link Attribute#ARMOR_TOUGHNESS} an entity would
 * otherwise get from wearing armor (call on player join/HUD tick — see
 * {@link ArmorDefenseListener} — and once on mob spawn, since mobs rarely
 * change gear after spawning), and {@link #defense(LivingEntity)} is the
 * number actually used everywhere "Defense" is shown or applied (replaces
 * the old, unrelated {@code GeneralSkillService#defense}, which was really
 * just Mining level). {@link #applyDefenseTooltip(Player)} rewrites the
 * armor item's own tooltip to match (players only — mobs don't have a
 * tooltip-visible inventory): vanilla's Armor/Armor Toughness attribute
 * lines hidden, a "Defense: +N" lore line shown instead, so the number the
 * player sees on the item itself is the number that actually applies.
 *
 * <p>Knockback resistance (netherite's vanilla perk) is deliberately left
 * alone — only ARMOR/ARMOR_TOUGHNESS are neutralized.
 */
public final class ArmorDefenseService {
    private final NamespacedKey armorKey = new NamespacedKey("foodtooltips", "vanilla_armor_zero");
    private final NamespacedKey toughnessKey = new NamespacedKey("foodtooltips", "vanilla_armor_toughness_zero");
    private final NamespacedKey tooltipKey = new NamespacedKey("foodtooltips", "defense_tooltip_applied");

    /** Sum of the equipped helmet/chestplate/leggings/boots' Defense values — works for any player or mob. */
    public int defense(LivingEntity e) {
        EntityEquipment eq = e.getEquipment();
        if (eq == null) {
            return 0;
        }
        return pieceDefense(eq.getHelmet()) + pieceDefense(eq.getChestplate()) + pieceDefense(eq.getLeggings()) + pieceDefense(eq.getBoots());
    }

    /** Same curve as before (defense/(defense+100)): 100 Defense = 50% reduction, approaching 100% asymptotically. */
    public double damageReduction(LivingEntity e) {
        int defense = this.defense(e);
        return (double) defense / ((double) defense + 100.0);
    }

    private static int pieceDefense(ItemStack item) {
        return item == null ? 0 : defenseFor(item.getType());
    }

    /**
     * Leather/Iron/Golden/Diamond values are the ones requested; Chainmail,
     * Copper and Netherite were left to balance — Copper sits just above
     * Leather (a soft metal, weaker than every other armor tier), Chainmail
     * between Copper and Iron (closer to Iron), Netherite a clear step above
     * Diamond (~+17% total), matching vanilla's relative material ordering.
     * Turtle Helmet gets a small value too so wearing one isn't a hard
     * defense downgrade to zero.
     */
    private static int defenseFor(Material m) {
        return switch (m) {
            case Material.LEATHER_HELMET -> 5;
            case Material.LEATHER_CHESTPLATE -> 15;
            case Material.LEATHER_LEGGINGS -> 10;
            case Material.LEATHER_BOOTS -> 5;

            case Material.COPPER_HELMET -> 6;
            case Material.COPPER_CHESTPLATE -> 18;
            case Material.COPPER_LEGGINGS -> 13;
            case Material.COPPER_BOOTS -> 6;

            case Material.CHAINMAIL_HELMET -> 9;
            case Material.CHAINMAIL_CHESTPLATE -> 23;
            case Material.CHAINMAIL_LEGGINGS -> 18;
            case Material.CHAINMAIL_BOOTS -> 8;

            case Material.GOLDEN_HELMET -> 10;
            case Material.GOLDEN_CHESTPLATE -> 25;
            case Material.GOLDEN_LEGGINGS -> 15;
            case Material.GOLDEN_BOOTS -> 5;

            case Material.IRON_HELMET -> 12;
            case Material.IRON_CHESTPLATE -> 30;
            case Material.IRON_LEGGINGS -> 25;
            case Material.IRON_BOOTS -> 10;

            case Material.DIAMOND_HELMET -> 15;
            case Material.DIAMOND_CHESTPLATE -> 40;
            case Material.DIAMOND_LEGGINGS -> 30;
            case Material.DIAMOND_BOOTS -> 15;

            case Material.NETHERITE_HELMET -> 18;
            case Material.NETHERITE_CHESTPLATE -> 46;
            case Material.NETHERITE_LEGGINGS -> 35;
            case Material.NETHERITE_BOOTS -> 18;

            case Material.TURTLE_HELMET -> 4;

            default -> 0;
        };
    }

    /**
     * Rewrites every armor piece in the player's inventory (equipped, offhand, or just
     * sitting in a bag slot) so its own tooltip shows "Defense: +N" instead of vanilla's
     * Armor/Armor Toughness attribute lines. Idempotent via a PDC marker on the item
     * itself, so it only rewrites each piece once (enchants/renames added later keep
     * working normally — this only ever prepends one lore line and hides attributes).
     */
    public void applyDefenseTooltip(Player p) {
        Language l = Language.of(p);
        PlayerInventory inv = p.getInventory();
        ItemStack[] storage = inv.getStorageContents();
        for (int i = 0; i < storage.length; i++) {
            ItemStack updated = tooltip(storage[i], l);
            if (updated != null) {
                inv.setItem(i, updated);
            }
        }
        ItemStack helmet = tooltip(inv.getHelmet(), l);
        if (helmet != null) {
            inv.setHelmet(helmet);
        }
        ItemStack chest = tooltip(inv.getChestplate(), l);
        if (chest != null) {
            inv.setChestplate(chest);
        }
        ItemStack legs = tooltip(inv.getLeggings(), l);
        if (legs != null) {
            inv.setLeggings(legs);
        }
        ItemStack boots = tooltip(inv.getBoots(), l);
        if (boots != null) {
            inv.setBoots(boots);
        }
        ItemStack offhand = tooltip(inv.getItemInOffHand(), l);
        if (offhand != null) {
            inv.setItemInOffHand(offhand);
        }
    }

    /** Returns the mutated item if it needed rewriting, or null if it's not armor or was already done. */
    private ItemStack tooltip(ItemStack item, Language l) {
        if (item == null || item.getType().isAir()) {
            return null;
        }
        int def = defenseFor(item.getType());
        if (def <= 0) {
            return null;
        }
        ItemMeta meta = item.getItemMeta();
        if (meta == null || meta.getPersistentDataContainer().has(this.tooltipKey, PersistentDataType.BYTE)) {
            return null;
        }
        meta.addItemFlags(ItemFlag.HIDE_ATTRIBUTES);
        List<Component> lore = meta.hasLore() ? new ArrayList<>(meta.lore()) : new ArrayList<>();
        lore.add(0, Component.text(l.choose("Defesa: +", "Defense: +") + def, NamedTextColor.GREEN).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        meta.getPersistentDataContainer().set(this.tooltipKey, PersistentDataType.BYTE, (byte) 1);
        item.setItemMeta(meta);
        return item;
    }

    /** Zeroes ARMOR and ARMOR_TOUGHNESS so only {@link #defense(LivingEntity)} matters for damage reduction. */
    public void neutralizeVanillaArmor(LivingEntity e) {
        zero(e, Attribute.ARMOR, this.armorKey);
        zero(e, Attribute.ARMOR_TOUGHNESS, this.toughnessKey);
    }

    private static void zero(LivingEntity e, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = e.getAttribute(attribute);
        if (instance == null) {
            return;
        }
        AttributeModifier old = instance.getModifier(Key.key(key.getNamespace(), key.getKey()));
        if (old != null) {
            instance.removeModifier(old);
        }
        double current = instance.getValue();
        if (current > 1.0E-4) {
            instance.addTransientModifier(new AttributeModifier(key, -current, AttributeModifier.Operation.ADD_NUMBER));
        }
    }
}
