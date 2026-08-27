package dev.icaro.foodtooltips.skills;

import org.bukkit.Material;

/**
 * Every combat ability, active or passive. Unlocking/upgrading a node is
 * gated purely by Blood Points and the tree's prerequisite chain (see
 * {@link CombatTreeNode}), handled by {@link CombatAbilityService} — there
 * is no separate Combat-level requirement.
 */
public enum CombatAbility {
    RUTHLESS_STRIKES(Material.FLINT, "Golpes Implacáveis", "Ruthless Strikes"),
    VAMPIRISM(Material.FERMENTED_SPIDER_EYE, "Vampirismo", "Vampirism"),
    SWORD_THROW(Material.IRON_SWORD, "Arremesso de Espada", "Sword Throw"),
    EXECUTIONER(Material.IRON_AXE, "Execução", "Executioner"),
    BLOOD_LUST(Material.REDSTONE, "Sede de Sangue", "Blood Lust"),
    TREASURE_HUNTER(Material.GOLD_INGOT, "Caçador de Tesouros", "Treasure Hunter"),
    HUNTERS_INSTINCT(Material.RABBIT_FOOT, "Instinto do Caçador", "Hunter's Instinct"),
    BERSERKER(Material.NETHERITE_AXE, "Berserker", "Berserker"),
    UNDYING_WILL(Material.SHIELD, "Vontade Inabalável", "Undying Will"),
    VITAL_TOUCH(Material.GLISTERING_MELON_SLICE, "Toque Vital", "Vital Touch"),
    COMBAT_MASTERY(Material.NETHER_STAR, "Maestria de Combate", "Combat Mastery"),
    ARCANE_SLASH(Material.BLAZE_ROD, "Corte Arcano", "Arcane Slash"),
    CLEAVE(Material.ENCHANTED_BOOK, "Golpe em Arco", "Cleave"),
    ARMOR_PIERCER(Material.ARMOR_STAND, "Perfurador de Armadura", "Armor Piercer"),
    SOUL_HARVEST(Material.ECHO_SHARD, "Colheita de Almas", "Soul Harvest"),
    CRITICAL_MASTERY(Material.NETHERITE_SWORD, "Maestria Crítica", "Critical Mastery"),
    SECOND_WIND(Material.TOTEM_OF_UNDYING, "Segundo Fôlego", "Second Wind"),
    RELENTLESS(Material.REDSTONE_TORCH, "Implacável", "Relentless"),
    APEX_WARRIOR(Material.DRAGON_EGG, "Guerreiro Supremo", "Apex Warrior");

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
