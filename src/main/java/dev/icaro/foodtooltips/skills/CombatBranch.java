package dev.icaro.foodtooltips.skills;

import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;

/** Thematic grouping used to lay out the combat ability tree menu. */
public enum CombatBranch {
    FURY(NamedTextColor.RED, "Fúria", "Fury"),
    SUSTAIN(NamedTextColor.DARK_RED, "Sangue", "Blood"),
    UTILITY(NamedTextColor.AQUA, "Precisão", "Precision"),
    SYNERGY(NamedTextColor.LIGHT_PURPLE, "Sinergia", "Synergy"),
    CAPSTONE(NamedTextColor.GOLD, "Ápice", "Apex");

    private final TextColor color;
    private final String pt;
    private final String en;

    CombatBranch(TextColor color, String pt, String en) {
        this.color = color;
        this.pt = pt;
        this.en = en;
    }

    public TextColor color() {
        return this.color;
    }

    public String name(boolean portuguese) {
        return portuguese ? this.pt : this.en;
    }
}
