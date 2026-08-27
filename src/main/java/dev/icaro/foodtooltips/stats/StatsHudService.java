package dev.icaro.foodtooltips.stats;

import dev.icaro.foodtooltips.stats.PlayerStats;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.entity.Player;

public final class StatsHudService {
    private final String gap;

    public StatsHudService(String gap) {
        this.gap = gap;
    }

    public void show(Player p, PlayerStats s) {
        p.sendActionBar(((TextComponent)Component.text((String)(Math.round(s.health()) + "/" + Math.round(s.maxHealth()) + "\u2764"), (TextColor)NamedTextColor.RED).append((Component)Component.text((String)this.gap))).append((Component)Component.text((String)(Math.round(s.mana()) + "/" + Math.round(s.maxMana()) + "\u270e Mana"), (TextColor)NamedTextColor.AQUA)));
    }
}

