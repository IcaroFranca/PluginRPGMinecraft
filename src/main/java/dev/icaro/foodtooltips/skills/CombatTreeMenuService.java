package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Consumer;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.event.inventory.ClickType;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;

/**
 * Renders and drives the combat ability tree: a 54-slot chest laying out
 * every {@link CombatAbility} at the slot defined by its {@link
 * CombatTreeNode}, with branches read top (capstone) to bottom (roots).
 *
 * <p>Left click unlocks/upgrades a node (spends Blood Points). Shift-click toggles
 * an unlocked passive on/off. Right click casts a menu-triggered active
 * ability (Arcane Slash, Vital Touch). The back button sits bottom-left and
 * the player's head (currency/status header) sits bottom-right.
 */
public final class CombatTreeMenuService {
    private static final int BACK_SLOT = 45;
    private static final int HEADER_SLOT = 53;
    private static final String CURRENCY_SYMBOL = "🩸";
    private static final Map<Integer, CombatAbility> SLOT_TO_ABILITY = buildSlotMap();

    private final CombatSkillService combat;
    private final CombatAbilityService abilities;
    private final CombatValorService valor;
    private final Consumer<Player> backCallback;
    private final Set<UUID> viewing = new HashSet<>();

    public CombatTreeMenuService(CombatSkillService combat, CombatAbilityService abilities, CombatValorService valor, Consumer<Player> backCallback) {
        this.combat = combat;
        this.abilities = abilities;
        this.valor = valor;
        this.backCallback = backCallback;
    }

    private static Map<Integer, CombatAbility> buildSlotMap() {
        Map<Integer, CombatAbility> map = new HashMap<>();
        for (CombatTreeNode node : CombatTreeNode.all().values()) {
            map.put(node.slot(), node.ability());
        }
        return map;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory v = Bukkit.createInventory(null, 54, l.choose("Árvore de Combate", "Combat Tree"));
        ItemStack filler = this.item(Material.GRAY_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < 54; i++) {
            v.setItem(i, filler);
        }
        v.setItem(HEADER_SLOT, this.headerItem(p, l));
        v.setItem(BACK_SLOT, this.item(Material.BARRIER, l.choose("Voltar às skills", "Back to skills"), List.of()));
        for (Map.Entry<Integer, CombatAbility> entry : SLOT_TO_ABILITY.entrySet()) {
            v.setItem(entry.getKey(), this.nodeItem(p, entry.getValue(), l));
        }
        p.openInventory(v);
        this.viewing.add(p.getUniqueId());
    }

    public boolean viewing(Player p) {
        return this.viewing.contains(p.getUniqueId());
    }

    public void close(Player p) {
        this.viewing.remove(p.getUniqueId());
    }

    public boolean handleClick(Player p, int slot, ClickType click) {
        if (slot == BACK_SLOT) {
            this.close(p);
            this.backCallback.accept(p);
            return true;
        }
        CombatAbility ability = SLOT_TO_ABILITY.get(slot);
        if (ability == null) {
            return true;
        }
        Language l = Language.of(p);
        CombatTreeNode node = CombatTreeNode.of(ability);
        if (click.isShiftClick()) {
            this.handleToggle(p, ability, l);
        } else if (click == ClickType.RIGHT) {
            this.handleCast(p, ability, node, l);
        } else {
            this.handlePurchase(p, ability, l);
        }
        this.open(p);
        return true;
    }

    private void handleToggle(Player p, CombatAbility ability, Language l) {
        if (!this.abilities.unlocked(p, ability)) {
            p.sendActionBar(this.text(l.choose("Ainda não desbloqueada.", "Not unlocked yet."), NamedTextColor.RED));
            return;
        }
        boolean now = this.abilities.toggle(p, ability);
        p.sendActionBar(this.text(ability.name(l == Language.PT) + ": " + (now ? l.choose("ATIVADA", "ENABLED") : l.choose("DESATIVADA", "DISABLED")), now ? NamedTextColor.GREEN : NamedTextColor.GRAY));
    }

