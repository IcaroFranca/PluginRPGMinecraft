package dev.icaro.foodtooltips.skills;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalLevelSnapshot;
import dev.icaro.foodtooltips.global.LevelColorMenuService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.mining.MiningMenuService;
import dev.icaro.foodtooltips.stats.PlayerStats;
import dev.icaro.foodtooltips.stats.PlayerStatsService;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class SkillsMenuService {
    private static final int[] N = new int[]{9, 18, 27, 28, 29, 20, 11, 2, 3, 4, 13, 22, 31, 32, 33, 24, 15, 6, 7, 8, 17, 26, 35, 44, 53};
    private static final Map<Integer, SkillType> S = Map.of(22, SkillType.FARMING, 24, SkillType.FISHING, 30, SkillType.MINING, 31, SkillType.FORAGING, 32, SkillType.ENCHANTING, 33, SkillType.ALCHEMY);

    private final CombatSkillService combat;
    private final GeneralSkillService general;
    private final PlayerStatsService stats;
    private final CombatAbilityService abilities;
    private final MiningMenuService mining;
    private final GlobalLevelService global;
    private final ArmorDefenseService armor;
    private BackpackService backpacks;
    private LevelColorMenuService levelColors;
    private CombatTreeMenuService tree;
    private final Map<UUID, View> views = new HashMap<>();

    public SkillsMenuService(CombatSkillService c, GeneralSkillService g, PlayerStatsService s, CombatAbilityService a, MiningMenuService m, GlobalLevelService global, ArmorDefenseService armor) {
        this.combat = c;
        this.general = g;
        this.stats = s;
        this.abilities = a;
        this.mining = m;
        this.global = global;
        this.armor = armor;
    }

    public void backpacks(BackpackService backpacks) {
        this.backpacks = backpacks;
    }

    public void levelColors(LevelColorMenuService levelColors) {
        this.levelColors = levelColors;
    }

    public void tree(CombatTreeMenuService tree) {
        this.tree = tree;
    }

    /**
     * Bestiário and Árvore de Combate are deliberately NOT buttons here — they live only
     * on the Combat skill screen ({@link #openCombat}), reachable from the Combat icon
     * below (Bestiário is also reachable via {@code /bestiary}).
     */
    public void openMain(Player p) {
        Language l = Language.of(p);
        Inventory v = this.inv(l.choose("Habilidades", "Skills"));
        v.setItem(4, this.head(p, l));
        v.setItem(20, this.item(Material.IRON_SWORD, l.choose("Combate", "Combat"), List.of(this.combatLine(p, l), this.click(l))));
        for (Map.Entry<Integer, SkillType> e : S.entrySet()) {
            SkillType t = e.getValue();
            v.setItem(e.getKey(), this.item(t.icon(), t.name(l == Language.PT), List.of(this.skillLine(p, t, l), this.click(l))));
        }
        v.setItem(13, this.globalLevelIcon(p, l));
        if (this.backpacks != null) {
            v.setItem(46, this.backpacks.menuIcon(l.choose("Mochilas", "Backpacks"), List.of(this.text(l.choose("Mochilas de todas as skills.", "Backpacks for every skill."), NamedTextColor.YELLOW))));
        }
        if (this.levelColors != null) {
            v.setItem(47, this.item(Material.NAME_TAG, l.choose("Cores do Nível", "Level Colors"), List.of(this.click(l))));
        }
        this.open(p, v, new View(Type.MAIN, 0, null));
    }

    public void openCombat(Player p, int page) {
        page = this.clamp(page, this.combat.maxLevel());
        Language l = Language.of(p);
        Inventory v = this.inv(l.choose("Skill de Combate", "Combat Skill"));
        CombatProgress x = this.combat.progress(p);
        for (int i = 0; i < 25; ++i) {
            int level = page * 25 + i + 1;
            ArrayList<Component> lore = new ArrayList<>(List.of(this.text("+0.5% " + l.choose("Chance crítica", "Crit Chance"), NamedTextColor.AQUA), this.text("+4% " + l.choose("de dano", "Damage"), NamedTextColor.RED)));
            if (level == x.level() + 1) {
                lore.add(this.xp(x.xp(), x.requiredXp()));
            }
            v.setItem(N[i], this.node(level, x.level(), l, lore));
        }
        v.setItem(0, this.item(Material.IRON_SWORD, l.choose("Progressão de Combate", "Combat Progression"), List.of(this.combatLine(p, l))));
        v.setItem(39, this.item(Material.KNOWLEDGE_BOOK, l.choose("Bestiário", "Bestiary"), List.of(this.click(l))));
        v.setItem(41, this.item(Material.CHEST, l.choose("Árvore de Combate", "Combat Tree"), List.of(this.click(l))));
        this.nav(v, l, page, this.combat.maxLevel());
        this.open(p, v, new View(Type.COMBAT, page, null));
    }

    public void openGeneral(Player p, SkillType t, int page) {
        page = this.clamp(page, this.general.maxLevel());
        Language l = Language.of(p);
        Inventory v = this.inv(t.name(l == Language.PT));
        SkillProgress x = this.general.progress(p, t);
        for (int i = 0; i < 25; ++i) {
            int level = page * 25 + i + 1;
            ArrayList<Component> lore = new ArrayList<>(List.of(this.text(l.choose("Continue usando esta skill para evoluir.", "Keep using this skill to level up."), NamedTextColor.GRAY), this.text(t == SkillType.MINING || t == SkillType.FARMING || t == SkillType.FORAGING ? "+4 " + t.name(l == Language.PT) + " Fortune" : l.choose("Nenhuma recompensa de atributo neste nível.", "No attribute reward at this level."), NamedTextColor.AQUA)));
            Component bag = this.bagReward(level, l);
            if (bag != null) {
                lore.add(bag);
            }
            if (t == SkillType.MINING) {
                lore.add(this.text("+1 " + l.choose("Defesa", "Defense"), NamedTextColor.GREEN));
                if (level == 3) {
                    lore.add(this.text("✦ " + l.choose("Desbloqueia: Vein Miner", "Unlocks: Vein Miner"), NamedTextColor.LIGHT_PURPLE));
                } else {
                    lore.add(this.text(l.choose("Nenhuma habilidade neste nível.", "No ability at this level."), NamedTextColor.DARK_GRAY));
                }
            } else {
                lore.add(this.text(l.choose("Nenhuma habilidade neste nível.", "No ability at this level."), NamedTextColor.DARK_GRAY));
            }
            if (level == x.level() + 1) {
                lore.add(this.xp(x.xp(), x.requiredXp()));
            }
            v.setItem(N[i], this.node(level, x.level(), l, lore));
        }
        v.setItem(0, this.item(t.icon(), l.choose("Progressão de ", "Progression: ") + t.name(l == Language.PT), List.of(this.skillLine(p, t, l))));
        if (t == SkillType.MINING) {
            v.setItem(40, this.item(Material.BOOK, l.choose("Compêndio de Mineração", "Mining Compendium"), List.of(this.text(l.choose("Contadores, milestones, XP, drops e camadas.", "Counters, milestones, XP, drops and layers."), NamedTextColor.YELLOW))));
        }
        this.nav(v, l, page, this.general.maxLevel());
        this.open(p, v, new View(Type.GENERAL, page, t));
    }

    public void openGlobal(Player p, int page) {
        int maxLevel = (int) Math.max(1L, Math.min(Integer.MAX_VALUE, this.global.maxAchievableLevel()));
        page = this.clamp(page, maxLevel);
        Language l = Language.of(p);
        Inventory v = this.inv(l.choose("Nível Global", "Global Level"));
        GlobalLevelSnapshot g = this.global.snapshot(p);
        int currentLevel = (int) Math.min(Integer.MAX_VALUE, g.level());
        int levelsPerStrength = Math.max(1, this.global.levelsPerStrength());
        for (int i = 0; i < 25; ++i) {
            int level = page * 25 + i + 1;
            ArrayList<Component> lore = new ArrayList<>(List.of(
                    this.text("❤ +" + Math.round(this.global.hpPerLevel()) + " " + l.choose("HP máximo", "max HP"), NamedTextColor.RED)));
            if (level % levelsPerStrength == 0) {
                lore.add(this.text("✹ +" + this.global.strengthPerGroup() + " Strength", NamedTextColor.GOLD));
            }
            if (level == this.global.telekinesisRequiredLevel()) {
                lore.add(this.text("🧲 " + l.choose("Desbloqueia: Telecinese", "Unlocks: Telekinesis"), NamedTextColor.LIGHT_PURPLE));
            }
            if (lore.size() == 1) {
                lore.add(this.text(l.choose("Nenhuma outra recompensa neste nível.", "No other reward at this level."), NamedTextColor.DARK_GRAY));
            }
            if (level == currentLevel + 1) {
                lore.add(this.text(g.progress() + "/" + g.required() + " XP", NamedTextColor.GREEN));
            }
            v.setItem(N[i], this.node(level, currentLevel, l, lore));
        }
        v.setItem(0, this.item(Material.NETHER_STAR, l.choose("Progressão de Nível Global", "Global Level Progression"), List.of(this.globalLine(p, l))));
        this.nav(v, l, page, maxLevel);
        this.open(p, v, new View(Type.GLOBAL, page, null));
    }

    public boolean handleClick(Player p, int slot) {
        View v = this.views.get(p.getUniqueId());
        if (v == null) {
            return false;
        }
        switch (v.type()) {
            case MAIN -> {
                if (slot == 4) {
                    this.openStats(p);
                } else if (slot == 13) {
                    this.openGlobal(p, 0);
                } else if (slot == 20) {
                    this.openCombat(p, 0);
                } else if (S.containsKey(slot)) {
                    this.openGeneral(p, S.get(slot), 0);
                } else if (slot == 46 && this.backpacks != null) {
                    this.views.remove(p.getUniqueId());
                    this.backpacks.openMenu(p);
                } else if (slot == 47 && this.levelColors != null) {
                    this.views.remove(p.getUniqueId());
                    this.levelColors.open(p);
                }
            }
            case GLOBAL -> {
                if (slot == 45) {
                    this.openMain(p);
                } else if (slot == 48 && v.page() > 0) {
                    this.openGlobal(p, v.page() - 1);
                } else if (slot == 50) {
                    this.openGlobal(p, v.page() + 1);
                }
            }
            case STATS -> {
                if (slot == 45) {
                    this.openMain(p);
                }
            }
            case COMBAT -> {
                if (slot == 45) {
                    this.openMain(p);
                } else if (slot == 39) {
                    p.performCommand("bestiary");
                } else if (slot == 41 && this.tree != null) {
                    this.views.remove(p.getUniqueId());
                    this.tree.open(p);
                } else if (slot == 48 && v.page() > 0) {
                    this.openCombat(p, v.page() - 1);
                } else if (slot == 50) {
                    this.openCombat(p, v.page() + 1);
                }
            }
            case GENERAL -> {
                if (slot == 45) {
                    this.openMain(p);
                } else if (slot == 40 && v.skill() == SkillType.MINING) {
                    this.views.remove(p.getUniqueId());
                    this.mining.open(p);
                } else if (slot == 48 && v.page() > 0) {
                    this.openGeneral(p, v.skill(), v.page() - 1);
                } else if (slot == 50) {
                    this.openGeneral(p, v.skill(), v.page() + 1);
                }
            }
        }
        return true;
    }

    public boolean viewing(Player p) {
        return this.views.containsKey(p.getUniqueId());
    }

    public void close(Player p) {
        this.views.remove(p.getUniqueId());
    }

    private void open(Player p, Inventory v, View view) {
        p.openInventory(v);
        this.views.put(p.getUniqueId(), view);
    }

    private Inventory inv(String title) {
        Inventory v = Bukkit.createInventory(null, 54, title);
        ItemStack f = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; ++i) {
            v.setItem(i, f);
        }
        return v;
    }

    private void nav(Inventory v, Language l, int page, int max) {
        v.setItem(45, this.item(Material.BARRIER, l.choose("Voltar às skills", "Back to skills"), List.of()));
        if (page > 0) {
            v.setItem(48, this.item(Material.ARROW, l.choose("Página anterior", "Previous page"), List.of()));
        }
        if ((page + 1) * 25 < max) {
            v.setItem(50, this.item(Material.ARROW, l.choose("Próxima página", "Next page"), List.of()));
        }
    }

    private int clamp(int p, int max) {
        return Math.max(0, Math.min((max - 1) / 25, p));
    }

    private ItemStack node(int n, int current, Language l, List<Component> lore) {
        ItemStack i = this.item(n <= current ? Material.LIME_STAINED_GLASS_PANE : (n == current + 1 ? Material.YELLOW_STAINED_GLASS_PANE : Material.RED_STAINED_GLASS_PANE), l.choose("Nível ", "Level ") + n, lore);
        i.setAmount(Math.min(64, n));
        return i;
    }

    private Component combatLine(Player p, Language l) {
        CombatProgress x = this.combat.progress(p);
        return this.text(l.choose("Nível ", "Level ") + x.level() + (x.level() >= this.combat.maxLevel() ? " (MAX)" : " • " + Math.round(x.xp()) + "/" + Math.round(x.requiredXp()) + " XP"), NamedTextColor.GREEN);
    }

    private Component globalLine(Player p, Language l) {
        GlobalLevelSnapshot g = this.global.snapshot(p);
        return this.text(l.choose("Nível ", "Level ") + g.level() + " • " + g.progress() + "/" + g.required() + " XP", NamedTextColor.GOLD);
    }

    /** Global Level's button on the main skills grid — a custom head if one is configured, otherwise an XP bottle. */
    private ItemStack globalLevelIcon(Player p, Language l) {
        List<Component> lore = List.of(this.globalLine(p, l), this.click(l));
        String texture = this.global.iconTexture();
        if (texture != null && !texture.isBlank()) {
            return this.customHead(texture, l.choose("Nível Global", "Global Level"), lore);
        }
        return this.item(Material.EXPERIENCE_BOTTLE, l.choose("Nível Global", "Global Level"), lore);
    }

    /** A player head wearing a custom skin (base64 "Value" texture), falling back to a plain head if it's bad. */
    private ItemStack customHead(String texture, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(Material.PLAYER_HEAD);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        try {
            PlayerProfile profile = Bukkit.createProfile(UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            m.setPlayerProfile(profile);
        } catch (Exception ignored) {
            // Bad texture value: fall back to a plain player head rather than failing the whole menu.
        }
        m.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private Component skillLine(Player p, SkillType t, Language l) {
        SkillProgress x = this.general.progress(p, t);
        return this.text(l.choose("Nível ", "Level ") + x.level() + (x.level() >= this.general.maxLevel() ? " (MAX)" : " • " + Math.round(x.xp()) + "/" + Math.round(x.requiredXp()) + " XP"), NamedTextColor.GREEN);
    }

    private Component xp(double a, double b) {
        return this.text(Math.round(a) + "/" + Math.round(b) + " XP", NamedTextColor.GREEN);
    }

    private Component click(Language l) {
        return this.text(l.choose("Clique para ver!", "Click to view!"), NamedTextColor.YELLOW);
    }

    private Component bagReward(int level, Language l) {
        int slots = switch (level) {
            case 1 -> 9;
            case 10 -> 18;
            case 20 -> 27;
            case 30 -> 36;
            case 40 -> 45;
            case 50 -> 54;
            default -> 0;
        };
        if (slots == 0) {
            return null;
        }
        return this.text("🎒 " + (level == 1 ? l.choose("Desbloqueia mochila: ", "Unlocks backpack: ") : l.choose("Melhora mochila: ", "Upgrades backpack: ")) + slots + l.choose(" espaços", " slots"), NamedTextColor.GOLD);
    }

    /** Condensed hover preview (the head icon in the main menu) — click it to open {@link #openStats}. */
    private ItemStack head(Player p, Language l) {
        PlayerStats s = this.stats.stats(p);
        CombatProgress c = this.combat.progress(p);
        int defense = this.armor.defense(p);
        long speedPercent = Math.round(this.value(p, Attribute.MOVEMENT_SPEED, 0.1) / 0.1 * 100.0);
        double critDamage = (this.abilities.criticalDamageMultiplier(p) - 1.0) * 100.0;
        double critChance = this.combat.critChance(c.level()) + this.abilities.critChanceBonus(p);
        List<Component> lore = List.of(
                this.text(l.choose("Veja seu equipamento, status e mais!", "View your equipment, stats, and more!"), NamedTextColor.GRAY),
                Component.empty(),
                this.text("🏃 " + l.choose("Velocidade: ", "Speed: ") + speedPercent, NamedTextColor.WHITE),
                this.text("✹ Strength: " + s.strength(), NamedTextColor.RED),
                this.text("✦ " + l.choose("Defesa: ", "Defense: ") + defense, NamedTextColor.GREEN),
                this.text("☠ " + l.choose("Dano Crítico: ", "Crit Damage: ") + String.format(Locale.US, "%.1f", critDamage) + "%", NamedTextColor.BLUE),
                this.text("☣ " + l.choose("Chance Crítica: ", "Crit Chance: ") + String.format(Locale.US, "%.1f", critChance) + "%", NamedTextColor.BLUE),
                this.text("❤ " + l.choose("Vida: ", "Health: ") + Math.round(s.health()) + "/" + Math.round(s.maxHealth()), NamedTextColor.RED),
                this.text("✎ " + l.choose("Inteligência: ", "Intelligence: ") + Math.round(s.intelligence()), NamedTextColor.AQUA),
                Component.empty(),
                this.text(l.choose("Clique para ver mais!", "Click to see more!"), NamedTextColor.YELLOW));
        ItemStack i = this.item(Material.PLAYER_HEAD, l.choose("Status & Equipamento", "Stats & Equipment"), lore);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        m.setOwningPlayer((OfflinePlayer) p);
        i.setItemMeta(m);
        return i;
    }

    /** Full breakdown (grouped by icon) plus the player's equipped armor. Opened by clicking {@link #head}. */
    public void openStats(Player p) {
        Language l = Language.of(p);
        Inventory v = this.inv(l.choose("Status & Equipamento", "Stats & Equipment"));
        v.setItem(4, this.statsOverviewHead(p, l));
        v.setItem(20, this.armorSlot(p.getInventory().getHelmet(), l.choose("Capacete", "Helmet"), l));
        v.setItem(29, this.armorSlot(p.getInventory().getChestplate(), l.choose("Peitoral", "Chestplate"), l));
        v.setItem(38, this.armorSlot(p.getInventory().getLeggings(), l.choose("Calças", "Leggings"), l));
        v.setItem(47, this.armorSlot(p.getInventory().getBoots(), l.choose("Botas", "Boots"), l));
        v.setItem(24, this.combatStatsItem(p, l));
        v.setItem(40, this.fortuneItem(p, SkillType.MINING, l, NamedTextColor.AQUA));
        v.setItem(42, this.fortuneItem(p, SkillType.FARMING, l, NamedTextColor.GREEN));
        v.setItem(44, this.fortuneItem(p, SkillType.FORAGING, l, NamedTextColor.DARK_GREEN));
        v.setItem(45, this.item(Material.BARRIER, l.choose("Voltar às skills", "Back to skills"), List.of()));
        this.open(p, v, new View(Type.STATS, 0, null));
    }

    private ItemStack statsOverviewHead(Player p, Language l) {
        GlobalLevelSnapshot g = this.global.snapshot(p);
        List<Component> lore = List.of(this.text("✦ " + l.choose("Nível Global: ", "Global Level: ") + g.level(), NamedTextColor.GOLD));
        ItemStack i = this.item(Material.PLAYER_HEAD, p.getName(), lore);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        m.setOwningPlayer((OfflinePlayer) p);
        i.setItemMeta(m);
        return i;
    }

    /** The player's actual equipped piece (real item, with its own name/enchants/lore), or an empty placeholder. */
    private ItemStack armorSlot(ItemStack equipped, String slotName, Language l) {
        if (equipped == null || equipped.getType().isAir()) {
            return this.item(Material.GRAY_STAINED_GLASS_PANE, slotName, List.of(this.text(l.choose("Nada equipado.", "Nothing equipped."), NamedTextColor.DARK_GRAY)));
        }
        return equipped.clone();
    }

    /**
     * The full Combat Stats list in one place — every combat stat the player has, in the
     * order Health/Defense/True Defense/Strength/Crit Chance/Crit Damage/Ferocity/Swing
     * Range/Intelligence/Ability Damage/Health Regen/Vitality/Mending. Everything here
     * except the first three is upgradeable through the combat tree (see
     * {@link CombatAbilityService}'s class doc for which ability grants which bonus).
     */
    private ItemStack combatStatsItem(Player p, Language l) {
        PlayerStats s = this.stats.stats(p);
        CombatProgress c = this.combat.progress(p);
        int defense = this.armor.defense(p);
        double critChance = this.combat.critChance(c.level()) + this.abilities.critChanceBonus(p);
        double critDamage = (this.abilities.criticalDamageMultiplier(p) - 1.0) * 100.0;
        List<Component> lore = List.of(
                this.text(l.choose("Status que influenciam quanto dano você recebe e causa em combate.", "Stats that influence how much damage you take and deal in combat."), NamedTextColor.GRAY),
                Component.empty(),
                this.text("❤ " + l.choose("Vida: ", "Health: ") + Math.round(s.health()) + "/" + Math.round(s.maxHealth()), NamedTextColor.RED),
                this.text("✦ " + l.choose("Defesa: ", "Defense: ") + defense, NamedTextColor.GREEN),
                this.text("🛡 " + l.choose("Defesa Verdadeira: ", "True Defense: ") + String.format(Locale.US, "%.0f", s.trueDefense()), NamedTextColor.GRAY),
                this.text("✹ Strength: " + s.strength(), NamedTextColor.RED),
                this.text("☣ " + l.choose("Chance Crítica: ", "Crit Chance: ") + String.format(Locale.US, "%.1f", critChance) + "%", NamedTextColor.AQUA),
                this.text("☠ " + l.choose("Dano Crítico: ", "Crit Damage: ") + String.format(Locale.US, "%.1f", critDamage) + "%", NamedTextColor.AQUA),
                this.text("Ⓕ Ferocity: " + Math.round(s.ferocity()), NamedTextColor.RED),
                this.text("↔ " + l.choose("Alcance de Ataque: ", "Swing Range: ") + String.format(Locale.US, "%.1f", s.swingRange()), NamedTextColor.YELLOW),
                this.text("✎ " + l.choose("Inteligência: ", "Intelligence: ") + Math.round(s.intelligence()), NamedTextColor.AQUA),
                this.text("❉ " + l.choose("Dano de Habilidade: ", "Ability Damage: ") + Math.round(s.abilityDamage()) + "%", NamedTextColor.LIGHT_PURPLE),
                this.text("❣ " + l.choose("Regen. de Vida: ", "Health Regen: ") + Math.round(s.healthRegen()) + "%", NamedTextColor.RED),
                this.text("✿ Vitality: " + Math.round(s.vitality()) + "/" + Math.round(s.maxVitality()), NamedTextColor.LIGHT_PURPLE),
                this.text("❋ " + l.choose("Cura (Mending): ", "Mending: ") + Math.round(s.mending()) + "%", NamedTextColor.GREEN));
        return this.item(Material.IRON_SWORD, l.choose("Status de Combate", "Combat Stats"), lore);
    }

    private ItemStack fortuneItem(Player p, SkillType t, Language l, NamedTextColor color) {
        List<Component> lore = List.of(this.text(t.name(l == Language.PT) + " Fortune: " + this.general.fortune(p, t), color));
        return this.item(t.icon(), t.name(l == Language.PT) + " Fortune", lore);
    }

    private double value(Player p, Attribute a, double f) {
        AttributeInstance x = p.getAttribute(a);
        return x == null ? f : x.getValue();
    }

    private Component text(String s, NamedTextColor c) {
        return Component.text(s, c);
    }

    private ItemStack item(Material mat, String name, List<Component> lore) {
        ItemStack i = ItemStack.of(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(this.text(name, NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(cmp -> cmp.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }

    private record View(Type type, int page, SkillType skill) {
    }

    private enum Type {
        MAIN, COMBAT, GENERAL, GLOBAL, STATS
    }
}
