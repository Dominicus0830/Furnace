// 바닐라 HoglinBase: 호글린·조글린의 공통 공격 — 공격력 피해를 주고, 어른이면 대상을 크게 날려 보낸다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.entity.ai.control.Mth;

public interface HoglinBase {
    int ATTACK_ANIMATION_DURATION = 10;

    int getAttackAnimationRemainingTicks();

    static boolean hurtAndThrowTarget(Mob attacker, Targetable target) {
        float f = (float) attacker.attributeValue(Attribute.ATTACK_DAMAGE);
        float f1;
        if (!attacker.isBaby() && (int) f > 0) f1 = f / 2.0F + attacker.level().random().nextInt((int) f);
        else f1 = f;
        boolean hurt = target.hurtByMob(new Entity.Damage("mob_attack", false, false, false), f1, attacker);
        if (hurt && !attacker.isBaby()) throwTarget(attacker, target);
        return hurt;
    }

    static void throwTarget(Mob attacker, Targetable target) {
        double d = attacker.attributeValue(Attribute.ATTACK_KNOCKBACK);
        double d1 = target instanceof LivingEntity le ? le.attributeValue(Attribute.KNOCKBACK_RESISTANCE) : 0.0;
        double d2 = d - d1;
        if (d2 > 0.0) {
            double dx = target.x() - attacker.x(), dz = target.z() - attacker.z();
            var r = attacker.level().random();
            float angle = (float) (r.nextInt(21) - 10);
            double d3 = d2 * (r.nextFloat() * 0.5F + 0.2F);
            double len = Math.sqrt(dx * dx + dz * dz);
            double nx = len < 1.0E-5F ? 0.0 : dx / len * d3, nz = len < 1.0E-5F ? 0.0 : dz / len * d3;
            float cos = Mth.cos(angle), sin = Mth.sin(angle);
            double rx = nx * cos + nz * sin, rz = nz * cos - nx * sin;
            double d4 = d2 * r.nextFloat() * 0.5;
            target.addDeltaMovement(rx, d4, rz);
            if (target instanceof Entity e) e.markHurt();
        }
    }
}
