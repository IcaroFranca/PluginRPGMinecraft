package dev.icaro.foodtooltips.mining;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.mining.BuriedTreasureService;
import dev.icaro.foodtooltips.mining.GemService;
import dev.icaro.foodtooltips.mining.MiningCatalog;
import dev.icaro.foodtooltips.mining.MiningEntry;
import dev.icaro.foodtooltips.mining.TreasureRarity;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class MiningMenuService {
    private static final int[] MILESTONE_TARGETS = new int[]{25, 100, 250, 500, 1000};
    private static final int[] MILESTONE_SLOTS = new int[]{20, 21, 22, 23, 24};
    private final Set<UUID> viewers = new HashSet<UUID>();
    private final Set<UUID> details = new HashSet<UUID>();
    private final GeneralSkillService skills = new GeneralSkillService();
    private final BuriedTreasureService treasures = new BuriedTreasureService(this.skills);
    private final GemService gems;

    public MiningMenuService() {
        this(null);
    }

    public MiningMenuService(GemService gems) {
        this.gems = gems;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)l.choose("Comp\u00eandio de Minera\u00e7\u00e3o", "Mining Compendium"));
        this.fill(inv);
        List<MiningEntry> entries = MiningCatalog.entries();
        for (int i = 0; i < entries.size(); ++i) {
            MiningEntry e = entries.get(i);
            int mined = this.skills.mined(p, e.block());
            int milestones = this.skills.miningMilestones(p, e.block());
            List<Component> lore = List.of(this.line(l.choose("Blocos minerados: ", "Blocks mined: ") + mined, NamedTextColor.WHITE), this.line("Milestones: " + milestones + "/5", NamedTextColor.GOLD), this.line(l.choose("XP de Minera\u00e7\u00e3o: ", "Mining XP: ") + this.format(e.skillXp()), NamedTextColor.AQUA), this.line(l.choose("Drop base: ", "Base drop: ") + String.valueOf(e.minDrop() == e.maxDrop() ? Integer.valueOf(e.minDrop()) : e.minDrop() + "\u2013" + e.maxDrop()), NamedTextColor.YELLOW), this.line(l.choose("Orbes de XP: ", "XP Orbs: ") + e.vanillaXp(), NamedTextColor.GREEN), this.line(l.choose("Camadas: ", "Layers: ") + (l == Language.PT ? e.layersPt() : e.layersEn()), NamedTextColor.GRAY), Component.empty(), this.line(l.choose("Clique para ver as milestones!", "Click to view milestones!"), NamedTextColor.YELLOW));
            inv.setItem(this.entrySlot(i), this.item(e.block(), Component.translatable((String)e.block().translationKey()).color((TextColor)NamedTextColor.GOLD), lore));
        }
        Material target = this.skills.commissionTarget(p);
        inv.setItem(45, this.item(Material.WRITABLE_BOOK, (Component)Component.text((String)l.choose("Comiss\u00e3o di\u00e1ria", "Daily Commission"), (TextColor)NamedTextColor.GOLD), List.of(Component.translatable((String)target.translationKey()).color((TextColor)NamedTextColor.YELLOW), this.line(this.skills.commissionProgress(p) + "/" + this.skills.commissionGoal(p), NamedTextColor.GREEN), this.line(l.choose("Recompensa: 500 XP e 250 P\u00f3 Mineral", "Reward: 500 XP and 250 Mineral Dust"), NamedTextColor.AQUA))));
        inv.setItem(46, this.treasureItem(p, l));
        inv.setItem(47, this.item(Material.AMETHYST_SHARD, (Component)Component.text((String)l.choose("P\u00f3 Mineral", "Mineral Dust"), (TextColor)NamedTextColor.LIGHT_PURPLE), List.of(this.line(Long.toString(this.skills.mineralDust(p)), NamedTextColor.AQUA), this.line(l.choose("Usado em futuras melhorias de Minera\u00e7\u00e3o.", "Used for future Mining upgrades."), NamedTextColor.GRAY))));
        inv.setItem(49, this.item(Material.ARROW, (Component)Component.text((String)l.choose("Voltar", "Back"), (TextColor)NamedTextColor.GOLD), List.of()));
        p.openInventory(inv);
        this.viewers.add(p.getUniqueId());
        this.details.remove(p.getUniqueId());
    }

    public void openDetail(Player p, MiningEntry entry) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)l.choose("Milestones de Minera\u00e7\u00e3o", "Mining Milestones"));
        this.fill(inv);
        int mined = this.skills.mined(p, entry.block());
        inv.setItem(4, this.item(entry.block(), Component.translatable((String)entry.block().translationKey()).color((TextColor)NamedTextColor.GOLD), List.of(this.line(l.choose("Total minerado: ", "Total mined: ") + mined, NamedTextColor.WHITE), this.line(l.choose("Milestones conclu\u00eddas: ", "Milestones completed: ") + this.skills.miningMilestones(p, entry.block()) + "/5", NamedTextColor.GOLD))));
        for (int i = 0; i < MILESTONE_TARGETS.length; ++i) {
            int target = MILESTONE_TARGETS[i];
            boolean complete = mined >= target;
            int previous = i == 0 ? 0 : MILESTONE_TARGETS[i - 1];
            int current = Math.max(0, Math.min(target - previous, mined - previous));
            List<Component> lore = List.of(this.line(l.choose("Meta: minerar ", "Goal: mine ") + target, NamedTextColor.GRAY), this.line(l.choose("Progresso: ", "Progress: ") + current + "/" + (target - previous), NamedTextColor.YELLOW), this.line(l.choose("Recompensa: +0,5% Minerador Cuidadoso", "Reward: +0.5% Careful Miner"), NamedTextColor.AQUA), this.line(complete ? l.choose("CONCLU\u00cdDA", "COMPLETED") : l.choose("BLOQUEADA", "LOCKED"), complete ? NamedTextColor.GREEN : NamedTextColor.RED));
            inv.setItem(MILESTONE_SLOTS[i], this.item(complete ? Material.LIME_DYE : Material.GRAY_DYE, l.choose("Milestone ", "Milestone ") + (i + 1), lore));
        }
        inv.setItem(49, this.item(Material.ARROW, (Component)Component.text((String)l.choose("Voltar ao Comp\u00eandio", "Back to Compendium"), (TextColor)NamedTextColor.GOLD), List.of()));
        p.openInventory(inv);
        this.viewers.add(p.getUniqueId());
        this.details.add(p.getUniqueId());
    }

    public boolean clickEntry(Player p, int slot) {
        if (slot == 48 && this.gems != null) {
            this.gems.open(p);
            return true;
        }
        if (this.details.contains(p.getUniqueId())) {
            return false;
        }
        List<MiningEntry> entries = MiningCatalog.entries();
        for (int i = 0; i < entries.size(); ++i) {
            if (slot != this.entrySlot(i)) continue;
            this.openDetail(p, entries.get(i));
            return true;
        }
        return false;
    }

    public GemService gems() {
        return this.gems;
    }

    public boolean detail(Player p) {
        return this.details.contains(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewers.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewers.remove(p.getUniqueId());
        this.details.remove(p.getUniqueId());
    }

    private ItemStack treasureItem(Player p, Language l) {
        ArrayList<Component> lore = new ArrayList<Component>();
        lore.add(this.line(l.choose("Encontrados ao minerar min\u00e9rios naturais.", "Found while mining natural ores."), NamedTextColor.GRAY));
        lore.add((Component)Component.empty());
        lore.add(this.treasureLine(p, l, TreasureRarity.COMMON, "1/500"));
        lore.add(this.treasureLine(p, l, TreasureRarity.UNCOMMON, "1/2.000"));
        lore.add(this.treasureLine(p, l, TreasureRarity.RARE, "1/8.333"));
        lore.add(this.treasureLine(p, l, TreasureRarity.EPIC, "1/33.333"));
        lore.add(this.treasureLine(p, l, TreasureRarity.LEGENDARY, "1/100.000"));
        return this.item(Material.CHEST, (Component)Component.text((String)l.choose("Tesouros Soterrados", "Buried Treasures"), (TextColor)NamedTextColor.GOLD), lore);
    }

    private Component treasureLine(Player p, Language l, TreasureRarity rarity, String chance) {
        return Component.text((String)("\u25c6 " + rarity.name(l == Language.PT) + " \u2022 " + chance + " \u2022 " + this.treasures.found(p, rarity)), (TextColor)rarity.color());
    }

    private int entrySlot(int i) {
        return 10 + i / 7 * 9 + i % 7;
    }

    private void fill(Inventory inv) {
        ItemStack pane = this.item(Material.GRAY_STAINED_GLASS_PANE, (Component)Component.text((String)" "), List.of());
        for (int i = 0; i < 54; ++i) {
            inv.setItem(i, pane);
        }
        if (this.gems != null) {
            inv.setItem(48, this.item(Material.EMERALD, (Component)Component.text((String)"\u2726", (TextColor)NamedTextColor.LIGHT_PURPLE), List.of()));
        }
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text((String)s, (TextColor)c);
    }

    private ItemStack item(Material mat, String name, List<Component> lore) {
        return this.item(mat, (Component)Component.text((String)name, (TextColor)NamedTextColor.GOLD), lore);
    }

    private ItemStack item(Material mat, Component name, List<Component> lore) {
        ItemStack i = ItemStack.of((Material)mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(name.decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        i.setItemMeta(m);
        return i;
    }

    private String format(double n) {
        return n == (double)((long)n) ? Long.toString((long)n) : Double.toString(n);
    }
}

