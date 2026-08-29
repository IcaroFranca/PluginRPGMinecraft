package dev.icaro.foodtooltips.item;

import net.kyori.adventure.text.format.NamedTextColor;

/**
 * Universal item rarity, expressed as a letter Tier rather than the classic
 * Hypixel rarity words (per the player's request: "vamos fazer diferente,
 * vai ser por Tiers"). D through S keep the same colors those Hypixel words
 * would have used (Common=white, Uncommon=green, Rare=blue, Epic=purple,
 * Legendary=gold); {@link #E} is a new bottom tier, below Common, for plain
 * / naturally-occurring materials (dirt, cobblestone, sticks...), colored
 * gray.
 */
public enum ItemTier {
    S(NamedTextColor.GOLD),
    A(NamedTextColor.DARK_PURPLE),
    B(NamedTextColor.BLUE),
    C(NamedTextColor.GREEN),
    D(NamedTextColor.WHITE),
    E(NamedTextColor.GRAY);

    private final NamedTextColor color;

    ItemTier(NamedTextColor color) {
        this.color = color;
    }

    public NamedTextColor color() {
        return this.color;
    }

    /** "TIER S" / "TIER A" / ... — kept in English in both languages, same as the reference tooltips. */
    public String label() {
        return "TIER " + this.name();
    }
}
