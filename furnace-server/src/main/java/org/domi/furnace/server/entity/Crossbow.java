// 바닐라 CrossbowItem 의 몹 쪽 동작: 장전 시간·당긴 뒤 탄약 장전(charged_projectiles)·발사. 몹이 손에 든 화살·폭죽 로켓을 쓰고(없으면 일반 화살 하나),
// 다중 발사 마법이면 여러 발(퍼짐 각)을, 폭죽 로켓이면 로켓을 쏜다. 플레이어 쪽은 RangedWeapons.
package org.domi.furnace.server.entity;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemStackCodecs;
import org.domi.furnace.server.item.ItemTools;

public final class Crossbow {
    private Crossbow() {}

    private static final String CHARGED = "minecraft:charged_projectiles", INTANGIBLE = "minecraft:intangible_projectile";

    /** CrossbowItem.getChargeDuration: 1.25초 − 0.25초 × 빠른 장전 레벨(0 아래로는 안 내려감). */
    public static int chargeDuration(ItemStack crossbow) {
        int quick = ItemTools.enchantmentLevel(crossbow, "minecraft:quick_charge");
        float seconds = Math.max(0.0F, 1.25F - 0.25F * quick);
        return (int) Math.ceil(seconds * 20.0F);
    }

    public static boolean isCharged(ItemStack crossbow) {
        return ItemTools.component(crossbow, CHARGED) instanceof List<?> l && !l.isEmpty();
    }

    private static boolean isArrowOrFirework(ItemStack s) {
        return !s.isEmpty() && (ItemContext.isInTag("minecraft:item", "minecraft:arrows", s.itemId()) || ItemTools.itemName(s).equals("minecraft:firework_rocket"));
    }

    private static boolean isFirework(ItemStack s) { return ItemTools.itemName(s).equals("minecraft:firework_rocket"); }

    /** ProjectileWeaponItem.getHeldProjectile: 왼손(보조) 먼저, 없으면 주 손의 화살·폭죽 로켓. 없으면 null. */
    private static EquipmentSlot heldProjectileSlot(Mob mob) {
        if (isArrowOrFirework(mob.getItemBySlot(EquipmentSlot.OFFHAND))) return EquipmentSlot.OFFHAND;
        if (isArrowOrFirework(mob.getItemBySlot(EquipmentSlot.MAINHAND))) return EquipmentSlot.MAINHAND;
        return null;
    }

    /**
     * ProjectileWeaponItem.draw(weapon, ammo, shooter): 다중 발사면 개수만큼 — 첫 발은 진짜 탄약에서(무한이면 줍지 못하는 사본),
     * 나머지는 줍기 불가 사본. 손에 든 탄약은 그 손에서 줄인다(몹은 무한 재료가 아니다).
     */
    private static List<ItemStack> draw(Mob mob, ItemStack weapon, EquipmentSlot ammoSlot, ItemStack ammo) {
        if (ammo.isEmpty()) return List.of();
        int n = RangedWeapons.projectileCount(weapon);
        List<ItemStack> list = new ArrayList<>(n);
        for (int j = 0; j < n; j++) {
            ItemStack s;
            if (j > 0) {
                s = ItemTools.with(ammo.withCount(1), INTANGIBLE, Map.of());   // 사본은 늘 줍기 불가
            } else {
                int use = RangedWeapons.ammoUse(weapon, ammo);
                if (use > ammo.count()) {
                    s = ItemStack.EMPTY;
                } else if (use == 0) {
                    s = ItemTools.with(ammo.withCount(1), INTANGIBLE, Map.of());
                } else {
                    s = ammo.withCount(use);
                    if (ammoSlot != null) mob.updateItemSlot(ammoSlot, ammo.withCount(ammo.count() - use));
                }
            }
            if (!s.isEmpty()) list.add(s);
        }
        return list;
    }

    /** CrossbowItem.tryLoadProjectiles: 손에 든 화살·폭죽(없으면 일반 화살 하나)을 장전한다. */
    private static boolean tryLoadProjectiles(Mob mob, EquipmentSlot slot, ItemStack crossbow) {
        EquipmentSlot ammoSlot = heldProjectileSlot(mob);
        ItemStack ammo = ammoSlot != null ? mob.getItemBySlot(ammoSlot) : new ItemStack(ItemContext.id("minecraft:item", "arrow"), 1);   // Mob.getProjectile
        List<ItemStack> list = draw(mob, crossbow, ammoSlot, ammo);
        if (list.isEmpty()) return false;
        List<Object> raw = new ArrayList<>(list.size());
        for (ItemStack s : list) raw.add(ItemStackCodecs.toCanonical(s));
        mob.updateItemSlot(slot, ItemTools.with(mob.getItemBySlot(slot), CHARGED, raw));   // 장전은 스택을 그 자리에서 고친다
        return true;
    }

    /** CrossbowItem.releaseUsing: 충분히 당겼고 비었으면 탄약을 장전하고 소리를 낸다. */
    public static void releaseUsing(Mob mob, EquipmentSlot slot, int ticksUsed) {
        ItemStack crossbow = mob.getItemBySlot(slot);
        float power = Math.min(1.0F, (float) ticksUsed / chargeDuration(crossbow));
        if (power >= 1.0F && !isCharged(crossbow) && tryLoadProjectiles(mob, slot, crossbow)) {
            mob.level().playSound(null, mob.x(), mob.y(), mob.z(), "item.crossbow.loading_end", mob.soundSource(), 1.0F,
                    1.0F / (mob.level().random().nextFloat() * 0.5F + 1.0F) + 0.2F);
        }
    }

