package dev.icaro.foodtooltips.skills;

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
    private BackpackService backpacks;
    private LevelColorMenuService levelColors;
    private CombatTreeMenuService tree;
    private final Map<UUID, View> views = new HashMap<>();

    public SkillsMenuService(CombatSkillService c, GeneralSkillService g, PlayerStatsService s, CombatAbilityService a, MiningMenuService m, GlobalLevelService global) {
        this.combat = c;
        this.general = g;
        this.stats = s;
        this.abilities = a;
        this.mining = m;
        this.global = global;
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

    public void openMain(Player p) {
        Language l = Language.of(p);
        Inventory v = this.inv(l.choose("Habilidades", "Skills"));
        v.setItem(4, this.head(p, l));
        v.setItem(20, this.item(Material.IRON_SWORD, l.choose("Combate", "Combat"), List.of(this.combatLine(p, l), this.click(l))));
        for (Map.Entry<Integer, SkillType> e : S.entrySet()) {
            SkillType t = e.getValue();
            v.setItem(e.getKey(), this.item(t.icon(), t.name(l == Language.PT), List.of(this.skillLine(p, t, l), this.click(l))));
        }
        if (this.backpacks != null) {
            v.setItem(46, this.backpacks.menuIcon(l.choose("Mochilas", "Backpacks"), List.of(this.text(l.choose("Mochilas de todas as skills.", "Backpacks for every skill."), NamedTextColor.YELLOW))));
        }
        if (this.levelColors != null) {
            v.setItem(47, this.item(Material.NAME_TAG, l.choose("Cores do Nível", "Level Colors"), List.of(this.click(l))));
        }
        v.setItem(48, this.item(Material.COMPARATOR, l.choose("Árvore de Combate", "Combat Tree"), List.of(this.click(l))));
        v.setItem(49, this.item(Material.KNOWLEDGE_BOOK, l.choose("Bestiário", "Bestiary"), List.of(this.click(l))));
        v.setItem(51, this.item(Material.EMERALD, l.choose("Loja", "Shop"), List.of(this.click(l))));
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
            Component bag = this.bagReward(level, l);
            if (bag != null) {
                lore.add(bag);
            }
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
                if (level == 1) {
                    lore.add(this.text("✦ " + l.choose("Desbloqueia: Telecinese", "Unlocks: Telekinesis"), NamedTextColor.LIGHT_PURPLE));
                } else if (level == 3) {
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

    public boolean handleClick(Player p, int slot) {
        View v = this.views.get(p.getUniqueId());
        if (v == null) {
            return false;
        }
        switch (v.type()) {
            case MAIN -> {
                if (slot == 20) {
                    this.openCombat(p, 0);
                } else if (S.containsKey(slot)) {
                    this.openGeneral(p, S.get(slot), 0);
                } else if (slot == 46 && this.backpacks != null) {
                    this.views.remove(p.getUniqueId());
                    this.backpacks.openMenu(p);
                } else if (slot == 47 && this.levelColors != null) {
                    this.views.remove(p.getUniqueId());
                    this.levelColors.open(p);
                } else if (slot == 48 && this.tree != null) {
                    this.views.remove(p.getUniqueId());
                    this.tree.open(p);
                } else if (slot == 49) {
                    p.performCommand("bestiary");
                } else if (slot == 51) {
                    p.performCommand("shop");
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

    private ItemStack head(Player p, Language l) {
        PlayerStats s = this.stats.stats(p);
        CombatProgress c = this.combat.progress(p);
        GlobalLevelSnapshot g = this.global.snapshot(p);
        int defense = this.general.defense(p);
        List<Component> lore = List.of(
                this.text("✦ " + l.choose("Nível Global: ", "Global Level: ") + g.level(), NamedTextColor.GOLD),
                this.text(l.choose("Progresso: ", "Progress: ") + g.progress() + "/" + g.required() + " XP", NamedTextColor.YELLOW),
                this.text(l.choose("XP Global total: ", "Total Global XP: ") + String.format(Locale.US, "%,d", g.totalXp()), NamedTextColor.GRAY),
                this.text(l.choose("Bônus: +", "Bonuses: +") + Math.round(g.bonusHealth()) + " HP e +" + g.strength() + " Strength", NamedTextColor.LIGHT_PURPLE),
                Component.empty(),
                this.text("❤ " + l.choose("Vida: ", "Health: ") + Math.round(s.health()) + "/" + Math.round(s.maxHealth()), NamedTextColor.RED),
                this.text("✎ Mana: " + Math.round(s.mana()) + "/" + Math.round(s.maxMana()), NamedTextColor.AQUA),
                this.text("✿ Vitality: " + Math.round(s.vitality()) + "/" + Math.round(s.maxVitality()), NamedTextColor.LIGHT_PURPLE),
                this.text("✹ Strength: " + s.strength(), NamedTextColor.RED),
                this.text(l.choose("Bônus do Nível Global: +", "Global Level Bonus: +") + s.globalStrength(), NamedTextColor.DARK_RED),
                this.text("☣ " + l.choose("Chance crítica: ", "Crit Chance: ") + String.format(Locale.US, "%.1f", this.combat.critChance(c.level()) + this.abilities.critChanceBonus(p)) + "%", NamedTextColor.AQUA),
                this.text("⚔ " + l.choose("Dano da arma: ", "Weapon Damage: ") + String.format(Locale.US, "%.1f", this.value(p, Attribute.ATTACK_DAMAGE, 1.0)), NamedTextColor.RED),
                this.text("⚡ " + l.choose("Velocidade da arma: ", "Weapon Attack Speed: ") + String.format(Locale.US, "%.1f", this.value(p, Attribute.ATTACK_SPEED, 4.0)), NamedTextColor.YELLOW),
                Component.empty(),
                this.text("Ⓕ Ferocity: " + Math.round(s.ferocity()), NamedTextColor.RED),
                this.text(l.choose("Alcance de Ataque: ", "Swing Range: ") + String.format(Locale.US, "%.1f", s.swingRange()), NamedTextColor.YELLOW),
                this.text(l.choose("Inteligência: ", "Intelligence: ") + Math.round(s.intelligence()), NamedTextColor.AQUA),
                this.text(l.choose("Dano de Habilidade: ", "Ability Damage: ") + Math.round(s.abilityDamage()) + "%", NamedTextColor.LIGHT_PURPLE),
                this.text(l.choose("Regen. de Vida: ", "Health Regen: ") + Math.round(s.healthRegen()) + "%", NamedTextColor.RED),
                this.text(l.choose("Cura (Mending): ", "Mending: ") + Math.round(s.mending()) + "%", NamedTextColor.GREEN),
                Component.empty(),
                this.text("✦ " + l.choose("Defesa: ", "Defense: ") + defense, NamedTextColor.GREEN),
                this.text("🛡 " + l.choose("Redução de dano: ", "Damage Reduction: ") + String.format(Locale.US, "%.1f", this.general.damageReduction(p) * 100.0) + "%", NamedTextColor.GREEN),
                Component.empty(),
                this.text("⛏ Mining Fortune: " + this.general.fortune(p, SkillType.MINING), NamedTextColor.AQUA),
                this.text("☀ Farming Fortune: " + this.general.fortune(p, SkillType.FARMING), NamedTextColor.GREEN),
                this.text("♣ Foraging Fortune: " + this.general.fortune(p, SkillType.FORAGING), NamedTextColor.DARK_GREEN));
        ItemStack i = this.item(Material.PLAYER_HEAD, l.choose("Seus status", "Your Stats"), lore);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        m.setOwningPlayer((OfflinePlayer) p);
        i.setItemMeta(m);
        return i;
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
        MAIN, COMBAT, GENERAL
    }
}
