package dev.icaro.foodtooltips.skills;

import org.bukkit.Material;

/**
 * Every combat ability, active or passive. {@link #level()} is no longer an
 * auto-unlock threshold: it is the minimum Combat skill level required to
 * become <em>eligible</em> to spend Valor on this node in the ability tree
 * (see {@link CombatTreeNode}). Actual unlocking/upgrading is currency- and
 * prerequisite-gated, handled by {@link CombatAbilityService}.
 */
public enum CombatAbility {
    TELEKINESIS(1, Material.ENDER_PEARL, "Telecinese", "Telekinesis"),
    RUTHLESS_STRIKES(5, Material.FLINT, "Golpes Implacáveis", "Ruthless Strikes"),
    VAMPIRISM(5, Material.FERMENTED_SPIDER_EYE, "Vampirismo", "Vampirism"),
    SWORD_THROW(15, Material.IRON_SWORD, "Arremesso de Espada", "Sword Throw"),
    EXECUTIONER(20, Material.IRON_AXE, "Execução", "Executioner"),
    BLOOD_LUST(25, Material.REDSTONE, "Sede de Sangue", "Blood Lust"),
    TREASURE_HUNTER(45, Material.GOLD_INGOT, "Caçador de Tesouros", "Treasure Hunter"),
    HUNTERS_INSTINCT(50, Material.RABBIT_FOOT, "Instinto do Caçador", "Hunter's Instinct"),
    BERSERKER(55, Material.NETHERITE_AXE, "Berserker", "Berserker"),
    UNDYING_WILL(60, Material.SHIELD, "Vontade Inabalável", "Undying Will"),
    VITAL_TOUCH(65, Material.GLISTERING_MELON_SLICE, "Toque Vital", "Vital Touch"),
    COMBAT_MASTERY(70, Material.NETHER_STAR, "Maestria de Combate", "Combat Mastery"),
    ARCANE_SLASH(80, Material.BLAZE_ROD, "Corte Arcano", "Arcane Slash"),
    CLEAVE(90, Material.ENCHANTED_BOOK, "Golpe em Arco", "Cleave"),
    ARMOR_PIERCER(100, Material.ARMOR_STAND, "Perfurador de Armadura", "Armor Piercer"),
    SOUL_HARVEST(110, Material.ECHO_SHARD, "Colheita de Almas", "Soul Harvest"),
    CRITICAL_MASTERY(150, Material.NETHERITE_SWORD, "Maestria Crítica", "Critical Mastery"),
    SECOND_WIND(160, Material.TOTEM_OF_UNDYING, "Segundo Fôlego", "Second Wind"),
    RELENTLESS(170, Material.REDSTONE_TORCH, "Implacável", "Relentless"),
    APEX_WARRIOR(200, Material.DRAGON_EGG, "Guerreiro Supremo", "Apex Warrior");

    private final int level;
    private final Material icon;
    private final String pt;
    private final String en;

    CombatAbility(int level, Material icon, String pt, String en) {
        this.level = level;
        this.icon = icon;
        this.pt = pt;
        this.en = en;
    }

    /** Minimum Combat skill level required for this node to become eligible in the tree. */
    public int level() {
        return this.level;
    }

    public Material icon() {
        return this.icon;
    }

    public String name(boolean portuguese) {
        return portuguese ? this.pt : this.en;
    }
}
