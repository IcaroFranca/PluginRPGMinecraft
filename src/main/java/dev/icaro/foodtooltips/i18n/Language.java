package dev.icaro.foodtooltips.i18n;

import org.bukkit.entity.Player;

public enum Language {
    PT,
    EN;


    public static Language of(Player p) {
        return p.locale().getLanguage().equalsIgnoreCase("pt") ? PT : EN;
    }

    public String choose(String pt, String en) {
        return this == PT ? pt : en;
    }
}

