package dev.icaro.foodtooltips.global;

import java.util.Arrays;
import org.bukkit.Material;

public record LevelColorTheme(String id, String name, int requiredLevel, Material icon, int intervalTicks, int[] palette) {
    private final int[] palette;

    public LevelColorTheme {
        palette = Arrays.copyOf(palette, palette.length);
    }

    public boolean animated() {
        return this.palette.length > 1;
    }

    public int frameInterval() {
        return this.intervalTicks > 0 ? this.intervalTicks : 7;
    }

    public boolean colorsWholeBadge() {
        return this.intervalTicks > 0;
    }

    public int[] palette() {
        return Arrays.copyOf(this.palette, this.palette.length);
    }
}

