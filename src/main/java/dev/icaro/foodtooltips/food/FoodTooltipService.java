package dev.icaro.foodtooltips.food;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import io.papermc.paper.datacomponent.DataComponentTypes;
import io.papermc.paper.datacomponent.item.FoodProperties;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import net.kyori.adventure.text.format.TextDecoration;
import net.kyori.adventure.text.serializer.plain.PlainTextComponentSerializer;
import org.bukkit.entity.Player;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.ItemMeta;

public final class FoodTooltipService {
    private static final PlainTextComponentSerializer P = PlainTextComponentSerializer.plainText();
    private final GeneralSkillService skills = new GeneralSkillService();

    public boolean update(ItemStack item, Language l, Player p) {
        FoodProperties food = (FoodProperties)item.getData(DataComponentTypes.FOOD);
        boolean pickaxe = item.getType().name().endsWith("_PICKAXE");
        if (food == null && !pickaxe) {
            return false;
        }
        ItemMeta meta = item.getItemMeta();
        List original = Objects.requireNonNullElse(meta.lore(), List.of());
        ArrayList<Component> lore = new ArrayList<Component>(original);
        this.clean(lore);
        if (food != null) {
            lore.add((Component)Component.empty());
            lore.add(this.line(l.choose("Atributos do alimento:", "Food attributes:"), NamedTextColor.GOLD));
            lore.add(this.line("\ud83c\udf57 " + l.choose("Fome", "Hunger") + ": +" + this.n(food.nutrition()) + " (" + this.n((double)food.nutrition() / 2.0) + " \ud83c\udf57)", NamedTextColor.GREEN));
            lore.add(this.line("\u2726 " + l.choose("Satura\u00e7\u00e3o", "Saturation") + ": +" + this.n(food.saturation()) + " (" + this.n((double)food.saturation() / 2.0) + " \u2726)", NamedTextColor.AQUA));
        }
        if (pickaxe) {
            lore.add((Component)Component.empty());
            lore.add(this.line(l.choose("Atributos de minera\u00e7\u00e3o:", "Mining attributes:"), NamedTextColor.GOLD));
            lore.add(this.line("\u26cf Mining Speed: " + this.skills.miningSpeed(p, item.getType()), NamedTextColor.AQUA));
        }
        if (lore.equals(original)) {
            return false;
        }
        meta.lore(lore);
        item.setItemMeta(meta);
        return true;
    }

    private void clean(List<Component> lore) {
        int i = 0;
        while (i < lore.size()) {
            boolean header;
            String s = P.serialize(lore.get(i));
            boolean bl = header = s.equals("Atributos do alimento:") || s.equals("Food attributes:") || s.equals("Atributos de minera\u00e7\u00e3o:") || s.equals("Mining attributes:");
            if (!header) {
                ++i;
                continue;
            }
            int lines = s.contains("alimento") || s.equals("Food attributes:") ? 3 : 2;
            int from = i > 0 && P.serialize(lore.get(i - 1)).isEmpty() ? i - 1 : i;
            lore.subList(from, Math.min(lore.size(), i + lines)).clear();
            i = Math.max(0, from - 1);
        }
    }

    private Component line(String s, NamedTextColor c) {
        return Component.text((String)s, (TextColor)c).decoration(TextDecoration.ITALIC, false);
    }

    private String n(double v) {
        return BigDecimal.valueOf(v).setScale(2, RoundingMode.HALF_UP).stripTrailingZeros().toPlainString();
    }
}

