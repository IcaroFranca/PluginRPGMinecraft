package dev.icaro.foodtooltips.global;

import dev.icaro.foodtooltips.skills.SkillType;

public enum GlobalSkill {
    COMBAT,
    FARMING,
    FISHING,
    MINING,
    FORAGING,
    ENCHANTING,
    ALCHEMY;


    public static GlobalSkill of(SkillType type) {
        return GlobalSkill.valueOf(type.name());
    }
}

