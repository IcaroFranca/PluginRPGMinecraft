package dev.icaro.foodtooltips.shop;

import org.bukkit.Material;

public enum ShopItem {
    SUPREME_FOOD(Material.COOKED_BEEF, 1200, 1, "Comida Suprema Infinita", "Infinite Supreme Food"),
    ANCESTRAL_ARROW(Material.SPECTRAL_ARROW, 1800, 8, "Flecha Ancestral", "Ancestral Arrow"),
    MACHINE_BOW(Material.BOW, 5000, 1, "Arco Metraladora", "Machine Bow"),
    FLYING_BOOTS(Material.CHAINMAIL_BOOTS, 6500, 1, "Botas Voadoras", "Flying Boots"),
    IMMORTAL_CHEST(Material.NETHERITE_CHESTPLATE, 12000, 1, "Peitoral Imortal", "Immortal Chestplate"),
    HEAVY_BOOTS(Material.IRON_BOOTS, 4500, 1, "Botas Pesadas", "Heavy Boots"),
    BOMB(Material.FIRE_CHARGE, 2500, 1, "Bomba", "Bomb"),
    FLAMETHROWER(Material.BLAZE_ROD, 9000, 1, "Lan\u00e7a-chamas", "Flamethrower"),
    METEOR(Material.MAGMA_CREAM, 30000, 1, "Meteoro", "Meteor"),
    LIFESTEAL_DAGGER(Material.GOLDEN_SWORD, 10000, 1, "Adaga de Lifesteal", "Lifesteal Dagger"),
    PORTAL(Material.RESPAWN_ANCHOR, 24000, 2, "Portal Instal\u00e1vel", "Placeable Portal"),
    SAFE_BEACON(Material.BEACON, 30000, 1, "\u00c1rea Segura", "Safe Area"),
    SOLAR_ARROW(Material.TIPPED_ARROW, 8000, 4, "Flecha de Fogo Solar", "Solar Fire Arrow"),
    LIGHTNING_PRISON(Material.LIGHTNING_ROD, 6000, 1, "Pris\u00e3o de Raios", "Lightning Prison"),
    WITHER_COATING(Material.ENCHANTED_BOOK, 7500, 1, "Encantamento de Wither", "Wither Coating"),
    BLEEDING_DAGGER(Material.IRON_NUGGET, 2200, 2, "Adaga de Sangramento", "Bleeding Dagger"),
    EXCAVATOR(Material.DIAMOND_PICKAXE, 15000, 1, "Escavador 2000", "Excavator 2000"),
    MADNESS_POTION(Material.SPLASH_POTION, 5000, 1, "Po\u00e7\u00e3o da Loucura", "Madness Potion"),
    GOD_POTION(Material.POTION, 50000, 1, "Po\u00e7\u00e3o Modo Deus", "God Mode Potion");

    private final Material icon;
    private final int price;
    private final int amount;
    private final String pt;
    private final String en;

    private ShopItem(Material i, int p, int amount, String pt, String en) {
        this.icon = i;
        this.price = p;
        this.amount = amount;
        this.pt = pt;
        this.en = en;
    }

    public Material icon() {
        return this.icon;
    }

    public int price() {
        return this.price;
    }

    public int amount() {
        return this.amount;
    }

    public String name(boolean ptLocale) {
        return ptLocale ? this.pt : this.en;
    }
}

