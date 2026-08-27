package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.global.LevelColorTheme;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import org.bukkit.Material;

public final class LevelColorCatalog {
    private static final List<LevelColorTheme> THEMES = List.of(LevelColorCatalog.t("white", "White", 1, Material.WHITE_DYE, 0, 0xFFFFFF), LevelColorCatalog.t("gray", "Gray", 2, Material.LIGHT_GRAY_DYE, 0, 0xAAAAAA), LevelColorCatalog.t("dark_gray", "Dark Gray", 3, Material.GRAY_DYE, 0, 0x555555), LevelColorCatalog.t("yellow", "Yellow", 4, Material.YELLOW_DYE, 0, 0xFFFF55), LevelColorCatalog.t("gold", "Gold", 5, Material.GOLD_INGOT, 0, 0xFFAA00), LevelColorCatalog.t("green", "Green", 6, Material.LIME_DYE, 0, 0x55FF55), LevelColorCatalog.t("dark_green", "Dark Green", 7, Material.GREEN_DYE, 0, 43520), LevelColorCatalog.t("aqua", "Aqua", 8, Material.LIGHT_BLUE_DYE, 0, 0x55FFFF), LevelColorCatalog.t("dark_aqua", "Dark Aqua", 9, Material.CYAN_DYE, 0, 43690), LevelColorCatalog.t("blue", "Blue", 10, Material.BLUE_DYE, 0, 0x5555FF), LevelColorCatalog.t("dark_blue", "Dark Blue", 11, Material.LAPIS_LAZULI, 0, 170), LevelColorCatalog.t("light_purple", "Light Purple", 12, Material.PINK_DYE, 0, 0xFF55FF), LevelColorCatalog.t("dark_purple", "Dark Purple", 13, Material.PURPLE_DYE, 0, 0xAA00AA), LevelColorCatalog.t("red", "Red", 14, Material.RED_DYE, 0, 0xFF5555), LevelColorCatalog.t("dark_red", "Dark Red", 15, Material.REDSTONE, 0, 0xAA0000), LevelColorCatalog.t("black", "Black", 16, Material.BLACK_DYE, 0, 0), LevelColorCatalog.t("coal", "Coal", 17, Material.COAL, 0, 2040617, 0x555555, 7831437), LevelColorCatalog.t("iron", "Iron", 18, Material.IRON_INGOT, 0, 10331310, 0xD8D8D8, 0xFFFFFF), LevelColorCatalog.t("copper", "Copper", 19, Material.COPPER_INGOT, 0, 13925456, 12741189, 4570508), LevelColorCatalog.t("quartz", "Quartz", 20, Material.QUARTZ, 0, 0xFFFFFF, 15656436, 14271722), LevelColorCatalog.t("gold_ore", "Gold Ore", 21, Material.RAW_GOLD, 0, 0xFFFF55, 16766250, 0xFFAA00), LevelColorCatalog.t("redstone", "Redstone", 22, Material.REDSTONE, 0, 0xAA0000, 13967392, 0xFF5555), LevelColorCatalog.t("lapis", "Lapis", 23, Material.LAPIS_LAZULI, 0, 170, 3158229, 0x5555FF), LevelColorCatalog.t("emerald", "Emerald", 24, Material.EMERALD, 0, 43520, 0x25D525, 0x55FF55), LevelColorCatalog.t("diamond", "Diamond", 25, Material.DIAMOND, 0, 43690, 0x55FFFF, 0xFFFFFF), LevelColorCatalog.t("amethyst", "Amethyst", 26, Material.AMETHYST_SHARD, 0, 0xAA00AA, 13970133, 0xFF55FF), LevelColorCatalog.t("netherite", "Netherite", 27, Material.NETHERITE_INGOT, 0, 2104358, 4535885, 7953282), LevelColorCatalog.t("obsidian", "Obsidian", 28, Material.OBSIDIAN, 0, 0, 2820669, 0xAA00AA), LevelColorCatalog.t("bedrock", "Bedrock", 29, Material.BEDROCK, 0, 0x555555, 0xAAAAAA, 0), LevelColorCatalog.t("prismatic", "Prismatic", 30, Material.NETHER_STAR, 7, 0xFF5555, 0xFFAA00, 0xFFFF55, 0x55FF55, 0x55FFFF, 0x5555FF, 0xAA00AA, 0xFF55FF), LevelColorCatalog.t("inferno", "Inferno", 35, Material.BLAZE_POWDER, 5, 0xAA0000, 0xFF5555, 0xFFAA00, 0xFFFF55, 0xFFAA00, 0xFF5555), LevelColorCatalog.t("oceanic", "Oceanic", 40, Material.HEART_OF_THE_SEA, 8, 170, 0x5555FF, 43690, 0x55FFFF, 0xFFFFFF, 0x55FFFF), LevelColorCatalog.t("void", "Void", 45, Material.ENDER_EYE, 10, 0, 0xAA00AA, 0xFF55FF, 0x5555FF, 0xAA00AA, 0), LevelColorCatalog.t("nexus", "Nexus", 50, Material.END_CRYSTAL, 6, 0xFFAA00, 0xFFFFFF, 0x55FFFF, 0xFF55FF, 0xFFFFFF, 0xFFAA00));
    private static final Map<String, LevelColorTheme> BY_ID;

    private LevelColorCatalog() {
    }

    public static List<LevelColorTheme> themes() {
        return THEMES;
    }

    public static Optional<LevelColorTheme> find(String id) {
        return Optional.ofNullable(id == null ? null : BY_ID.get(id.toLowerCase(Locale.ROOT)));
    }

    public static LevelColorTheme white() {
        return BY_ID.get("white");
    }

    private static LevelColorTheme t(String id, String name, int level, Material icon, int ticks, int ... colors) {
        return new LevelColorTheme(id, name, level, icon, ticks, colors);
    }

    static {
        LinkedHashMap<String, LevelColorTheme> map = new LinkedHashMap<String, LevelColorTheme>();
        for (LevelColorTheme theme : THEMES) {
            map.put(theme.id(), theme);
        }
        BY_ID = Map.copyOf(map);
    }
}

