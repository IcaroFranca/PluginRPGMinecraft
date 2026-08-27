package dev.icaro.foodtooltips.mining;

import dev.icaro.foodtooltips.mining.MiningEntry;
import java.util.EnumSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import org.bukkit.Material;

public final class MiningCatalog {
    private static final List<MiningEntry> ENTRIES = List.of(MiningCatalog.e(Material.COAL_ORE, Material.COAL, 1, 1, 2, "Y 0 a 192; comum em Y 96", "Y 0 to 192; common near Y 96", 5.0), MiningCatalog.e(Material.DEEPSLATE_COAL_ORE, Material.COAL, 1, 1, 2, "Abaixo de Y 8", "Below Y 8", 6.0), MiningCatalog.e(Material.COPPER_ORE, Material.RAW_COPPER, 2, 5, 0, "Y -16 a 112; comum em Y 48", "Y -16 to 112; common near Y 48", 5.0), MiningCatalog.e(Material.DEEPSLATE_COPPER_ORE, Material.RAW_COPPER, 2, 5, 0, "Abaixo de Y 8", "Below Y 8", 6.0), MiningCatalog.e(Material.IRON_ORE, Material.RAW_IRON, 1, 1, 0, "Y -64 a 320; comum em Y 16 e 232", "Y -64 to 320; common near Y 16 and 232", 7.0), MiningCatalog.e(Material.DEEPSLATE_IRON_ORE, Material.RAW_IRON, 1, 1, 0, "Abaixo de Y 8", "Below Y 8", 8.0), MiningCatalog.e(Material.GOLD_ORE, Material.RAW_GOLD, 1, 1, 0, "Y -64 a 32; comum em Y -16", "Y -64 to 32; common near Y -16", 10.0), MiningCatalog.e(Material.DEEPSLATE_GOLD_ORE, Material.RAW_GOLD, 1, 1, 0, "Abaixo de Y 8", "Below Y 8", 12.0), MiningCatalog.e(Material.NETHER_GOLD_ORE, Material.GOLD_NUGGET, 2, 6, 1, "Nether: Y 10 a 117", "Nether: Y 10 to 117", 8.0), MiningCatalog.e(Material.REDSTONE_ORE, Material.REDSTONE, 4, 5, 3, "Y -64 a 15; melhor em Y -59", "Y -64 to 15; best near Y -59", 9.0), MiningCatalog.e(Material.DEEPSLATE_REDSTONE_ORE, Material.REDSTONE, 4, 5, 3, "Abaixo de Y 8", "Below Y 8", 11.0), MiningCatalog.e(Material.LAPIS_ORE, Material.LAPIS_LAZULI, 4, 9, 3, "Y -64 a 64; comum em Y 0", "Y -64 to 64; common near Y 0", 10.0), MiningCatalog.e(Material.DEEPSLATE_LAPIS_ORE, Material.LAPIS_LAZULI, 4, 9, 3, "Abaixo de Y 8", "Below Y 8", 12.0), MiningCatalog.e(Material.DIAMOND_ORE, Material.DIAMOND, 1, 1, 5, "Y -64 a 16; melhor em Y -59", "Y -64 to 16; best near Y -59", 25.0), MiningCatalog.e(Material.DEEPSLATE_DIAMOND_ORE, Material.DIAMOND, 1, 1, 5, "Abaixo de Y 8; melhor em Y -59", "Below Y 8; best near Y -59", 30.0), MiningCatalog.e(Material.EMERALD_ORE, Material.EMERALD, 1, 1, 5, "Montanhas: Y -16 a 320; melhor em Y 232", "Mountains: Y -16 to 320; best near Y 232", 30.0), MiningCatalog.e(Material.DEEPSLATE_EMERALD_ORE, Material.EMERALD, 1, 1, 5, "Montanhas abaixo de Y 8", "Mountains below Y 8", 35.0), MiningCatalog.e(Material.NETHER_QUARTZ_ORE, Material.QUARTZ, 1, 1, 3, "Nether: Y 10 a 117", "Nether: Y 10 to 117", 7.0), MiningCatalog.e(Material.ANCIENT_DEBRIS, Material.ANCIENT_DEBRIS, 1, 1, 0, "Nether: Y 8 a 119; melhor em Y 15", "Nether: Y 8 to 119; best near Y 15", 75.0), MiningCatalog.e(Material.STONE, Material.COBBLESTONE, 1, 1, 0, "Overworld: abaixo da superf\u00edcie", "Overworld: below the surface", 1.0), MiningCatalog.e(Material.DEEPSLATE, Material.COBBLED_DEEPSLATE, 1, 1, 0, "Overworld: principalmente abaixo de Y 8", "Overworld: mostly below Y 8", 1.0), MiningCatalog.e(Material.GRANITE, Material.GRANITE, 1, 1, 0, "Veios subterr\u00e2neos no Overworld", "Underground veins in the Overworld", 1.0), MiningCatalog.e(Material.DIORITE, Material.DIORITE, 1, 1, 0, "Veios subterr\u00e2neos no Overworld", "Underground veins in the Overworld", 1.0), MiningCatalog.e(Material.ANDESITE, Material.ANDESITE, 1, 1, 0, "Veios subterr\u00e2neos no Overworld", "Underground veins in the Overworld", 1.0), MiningCatalog.e(Material.TUFF, Material.TUFF, 1, 1, 0, "Overworld: abaixo de Y 0", "Overworld: below Y 0", 1.0), MiningCatalog.e(Material.CALCITE, Material.CALCITE, 1, 1, 0, "Geodos e picos pedregosos", "Geodes and stony peaks", 1.0));
    private static final Set<Material> STONES = EnumSet.of(Material.STONE, new Material[]{Material.DEEPSLATE, Material.GRANITE, Material.DIORITE, Material.ANDESITE, Material.TUFF, Material.CALCITE});

    private static MiningEntry e(Material b, Material d, int min, int max, int xp, String pt, String en, double skill) {
        return new MiningEntry(b, d, min, max, xp, pt, en, skill);
    }

    public static List<MiningEntry> entries() {
        return ENTRIES;
    }

    public static Optional<MiningEntry> find(Material m) {
        return ENTRIES.stream().filter(e -> e.block() == m).findFirst();
    }

    public static boolean isStone(Material m) {
        return STONES.contains(m);
    }

    public static boolean isOre(Material m) {
        return MiningCatalog.find(m).isPresent() && !MiningCatalog.isStone(m);
    }
}

