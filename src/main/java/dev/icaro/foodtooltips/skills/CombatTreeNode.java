package dev.icaro.foodtooltips.skills;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Static layout/metadata for one node of the combat ability tree: which
 * branch it belongs to, which node(s) must already be unlocked before it
 * becomes purchasable, how many ranks it can be upgraded to, how deep it
 * sits in the tree (its {@code tier}, used to scale its Blood Points cost),
 * how the player interacts with it, and where it sits in the tree menu (a
 * fixed 54-slot chest inventory).
 *
 * <p>Unlocking a node requires every prerequisite to already have rank
 * &gt;= 1 (just unlocked, not necessarily maxed), enough Blood Points, and
 * a minimum Combat level scaling with {@code tier} (see {@link
 * CombatAbilityService#levelRequirement}).
 */
public record CombatTreeNode(CombatAbility ability, CombatBranch branch, List<CombatAbility> prerequisites,
                              int maxRank, int tier, Kind kind, int slot) {

    public enum Kind {
        /** Always-on effect once unlocked; can still be toggled off. */
        PASSIVE,
        /** Triggered by a dedicated in-world keybind (e.g. swap-hands for Sword Throw). */
        ACTIVE_KEYBIND
    }

    private static final Map<CombatAbility, CombatTreeNode> REGISTRY = new EnumMap<>(CombatAbility.class);

    private static void register(CombatAbility ability, CombatBranch branch, int maxRank, Kind kind, int slot,
                                  CombatAbility... prerequisites) {
        List<CombatAbility> prereqList = List.of(prerequisites);
        int tier = 1;
        for (CombatAbility prereq : prereqList) {
            CombatTreeNode prereqNode = REGISTRY.get(prereq);
            if (prereqNode == null) {
                throw new IllegalStateException("Unknown prerequisite " + prereq + " for " + ability + " (register prerequisites before dependents)");
            }
            tier = Math.max(tier, prereqNode.tier() + 1);
        }
        CombatTreeNode node = new CombatTreeNode(ability, branch, prereqList, maxRank, tier, kind, slot);
        REGISTRY.put(ability, node);
    }

    static {
        // ---- Slot layout ---- column 0 of every row is reserved for the Combat Level
        // gauge (see CombatTreeMenuService#placeLevelIndicators) plus the Back/Reset/
        // Header buttons on the root row, so every node below sits in columns 1-8. Max
        // rank is a function of tier (10/14/18/22/26/32 for tiers 1-6) for every ability
        // node; the 6 Backpack nodes are single-unlock milestones (maxRank 1) instead,
        // since backpack capacity is a step function, not something to grind ranks into.

        // ---- Fury (offense) ---- RUTHLESS_STRIKES (root, flat crit chance) < BERSERKER
        // (conditional damage under 10% HP) < CRITICAL_MASTERY (unconditional crit-damage
        // multiplier, the branch's finisher).
        register(CombatAbility.RUTHLESS_STRIKES, CombatBranch.FURY, 10, Kind.PASSIVE, 47);
        register(CombatAbility.BERSERKER, CombatBranch.FURY, 14, Kind.PASSIVE, 38, CombatAbility.RUTHLESS_STRIKES);
        register(CombatAbility.CRITICAL_MASTERY, CombatBranch.FURY, 18, Kind.PASSIVE, 29, CombatAbility.BERSERKER);

        // ---- Sustain (blood) ---- BLOOD_LUST (root, streak-gated damage) < SOUL_HARVEST
        // (heal on kill + Health Regen) < SECOND_WIND (emergency save + Mending).
        register(CombatAbility.BLOOD_LUST, CombatBranch.SUSTAIN, 10, Kind.PASSIVE, 50);
        register(CombatAbility.SOUL_HARVEST, CombatBranch.SUSTAIN, 14, Kind.PASSIVE, 41, CombatAbility.BLOOD_LUST);
        register(CombatAbility.SECOND_WIND, CombatBranch.SUSTAIN, 18, Kind.PASSIVE, 32, CombatAbility.SOUL_HARVEST);

        // ---- Utility (precision) ---- SWORD_THROW now sits above both Fury and Sustain's
        // 3-tier chains, requiring the top of each: it's the tree's real pinnacle active
        // ability, not a root gated behind a throwaway passive like it used to be.
        register(CombatAbility.SWORD_THROW, CombatBranch.UTILITY, 22, Kind.ACTIVE_KEYBIND, 22,
                CombatAbility.CRITICAL_MASTERY, CombatAbility.SECOND_WIND);

        // ---- Storage ---- the Combat Backpack's 6 capacity levels (9/18/27/36/45/54
        // slots), moved into the tree as its own independent chain: one node per level,
        // single-unlock (maxRank 1), gated by Blood Points + Combat level like everything
        // else here instead of unlocking automatically from raw Combat level. See
        // CombatAbilityService#backpackRank and BackpackService#capacity.
        register(CombatAbility.BACKPACK_1, CombatBranch.STORAGE, 1, Kind.PASSIVE, 52);
        register(CombatAbility.BACKPACK_2, CombatBranch.STORAGE, 1, Kind.PASSIVE, 43, CombatAbility.BACKPACK_1);
        register(CombatAbility.BACKPACK_3, CombatBranch.STORAGE, 1, Kind.PASSIVE, 34, CombatAbility.BACKPACK_2);
        register(CombatAbility.BACKPACK_4, CombatBranch.STORAGE, 1, Kind.PASSIVE, 25, CombatAbility.BACKPACK_3);
        register(CombatAbility.BACKPACK_5, CombatBranch.STORAGE, 1, Kind.PASSIVE, 13, CombatAbility.BACKPACK_4);
        register(CombatAbility.BACKPACK_6, CombatBranch.STORAGE, 1, Kind.PASSIVE, 4, CombatAbility.BACKPACK_5);

        if (REGISTRY.size() != CombatAbility.values().length) {
            throw new IllegalStateException("CombatTreeNode registry is missing entries for some CombatAbility values");
        }
        java.util.Set<Integer> slots = new java.util.HashSet<>();
        for (CombatTreeNode node : REGISTRY.values()) {
            if (node.slot < 0 || node.slot > 53) {
                throw new IllegalStateException("CombatTreeNode slot out of range: " + node.ability());
            }
            if (!slots.add(node.slot)) {
                throw new IllegalStateException("Duplicate CombatTreeNode slot " + node.slot + " for " + node.ability());
            }
        }
    }

    public static CombatTreeNode of(CombatAbility ability) {
        CombatTreeNode node = REGISTRY.get(ability);
        if (node == null) {
            throw new IllegalArgumentException("No tree node registered for " + ability);
        }
        return node;
    }

    public static Map<CombatAbility, CombatTreeNode> all() {
        return REGISTRY;
    }

    public boolean isRoot() {
        return this.prerequisites.isEmpty();
    }
}
