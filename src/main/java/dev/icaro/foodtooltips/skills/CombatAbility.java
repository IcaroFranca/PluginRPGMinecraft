package dev.icaro.foodtooltips.skills;

import org.bukkit.Material;

public enum CombatAbility {
    TELEKINESIS(1, Material.ENDER_PEARL, "Telecinese", "Telekinesis"),
    EXECUTIONER(10, Material.IRON_AXE, "Execu\u00e7\u00e3o", "Executioner"),
    SWORD_THROW(15, Material.IRON_SWORD, "Arremesso de Espada", "Sword Throw"),
    BLOOD_LUST(25, Material.REDSTONE, "Sede de Sangue", "Blood Lust"),
    VAMPIRISM(35, Material.FERMENTED_SPIDER_EYE, "Vampirismo", "Vampirism"),
    COMBAT_MASTERY(50, Material.NETHER_STAR, "Maestria de Combate", "Combat Mastery"),
    HUNTERS_INSTINCT(60, Material.RABBIT_FOOT, "Instinto do Ca\u00e7ador", "Hunter's Instinct"),
    BERSERKER(75, Material.NETHERITE_AXE, "Berserker", "Berserker"),
    CLEAVE(90, Material.ENCHANTED_BOOK, "Golpe em Arco", "Cleave"),
    TREASURE_HUNTER(100, Material.GOLD_INGOT, "Ca\u00e7ador de Tesouros", "Treasure Hunter"),
    SECOND_WIND(125, Material.TOTEM_OF_UNDYING, "Segundo F\u00f4lego", "Second Wind"),
    ARMOR_PIERCER(140, Material.ARMOR_STAND, "Perfurador de Armadura", "Armor Piercer"),
    CRITICAL_MASTERY(150, Material.NETHERITE_SWORD, "Maestria Cr\u00edtica", "Critical Mastery"),
    SOUL_HARVEST(175, Material.ECHO_SHARD, "Colheita de Almas", "Soul Harvest"),
    RELENTLESS(190, Material.REDSTONE_TORCH, "Implac\u00e1vel", "Relentless"),
    APEX_WARRIOR(200, Material.DRAGON_EGG, "Guerreiro Supremo", "Apex Warrior");

    private final int level;
    private final Material icon;
    private final String pt;
    private final String en;

    private CombatAbility(int level, Material icon, String pt, String en) {
        this.level = level;
        this.icon = icon;
        this.pt = pt;
        this.en = en;
    }

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