    private void handleCast(Player p, CombatAbility ability, CombatTreeNode node, Language l) {
        if (node.kind() != CombatTreeNode.Kind.ACTIVE_MENU) {
            p.sendActionBar(this.text(l.choose("Esta habilidade não é ativada pelo menu.", "This ability isn't activated from the menu."), NamedTextColor.GRAY));
            return;
        }
        CombatAbilityService.CastResult result = ability == CombatAbility.ARCANE_SLASH ? this.abilities.castArcaneSlash(p) : this.abilities.castVitalTouch(p);
        switch (result) {
            case SUCCESS -> p.sendActionBar(this.text("✦ " + ability.name(l == Language.PT) + "! ✦", NamedTextColor.LIGHT_PURPLE));
            case LOCKED -> p.sendActionBar(this.text(l.choose("Ainda não desbloqueada.", "Not unlocked yet."), NamedTextColor.RED));
            case ON_COOLDOWN -> p.sendActionBar(this.text(l.choose("Em recarga.", "On cooldown."), NamedTextColor.RED));
            case INSUFFICIENT_RESOURCE -> p.sendActionBar(this.text(l.choose("Recurso insuficiente.", "Not enough resource."), NamedTextColor.RED));
            case NO_TARGET -> p.sendActionBar(this.text(l.choose("Sem alvo à vista.", "No target in sight."), NamedTextColor.RED));
        }
    }

    private void handlePurchase(Player p, CombatAbility ability, Language l) {
        CombatAbilityService.PurchaseResult result = this.abilities.purchaseRank(p, ability);
        switch (result) {
            case SUCCESS -> p.sendActionBar(this.text("✦ " + ability.name(l == Language.PT) + " " + l.choose("melhorada!", "upgraded!") + " (" + this.abilities.rank(p, ability) + "/" + this.abilities.maxRank(ability) + ")", NamedTextColor.GREEN));
            case ALREADY_MAX -> p.sendActionBar(this.text(l.choose("Já está no nível máximo.", "Already at max level."), NamedTextColor.GRAY));
            case PREREQUISITE_MISSING -> p.sendActionBar(this.text(l.choose("Desbloqueie os pré-requisitos primeiro.", "Unlock the prerequisites first."), NamedTextColor.RED));
            case INSUFFICIENT_VALOR -> p.sendActionBar(this.text(l.choose("Pontos de Sangue insuficientes.", "Not enough Blood Points."), NamedTextColor.RED));
        }
    }

    private ItemStack headerItem(Player p, Language l) {
        int level = this.combat.progress(p).level();
        long balance = this.valor.balance(p);
        List<Component> lore = List.of(
                this.text(l.choose("Nível de Combate: ", "Combat Level: ") + level, NamedTextColor.GREEN),
                this.text(CURRENCY_SYMBOL + " " + l.choose("Pontos de Sangue: ", "Blood Points: ") + this.valor.format(balance), NamedTextColor.DARK_RED),
                this.text(l.choose("Ganhe matando mobs hostis (veja o Bestiário) e ao subir de nível de Combate.", "Earn them by killing hostile mobs (see the Bestiary) and leveling up Combat."), NamedTextColor.GRAY),
                Component.empty(),
                this.text(l.choose("🔒 Carvão: bloqueada", "🔒 Coal: locked"), NamedTextColor.DARK_GRAY),
                this.text(l.choose("✔ Esmeralda: desbloqueada", "✔ Emerald: unlocked"), NamedTextColor.GREEN),
                this.text(l.choose("★ Diamante: nível máximo", "★ Diamond: max level"), NamedTextColor.AQUA),
                this.text(l.choose("(bloco = ativa, minério/gema = passiva)", "(block = active, ore/gem = passive)"), NamedTextColor.DARK_GRAY),
                Component.empty(),
                this.text(l.choose("Clique: desbloquear/melhorar", "Click: unlock/upgrade"), NamedTextColor.YELLOW),
                this.text(l.choose("Shift + clique: ativar/desativar", "Shift + click: enable/disable"), NamedTextColor.YELLOW),
                this.text(l.choose("Clique direito: usar (habilidades ativas)", "Right click: cast (active abilities)"), NamedTextColor.YELLOW));
        ItemStack i = this.item(Material.PLAYER_HEAD, l.choose("Sua Árvore de Combate", "Your Combat Tree"), lore);
        SkullMeta m = (SkullMeta) i.getItemMeta();
        m.setOwningPlayer((OfflinePlayer) p);
        i.setItemMeta(m);
        return i;
    }

    /** Locked → coal, unlocked → emerald, maxed → diamond; block variant = active, item variant = passive. */
    private Material stateIcon(boolean active, int rank, int max) {
        if (rank <= 0) {
            return active ? Material.COAL_BLOCK : Material.COAL;
        }
        if (rank >= max) {
            return active ? Material.DIAMOND_BLOCK : Material.DIAMOND;
        }
        return active ? Material.EMERALD_BLOCK : Material.EMERALD;
    }

