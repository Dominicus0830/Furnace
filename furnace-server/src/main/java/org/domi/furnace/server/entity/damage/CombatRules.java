// 바닐라 CombatRules + EnchantmentHelper.getDamageProtection: 몹·플레이어가 같이 쓰는 피해 경감 식.
package org.domi.furnace.server.entity.damage;

import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemTools;

public final class CombatRules {

    private CombatRules() {}

    /** CombatRules.getDamageAfterAbsorb(방어력·강인함). 차이: 무기 아이템의 modifyArmorEffectiveness(철퇴 관통)는 아직 없다. */
    public static float getDamageAfterAbsorb(float damage, float armor, float toughness) {
        float f = 2.0F + toughness / 4.0F;
        float f1 = Math.clamp(armor - damage / f, armor * 0.2F, 20.0F);
        return damage * (1.0F - f1 / 25.0F);
    }

    /** CombatRules.getDamageAfterMagicAbsorb: 보호량 0~20 으로 자르고 1 보호당 4%. */
    public static float getDamageAfterMagicAbsorb(float damage, float protection) {
        float f = Math.clamp(protection, 0.0F, 20.0F);
        return damage * (1.0F - f / 25.0F);
    }

    /** 저항 효과(LivingEntity.getDamageAfterMagicAbsorb 앞부분): 단계당 20%. */
    public static float afterResistance(float damage, int amplifier) {
        int i = (amplifier + 1) * 5;
        int j = 25 - i;
        return Math.max(damage * j / 25.0F, 0.0F);
    }

    /**
     * EnchantmentHelper.getDamageProtection: 방어구 칸(slots armor = EquipmentSlot.isArmor: 머리·가슴·다리·발·BODY)마다 minecraft:damage_protection 효과를 더한다.
     * 1.21.1 데이터(enchantment/*.json): 모두 linear(base=b, per_level=b) 이고 조건은 bypasses_invulnerability 가 아닐 것 +
     * protection 1·is_fire 2(fire_protection)·is_explosion 2(blast)·is_projectile 2(projectile)·is_fall 3(feather_falling).
     * 가정: 데이터팩으로 바뀐 인챈트 정의는 반영하지 않는다.
     */
    public static float damageProtection(String damageType, ItemStack... armor) {
        if (DamageType.keyHasTag(damageType, "bypasses_invulnerability")) return 0.0F;
        boolean fire = DamageType.keyHasTag(damageType, "is_fire"), explosion = DamageType.keyHasTag(damageType, "is_explosion");
        boolean projectile = DamageType.keyHasTag(damageType, "is_projectile"), fall = DamageType.keyHasTag(damageType, "is_fall");
        float sum = 0.0F;
        for (ItemStack s : armor) {
            if (s == null || s.isEmpty()) continue;
            sum += ItemTools.enchantmentLevel(s, "minecraft:protection");
            if (fire) sum += 2 * ItemTools.enchantmentLevel(s, "minecraft:fire_protection");
            if (explosion) sum += 2 * ItemTools.enchantmentLevel(s, "minecraft:blast_protection");
            if (projectile) sum += 2 * ItemTools.enchantmentLevel(s, "minecraft:projectile_protection");
            if (fall) sum += 3 * ItemTools.enchantmentLevel(s, "minecraft:feather_falling");
        }
        return sum;
    }
}
