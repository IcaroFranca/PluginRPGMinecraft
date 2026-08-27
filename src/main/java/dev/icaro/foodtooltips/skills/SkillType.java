package dev.icaro.foodtooltips.skills;

import org.bukkit.Material;

public enum SkillType {
    FARMING(Material.GOLDEN_HOE, "Agricultura", "Farming"),
    FISHING(Material.FISHING_ROD, "Pesca", "Fishing"),
    MINING(Material.IRON_PICKAXE, "Minera\u00e7\u00e3o", "Mining"),
    FORAGING(Material.OAK_SAPLING, "Coleta", "Foraging"),
    ENCHANTING(Material.ENCHANTING_TABLE, "Encantamento", "Enchanting"),
    ALCHEMY(Material.BREWING_STAND, "Alquimia", "Alchemy");

    private final Material icon;
    private final String pt;
    private final String en;

    private SkillType(Material icon, String pt, String en) {
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

