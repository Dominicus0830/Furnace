// 플레이어 근접 공격(바닐라 Player.attack): 쿨다운 배율·치명타·질주 넉백·휘두르기 쓸기·공격 인챈트·불 붙이기. 대상은 몹·플레이어 둘 다.
// 쓸기(sweep_attack)·피해 표시(damage_indicator)는 level_particles 로, 치명타(crit)·마법 치명타는 animate 로 보낸다.
// 공격력·공격 속도·이동 속도·휘두르기 비율은 PlayerAttributes(힘·나약함·성급함·피로 포함)로 계산하고, 살충은 느림 IV 를 건다.
// 철퇴: 낙하 보너스·밀도(MaceSmash), 관통(CombatRules), 돌풍·가시(EnchantEffects). TODO(팀): isAlliedTo 는 팀이 없어 늘 거짓.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.item.ItemTools;

public final class PlayerMelee {
    private PlayerMelee() {}

    /** 공격 결과 — 무기 내구도(postHurtEnemy)는 손 사본을 가진 region 이 깎는다. */
    public record Result(boolean hurt, int weaponDamage) {
        static final Result MISS = new Result(false, 0);
    }

    /** 쓸기 대상을 찾는 곳: 엔티티(몹)와 플레이어(엔티티가 아니다). */
    public interface World {
        ChunkLevel level();
        Iterable<Player> players();
    }

    /** getEnchantedDamage(target, base) - base: 날카로움 0.5 × 레벨 + 0.5, 강타(언데드)·살충(절지류) 2.5 × 레벨. */
    static float enchantDamageBonus(ItemStack weapon, Targetable target) {
        float bonus = 0.0F;
        int sharp = ItemTools.enchantmentLevel(weapon, "minecraft:sharpness");
        if (sharp > 0) bonus += 1.0F + 0.5F * (sharp - 1);
        if (target instanceof Entity e) {
            int smite = ItemTools.enchantmentLevel(weapon, "minecraft:smite");
            if (smite > 0 && e.type().is("minecraft:sensitive_to_smite")) bonus += 2.5F * smite;
            int bane = ItemTools.enchantmentLevel(weapon, "minecraft:bane_of_arthropods");
            if (bane > 0 && e.type().is("minecraft:sensitive_to_bane_of_arthropods")) bonus += 2.5F * bane;
        }
        return bonus;
    }

    /** getKnockback: attack_knockback 속성(주 손 add_value) + 밀치기 레벨. */
    static float knockback(ItemStack weapon) {
        return ItemTools.attributeBonus(weapon, "minecraft:generic.attack_knockback", "mainhand") + ItemTools.enchantmentLevel(weapon, "minecraft:knockback");
    }

    /** sweeping_damage_ratio: 휘두르기 날 레벨 / (레벨 + 1). */
    static float sweepingRatio(ItemStack weapon) {
        int lvl = ItemTools.enchantmentLevel(weapon, "minecraft:sweeping_edge");
        return lvl > 0 ? (float) lvl / (lvl + 1) : 0.0F;
    }

    /** postHurtEnemy 내구 감소: 검·삼지창·철퇴 1, 도끼·곡괭이·삽·괭이 2, 그 밖 0. */
    static int weaponDurabilityCost(ItemStack weapon) {
        if (weapon.isEmpty()) return 0;
        String n = ItemTools.itemName(weapon);
        if (n.endsWith("_sword") || n.equals("minecraft:trident") || n.equals("minecraft:mace")) return 1;
        if (n.endsWith("_axe") || n.endsWith("_pickaxe") || n.endsWith("_shovel") || n.endsWith("_hoe")) return 2;
        return 0;
    }

    private static float healthOf(Targetable t) {
        if (t instanceof LivingEntity le) return le.health();
        if (t instanceof Player p) return p.vitals().health();
        return 0.0F;
    }

    private static boolean isLiving(Targetable t) { return t instanceof LivingEntity || t instanceof Player; }

