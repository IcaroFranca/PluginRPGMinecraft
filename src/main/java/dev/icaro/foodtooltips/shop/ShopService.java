package dev.icaro.foodtooltips.shop;

import com.destroystokyo.paper.profile.PlayerProfile;
import com.destroystokyo.paper.profile.ProfileProperty;
import dev.icaro.foodtooltips.economy.EconomyService;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.shop.ShopItem;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import org.bukkit.Bukkit;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.entity.Player;
import org.bukkit.inventory.Inventory;
import org.bukkit.inventory.ItemFlag;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;
import org.bukkit.inventory.meta.SkullMeta;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;

public final class ShopService {
    private final Plugin plugin;
    private final NamespacedKey itemKey;
    private final NamespacedKey salePreviewKey;
    private final EconomyService economy;
    private final Map<UUID, View> views = new HashMap<UUID, View>();
    private final Map<Integer, ShopItem> buttons = new HashMap<Integer, ShopItem>();

    public ShopService(Plugin p, EconomyService e) {
        this.plugin = p;
        this.itemKey = new NamespacedKey("foodtooltips", "shop_item");
        this.salePreviewKey = new NamespacedKey("foodtooltips", "sale_preview");
        this.economy = e;
    }

    public void open(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)l.choose("Loja RPG", "RPG Shop"));
        this.fill(inv);
        this.buttons.clear();
        ShopItem[] all = ShopItem.values();
        for (int i = 0; i < all.length; ++i) {
            int slot = (i / 7 + 1) * 9 + i % 7 + 1;
            ShopItem entry = all[i];
            inv.setItem(slot, this.preview(entry, l));
            this.buttons.put(slot, entry);
        }
        inv.setItem(48, this.item(Material.HOPPER, l.choose("Vender itens", "Sell Items"), List.of(Component.text((String)l.choose("Venda materiais do invent\u00e1rio.", "Sell inventory materials."), (TextColor)NamedTextColor.YELLOW))));
        inv.setItem(50, this.item(Material.GOLD_INGOT, l.choose("Seu saldo", "Your Balance"), List.of(Component.text((String)(this.economy.format(this.economy.balance(p)) + " \u26c3"), (TextColor)NamedTextColor.YELLOW))));
        p.openInventory(inv);
        this.views.put(p.getUniqueId(), View.SHOP);
    }

    public void openSell(Player p) {
        Language l = Language.of(p);
        Inventory inv = Bukkit.createInventory(null, (int)54, (String)l.choose("Coloque itens para vender", "Place items to sell"));
        p.openInventory(inv);
        this.views.put(p.getUniqueId(), View.SELL);
    }

    public boolean viewing(Player p) {
        return this.views.containsKey(p.getUniqueId());
    }

    public boolean isShopView(Player p) {
        return this.views.get(p.getUniqueId()) == View.SHOP;
    }

    public void click(Player p, int slot) {
        if (this.views.get(p.getUniqueId()) != View.SHOP) {
            return;
        }
        if (slot == 48) {
            this.openSell(p);
            return;
        }
        ShopItem entry = this.buttons.get(slot);
        if (entry == null) {
            return;
        }
        if (!this.economy.withdraw(p, entry.price())) {
            p.sendMessage((Component)Component.text((String)Language.of(p).choose("Moedas insuficientes.", "Not enough coins."), (TextColor)NamedTextColor.RED));
            return;
        }
        ItemStack bought = this.create(entry, Language.of(p));
        for (ItemStack overflow : p.getInventory().addItem(new ItemStack[]{bought}).values()) {
            p.getWorld().dropItemNaturally(p.getLocation(), overflow);
        }
        p.sendMessage((Component)Component.text((String)(Language.of(p).choose("Compra realizada: ", "Purchased: ") + entry.name(Language.of(p) == Language.PT) + " \u2022 -" + this.economy.format(entry.price()) + " \u26c3"), (TextColor)NamedTextColor.GREEN));
        this.open(p);
    }

    public void close(Player p, Inventory top) {
        View view = this.views.remove(p.getUniqueId());
        if (view != View.SELL) {
            return;
        }
        for (ItemStack stack : p.getInventory().getContents()) {
            if (stack == null) continue;
            this.clearSalePreview(stack);
        }
        long total = 0L;
        for (ItemStack stack : top.getContents()) {
            if (stack == null || stack.getType().isAir()) continue;
            this.clearSalePreview(stack);
            int unit = this.saleValue(stack);
            if (unit <= 0 || this.isSpecial(stack)) {
                for (ItemStack overflow : p.getInventory().addItem(new ItemStack[]{stack}).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), overflow);
                }
                continue;
            }
            total += (long)unit * (long)stack.getAmount();
        }
        top.clear();
        if (total > 0L) {
            this.economy.deposit(p, total);
            p.sendMessage((Component)Component.text((String)(Language.of(p).choose("Itens vendidos por ", "Items sold for ") + this.economy.format(total) + " \u26c3"), (TextColor)NamedTextColor.GOLD));
        }
    }

    public ItemStack create(ShopItem entry, Language l) {
        String texture = this.texture(entry);
        ItemStack stack = ItemStack.of((Material)(texture == null ? entry.icon() : Material.PLAYER_HEAD), (int)entry.amount());
        ItemMeta meta = stack.getItemMeta();
        if (texture != null && meta instanceof SkullMeta) {
            SkullMeta skull = (SkullMeta)meta;
            PlayerProfile profile = Bukkit.createProfile((UUID)UUID.randomUUID());
            profile.setProperty(new ProfileProperty("textures", texture));
            skull.setPlayerProfile(profile);
            meta = skull;
        }
        meta.displayName(Component.text((String)entry.name(l == Language.PT), (TextColor)NamedTextColor.LIGHT_PURPLE).decoration(TextDecoration.ITALIC, false));
        meta.lore(List.of((TextComponent)Component.text((String)this.description(entry, l == Language.PT), (TextColor)NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, false), (TextComponent)Component.text((String)(l.choose("Item especial \u2022 ", "Special item \u2022 ") + this.economy.format(entry.price()) + " \u26c3"), (TextColor)NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false)));
        meta.getPersistentDataContainer().set(this.itemKey, PersistentDataType.STRING, (Object)entry.name());
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        stack.setItemMeta(meta);
        return stack;
    }

    private String texture(ShopItem entry) {
        return switch (entry) {
            case ShopItem.SUPREME_FOOD -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvZGU3Mzg1ZTliMjI1YzUzMjUyYjk4NzNmZDlmM2M1MjkyY2ZmNTY3NWMzZjY5MzJiMTY3YjBmNjA4NjQxZWUxZiJ9fX0=";
            case ShopItem.BOMB -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvNzEzYjc4YjUyMDc4MWI1OTcwYzRlMTE3Mjc0ZTkzNjNlNjY3MjFlZmQ3YmJlMTk1OWUyNjZiOThlMzc3NTljZSJ9fX0=";
            case ShopItem.METEOR -> "eyJ0ZXh0dXJlcyI6eyJTS0lOIjp7InVybCI6Imh0dHA6Ly90ZXh0dXJlcy5taW5lY3JhZnQubmV0L3RleHR1cmUvN2IwODJhOTFjZjRkM2M2YThjNmM5YjQwNzQzZmMwNzlhY2JhYWE0YzczMDM0YTQ3Mjc0MzA4NzIyY2QxZmNiOSJ9fX0=";
            default -> null;
        };
    }

    public ShopItem type(ItemStack stack) {
        if (stack == null || !stack.hasItemMeta()) {
            return null;
        }
        String id = (String)stack.getItemMeta().getPersistentDataContainer().get(this.itemKey, PersistentDataType.STRING);
        if (id == null) {
            return null;
        }
        try {
            return ShopItem.valueOf(id);
        }
        catch (IllegalArgumentException ignored) {
            return null;
        }
    }

    public boolean isSpecial(ItemStack stack) {
        return this.type(stack) != null;
    }

    public void refreshSaleLoreNextTick(Player p, Inventory inventory) {
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            for (ItemStack stack : p.getInventory().getContents()) {
                if (stack == null) continue;
                this.clearSalePreview(stack);
            }
            if (this.views.get(p.getUniqueId()) == View.SELL) {
                this.refreshSaleLore(inventory);
            }
        });
    }

    private void refreshSaleLore(Inventory inventory) {
        for (ItemStack stack : inventory.getContents()) {
            ItemMeta meta;
            if (stack == null || stack.getType().isAir() || !stack.hasItemMeta() || (meta = stack.getItemMeta()).getPersistentDataContainer().has(this.salePreviewKey, PersistentDataType.BYTE)) continue;
            ArrayList<Object> lore = new ArrayList<Object>(Objects.requireNonNullElse(meta.lore(), List.of()));
            int value = this.isSpecial(stack) ? 0 : this.saleValue(stack);
            lore.add(Component.empty());
            lore.add(Component.text((String)(value <= 0 ? "N\u00e3o pode ser vendido" : "Valor de venda: " + this.economy.format((long)value * (long)stack.getAmount()) + " \u26c3"), (TextColor)(value <= 0 ? NamedTextColor.RED : NamedTextColor.GOLD)).decoration(TextDecoration.ITALIC, false));
            meta.lore(lore);
            meta.getPersistentDataContainer().set(this.salePreviewKey, PersistentDataType.BYTE, (Object)1);
            stack.setItemMeta(meta);
        }
    }

    private void clearSalePreview(ItemStack stack) {
        if (!stack.hasItemMeta()) {
            return;
        }
        ItemMeta meta = stack.getItemMeta();
        if (!meta.getPersistentDataContainer().has(this.salePreviewKey, PersistentDataType.BYTE)) {
            return;
        }
        ArrayList lore = new ArrayList(Objects.requireNonNullElse(meta.lore(), List.of()));
        if (!lore.isEmpty()) {
            lore.remove(lore.size() - 1);
        }
        if (!lore.isEmpty()) {
            lore.remove(lore.size() - 1);
        }
        meta.lore(lore);
        meta.getPersistentDataContainer().remove(this.salePreviewKey);
        stack.setItemMeta(meta);
    }

    private ItemStack preview(ShopItem e, Language l) {
        ItemStack stack = this.create(e, l);
        ItemMeta meta = stack.getItemMeta();
        ArrayList<Object> lore = new ArrayList<Object>(Objects.requireNonNullElse(meta.lore(), List.of()));
        lore.add(Component.empty());
        lore.add(Component.text((String)l.choose("Clique para comprar", "Click to purchase"), (TextColor)NamedTextColor.YELLOW).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore);
        stack.setItemMeta(meta);
        return stack;
    }

    private String description(ShopItem e, boolean pt) {
        return switch (e) {
            default -> throw new MatchException(null, null);
            case ShopItem.SUPREME_FOOD -> {
                if (pt) {
                    yield "Comida infinita; 30s de recarga.";
                }
                yield "Infinite food; 30s cooldown.";
            }
            case ShopItem.ANCESTRAL_ARROW -> {
                if (pt) {
                    yield "Causa dano aumentado, exceto em chefes.";
                }
                yield "Deals increased damage, except to bosses.";
            }
            case ShopItem.MACHINE_BOW -> {
                if (pt) {
                    yield "Rajada r\u00e1pida; perde muita durabilidade.";
                }
                yield "Rapid burst; loses durability quickly.";
            }
            case ShopItem.FLYING_BOOTS -> {
                if (pt) {
                    yield "Permite planar por 5 segundos durante quedas.";
                }
                yield "Glide for 5 seconds while falling.";
            }
            case ShopItem.IMMORTAL_CHEST -> {
                if (pt) {
                    yield "\u00c9 consumido para impedir uma morte.";
                }
                yield "Consumed to prevent death.";
            }
            case ShopItem.HEAVY_BOOTS -> {
                if (pt) {
                    yield "Converte queda alta em dano de impacto.";
                }
                yield "Converts high falls into impact damage.";
            }
            case ShopItem.BOMB -> {
                if (pt) {
                    yield "Explosivo arremess\u00e1vel e consum\u00edvel.";
                }
                yield "Throwable consumable explosive.";
            }
            case ShopItem.FLAMETHROWER -> {
                if (pt) {
                    yield "Projeta uma rajada de fogo.";
                }
                yield "Projects a burst of fire.";
            }
            case ShopItem.METEOR -> {
                if (pt) {
                    yield "Impacto destrutivo que corrompe o terreno.";
                }
                yield "Destructive impact that corrupts terrain.";
            }
            case ShopItem.LIFESTEAL_DAGGER -> {
                if (pt) {
                    yield "Alcance curto; cura 100% do dano causado.";
                }
                yield "Short range; heals 100% of damage dealt.";
            }
            case ShopItem.PORTAL -> {
                if (pt) {
                    yield "Duas \u00e2ncoras que ligam dois lugares.";
                }
                yield "Two anchors linking two locations.";
            }
            case ShopItem.SAFE_BEACON -> {
                if (pt) {
                    yield "Impede mobs hostis num raio de 16 blocos.";
                }
                yield "Prevents hostile mobs in a 16-block radius.";
            }
            case ShopItem.SOLAR_ARROW -> {
                if (pt) {
                    yield "Fogo solar persistente; cuidado com o disparo.";
                }
                yield "Persistent solar fire; handle carefully.";
            }
            case ShopItem.LIGHTNING_PRISON -> {
                if (pt) {
                    yield "Invoca 5 raios priorizando inimigos.";
                }
                yield "Summons 5 strikes prioritizing enemies.";
            }
            case ShopItem.WITHER_COATING -> {
                if (pt) {
                    yield "Aplica Wither permanentemente a uma espada.";
                }
                yield "Permanently adds Wither to a sword.";
            }
            case ShopItem.BLEEDING_DAGGER -> {
                if (pt) {
                    yield "Arremess\u00e1vel; aplica Sangramento III.";
                }
                yield "Throwable; applies Bleeding III.";
            }
            case ShopItem.EXCAVATOR -> {
                if (pt) {
                    yield "Escava 3x3 por at\u00e9 90 blocos.";
                }
                yield "Digs 3x3 up to 90 blocks deep.";
            }
            case ShopItem.MADNESS_POTION -> {
                if (pt) {
                    yield "Faz inimigos atacarem outros monstros.";
                }
                yield "Makes enemies attack other monsters.";
            }
            case ShopItem.GOD_POTION -> pt ? "10s imune e 30s com efeitos positivos." : "10s immune and 30s of positive effects.";
        };
    }

    public int saleValue(ItemStack s) {
        Material m = s.getType();
        String n = m.name();
        if (m == Material.DIAMOND) {
            return 40;
        }
        if (m == Material.EMERALD) {
            return 12;
        }
        if (m == Material.ANCIENT_DEBRIS || m == Material.NETHERITE_SCRAP) {
            return 100;
        }
        if (m == Material.NETHERITE_INGOT) {
            return 450;
        }
        if (m == Material.GOLD_INGOT) {
            return 8;
        }
        if (m == Material.IRON_INGOT) {
            return 4;
        }
        if (m == Material.COPPER_INGOT) {
            return 2;
        }
        if (m == Material.COAL || m == Material.CHARCOAL) {
            return 1;
        }
        if (n.endsWith("_LOG") || n.endsWith("_PLANKS")) {
            return 1;
        }
        if (n.contains("ORE")) {
            return 3;
        }
        if (m.isEdible()) {
            return 2;
        }
        if (n.endsWith("_WOOL") || m == Material.LEATHER || m == Material.STRING || m == Material.BONE || m == Material.ROTTEN_FLESH || m == Material.GUNPOWDER) {
            return 1;
        }
        return 0;
    }

    private void fill(Inventory inv) {
        ItemStack filler = this.item(Material.BLACK_STAINED_GLASS_PANE, " ", List.of());
        for (int i = 0; i < inv.getSize(); ++i) {
            inv.setItem(i, filler);
        }
    }

    private ItemStack item(Material m, String name, List<Component> lore) {
        ItemStack s = ItemStack.of((Material)m);
        ItemMeta meta = s.getItemMeta();
        meta.displayName(Component.text((String)name, (TextColor)NamedTextColor.GOLD).decoration(TextDecoration.ITALIC, false));
        meta.lore(lore.stream().map(c -> c.decoration(TextDecoration.ITALIC, false)).toList());
        meta.addItemFlags(new ItemFlag[]{ItemFlag.HIDE_ATTRIBUTES, ItemFlag.HIDE_ADDITIONAL_TOOLTIP});
        s.setItemMeta(meta);
        return s;
    }

    private static enum View {
        SHOP,
        SELL;

    }
}

