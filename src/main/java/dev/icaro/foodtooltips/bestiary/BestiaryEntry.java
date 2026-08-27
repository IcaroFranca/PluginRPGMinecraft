package dev.icaro.foodtooltips.bestiary;

import dev.icaro.foodtooltips.bestiary.BestiaryCategory;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.entity.EntityType;

public record BestiaryEntry(EntityType type, Material icon, int combatXp, String orbXp, List<String> drops) {
    public int awardedCombatXp() {
        return this.combatXp <= 0 ? 0 : Math.max(1, (int)Math.round((double)this.combatXp / 10.0));
    }

    public BestiaryCategory category() {
        return switch (this.type) {
            case EntityType.DROWNED, EntityType.GUARDIAN, EntityType.ELDER_GUARDIAN, EntityType.DOLPHIN, EntityType.TURTLE, EntityType.COD, EntityType.SALMON, EntityType.SQUID, EntityType.GLOW_SQUID, EntityType.AXOLOTL -> BestiaryCategory.AQUATIC;
            case EntityType.CAVE_SPIDER, EntityType.SLIME, EntityType.WARDEN, EntityType.BREEZE, EntityType.BAT -> BestiaryCategory.CAVES;
            case EntityType.MAGMA_CUBE, EntityType.BLAZE, EntityType.GHAST, EntityType.WITHER_SKELETON, EntityType.HOGLIN, EntityType.ZOGLIN, EntityType.PIGLIN_BRUTE, EntityType.PIGLIN, EntityType.STRIDER, EntityType.WITHER -> BestiaryCategory.NETHER;
            case EntityType.ENDERMAN, EntityType.SHULKER, EntityType.ENDER_DRAGON -> BestiaryCategory.THE_END;
            case EntityType.PIG, EntityType.COW, EntityType.SHEEP, EntityType.CHICKEN, EntityType.RABBIT, EntityType.HORSE, EntityType.FOX, EntityType.PANDA, EntityType.GOAT, EntityType.CAT, EntityType.OCELOT, EntityType.DONKEY, EntityType.MULE, EntityType.LLAMA, EntityType.CAMEL, EntityType.MOOSHROOM, EntityType.SNIFFER, EntityType.ARMADILLO, EntityType.FROG, EntityType.PARROT -> BestiaryCategory.ANIMALS;
            case EntityType.WOLF, EntityType.BEE, EntityType.IRON_GOLEM, EntityType.POLAR_BEAR, EntityType.VILLAGER, EntityType.SNOW_GOLEM -> BestiaryCategory.NEUTRAL;
            default -> BestiaryCategory.TERRESTRIAL;
        };
    }
}

