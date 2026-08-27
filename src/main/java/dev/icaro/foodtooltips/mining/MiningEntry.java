package dev.icaro.foodtooltips.mining;

import org.bukkit.Material;

public record MiningEntry(Material block, Material drop, int minDrop, int maxDrop, int vanillaXp, String layersPt, String layersEn, double skillXp) {
}

