package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.global.GlobalLevelService;
import dev.icaro.foodtooltips.global.GlobalSkill;
import dev.icaro.foodtooltips.global.GlobalXpSource;
import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.mining.BuriedTreasureService;
import dev.icaro.foodtooltips.mining.MiningCatalog;
import dev.icaro.foodtooltips.mining.MiningEntry;
import dev.icaro.foodtooltips.skills.GeneralSkillService;
import dev.icaro.foodtooltips.skills.SkillProgress;
import dev.icaro.foodtooltips.skills.SkillProgressBarService;
import dev.icaro.foodtooltips.skills.SkillType;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.TextComponent;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.block.Block;
import org.bukkit.block.data.Ageable;
import org.bukkit.block.data.BlockData;
import org.bukkit.enchantments.Enchantment;
import org.bukkit.entity.Entity;
import org.bukkit.entity.HumanEntity;
import org.bukkit.entity.Item;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockBreakEvent;
import org.bukkit.event.block.BlockDropItemEvent;
import org.bukkit.event.block.BlockPlaceEvent;
import org.bukkit.event.enchantment.EnchantItemEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.inventory.InventoryClickEvent;
import org.bukkit.event.player.PlayerFishEvent;
import org.bukkit.event.player.PlayerItemDamageEvent;
import org.bukkit.inventory.BrewerInventory;
import org.bukkit.inventory.ItemStack;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class GeneralSkillListener
implements Listener {
    private final Plugin plugin;
    private final GeneralSkillService skills;
    private final SkillProgressBarService bars;
    private final BuriedTreasureService treasures;
    private final GlobalLevelService global;
    private final Set<String> placed = new HashSet<String>();
    private final Set<UUID> veinActive = new HashSet<UUID>();
    private final Map<String, Target> targets = new HashMap<String, Target>();
    private final Map<UUID, Combo> combos = new HashMap<UUID, Combo>();

    public GeneralSkillListener(Plugin p, GeneralSkillService s, SkillProgressBarService b, GlobalLevelService g) {
        this.plugin = p;
        this.skills = s;
        this.bars = b;
        this.global = g;
        this.treasures = new BuriedTreasureService(p, s);
    }

    @EventHandler(ignoreCancelled=true)
    public void place(BlockPlaceEvent e) {
        this.placed.add(this.key(e.getBlock().getLocation()));
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void vein(BlockBreakEvent e) {
        Player p = e.getPlayer();
        if (this.veinActive.contains(p.getUniqueId()) || this.skills.progress(p, SkillType.MINING).level() < 3 || !MiningCatalog.isOre(e.getBlock().getType())) {
            return;
        }
        List<Block> blocks = this.connected(e.getBlock(), 32);
        if (blocks.isEmpty()) {
            return;
        }
        Bukkit.getScheduler().runTask(this.plugin, () -> {
            this.veinActive.add(p.getUniqueId());
            try {
                for (Block block : blocks) {
                    if (block.getType().isAir() || this.placed.contains(this.key(block.getLocation()))) continue;
                    p.breakBlock(block);
                }
            }
            finally {
                this.veinActive.remove(p.getUniqueId());
            }
        });
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void broken(BlockBreakEvent e) {
        String k = this.key(e.getBlock().getLocation());
        if (this.placed.remove(k)) {
            return;
        }
        Player p = e.getPlayer();
        Material m = e.getBlock().getType();
        MiningCatalog.find(m).ifPresent(x -> {
            int haste;
            int before = this.skills.miningMilestones(p, m);
            GeneralSkillService.MiningRecord record = this.skills.recordMined(p, m);
            int combo = this.nextCombo(p);
            double depth = e.getBlock().getY() < 0 ? 1.15 : (e.getBlock().getY() < 32 ? 1.08 : 1.0);
            this.gain(p, SkillType.MINING, MiningCatalog.isStone(m) ? 1.0 : x.skillXp() * depth * (1.0 + (double)Math.min(25, combo) * 0.01));
            if (record.commissionCompleted()) {
                this.gain(p, SkillType.MINING, 500.0);
            }
            this.treasures.tryFind(p, e.getBlock().getLocation(), xp -> this.gain(p, SkillType.MINING, xp));
            if (this.skills.miningMilestones(p, m) > before) {
                long reward = this.global.creditMilestones(p, "mining", this.skills.totalMiningMilestones(p), GlobalXpSource.MINING_MILESTONE);
                p.sendMessage(((TextComponent)Component.text((String)("\u2726 " + Language.of(p).choose("MILESTONE DE MINERA\u00c7\u00c3O! ", "MINING MILESTONE! ") + record.count() + " \u00d7 "), (TextColor)NamedTextColor.GOLD).append((Component)Component.translatable((String)m.translationKey()))).append((Component)Component.text((String)(" \u2022 +" + reward + " " + Language.of(p).choose("XP de N\u00edvel Global", "Global Level XP")), (TextColor)NamedTextColor.AQUA)));
            }
            if ((haste = Math.min(4, this.skills.progress(p, SkillType.MINING).level() / 40)) > 0) {
                p.addPotionEffect(new PotionEffect(PotionEffectType.HASTE, 60, haste - 1, false, false, false));
            }
            if (MiningCatalog.isOre(m) && !p.getInventory().getItemInMainHand().containsEnchantment(Enchantment.SILK_TOUCH)) {
                this.targets.put(k, new Target(SkillType.MINING, x.drop()));
            }
        });
        if (this.isLog(m)) {
            this.gain(p, SkillType.FORAGING, this.logXp(m));
            this.targets.put(k, new Target(SkillType.FORAGING, m));
        } else {
            Ageable a;
            BlockData blockData = e.getBlock().getBlockData();
            if (blockData instanceof Ageable && (a = (Ageable)blockData).getAge() == a.getMaximumAge()) {
                this.gain(p, SkillType.FARMING, this.cropXp(m));
                this.targets.put(k, new Target(SkillType.FARMING, this.cropDrop(m)));
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void drops(BlockDropItemEvent e) {
        Target t = this.targets.remove(this.key(e.getBlock().getLocation()));
        if (t == null) {
            return;
        }
        int fortune = this.skills.fortune(e.getPlayer(), t.skill);
        int copies = fortune / 100 + (ThreadLocalRandom.current().nextInt(100) < fortune % 100 ? 1 : 0);
        for (Item entity : new ArrayList(e.getItems())) {
            ItemStack base = entity.getItemStack();
            if (base.getType() != t.drop) continue;
            int extra = base.getAmount() * copies;
            int max = base.getMaxStackSize();
            int add = Math.min(extra, max - base.getAmount());
            base.setAmount(base.getAmount() + add);
            entity.setItemStack(base);
            extra -= add;
            while (extra > 0) {
                ItemStack overflow = base.clone();
                overflow.setAmount(Math.min(max, extra));
                e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), overflow);
                extra -= overflow.getAmount();
            }
        }
        if (t.skill == SkillType.MINING && this.skills.progress(e.getPlayer(), SkillType.MINING).level() >= 1) {
            for (Item item : new ArrayList(e.getItems())) {
                for (ItemStack overflow : e.getPlayer().getInventory().addItem(new ItemStack[]{item.getItemStack()}).values()) {
                    e.getBlock().getWorld().dropItemNaturally(e.getBlock().getLocation(), overflow);
                }
                item.remove();
            }
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void defense(EntityDamageEvent e) {
        Entity entity = e.getEntity();
        if (entity instanceof Player) {
            Player p = (Player)entity;
            e.setDamage(e.getDamage() * (1.0 - this.skills.damageReduction(p)));
        }
    }

    @EventHandler(priority=EventPriority.HIGHEST, ignoreCancelled=true)
    public void careful(PlayerItemDamageEvent e) {
        if (e.getItem().getType().name().endsWith("_PICKAXE") && ThreadLocalRandom.current().nextDouble(100.0) < this.skills.carefulChance(e.getPlayer())) {
            e.setCancelled(true);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void fish(PlayerFishEvent e) {
        if (e.getState() == PlayerFishEvent.State.CAUGHT_FISH) {
            this.gain(e.getPlayer(), SkillType.FISHING, e.getCaught() instanceof Item ? 18.0 : 10.0);
        }
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void enchant(EnchantItemEvent e) {
        this.gain(e.getEnchanter(), SkillType.ENCHANTING, Math.max(5, e.getExpLevelCost() * 4));
    }

    @EventHandler(priority=EventPriority.MONITOR, ignoreCancelled=true)
    public void potion(InventoryClickEvent e) {
        Player p;
        block8: {
            block7: {
                HumanEntity humanEntity = e.getWhoClicked();
                if (!(humanEntity instanceof Player)) break block7;
                p = (Player)humanEntity;
                if (e.getInventory() instanceof BrewerInventory && e.getRawSlot() >= 0 && e.getRawSlot() <= 2) break block8;
            }
            return;
        }
        switch (e.getAction()) {
            case PICKUP_ALL: 
            case PICKUP_HALF: 
            case PICKUP_ONE: 
            case PICKUP_SOME: 
            case MOVE_TO_OTHER_INVENTORY: 
            case HOTBAR_SWAP: 
            case HOTBAR_MOVE_AND_READD: {
                break;
            }
            default: {
                return;
            }
        }
        ItemStack i = e.getCurrentItem();
        if (i != null && i.getType().name().contains("POTION")) {
            this.gain(p, SkillType.ALCHEMY, 12.0);
        }
    }

    private List<Block> connected(Block origin, int limit) {
        Material drop = MiningCatalog.find(origin.getType()).orElseThrow().drop();
        ArrayList<Block> out = new ArrayList<Block>();
        HashSet<String> seen = new HashSet<String>();
        ArrayDeque<Block> queue = new ArrayDeque<Block>();
        queue.add(origin);
        seen.add(this.key(origin.getLocation()));
        block0: while (!queue.isEmpty() && out.size() < limit) {
            Block current = (Block)queue.removeFirst();
            for (int x = -1; x <= 1; ++x) {
                for (int y = -1; y <= 1; ++y) {
                    for (int z = -1; z <= 1; ++z) {
                        Optional<MiningEntry> entry;
                        Block next;
                        String k;
                        if (x == 0 && y == 0 && z == 0 || !seen.add(k = this.key((next = current.getRelative(x, y, z)).getLocation())) || this.placed.contains(k) || !(entry = MiningCatalog.find(next.getType())).isPresent() || entry.get().drop() != drop) continue;
                        out.add(next);
                        queue.add(next);
                        if (out.size() >= limit) continue block0;
                    }
                }
            }
        }
        return out;
    }

    private int nextCombo(Player p) {
        long now = System.currentTimeMillis();
        Combo old = this.combos.get(p.getUniqueId());
        int count = old != null && now - old.time < 3000L ? old.count + 1 : 1;
        this.combos.put(p.getUniqueId(), new Combo(now, count));
        return count;
    }

    private void gain(Player p, SkillType t, double xp) {
        SkillProgress before = this.skills.progress(p, t);
        int levels = this.skills.addXp(p, t, xp);
        SkillProgress after = this.skills.progress(p, t);
        this.bars.show(p, t, xp, after, this.skills.maxLevel());
        if (levels > 0) {
            long reward = this.global.creditSkillLevels(p, GlobalSkill.of(t), before.level(), after.level());
            Language l = Language.of(p);
            p.sendMessage((Component)Component.text((String)("\u2726 " + t.name(l == Language.PT).toUpperCase(Locale.ROOT) + " " + l.choose("SUBIU DE N\u00cdVEL! ", "LEVEL UP! ") + before.level() + " \u2192 " + after.level() + " \u2022 +" + reward + " " + l.choose("XP de N\u00edvel Global", "Global Level XP")), (TextColor)NamedTextColor.GOLD));
        }
    }

    private boolean isLog(Material m) {
        return m.name().endsWith("_LOG") || m.name().endsWith("_STEM") || m.name().endsWith("_HYPHAE");
    }

    private double logXp(Material m) {
        return m.name().contains("CRIMSON") || m.name().contains("WARPED") ? 8.0 : 5.0;
    }

    private double cropXp(Material m) {
        return switch (m) {
            case Material.WHEAT, Material.CARROTS, Material.POTATOES, Material.BEETROOTS -> 4.0;
            case Material.NETHER_WART -> 6.0;
            case Material.COCOA, Material.SWEET_BERRY_BUSH -> 5.0;
            default -> 3.0;
        };
    }

    private Material cropDrop(Material m) {
        return switch (m) {
            case Material.WHEAT -> Material.WHEAT;
            case Material.CARROTS -> Material.CARROT;
            case Material.POTATOES -> Material.POTATO;
            case Material.BEETROOTS -> Material.BEETROOT;
            case Material.NETHER_WART -> Material.NETHER_WART;
            case Material.COCOA -> Material.COCOA_BEANS;
            case Material.SWEET_BERRY_BUSH -> Material.SWEET_BERRIES;
            default -> m;
        };
    }

    private String key(Location l) {
        return String.valueOf(l.getWorld().getUID()) + ":" + l.getBlockX() + ":" + l.getBlockY() + ":" + l.getBlockZ();
    }

    private record Target(SkillType skill, Material drop) {
    }

    private record Combo(long time, int count) {
    }
}

