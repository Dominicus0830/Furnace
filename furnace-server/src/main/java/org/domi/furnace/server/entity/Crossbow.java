// 바닐라 CrossbowItem 의 몹 쪽 동작: 장전 시간·당긴 뒤 화살 장전(charged_projectiles)·발사. 아이템 객체 상태(소리 플래그)는 몹별로 둔다.
// TODO(다중발사·불꽃놀이): 다중발사 마법과 불꽃 로켓 장전은 해당 아이템/마법이 생기면.
package org.domi.furnace.server.entity;

import java.util.List;
import java.util.Map;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;

public final class Crossbow {
    private Crossbow() {}

    private static final String CHARGED = "minecraft:charged_projectiles";

    /** CrossbowItem.getChargeDuration: 1.25초 − 0.25초 × 빠른 장전 레벨(0 아래로는 안 내려감). */
    public static int chargeDuration(ItemStack crossbow) {
        int quick = ItemTools.enchantmentLevel(crossbow, "minecraft:quick_charge");
        float seconds = Math.max(0.0F, 1.25F - 0.25F * quick);
        return (int) Math.ceil(seconds * 20.0F);
    }

    public static boolean isCharged(ItemStack crossbow) {
        return ItemTools.component(crossbow, CHARGED) instanceof List<?> l && !l.isEmpty();
    }

    /** 몹이 손에 든 화살(없으면 기본 화살 하나) — Mob.getProjectile(weapon). */
    private static ItemStack projectileFor(Mob shooter) {
        return new ItemStack(ItemContext.id("minecraft:item", "arrow"), 1);
    }

    /** CrossbowItem.releaseUsing: 충분히 당겼고 비었으면 화살을 장전하고 소리를 낸다. */
    public static void releaseUsing(Mob mob, EquipmentSlot slot, int ticksUsed) {
        ItemStack crossbow = mob.getItemBySlot(slot);
        float power = Math.min(1.0F, (float) ticksUsed / chargeDuration(crossbow));
        if (power >= 1.0F && !isCharged(crossbow)) {
            ItemStack arrow = projectileFor(mob);
            ItemStack loaded = ItemTools.with(crossbow, CHARGED, List.of(Map.of("id", "minecraft:arrow", "count", 1)));
            mob.updateItemSlot(slot, loaded);   // 장전은 스택을 그 자리에서 고친다
            mob.level().playSound(null, mob.x(), mob.y(), mob.z(), "item.crossbow.loading_end", mob.soundSource(), 1.0F,
                    1.0F / (mob.level().random().nextFloat() * 0.5F + 1.0F) + 0.2F);
        }
    }

    /** CrossbowItem.performShooting: 장전된 화살을 쏘고 비운다. */
    public static void performShooting(Mob shooter, int hand, float speed, float inaccuracy, Targetable target) {
        EquipmentSlot slot = hand == 0 ? EquipmentSlot.MAINHAND : EquipmentSlot.OFFHAND;
        ItemStack crossbow = shooter.getItemBySlot(slot);
        if (!(ItemTools.component(crossbow, CHARGED) instanceof List<?> l) || l.isEmpty()) return;
        // 투사체 목록 하나(다중발사 없음): 각도 0
        Arrow arrow = new Arrow(shooter.level(), shooter.level().nextEntityId(), shooter.x(), shooter.eyeY() - 0.1F, shooter.z());
        arrow.setOwner(shooter);
        arrow.setSoundEvent("item.crossbow.hit");
        int pierce = ItemTools.enchantmentLevel(crossbow, "minecraft:piercing");
        if (pierce > 0) arrow.setPierceLevel((byte) pierce);
        shootProjectile(shooter, arrow, speed, inaccuracy, target);
        shooter.level().addFreshEntity(arrow);
        shooter.updateItemSlot(slot, ItemTools.with(shooter.getItemBySlot(slot), CHARGED, List.of()));
        shooter.hurtEquipped(slot, 1);
    }

    private static void shootProjectile(Mob shooter, Arrow arrow, float velocity, float inaccuracy, Targetable target) {
        float vx, vy, vz;
        if (target != null) {
            double dx = target.x() - shooter.x();
            double dz = target.z() - shooter.z();
            double h = Math.sqrt(dx * dx + dz * dz);
            double dy = target.y() + target.bbHeight() * 0.3333333333333333 - arrow.y() + h * 0.2F;
            float fx = (float) dx, fy = (float) dy, fz = (float) dz;
            float inv = 1.0F / (float) Math.sqrt(Math.fma(fx, fx, Math.fma(fy, fy, fz * fz)));
            vx = fx * inv;
            vy = fy * inv;
            vz = fz * inv;
        } else {
            vx = vy = vz = 0.0F;
        }
        arrow.shoot(vx, vy, vz, velocity, inaccuracy);
        shooter.level().playSound(null, shooter.x(), shooter.y(), shooter.z(), "item.crossbow.shoot", shooter.soundSource(), 1.0F, 1.0F);
    }
}
