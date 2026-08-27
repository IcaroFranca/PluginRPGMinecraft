package dev.icaro.foodtooltips.mining;

import org.bukkit.Material;

public enum GemType {
    OPAL(Material.WHITE_STAINED_GLASS, "Opala", "Opal", "Vida", "Health", 80),
    AMBER(Material.ORANGE_STAINED_GLASS, "\u00c2mbar", "Amber", "Defesa", "Defense", 90),
    TOURMALINE(Material.MAGENTA_STAINED_GLASS, "Turmalina", "Tourmaline", "Dano Cr\u00edtico", "Critical Damage", 35),
    AQUAMARINE(Material.LIGHT_BLUE_STAINED_GLASS, "\u00c1gua-marinha", "Aquamarine", "Mining Speed", "Mining Speed", 75),
    TOPAZ(Material.YELLOW_STAINED_GLASS, "Top\u00e1zio", "Topaz", "Chance de Tesouro", "Treasure Chance", 30),
    PERIDOT(Material.LIME_STAINED_GLASS, "Peridoto", "Peridot", "Farming Fortune", "Farming Fortune", 70),
    ROSE_QUARTZ(Material.PINK_STAINED_GLASS, "Quartzo-rosa", "Rose Quartz", "Regenera\u00e7\u00e3o de Vida", "Health Regeneration", 65),
    SMOKY_QUARTZ(Material.GRAY_STAINED_GLASS, "Quartzo-fum\u00ea", "Smoky Quartz", "Preserva\u00e7\u00e3o de Durabilidade", "Durability Preservation", 60),
    MOONSTONE(Material.LIGHT_GRAY_STAINED_GLASS, "Pedra-da-lua", "Moonstone", "XP de Skills", "Skill XP", 40),
    TURQUOISE(Material.CYAN_STAINED_GLASS, "Turquesa", "Turquoise", "Regenera\u00e7\u00e3o de Mana", "Mana Regeneration", 55),
    AMETHYST(Material.PURPLE_STAINED_GLASS, "Ametista", "Amethyst", "Dano M\u00e1gico", "Magic Damage", 45),
    SAPPHIRE(Material.BLUE_STAINED_GLASS, "Safira", "Sapphire", "Mana", "Mana", 50),
    TIGER_EYE(Material.BROWN_STAINED_GLASS, "Olho-de-tigre", "Tiger's Eye", "Foraging Fortune", "Foraging Fortune", 70),
    JADE(Material.GREEN_STAINED_GLASS, "Jade", "Jade", "Mining Fortune", "Mining Fortune", 65),
    RUBY(Material.RED_STAINED_GLASS, "Rubi", "Ruby", "Dano", "Damage", 50),
    ONYX(Material.BLACK_STAINED_GLASS, "\u00d4nix", "Onyx", "Redu\u00e7\u00e3o de Dano", "Damage Reduction", 25);

    private final Material block;
    private final String pt;
    private final String en;
    private final String attributePt;
    private final String attributeEn;
    private final int weight;

    private GemType(Material b, String pt, String en, String ap, String ae, int w) {
        this.block = b;
        this.pt = pt;
        this.en = en;
        this.attributePt = ap;
        this.attributeEn = ae;
        this.weight = w;
    }

    public Material block() {
        return this.block;
    }

    public String name(boolean pt) {
        return pt ? this.pt : this.en;
    }

    public String attribute(boolean pt) {
        return pt ? this.attributePt : this.attributeEn;
    }

    public int weight() {
        return this.weight;
    }
}