    @SuppressWarnings("unchecked")
    private static List<ItemStack> charged(ItemStack crossbow) {
        List<ItemStack> out = new ArrayList<>();
        if (ItemTools.component(crossbow, CHARGED) instanceof List<?> l) {
            for (Object o : l) if (o instanceof Map<?, ?> m) out.add(ItemStackCodecs.fromCanonical((Map<String, Object>) m));
        }
        return out;
    }

    /**
     * CrossbowItem.performShooting → ProjectileWeaponItem.shoot: 장전을 비우고, 장전돼 있던 것을 퍼짐 각(다중 발사) 순서(가운데, 왼쪽, 오른쪽, …)로 쏜다.
     * 발마다 쇠뇌 내구도(폭죽 3, 화살 1). 부서지면 멈춘다. 몹이라 치명타는 없다.
     */
    public static void performShooting(Mob shooter, int hand, float speed, float inaccuracy, Targetable target) {
        EquipmentSlot slot = hand == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        ItemStack crossbow = shooter.getItemBySlot(slot);
        List<ItemStack> list = charged(crossbow);
        if (list.isEmpty()) return;
        crossbow = ItemTools.with(crossbow, CHARGED, List.of());
        shooter.updateItemSlot(slot, crossbow);
        float spread = RangedWeapons.projectileSpread(crossbow);
        float step = list.size() == 1 ? 0.0F : 2.0F * spread / (list.size() - 1);
        float offset = (list.size() - 1) % 2 * step / 2.0F;
        float sign = 1.0F;
        for (int j = 0; j < list.size(); j++) {
            ItemStack ammo = list.get(j);
            if (ammo.isEmpty()) continue;
            float angle = offset + sign * ((j + 1) / 2) * step;
            sign = -sign;
            Projectile proj;
            if (isFirework(ammo)) {
                var rocket = new FireworkRocketEntity(shooter.level(), shooter.level().nextEntityId(), shooter.x(), shooter.eyeY() - 0.15F, shooter.z(), ammo);
                rocket.setOwner(shooter);
                rocket.setShotAtAngle(true);
                proj = rocket;
            } else {
                AbstractArrow arrow = RangedWeapons.createArrow(shooter.level(), shooter, shooter.x(), shooter.eyeY() - 0.1F, shooter.z(), ammo, crossbow);
                arrow.setSoundEvent("item.crossbow.hit");
                proj = arrow;
            }
            shootProjectile(shooter, proj, j, speed, inaccuracy, angle, target);
            shooter.level().addFreshEntity(proj);
            shooter.hurtEquipped(slot, isFirework(ammo) ? 3 : 1);
            if (shooter.getItemBySlot(slot).isEmpty()) break;
        }
    }

    /** CrossbowItem.shootProjectile: 대상이 있으면 대상 쪽(+ 포물선 보정) 방향을 각도만큼 돌려, 없으면 시선 방향을 위쪽 벡터 축으로 돌려 쏜다. */
    private static void shootProjectile(Mob shooter, Projectile proj, int index, float velocity, float inaccuracy, float angle, Targetable target) {
        float[] v;
        if (target != null) {
            double dx = target.x() - shooter.x();
            double dz = target.z() - shooter.z();
            double h = Math.sqrt(dx * dx + dz * dz);
            double dy = target.y() + target.bbHeight() * 0.3333333333333333 - proj.y() + h * 0.2F;
            v = projectileShotVector(shooter, (float) dx, (float) dy, (float) dz, angle);
        } else {
            float[] up = RangedWeapons.viewVector(shooter.xRot() - 90.0F, shooter.yRot());
            float[] view = RangedWeapons.viewVector(shooter.xRot(), shooter.yRot());
            v = RangedWeapons.rotate(view, angle * (float) (Math.PI / 180.0), up);
        }
        proj.shoot(v[0], v[1], v[2], velocity, inaccuracy);
        shooter.level().playSound(null, shooter.x(), shooter.y(), shooter.z(), "item.crossbow.shoot", shooter.soundSource(), 1.0F,
                RangedWeapons.shotPitch(shooter.random(), index));
    }

    /**
     * CrossbowItem.getProjectileShotVector: 방향을 정규화하고, (방향 × 위)에 수직인 축으로 90° 돌려 만든 축 둘레로 angle 만큼 돌린다.
     * 방향이 위/아래와 거의 나란하면 몹의 위쪽 벡터와 외적한다. 각 0 이면 정규화 방향 그대로다.
     */
    private static float[] projectileShotVector(Mob shooter, float dx, float dy, float dz, float angle) {
        float inv = 1.0F / (float) Math.sqrt(dx * dx + dy * dy + dz * dz);   // JOML 기본값은 FMA 없이 a*b+c
        float[] n = {dx * inv, dy * inv, dz * inv};
        float[] c = cross(n, 0.0F, 1.0F, 0.0F);
        if ((double) (c[0] * c[0] + c[1] * c[1] + c[2] * c[2]) <= 1.0E-7) {
            float[] up = RangedWeapons.viewVector(shooter.xRot() - 90.0F, shooter.yRot());
            c = cross(n, up[0], up[1], up[2]);
        }
        float[] axis = RangedWeapons.rotateAxis(n.clone(), (float) (Math.PI / 2.0), c[0], c[1], c[2]);
        return RangedWeapons.rotateAxis(n.clone(), angle * (float) (Math.PI / 180.0), axis[0], axis[1], axis[2]);
    }

    /** JOML Vector3f.cross(x, y, z). */
    private static float[] cross(float[] v, float x, float y, float z) {
        return new float[] {v[1] * z - v[2] * y, v[2] * x - v[0] * z, v[0] * y - v[1] * x};
    }
}
