package dev.icaro.foodtooltips.skills;

import org.bukkit.Material;

public enum BackpackType {
    COMBAT("Combate", "Combat", Material.IRON_SWORD),
    FARMING("Agricultura", "Farming", Material.WHEAT),
    FISHING("Pesca", "Fishing", Material.COD),
    MINING("Minera\u00e7\u00e3o", "Mining", Material.DIAMOND),
    FORAGING("Coleta", "Foraging", Material.OAK_LOG),
    ENCHANTING("Encantamento", "Enchanting", Material.ENCHANTED_BOOK),
    ALCHEMY("Alquimia", "Alchemy", Material.POTION);

    private final String pt;
    private final String en;
    private final Material fallback;

    private BackpackType(String pt, String en, Material fallback) {
        this.pt = pt;
        this.en = en;
        this.fallback = fallback;
    }

    public String name(boolean pt) {
        return pt ? this.pt : this.en;
    }

    public Material fallback() {
        return this.fallback;
    }
}

