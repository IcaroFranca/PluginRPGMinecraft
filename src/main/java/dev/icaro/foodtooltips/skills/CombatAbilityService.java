package dev.icaro.foodtooltips.skills;

import dev.icaro.foodtooltips.i18n.Language;
import dev.icaro.foodtooltips.skills.CombatAbility;
import dev.icaro.foodtooltips.skills.CombatSkillService;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import net.kyori.adventure.text.format.TextColor;
import org.bukkit.NamespacedKey;
import org.bukkit.attribute.Attribute;
import org.bukkit.attribute.AttributeInstance;
import org.bukkit.entity.LivingEntity;
import org.bukkit.entity.Player;
import org.bukkit.persistence.PersistentDataType;
import org.bukkit.plugin.Plugin;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public final class CombatAbilityService {
    private final Plugin plugin;
    private final CombatSkillService combat;
    private final Map<UUID, Integer> bloodStreak = new HashMap<UUID, Integer>();
    private final Map<UUID, Integer> attackCounter = new HashMap<UUID, Integer>();

    public CombatAbilityService(Plugin p, CombatSkillService c) {
        this.plugin = p;
        this.combat = c;
    }

    public boolean unlocked(Player p, CombatAbility a) {
        return this.combat.progress(p).level() >= a.level();
    }

    public boolean enabled(Player p, CombatAbility a) {
        return this.unlocked(p, a) && (Byte)p.getPersistentDataContainer().getOrDefault(this.key(a), PersistentDataType.BYTE, (Object)1) == 1;
    }

    public boolean toggle(Player p, CombatAbility a) {
        if (!this.unlocked(p, a)) {
            return false;
        }
        boolean next = !this.enabled(p, a);
        p.getPersistentDataContainer().set(this.key(a), PersistentDataType.BYTE, (Object)(next ? (byte)1 : 0));
        return next;
    }

    public double outgoingMultiplier(Player p, LivingEntity target) {
        AttributeInstance armor;
        double multiplier = 1.0;
        if (this.enabled(p, CombatAbility.EXECUTIONER) && target.getHealth() <= CombatAbilityService.maxHealth(target) * 0.25) {
            multiplier *= 1.15;
        }
        if (this.enabled(p, CombatAbility.BLOOD_LUST) && this.bloodStreak.getOrDefault(p.getUniqueId(), 0) >= 10) {
            multiplier *= this.enabled(p, CombatAbility.COMBAT_MASTERY) ? 1.15 : 1.1;
        }
        if (this.enabled(p, CombatAbility.BERSERKER) && p.getHealth() <= CombatAbilityService.maxHealth((LivingEntity)p) * 0.3) {
            multiplier *= 1.2;
        }
        if (this.enabled(p, CombatAbility.ARMOR_PIERCER) && (armor = target.getAttribute(Attribute.ARMOR)) != null && armor.getValue() > 0.0) {
            multiplier *= 1.15;
        }
        if (this.enabled(p, CombatAbility.APEX_WARRIOR)) {
            multiplier *= 1.1;
        }
        return multiplier;
    }

    private static double maxHealth(LivingEntity e) {
        AttributeInstance a = e.getAttribute(Attribute.MAX_HEALTH);
        return a == null ? Math.max(1.0, e.getHealth()) : a.getValue();
    }

    public void hostileKill(Player p) {
        double healing;
        if (this.enabled(p, CombatAbility.HUNTERS_INSTINCT)) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 100, 0));
        }
        if (this.enabled(p, CombatAbility.BLOOD_LUST)) {
            int before = this.bloodStreak.getOrDefault(p.getUniqueId(), 0);
            int after = Math.min(10, before + 1);
            this.bloodStreak.put(p.getUniqueId(), after);
            if (before < 10 && after == 10) {
                p.sendMessage((Component)Component.text((String)("\u2726 " + Language.of(p).choose("SEDE DE SANGUE ATIVADA! Dano +10% at\u00e9 receber um golpe.", "BLOOD LUST ACTIVATED! +10% damage until you are hit.") + " \u2726"), (TextColor)NamedTextColor.RED));
            }
        }
        if ((healing = (double)((this.enabled(p, CombatAbility.VAMPIRISM) ? 1 : 0) + (this.enabled(p, CombatAbility.SOUL_HARVEST) ? 2 : 0))) > 0.0) {
            double max = p.getAttribute(Attribute.MAX_HEALTH).getValue();
            p.setHealth(Math.min(max, p.getHealth() + healing));
        }
    }

    public void hostileHit(Player p) {
        if (this.bloodStreak.remove(p.getUniqueId()) != null && this.enabled(p, CombatAbility.BLOOD_LUST)) {
            p.sendActionBar((Component)Component.text((String)Language.of(p).choose("Sede de Sangue reiniciada", "Blood Lust reset"), (TextColor)NamedTextColor.DARK_RED));
        }
    }

    public boolean guaranteedCritical(Player p) {
        if (!this.enabled(p, CombatAbility.RELENTLESS)) {
            return false;
        }
        int count = this.attackCounter.merge(p.getUniqueId(), 1, (a, b) -> a >= 4 ? 1 : a + 1);
        return count == 4;
    }

    public int bloodStreak(Player p) {
        return this.bloodStreak.getOrDefault(p.getUniqueId(), 0);
    }

    public void clear(Player p) {
        this.bloodStreak.remove(p.getUniqueId());
        this.attackCounter.remove(p.getUniqueId());
    }

    public String description(CombatAbility a, boolean pt) {
        return switch (a) {
            default -> throw new MatchException(null, null);
            case CombatAbility.TELEKINESIS -> {
                if (pt) {
                    yield "Drops e orbes v\u00e3o direto ao invent\u00e1rio.";
                }
                yield "Drops and XP orbs go directly to your inventory.";
            }
            case CombatAbility.EXECUTIONER -> {
                if (pt) {
                    yield "+15% de dano contra alvos abaixo de 25% HP.";
                }
                yield "+15% damage against targets below 25% HP.";
            }
            case CombatAbility.SWORD_THROW -> {
                if (pt) {
                    yield "F arremessa a espada; 50% do dano, 30s de recarga.";
                }
                yield "F throws your sword; 50% damage, 30s cooldown.";
            }
            case CombatAbility.BLOOD_LUST -> {
                if (pt) {
                    yield "Ap\u00f3s 10 abates sem ser atingido: +10% de dano.";
                }
                yield "After 10 kills without being hit: +10% damage.";
            }
            case CombatAbility.VAMPIRISM -> {
                if (pt) {
                    yield "Recupera 1 HP ao matar um mob hostil.";
                }
                yield "Recover 1 HP after killing a hostile mob.";
            }
            case CombatAbility.COMBAT_MASTERY -> {
                if (pt) {
                    yield "Blood Lust vira +15% e o arremesso recarrega em 15s.";
                }
                yield "Blood Lust becomes +15% and sword throw cooldown becomes 15s.";
            }
            case CombatAbility.HUNTERS_INSTINCT -> {
                if (pt) {
                    yield "Recebe Velocidade I por 5s ap\u00f3s um abate hostil.";
                }
                yield "Gain Speed I for 5s after a hostile kill.";
            }
            case CombatAbility.BERSERKER -> {
                if (pt) {
                    yield "+20% de dano quando estiver abaixo de 30% HP.";
                }
                yield "+20% damage while below 30% HP.";
            }
            case CombatAbility.CLEAVE -> {
                if (pt) {
                    yield "20% do dano atinge at\u00e9 3 inimigos pr\u00f3ximos.";
                }
                yield "20% damage hits up to 3 nearby enemies.";
            }
            case CombatAbility.TREASURE_HUNTER -> {
                if (pt) {
                    yield "Recebe 25% mais moedas de mobs.";
                }
                yield "Earn 25% more coins from mobs.";
            }
            case CombatAbility.SECOND_WIND -> {
                if (pt) {
                    yield "Evita um golpe fatal uma vez a cada 3 minutos.";
                }
                yield "Prevent a fatal hit once every 3 minutes.";
            }
            case CombatAbility.ARMOR_PIERCER -> {
                if (pt) {
                    yield "+15% de dano contra alvos com armadura.";
                }
                yield "+15% damage against armored targets.";
            }
            case CombatAbility.CRITICAL_MASTERY -> {
                if (pt) {
                    yield "Dano cr\u00edtico aumentado de 1.5x para 1.75x.";
                }
                yield "Critical damage increased from 1.5x to 1.75x.";
            }
            case CombatAbility.SOUL_HARVEST -> {
                if (pt) {
                    yield "Recupera mais 2 HP por abate hostil.";
                }
                yield "Recover 2 additional HP per hostile kill.";
            }
            case CombatAbility.RELENTLESS -> {
                if (pt) {
                    yield "Cada quarto ataque \u00e9 um cr\u00edtico garantido.";
                }
                yield "Every fourth attack is a guaranteed critical.";
            }
            case CombatAbility.APEX_WARRIOR -> pt ? "+10% de dano final e recargas ofensivas menores." : "+10% final damage and shorter offensive cooldowns.";
        };
    }

    private NamespacedKey key(CombatAbility a) {
        return new NamespacedKey("foodtooltips", "ability_" + a.name().toLowerCase(Locale.ROOT));
    }
}

