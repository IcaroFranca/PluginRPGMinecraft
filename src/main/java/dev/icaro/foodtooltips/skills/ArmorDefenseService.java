package dev.icaro.foodtooltips.skills;

import net.kyori.adventure.key.Key;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.attribute.AttributeModifier;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;

/**
 * Defense now comes entirely from a fixed per-piece table below, not from
 * vanilla armor points/toughness — {@link #neutralizeVanillaArmor(Player)}
 * cancels the {@link Attribute#ARMOR}/{@link Attribute#ARMOR_TOUGHNESS} a
 * player would otherwise get from wearing armor (call on join and whenever
 * equipment changes; see {@link ArmorDefenseListener}), and {@link
 * #defense(Player)} is the number actually used everywhere "Defense" is
 * shown or applied (replaces the old, unrelated
 * {@code GeneralSkillService#defense}, which was really just Mining level).
 *
 * <p>Knockback resistance (netherite's vanilla perk) is deliberately left
 * alone — only ARMOR/ARMOR_TOUGHNESS are neutralized.
 */
public final class ArmorDefenseService {
    private final NamespacedKey armorKey = new NamespacedKey("foodtooltips", "vanilla_armor_zero");
    private final NamespacedKey toughnessKey = new NamespacedKey("foodtooltips", "vanilla_armor_toughness_zero");

    /** Sum of the equipped helmet/chestplate/leggings/boots' Defense values. */
    public int defense(Player p) {
        return pieceDefense(p.getInventory().getHelmet())
                + pieceDefense(p.getInventory().getChestplate())
                + pieceDefense(p.getInventory().getLeggings())
                + pieceDefense(p.getInventory().getBoots());
    }

    /** Same curve as before (defense/(defense+100)): 100 Defense = 50% reduction, approaching 100% asymptotically. */
    public double damageReduction(Player p) {
        int defense = this.defense(p);
        return (double) defense / ((double) defense + 100.0);
    }

    private static int pieceDefense(ItemStack item) {
        return item == null ? 0 : defenseFor(item.getType());
    }

    /**
     * Leather/Iron/Golden/Diamond values are the ones requested; Chainmail and
     * Netherite were left to balance — Chainmail sits between Leather and Iron
     * (closer to Iron), Netherite a clear step above Diamond (~+17% total),
     * matching vanilla's relative material ordering. Turtle Helmet gets a
     * small value too so wearing one isn't a hard defense downgrade to zero.
     */
    private static int defenseFor(Material m) {
        return switch (m) {
            case Material.LEATHER_HELMET -> 5;
            case Material.LEATHER_CHESTPLATE -> 15;
            case Material.LEATHER_LEGGINGS -> 10;
            case Material.LEATHER_BOOTS -> 5;

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

    /** Zeroes ARMOR and ARMOR_TOUGHNESS so only {@link #defense(Player)} matters for damage reduction. */
    public void neutralizeVanillaArmor(Player p) {
        zero(p, Attribute.ARMOR, this.armorKey);
        zero(p, Attribute.ARMOR_TOUGHNESS, this.toughnessKey);
    }

    private static void zero(Player p, Attribute attribute, NamespacedKey key) {
        AttributeInstance instance = p.getAttribute(attribute);
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
