// 바닐라 MaceItem: 스매시 조건(낙하 > 1.5, 겉날개 비행 아님)·낙하 거리 기반 추가 피해·착지 충격파 knockback.
// 밀도(density)의 낙하 1칸당 +0.5×레벨(smash_damage_per_fallen_block)은 여기서 합산한다.
// 소유권: 플레이어를 때리는 region 스레드에서만 부른다(주변 몹·플레이어를 같은 region 에서 민다).
// 차이: 착지 추가 입자(setSpawnExtraParticlesOnFall)와 낙하-폭발 발전 과제(FALL_AFTER_EXPLOSION)는 아직 없다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.attribute.Attribute;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.net.protocol.play.SetEntityMotion;

public final class MaceSmash {
    private MaceSmash() {}

    private static final EquipmentSlot[] ARMOR = {EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET};

    public static boolean isMace(ItemStack s) { return !s.isEmpty() && ItemTools.itemName(s).equals("minecraft:mace"); }

    /** MaceItem.canSmashAttack(attacker): 낙하 거리 > 1.5 이고 겉날개 비행 중이 아니다. */
    public static boolean canSmash(Player p) { return p.vitals().fallDistance() > 1.5F && !p.isFallFlying(); }

    /** 낙하 거리 f 의 기본 보너스: ≤3 → 4f, ≤8 → 12 + 2(f-3), 그 위 → 22 + f - 8. */
    static float baseBonus(float f) {
        if (f <= 3.0F) return 4.0F * f;
        if (f <= 8.0F) return 12.0F + 2.0F * (f - 3.0F);
        return 22.0F + f - 8.0F;
    }

    /** smash_damage_per_fallen_block: 밀도 레벨 L 이면 0.5F + 0.5F × (L-1). */
    static float densityPerBlock(ItemStack weapon) {
        int l = ItemTools.enchantmentLevel(weapon, "minecraft:density");
        return l > 0 ? 0.5F + 0.5F * (l - 1) : 0.0F;
    }

    /** MaceItem.getAttackDamageBonus(target, damage, source): 철퇴가 아니거나 스매시 조건이 아니면 0. */
    public static float attackDamageBonus(Player p, ItemStack weapon) {
        if (!isMace(weapon) || !canSmash(p)) return 0.0F;
        float f = p.vitals().fallDistance();
        return baseBonus(f) + Math.max(0.0F, densityPerBlock(weapon)) * f;
    }

    /**
     * MaceItem.hurtEnemy (플레이어가 맞힌 뒤): 충격 맥락을 잡고 위로 0.01 속도를 보내며 소리를 내고 주변을 민다.
     */
    public static void hurtEnemy(PlayerMelee.World w, Player p, ItemStack weapon, Targetable target) {
        if (!isMace(weapon) || !canSmash(p)) return;
        ChunkLevel level = w.level();
        var v = p.vitals();
        double[] pos = {p.x(), p.y(), p.z()};
        double[] cur = v.currentImpulsePos();
        if (v.isIgnoringFallDamageFromCurrentImpulse() && cur != null) {
            if (cur[1] > p.y()) v.setCurrentImpulsePos(pos);
        } else {
            v.setCurrentImpulsePos(pos);
        }
        v.setIgnoreFallDamageFromCurrentImpulse(true);
        double[] mv = p.knownMovement();
        p.send(new SetEntityMotion(p.id(), mv[0], (double) 0.01F, mv[2]));   // 속도 Y 를 0.01 로
        String sound;
        if (onGroundOf(target)) {
            sound = v.fallDistance() > 5.0F ? "minecraft:item.mace.smash_ground_heavy" : "minecraft:item.mace.smash_ground";
        } else {
            sound = "minecraft:item.mace.smash_air";
        }
        if (level != null) level.playSound(null, p.x(), p.y(), p.z(), sound, "players", 1.0F, 1.0F);
        knockback(w, p, target);
    }

    private static boolean onGroundOf(Targetable t) {
        if (t instanceof Entity e) return e.onGround();
        if (t instanceof Player pl) return pl.onGround();
        return false;
    }

    /** MaceItem.knockback: 대상 상자를 3.5 넓힌 안, 대상에서 3.5 이내의 살아 있는 것들을 대상 기준 바깥쪽으로 민다. */
    static void knockback(PlayerMelee.World w, Player p, Targetable target) {
        ChunkLevel level = w.level();
        if (level == null) return;
        level.levelEvent(null, 2013, new Pos((int) Math.floor(p.x()), (int) Math.floor(p.y() - 1.0E-5), (int) Math.floor(p.z())), 750);
        AABB box = target.boundingBox().inflate(3.5);
        boolean heavy = p.vitals().fallDistance() > 5.0F;
        for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, e -> eligible(p, target, e))) {
            push(le, le.x() - target.x(), le.y() - target.y(), le.z() - target.z(), heavy, le.attributeValue(Attribute.KNOCKBACK_RESISTANCE));
        }
        for (Player o : w.players()) {
            if (o == p || o == target || !o.isAlive() || o.vitals().isSpectator() || !o.boundingBox().intersects(box)) continue;
            double dx = target.x() - o.x(), dy = target.y() - o.y(), dz = target.z() - o.z();
            if (dx * dx + dy * dy + dz * dz > 3.5 * 3.5) continue;
            push(o, o.x() - target.x(), o.y() - target.y(), o.z() - target.z(), heavy, armorKnockbackResistance(o));
        }
    }

    /** 살아 있는 몹 쪽 대상 조건: 대상 자신 아님·주인이 나인 길들인 동물 아님·표식 거치대 아님·거리 ≤ 3.5. */
    private static boolean eligible(Player p, Targetable target, LivingEntity e) {
        if (e == target) return false;
        if (e instanceof TamableAnimal t && t.isTame() && p.uuid().equals(t.getOwnerUUID())) return false;
        if (e instanceof ArmorStand as && as.isMarker()) return false;
        double dx = target.x() - e.x(), dy = target.y() - e.y(), dz = target.z() - e.z();
        return dx * dx + dy * dy + dz * dz <= 3.5 * 3.5;
    }

    /** 플레이어는 속성 시스템이 없어 방어구 칸의 knockback_resistance 합(0~1)으로 본다. */
    private static double armorKnockbackResistance(Player o) {
        float sum = 0.0F;
        String[] names = {"head", "chest", "legs", "feet"};
        for (int i = 0; i < 4; i++) sum += ItemTools.attributeBonus(o.equipment(ARMOR[i]), "minecraft:generic.knockback_resistance", names[i]);
        return Math.clamp(sum, 0.0F, 1.0F);
    }

    /** 힘 = (3.5 - 거리) × 0.7F × (낙하 > 5 이면 2배) × (1 - 넉백 저항). 양수면 (방향×힘).x, 0.7F, .z 만큼 민다. */
    private static void push(Targetable t, double dx, double dy, double dz, boolean heavy, double resistance) {
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double power = (3.5 - len) * (double) 0.7F * (heavy ? 2 : 1) * (1.0 - resistance);
        if (!(power > 0.0)) return;
        double nx = 0.0, nz = 0.0;
        if (len >= 1.0E-5) { nx = dx / len * power; nz = dz / len * power; }   // Vec3.normalize().scale(power)
        t.addDeltaMovement(nx, (double) 0.7F, nz);
    }
}
