package dev.icaro.foodtooltips.skills;

public record CombatProgress(int level, double xp, double requiredXp) {
    public double fraction() {
        return this.requiredXp <= 0.0 ? 1.0 : Math.min(1.0, this.xp / this.requiredXp);
    }
}

