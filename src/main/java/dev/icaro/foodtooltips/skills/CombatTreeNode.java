package dev.icaro.foodtooltips.skills;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

/**
 * Static layout/metadata for one node of the combat ability tree: which
 * branch it belongs to, which node(s) must already be unlocked before it
 * becomes purchasable, how many ranks it can be upgraded to, how the player
 * interacts with it, and where it sits in the tree menu (a fixed 54-slot
 * chest inventory).
 *
 * <p>All prerequisites of a node are required to have rank &gt;= 1 (just
 * unlocked, not necessarily maxed) before the node itself can be unlocked.
 */
public record CombatTreeNode(CombatAbility ability, CombatBranch branch, List<CombatAbility> prerequisites,
                              int maxRank, Kind kind, int slot) {

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
        CombatTreeNode node = new CombatTreeNode(ability, branch, List.of(prerequisites), maxRank, kind, slot);
        REGISTRY.put(ability, node);
    }

    static {
        // ---- Fury (offense) ----
        register(CombatAbility.RUTHLESS_STRIKES, CombatBranch.FURY, 5, Kind.PASSIVE, 51);
        register(CombatAbility.EXECUTIONER, CombatBranch.FURY, 5, Kind.PASSIVE, 42, CombatAbility.RUTHLESS_STRIKES);
        register(CombatAbility.BERSERKER, CombatBranch.FURY, 5, Kind.PASSIVE, 33, CombatAbility.EXECUTIONER);
        register(CombatAbility.ARMOR_PIERCER, CombatBranch.FURY, 5, Kind.PASSIVE, 24, CombatAbility.BERSERKER);
        register(CombatAbility.CRITICAL_MASTERY, CombatBranch.FURY, 5, Kind.PASSIVE, 15, CombatAbility.ARMOR_PIERCER);

        // ---- Sustain (blood) ----
        register(CombatAbility.VAMPIRISM, CombatBranch.SUSTAIN, 5, Kind.PASSIVE, 49);
        register(CombatAbility.BLOOD_LUST, CombatBranch.SUSTAIN, 5, Kind.PASSIVE, 40, CombatAbility.VAMPIRISM);
        register(CombatAbility.TREASURE_HUNTER, CombatBranch.SUSTAIN, 5, Kind.PASSIVE, 41, CombatAbility.VAMPIRISM);
        register(CombatAbility.UNDYING_WILL, CombatBranch.SUSTAIN, 5, Kind.PASSIVE, 31, CombatAbility.BLOOD_LUST);
        register(CombatAbility.SOUL_HARVEST, CombatBranch.SUSTAIN, 5, Kind.PASSIVE, 22, CombatAbility.UNDYING_WILL);
        register(CombatAbility.SECOND_WIND, CombatBranch.SUSTAIN, 5, Kind.PASSIVE, 13, CombatAbility.SOUL_HARVEST);

        // ---- Utility (precision) ----
        register(CombatAbility.TELEKINESIS, CombatBranch.UTILITY, 1, Kind.PASSIVE, 47);
        register(CombatAbility.SWORD_THROW, CombatBranch.UTILITY, 5, Kind.ACTIVE_KEYBIND, 38, CombatAbility.TELEKINESIS);
        register(CombatAbility.HUNTERS_INSTINCT, CombatBranch.UTILITY, 5, Kind.PASSIVE, 29, CombatAbility.SWORD_THROW);
        register(CombatAbility.CLEAVE, CombatBranch.UTILITY, 5, Kind.PASSIVE, 20, CombatAbility.HUNTERS_INSTINCT);
        register(CombatAbility.RELENTLESS, CombatBranch.UTILITY, 5, Kind.PASSIVE, 11, CombatAbility.CLEAVE);

        // ---- Synergy (bridges between branches) ----
        register(CombatAbility.VITAL_TOUCH, CombatBranch.SYNERGY, 5, Kind.ACTIVE_MENU, 39, CombatAbility.VAMPIRISM);
        register(CombatAbility.COMBAT_MASTERY, CombatBranch.SYNERGY, 3, Kind.PASSIVE, 30,
                CombatAbility.BLOOD_LUST, CombatAbility.SWORD_THROW);
        register(CombatAbility.ARCANE_SLASH, CombatBranch.SYNERGY, 5, Kind.ACTIVE_MENU, 34, CombatAbility.EXECUTIONER);

        // ---- Capstone ----
        register(CombatAbility.APEX_WARRIOR, CombatBranch.CAPSTONE, 3, Kind.PASSIVE, 4,
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
            for (CombatAbility prereq : node.prerequisites()) {
                CombatTreeNode prereqNode = REGISTRY.get(prereq);
                if (prereqNode == null) {
                    throw new IllegalStateException("Unknown prerequisite " + prereq + " for " + node.ability());
                }
                if (prereqNode.ability().level() >= node.ability().level()) {
                    throw new IllegalStateException("Prerequisite " + prereq + " (level " + prereqNode.ability().level()
                            + ") must require a lower level than " + node.ability() + " (level " + node.ability().level() + ")");
                }
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
