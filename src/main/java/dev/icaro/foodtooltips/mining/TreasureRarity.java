package dev.icaro.foodtooltips.mining;

import net.kyori.adventure.text.format.NamedTextColor;

public enum TreasureRarity {
    COMMON("Comum", "Common", NamedTextColor.WHITE),
    UNCOMMON("Incomum", "Uncommon", NamedTextColor.GREEN),
    RARE("Raro", "Rare", NamedTextColor.AQUA),
    EPIC("\u00c9pico", "Epic", NamedTextColor.LIGHT_PURPLE),
    LEGENDARY("Lend\u00e1rio", "Legendary", NamedTextColor.GOLD);

    private final String pt;
    private final String en;
    private final NamedTextColor color;

    private TreasureRarity(String pt, String en, NamedTextColor color) {
        this.pt = pt;
        this.en = en;
        this.color = color;
    }

    public String name(boolean pt) {
        return pt ? this.pt : this.en;
    }

    public NamedTextColor color() {
        return this.color;
    }
}

