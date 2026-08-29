package dev.icaro.foodtooltips.skills;

import org.bukkit.Material;

/**
 * Every combat ability, active or passive, plus the 6 Combat Backpack
 * capacity nodes (moved into the tree so backpack growth is purchased with
 * Blood Points like everything else, instead of being tied directly to
 * Combat level - see {@link BackpackService}). Unlocking/upgrading a node is
 * gated by Blood Points, the tree's prerequisite chain (see {@link
 * CombatTreeNode}), and a minimum Combat level per tree tier (see {@link
 * CombatAbilityService#levelRequirement}) — handled by {@link
 * CombatAbilityService}.
 */
public enum CombatAbility {
    RUTHLESS_STRIKES(Material.FLINT, "Golpes Implacáveis", "Ruthless Strikes"),
    SWORD_THROW(Material.IRON_SWORD, "Arremesso de Espada", "Sword Throw"),
    BLOOD_LUST(Material.REDSTONE, "Sede de Sangue", "Blood Lust"),
    BERSERKER(Material.NETHERITE_AXE, "Berserker", "Berserker"),
    SOUL_HARVEST(Material.ECHO_SHARD, "Colheita de Almas", "Soul Harvest"),
    CRITICAL_MASTERY(Material.NETHERITE_SWORD, "Maestria Crítica", "Critical Mastery"),
    SECOND_WIND(Material.TOTEM_OF_UNDYING, "Segundo Fôlego", "Second Wind"),

    // ---- Combat Backpack capacity, one node per backpack level (9/18/27/36/45/54 slots) ----
    BACKPACK_1(Material.BUNDLE, "Mochila de Combate (9)", "Combat Backpack (9)"),
    BACKPACK_2(Material.BUNDLE, "Mochila de Combate (18)", "Combat Backpack (18)"),
    BACKPACK_3(Material.BUNDLE, "Mochila de Combate (27)", "Combat Backpack (27)"),
    BACKPACK_4(Material.BUNDLE, "Mochila de Combate (36)", "Combat Backpack (36)"),
    BACKPACK_5(Material.BUNDLE, "Mochila de Combate (45)", "Combat Backpack (45)"),
    BACKPACK_6(Material.BUNDLE, "Mochila de Combate (54)", "Combat Backpack (54)");

    private final Material icon;
    private final String pt;
    private final String en;

    CombatAbility(Material icon, String pt, String en) {
        this.icon = icon;
        this.pt = pt;
        this.en = en;
    }

    public Material icon() {
        return this.icon;
    }

    public String name(boolean portuguese) {
        return portuguese ? this.pt : this.en;
    }
}
