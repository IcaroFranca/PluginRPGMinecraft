package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalLevelSnapshot;
import dev.icaro.foodtooltips.global.GlobalPresentationService;
import dev.icaro.foodtooltips.global.LevelBadgeRenderer;
import dev.icaro.foodtooltips.global.LevelColorCatalog;
import dev.icaro.foodtooltips.global.LevelColorService;
import dev.icaro.foodtooltips.global.LevelColorTheme;
import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.inventory.InventoryCloseEvent;
import org.bukkit.event.inventory.InventoryDragEvent;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

public final class LevelColorMenuService
implements Listener {
    private final GlobalLevelService global;
    private final LevelColorService colors;
    private final LevelBadgeRenderer renderer;
    private final GlobalPresentationService presentation;
    private final Consumer<Player> back;
    private final Set<UUID> viewers = new HashSet<UUID>();

    public LevelColorMenuService(GlobalLevelService global, LevelColorService colors, LevelBadgeRenderer renderer, GlobalPresentationService presentation, Consumer<Player> back) {
        this.global = global;
        this.colors = colors;
        this.renderer = renderer;
        this.presentation = presentation;
        this.back = back;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)l.choose("Cores do N\u00edvel", "Level Colors"));
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of(), false);
        for (int i = 0; i < 54; ++i) {
            inv.setItem(i, filler);
        }
        GlobalLevelSnapshot snapshot = this.global.snapshot(p);
        LevelColorTheme selected = this.colors.selected(p);
        ItemStack head = this.item(Material.PLAYER_HEAD, l.choose("Preview do N\u00edvel", "Level Preview"), List.of(this.presentation.badge(p).append((Component)Component.text((String)p.getName(), (TextColor)NamedTextColor.WHITE)), this.text(l.choose("N\u00edvel Global: ", "Global Level: ") + snapshot.level(), NamedTextColor.GOLD), this.text(l.choose("Selecionado: ", "Selected: ") + selected.name(), NamedTextColor.YELLOW), !this.colors.unlocked(p, selected) ? this.text(l.choose("Temporariamente suspenso: n\u00edvel insuficiente", "Temporarily suspended: insufficient level"), NamedTextColor.RED) : Component.empty()), false);
        SkullMeta skull = (SkullMeta)head.getItemMeta();
        skull.setOwningPlayer((OfflinePlayer)p);
        head.setItemMeta((ItemMeta)skull);
        inv.setItem(4, head);
        List<LevelColorTheme> themes = LevelColorCatalog.themes();
        for (int i = 0; i < themes.size(); ++i) {
            LevelColorTheme theme = themes.get(i);
            boolean unlocked = this.colors.unlocked(p, theme);
            boolean active = selected.id().equals(theme.id());
            ArrayList<Component> lore = new ArrayList<Component>();
            lore.add(this.text("ID: " + theme.id(), NamedTextColor.DARK_GRAY));
            lore.add(this.text(l.choose("Requer N\u00edvel Global ", "Requires Global Level ") + theme.requiredLevel(), unlocked ? NamedTextColor.GREEN : NamedTextColor.RED));
            lore.add(this.text(this.palette(theme), NamedTextColor.GRAY));
            lore.add(this.text(active ? l.choose("SELECIONADO", "SELECTED") : (unlocked ? l.choose("Clique para selecionar", "Click to select") : l.choose("BLOQUEADO", "LOCKED")), active ? NamedTextColor.GOLD : (unlocked ? NamedTextColor.YELLOW : NamedTextColor.RED)));
            inv.setItem(9 + i, this.item(unlocked ? theme.icon() : Material.GRAY_DYE, theme.name(), lore, active));
        }
        inv.setItem(49, this.item(Material.ARROW, l.choose("Voltar", "Back"), List.of(), false));
        p.openInventory(inv);
        this.viewers.add(p.getUniqueId());
    }

    @EventHandler(ignoreCancelled=true)
    public void click(InventoryClickEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (!(humanEntity instanceof Player) || !this.viewers.contains((p = (Player)humanEntity).getUniqueId())) {
            return;
        }
        e.setCancelled(true);
        int slot = e.getRawSlot();
        if (slot == 49) {
            this.viewers.remove(p.getUniqueId());
            this.back.accept(p);
            return;
        }
        int index = slot - 9;
        if (index < 0 || index >= LevelColorCatalog.themes().size()) {
            return;
        }
        LevelColorTheme theme = LevelColorCatalog.themes().get(index);
        Language l = Language.of(p);
        if (!this.colors.select(p, theme)) {
            p.sendMessage((Component)Component.text((String)(l.choose("Voc\u00ea precisa do N\u00edvel Global ", "You need Global Level ") + theme.requiredLevel() + l.choose(" para usar este tema.", " to use this theme.")), (TextColor)NamedTextColor.RED));
            return;
        }
        p.sendMessage((Component)Component.text((String)(l.choose("Tema de n\u00edvel selecionado: ", "Level theme selected: ") + theme.name()), (TextColor)NamedTextColor.GREEN));
        this.open(p);
    }

    @EventHandler(ignoreCancelled=true)
    public void drag(InventoryDragEvent e) {
        Player p;
        HumanEntity humanEntity = e.getWhoClicked();
        if (humanEntity instanceof Player && this.viewers.contains((p = (Player)humanEntity).getUniqueId())) {
            e.setCancelled(true);
        }
    }

    @EventHandler
    public void close(InventoryCloseEvent e) {
        HumanEntity humanEntity = e.getPlayer();
        if (humanEntity instanceof Player) {
            Player p = (Player)humanEntity;
            this.viewers.remove(p.getUniqueId());
        }
    }

    private String palette(LevelColorTheme theme) {
        StringJoiner out = new StringJoiner(" \u2192 ");
        for (int color : theme.palette()) {
            out.add(String.format("#%06X", color));
        }
        return out.toString();
    }

    private Component text(String value, NamedTextColor color) {
        return Component.text((String)value, (TextColor)color);
    }

    private ItemStack item(Material material, String name, List<Component> lore, boolean glint) {
        ItemStack stack = ItemStack.of((Material)material);
        ItemMeta meta = stack.getItemMeta();
        meta.displayName(Component.text((String)name, (TextColor)NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(x -> x.decoration(TextDecoration.ITALIC, false)).toList());
        meta.setEnchantmentGlintOverride(Boolean.valueOf(glint));
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        stack.setItemMeta(meta);
        return stack;
    }
}