    /** Player.attack(target). 주 손 무기는 p.inventory() 의 들고 있는 칸. */
    public static Result attack(World w, Player p, Targetable target) {
        ChunkLevel level = w.level();
        ItemStack weapon = p.inventory().get(p.inventory().heldWindowSlot());
        float f = p.autoSpinAttackTicks > 0 ? p.autoSpinAttackDmg : (float) PlayerAttributes.value(p, org.domi.furnace.server.attribute.Attribute.ATTACK_DAMAGE);
        float g = (f + enchantDamageBonus(weapon, target)) - f;   // getEnchantedDamage(target, f, source) - f
        float h = p.attackStrengthScale(0.5F);
        f *= 0.2F + h * h * 0.8F;
        g *= h;
        p.attackStrengthTicker = 0;   // resetAttackStrengthTicker
        if (!(f > 0.0F || g > 0.0F)) return Result.MISS;
        boolean strong = h > 0.9F;
        boolean sprintHit = p.sprinting() && strong;
        if (sprintHit) sound(level, p, "minecraft:entity.player.attack.knockback");
        f += MaceSmash.attackDamageBonus(p, weapon);   // item.getAttackDamageBonus: 치명타 배율 앞
        boolean crit = strong && p.vitals().fallDistance() > 0.0F && !p.onGround() && !p.onClimbable && !p.wasTouchingWater
                && !p.vitals().hasEffect(MobEffect.BLINDNESS) && p.vehicle() == null && isLiving(target) && !p.sprinting();
        if (crit) f *= 1.5F;
        float total = f + g;
        // walkDist - walkDistO: 이번 틱 수평 이동 × 0.6 (마지막 이동 패킷의 변위로 본다) < getSpeed(0.1)
        double[] mv = p.knownMovement();
        double walked = Math.sqrt(mv[0] * mv[0] + mv[2] * mv[2]) * 0.6;
        float speed = (float) PlayerAttributes.value(p, org.domi.furnace.server.attribute.Attribute.MOVEMENT_SPEED);   // getSpeed()
        boolean sweep = strong && !crit && !sprintHit && p.onGround() && walked < speed && ItemTools.itemName(weapon).endsWith("_sword");
        float before = healthOf(target);
        boolean hurt = hurt(level, p, target, total, weapon);
        if (!hurt) {
            sound(level, p, "minecraft:entity.player.attack.nodamage");
            return Result.MISS;
        }
        float yr = p.yaw() * (float) (Math.PI / 180.0);
        float sin = org.domi.furnace.server.block.Dir.sin(yr), cos = org.domi.furnace.server.block.Dir.cos(yr);
        float k = knockback(weapon) + (sprintHit ? 1.0F : 0.0F);
        if (k > 0.0F) {
            if (isLiving(target)) target.knockback(k * 0.5F, sin, -cos);
            else target.addDeltaMovement(-sin * k * 0.5F, 0.1, cos * k * 0.5F);   // push
            // 때린 쪽 속도 × (0.6, 1, 0.6) 은 클라이언트가 스스로 한다
            p.sprinting(false);
        }
        // 바닐라: 맞은 쪽이 ServerPlayer 이고 hurtMarked 면 틱 끝을 기다리지 않고 속도 패킷을 바로 보낸다
        if (target instanceof Player victim) victim.sendPendingKnockback();
        if (sweep) {
            float l = 1.0F + (float) PlayerAttributes.value(p, org.domi.furnace.server.attribute.Attribute.SWEEPING_DAMAGE_RATIO) * f;
            AABB box = target.boundingBox().inflate(1.0, 0.25, 1.0);
            for (LivingEntity le : level.getEntitiesOfClass(LivingEntity.class, box, e -> true)) {
                if (le == target || (le instanceof ArmorStand as && as.isMarker()) || distSqr(p, le) >= 9.0) continue;
                float m = (l + enchantDamageBonus(weapon, le)) * h;
                le.knockback(0.4F, sin, -cos);
                le.hurtBy(new Entity.Damage("player_attack", false, false, false).withWeapon(weapon), m, p);
                postAttack(level, p, weapon, le);
            }
            for (Player o : w.players()) {
                if (o == p || o == target || !o.isAlive() || !o.boundingBox().intersects(box) || distSqr(p, o) >= 9.0) continue;
                float m = (l + enchantDamageBonus(weapon, o)) * h;
                o.knockback(0.4F, sin, -cos);
                hurt(level, p, o, m, weapon);
                postAttack(level, p, weapon, o);
            }
            sound(level, p, "minecraft:entity.player.attack.sweep");
            if (level != null) {   // Player.sweepAttack
                double sx = -org.domi.furnace.server.block.Dir.sin(yr), sz = org.domi.furnace.server.block.Dir.cos(yr);
                level.sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions.simple("sweep_attack"),
                        p.x() + sx, p.y() + p.bbHeight() * 0.5, p.z() + sz, 0, sx, 0.0, sz, 0.0);
            }
        }
        if (crit) {
            sound(level, p, "minecraft:entity.player.attack.crit");
            animate(p, target, 4);   // crit
        }
        if (!crit && !sweep) sound(level, p, strong ? "minecraft:entity.player.attack.strong" : "minecraft:entity.player.attack.weak");
        if (g > 0.0F) animate(p, target, 5);   // magicCrit
        p.setLastHurtMob(target);
        int cost = isLiving(target) ? weaponDurabilityCost(weapon) : 0;   // hurtEnemy → postHurtEnemy
        if (isLiving(target)) MaceSmash.hurtEnemy(w, p, weapon, target);   // Item.hurtEnemy → 철퇴 스매시
        postAttack(level, p, weapon, target);
        if (isLiving(target) && MaceSmash.isMace(weapon) && MaceSmash.canSmash(p)) p.vitals().resetFallDistance();   // Mace.postHurtEnemy
        float dealt = before - healthOf(target);   // 바닐라 순서: setLastHurtMob → 공격 후 효과 → 피해 표시 → 통계
        if (level != null && isLiving(target) && dealt > 2.0F) {   // damage_indicator: 입힌 양 × 0.5 개
            level.sendParticles(org.domi.furnace.server.net.protocol.play.ParticleOptions.simple("damage_indicator"),
                    target.x(), target.y() + target.bbHeight() * 0.5, target.z(), (int) (dealt * 0.5), 0.1, 0.0, 0.1, 0.2);
        }
        if (isLiving(target)) p.awardStat("minecraft:damage_dealt", Math.round((before - healthOf(target)) * 10.0F));
        p.vitals().causeFoodExhaustion(0.1F);
        return new Result(true, cost);
    }

    /** target.hurt(playerAttack(p), amount). 플레이어 대상은 바닐라 LivingEntity.hurt 의 0.4 넉백까지(그쪽 피해 경로엔 넉백이 없다). */
    static boolean hurt(ChunkLevel level, Player p, Targetable target, float amount, ItemStack weapon) {
        var dmg = new Entity.Damage("player_attack", false, false, false).withWeapon(weapon);
        if (target instanceof LivingEntity le) return le.hurtBy(dmg, amount, p);
        if (target instanceof Player victim) {
            victim.shieldLevel = level;
            boolean r = victim.vitals().hurt(DamageType.PLAYER_ATTACK, amount, p, victim.shieldSource(DamageType.PLAYER_ATTACK.key(), p, null));
            if (victim.vitals().lastHurtFresh) {
                // 방향이 거의 0 일 때의 보정은 knockback 안에서(엔티티 난수 소비 없음)
                victim.knockback(0.4F, p.x() - victim.x(), p.z() - victim.z());
            }
            return r;
        }
        return target.hurtByMob(dmg, amount, null);
    }

    /**
     * doPostAttackEffectsWithItemSource: 먼저 맞은 쪽 장비의 가시, 이어서 무기의 발화(불 붙이기 4초 × 레벨. 플레이어 대상은 불 상태가 아직 없어 넘어간다)·돌풍,
     * 살충(절지류에게 느림 IV).
     * 살충: apply_mob_effect — 대상의 난수로 후보 고르기 nextInt(1) → 지속 nextFloat 보간(1.5초 ~ 1.5 + 0.5×(레벨−1)초, 반올림 ×20)
     * → 증폭 nextFloat 보간(3 ~ 3).
     */
    static void postAttack(ChunkLevel level, Player p, ItemStack weapon, Targetable target) {
        if (isLiving(target)) EnchantEffects.thorns(level, target, p);
        int fire = ItemTools.enchantmentLevel(weapon, "minecraft:fire_aspect");
        if (fire > 0 && target instanceof LivingEntity le) le.igniteForSeconds(4.0F * fire);
        EnchantEffects.windBurst(level, p, weapon);
        int bane = ItemTools.enchantmentLevel(weapon, "minecraft:bane_of_arthropods");
        if (bane > 0 && target instanceof Entity e && e.type().is("minecraft:sensitive_to_bane_of_arthropods")) {
            var rnd = e.random();
            rnd.nextInt(1);
            float max = 1.5F + 0.5F * (bane - 1);
            int duration = Math.round((rnd.nextFloat() * (max - 1.5F) + 1.5F) * 20.0F);
            int amp = Math.max(0, Math.round(rnd.nextFloat() * (3.0F - 3.0F) + 3.0F));
            var slow = new org.domi.furnace.server.effect.MobEffectInstance(MobEffect.SLOWNESS, duration, amp);
            if (e instanceof LivingEntity le) le.addEffect(slow);
        }
    }

    private static double distSqr(Player p, Targetable t) {
        double dx = p.x() - t.x(), dy = p.y() - t.y(), dz = p.z() - t.z();
        return dx * dx + dy * dy + dz * dz;
    }

    /** level.playSound(null, 플레이어 위치, 소리, PLAYERS, 1, 1). */
    private static void sound(ChunkLevel level, Player p, String s) {
        if (level != null) level.playSound(null, p.x(), p.y(), p.z(), s, "players", 1.0F, 1.0F);
    }

    /** crit/magicCrit: ServerChunkCache.broadcastAndSend(때린 쪽, animate(대상, 4·5)). */
    private static void animate(Player p, Targetable target, int action) {
        var pkt = new org.domi.furnace.server.net.protocol.play.Animate(target.id(), action);
        p.send(pkt);
        p.vitals().broadcastToTrackers(pkt);
    }
}