    private ItemStack nodeItem(Player p, CombatAbility ability, Language l) {
        CombatTreeNode node = CombatTreeNode.of(ability);
        int rank = this.abilities.rank(p, ability);
        int max = this.abilities.maxRank(ability);
        boolean unlocked = rank > 0;
        boolean maxed = rank >= max;
        boolean prereqOk = this.abilities.prerequisitesMet(p, ability);
        boolean purchasable = !maxed && prereqOk;
        boolean active = node.kind() != CombatTreeNode.Kind.PASSIVE;

        NamedTextColor nameColor = maxed ? NamedTextColor.GOLD : unlocked ? NamedTextColor.GREEN : purchasable ? NamedTextColor.YELLOW : NamedTextColor.GRAY;
        String prefix = maxed ? "★ " : unlocked ? "✔ " : purchasable ? "" : "🔒 ";
        String name = prefix + ability.name(l == Language.PT);

        List<Component> lore = new ArrayList<>();
        lore.add(this.text(this.abilities.description(ability, l == Language.PT), NamedTextColor.GRAY));
        lore.add(this.text(node.branch().name(l == Language.PT) + " • " + l.choose("Nível ", "Level ") + rank + "/" + max, NamedTextColor.DARK_AQUA));
        for (CombatAbilityService.StatPreview stat : this.abilities.statPreview(ability, rank, l == Language.PT)) {
            if (stat.next() != null) {
                lore.add(this.text("  " + stat.label() + ": " + stat.current() + " → " + stat.next(), NamedTextColor.WHITE));
            } else {
                lore.add(this.text("  " + stat.label() + ": " + stat.current() + " (" + l.choose("máx", "max") + ")", NamedTextColor.WHITE));
            }
        }
        if (!node.prerequisites().isEmpty()) {
            for (CombatAbility prereq : node.prerequisites()) {
                boolean ok = this.abilities.unlocked(p, prereq);
                lore.add(this.text((ok ? "✔ " : "✖ ") + l.choose("Requer: ", "Requires: ") + prereq.name(l == Language.PT), ok ? NamedTextColor.DARK_GREEN : NamedTextColor.RED));
            }
        }
        if (maxed) {
            lore.add(this.text(l.choose("NÍVEL MÁXIMO", "MAX LEVEL"), NamedTextColor.GOLD));
        } else {
            long cost = this.abilities.nextRankCost(p, ability);
            lore.add(this.text(CURRENCY_SYMBOL + " " + (unlocked ? l.choose("Melhorar: ", "Upgrade: ") : l.choose("Desbloquear: ", "Unlock: ")) + this.valor.format(cost) + " " + l.choose("Pontos de Sangue", "Blood Points"), NamedTextColor.DARK_RED));
        }
        if (unlocked && node.kind() == CombatTreeNode.Kind.PASSIVE) {
            boolean enabled = this.abilities.enabled(p, ability);
            lore.add(this.text(enabled ? l.choose("ATIVADA (shift-clique desativa)", "ENABLED (shift-click disables)") : l.choose("DESATIVADA (shift-clique ativa)", "DISABLED (shift-click enables)"), enabled ? NamedTextColor.GREEN : NamedTextColor.GRAY));
        }
        if (node.kind() == CombatTreeNode.Kind.ACTIVE_KEYBIND) {
            lore.add(this.text(l.choose("Ativa: trocar de mão (F) sem agachar", "Activates: swap hands (F) without sneaking"), NamedTextColor.AQUA));
        }
        if (node.kind() == CombatTreeNode.Kind.ACTIVE_MENU) {
            if (unlocked) {
                long remaining = ability == CombatAbility.ARCANE_SLASH ? this.abilities.arcaneSlashCooldownRemainingMillis(p) : this.abilities.vitalTouchCooldownRemainingMillis(p);
                if (remaining > 0L) {
                    lore.add(this.text(String.format(Locale.US, l.choose("Recarga: %.1fs", "Cooldown: %.1fs"), remaining / 1000.0), NamedTextColor.RED));
                } else {
                    lore.add(this.text(l.choose("Clique direito para usar!", "Right click to use!"), NamedTextColor.AQUA));
                }
            }
        }

        ItemStack item = this.item(this.stateIcon(active, rank, max), name, lore, nameColor);
        item.setAmount(Math.max(1, Math.min(64, rank)));
        return item;
    }

    private Component text(String s, NamedTextColor c) {
        return Component.text(s, c);
    }

    private ItemStack item(Material mat, String name, List<Component> lore) {
        return this.item(mat, name, lore, NamedTextColor.GOLD);
    }

    private ItemStack item(Material mat, String name, List<Component> lore, NamedTextColor nameColor) {
        ItemStack i = ItemStack.of(mat);
        ItemMeta m = i.getItemMeta();
        m.displayName(this.text(name, nameColor).decoration(TextDecoration.ITALIC, false));
        m.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        m.addItemFlags(ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP);
        i.setItemMeta(m);
        return i;
    }
}
