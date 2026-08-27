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
 * &gt;= 1 (just unlocked, not necessarily maxed) and enough Blood Points —
 * nothing else gates it.
 */
public record CombatTreeNode(CombatAbility ability, CombatBranch branch, List<CombatAbility> prerequisites,
                              int maxRank, int tier, Kind kind, int slot) {

    public enum Kind {
        /** Always-on effect once unlocked; can still be toggled off. */
        PASSIVE,
        /** Triggered by a dedicated in-world keybind (e.g. swap-hands for Sword Throw). */
        ACTIVE_KEYBIND,
        /** Triggered by right-clicking the node in the tree menu. */
        ACTIVE_MENU
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
        // ---- Fury (offense) ---- ordered so each node's max-rank payoff is >= the one
        // before it: RUTHLESS_STRIKES (+10% crit, flat) < EXECUTIONER/ARMOR_PIERCER (+25%
        // dmg, both conditional) < BERSERKER (+30% dmg, conditional) < CRITICAL_MASTERY
        // (unconditional crit-damage multiplier, the branch's real finisher).
        register(CombatAbility.RUTHLESS_STRIKES, CombatBranch.FURY, 10, Kind.PASSIVE, 51);
        register(CombatAbility.EXECUTIONER, CombatBranch.FURY, 10, Kind.PASSIVE, 42, CombatAbility.RUTHLESS_STRIKES);
        register(CombatAbility.ARMOR_PIERCER, CombatBranch.FURY, 10, Kind.PASSIVE, 33, CombatAbility.EXECUTIONER);
        register(CombatAbility.BERSERKER, CombatBranch.FURY, 10, Kind.PASSIVE, 24, CombatAbility.ARMOR_PIERCER);
        register(CombatAbility.CRITICAL_MASTERY, CombatBranch.FURY, 10, Kind.PASSIVE, 15, CombatAbility.BERSERKER);

        // ---- Sustain (blood) ----
        register(CombatAbility.VAMPIRISM, CombatBranch.SUSTAIN, 10, Kind.PASSIVE, 49);
        register(CombatAbility.BLOOD_LUST, CombatBranch.SUSTAIN, 10, Kind.PASSIVE, 40, CombatAbility.VAMPIRISM);
        register(CombatAbility.TREASURE_HUNTER, CombatBranch.SUSTAIN, 10, Kind.PASSIVE, 41, CombatAbility.VAMPIRISM);
        register(CombatAbility.UNDYING_WILL, CombatBranch.SUSTAIN, 10, Kind.PASSIVE, 31, CombatAbility.BLOOD_LUST);
        register(CombatAbility.SOUL_HARVEST, CombatBranch.SUSTAIN, 10, Kind.PASSIVE, 22, CombatAbility.UNDYING_WILL);
        register(CombatAbility.SECOND_WIND, CombatBranch.SUSTAIN, 10, Kind.PASSIVE, 13, CombatAbility.SOUL_HARVEST);

        // ---- Utility (precision) ---- root is a minor passive (like every other branch's
        // root), not a full active ability: SWORD_THROW (an active with real burst damage)
        // now costs a tier more than the other branches' cheapest node, same as ARCANE_SLASH
        // and VITAL_TOUCH already do in Synergy.
        register(CombatAbility.HUNTERS_INSTINCT, CombatBranch.UTILITY, 10, Kind.PASSIVE, 47);
        register(CombatAbility.SWORD_THROW, CombatBranch.UTILITY, 10, Kind.ACTIVE_KEYBIND, 38, CombatAbility.HUNTERS_INSTINCT);
        register(CombatAbility.CLEAVE, CombatBranch.UTILITY, 10, Kind.PASSIVE, 29, CombatAbility.SWORD_THROW);
        register(CombatAbility.RELENTLESS, CombatBranch.UTILITY, 10, Kind.PASSIVE, 20, CombatAbility.CLEAVE);

        // ---- Synergy (bridges between branches) ----
        register(CombatAbility.VITAL_TOUCH, CombatBranch.SYNERGY, 10, Kind.ACTIVE_MENU, 39, CombatAbility.VAMPIRISM);
        register(CombatAbility.COMBAT_MASTERY, CombatBranch.SYNERGY, 10, Kind.PASSIVE, 30,
                CombatAbility.BLOOD_LUST, CombatAbility.SWORD_THROW);
        register(CombatAbility.ARCANE_SLASH, CombatBranch.SYNERGY, 10, Kind.ACTIVE_MENU, 34, CombatAbility.EXECUTIONER);

        // ---- Capstone ----
        register(CombatAbility.APEX_WARRIOR, CombatBranch.CAPSTONE, 10, Kind.PASSIVE, 4,
                CombatAbility.CRITICAL_MASTERY, CombatAbility.SECOND_WIND, CombatAbility.RELENTLESS);

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
