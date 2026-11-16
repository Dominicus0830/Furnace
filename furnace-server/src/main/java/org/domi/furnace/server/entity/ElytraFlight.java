// 겉날개 비행 — 바닐라 Player.tryToStartFallFlying/stopFallFlying, LivingEntity.updateFallFlying, LivingEntity.travel 의 겉날개 분기.
// 플레이어 위치는 클라이언트 권위다. 서버는 바닐라 ServerPlayer 처럼 자기 deltaMovement 를 따로 굴려
// 충돌 운동에너지 피해·착지 해제·낙하 거리 보정을 판정하고, 위치는 옮기지 않는다(다음 이동 패킷이 덮는다).
// 모든 메서드는 플레이어를 가진 region 스레드에서만 부른다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.effect.MobEffect;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;

public final class ElytraFlight {
    private ElytraFlight() {}

    /** 인벤토리 가슴 칸(창 칸 번호). */
    public static final int CHEST_SLOT = 6;
    /** 플레이어 기본 중력(generic.gravity 기본값). */
    public static final double GRAVITY = 0.08;

    /** ElytraItem.isFlyEnabled: 내구도가 1 보다 많이 남았다. */
    public static boolean isFlyEnabled(ItemStack s) {
        return ItemTools.damage(s) < ItemTools.maxDamage(s) - 1;
    }

    static boolean wearsUsableElytra(Player p) {
        ItemStack chest = p.inventory().get(CHEST_SLOT);
        return !chest.isEmpty() && "minecraft:elytra".equals(ItemTools.itemName(chest)) && isFlyEnabled(chest);
    }

    /** Player.tryToStartFallFlying: 공중·비행 중 아님·물 밖·부유 없음, 쓸 수 있는 겉날개면 시작. */
    public static boolean tryToStartFallFlying(Player p) {
        if (!p.onGround() && !p.isFallFlying() && !p.wasTouchingWater && !p.vitals().hasEffect(MobEffect.LEVITATION)
                && wearsUsableElytra(p)) {
            startFallFlying(p);
            return true;
        }
        return false;
    }

    /** startFallFlying: 공유 플래그 7. 서버 deltaMovement 는 마지막 이동 패킷 변위로 시작한다(차이: 바닐라는 서버가 굴리던 값). */
    public static void startFallFlying(Player p) {
        if (!p.isFallFlying()) {
            double[] m = p.knownMovement();
            p.flyVx = m[0];
            p.flyVy = m[1];
            p.flyVz = m[2];
        }
        p.setFallFlying(true);
    }

    /** stopFallFlying: 켰다가 끈다 — 값이 같아도 dirty 라 공유 플래그를 다시 보낸다. */
    public static void stopFallFlying(Player p) {
        p.setFallFlying(false);
        p.forceSharedFlagsResend();
    }

    /** 리스폰(새 ServerPlayer): 소유 스레드에서 비행 상태를 초기화한다. */
    public static void resetForRespawn(Player p) {
        p.setFallFlying(false);
        p.fallFlyTicks = 0;
        p.flyVx = p.flyVy = p.flyVz = 0.0;
    }

    /** handlePlayerCommand START_FALL_FLYING. */
    public static void handleStartCommand(Player p) {
        if (!tryToStartFallFlying(p)) stopFallFlying(p);
    }

    /**
     * 한 틱: aiStep 의 updateFallFlying → travel(겉날개 분기) → tick 의 fallFlyTicks 증감.
     * level 이 null 이면 충돌·소리·게임 이벤트 없이 플래그·내구도만 본다.
     */
    public static void tick(Player p, ChunkLevel level) {
        updateFallFlying(p, level);
        if (p.isFallFlying()) {
            if (!p.wasTouchingWater) {
                travel(p, level);
            } else {   // 물 분기가 겉날개 분기를 가린다 — 물 이동은 서버가 굴리지 않아 이동 패킷 변위로 이어 간다(용암 분기는 아직 없다)
                double[] m = p.knownMovement();
                p.flyVx = m[0]; p.flyVy = m[1]; p.flyVz = m[2];
            }
        }
        // Player.travel: 창조 비행 중이고 탈것이 아니면 travel 뒤 공유 플래그 7 을 끈다
        if (p.vitals().isFlying() && p.vehicle() == null) p.setFallFlying(false);
        p.fallFlyTicks = p.isFallFlying() ? p.fallFlyTicks + 1 : 0;
    }

    /** LivingEntity.updateFallFlying: 땅·탈것·부유면 끝, 겉날개면 10 틱마다 게임 이벤트, 20 틱마다 내구도 1. */
    static void updateFallFlying(Player p, ChunkLevel level) {
        boolean f = p.isFallFlying();
        if (f && !p.onGround() && p.vehicle() == null && !p.vitals().hasEffect(MobEffect.LEVITATION)) {
            ItemStack chest = p.inventory().get(CHEST_SLOT);
            if (!chest.isEmpty() && "minecraft:elytra".equals(ItemTools.itemName(chest)) && isFlyEnabled(chest)) {
                int t = p.fallFlyTicks + 1;
                if (t % 10 == 0) {
                    if ((t / 10) % 2 == 0) hurtChest(p, level, chest);
                    if (level != null) level.gameEvent(GameEvent.ELYTRA_GLIDE, new Vec3(p.x(), p.y(), p.z()), GameEventContext.of(p));
                }
            } else {
                f = false;
            }
        } else {
            f = false;
        }
        p.setFallFlying(f);
    }

    /** ItemStack.hurtAndBreak(1, player, CHEST): 무한 재료면 그대로, 내구성 확률, 부서지면 entity_event 50. */
    private static void hurtChest(Player p, ChunkLevel level, ItemStack chest) {
        if (Shields.hurtAndBreak(p, CHEST_SLOT, chest, 1, level == null ? null : level.random())) {
            var ev = new org.domi.furnace.server.net.protocol.play.EntityEvent(p.entityId(), (byte) 50);
            p.send(ev);
            p.vitals().broadcastToTrackers(ev);
        }
    }

    /**
     * LivingEntity.travel 의 겉날개 공식. v 는 deltaMovement, look 은 getLookAngle, xRot 은 도 단위 pitch.
     * 반환: 감쇠까지 끝난 새 deltaMovement.
     */
    public static double[] glide(double vx, double vy, double vz, double lx, double ly, double lz, float xRot, double gravity) {
        float f = xRot * (float) (Math.PI / 180.0);
        double hl = Math.sqrt(lx * lx + lz * lz);
        double hv = Math.sqrt(vx * vx + vz * vz);
        double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
        double c = Math.cos(f);
        c = c * c * Math.min(1.0, len / 0.4);
        vy += gravity * (-1.0 + c * 0.75);
        if (vy < 0.0 && hl > 0.0) {
            double d = vy * -0.1 * c;
            vx += lx * d / hl;
            vy += d;
            vz += lz * d / hl;
        }
        if (f < 0.0F && hl > 0.0) {
            double d = hv * -org.domi.furnace.server.block.Dir.sin(f) * 0.04;
            vx += -lx * d / hl;
            vy += d * 3.2;
            vz += -lz * d / hl;
        }
        if (hl > 0.0) {
            vx += (lx / hl * hv - vx) * 0.1;
            vz += (lz / hl * hv - vz) * 0.1;
        }
        return new double[] {vx * (double) 0.99F, vy * (double) 0.98F, vz * (double) 0.99F};
    }

    /** 충돌 피해량: (충돌 전 수평 속력 − 충돌 후 수평 속력) × 10 − 3. 0 이하면 피해 없음. */
    public static float crashDamage(double speedBefore, double speedAfter) {
        return (float) ((speedBefore - speedAfter) * 10.0 - 3.0);
    }

    /** travel 겉날개 분기(서버 몫): 낙하 거리 보정 → 공식 → move(SELF) 충돌 → 운동에너지 피해 → 땅이면 해제. */
    static void travel(Player p, ChunkLevel level) {
        double gravity = GRAVITY;
        if (p.flyVy <= 0.0 && p.vitals().hasEffect(MobEffect.SLOW_FALLING)) gravity = Math.min(gravity, 0.01);
        p.vitals().checkSlowFallDistance(p.flyVy);   // Entity.checkSlowFallDistance
        double[] look = org.domi.furnace.server.block.Clip.viewVector(p.pitch(), p.yaw());
        double speedBefore = Math.sqrt(p.flyVx * p.flyVx + p.flyVz * p.flyVz);
        double[] v = glide(p.flyVx, p.flyVy, p.flyVz, look[0], look[1], look[2], p.pitch(), gravity);
        p.flyVx = v[0];
        p.flyVy = v[1];
        p.flyVz = v[2];
        if (level == null) return;
        // Entity.move(SELF): 엔티티(배·셜커)·월드 보더·블록 충돌(걸어 오르기·블록 속도 계수는 아직 없다)
        AABB box = p.boundingBox();
        double[] m = Entity.collideBoundingBox(level, p, box, v[0], v[1], v[2]);
        boolean collX = !Entity.equalish(v[0], m[0]), collZ = !Entity.equalish(v[2], m[2]);
        boolean vertical = v[1] != m[1];
        boolean ground = vertical && v[1] < 0.0;
        if (collX) p.flyVx = 0.0;
        if (collZ) p.flyVz = 0.0;
        if (vertical) p.flyVy = 0.0;   // Block.updateEntityAfterFallOn 기본(y 속도 0)
        if (collX || collZ) {
            double speedAfter = Math.sqrt(p.flyVx * p.flyVx + p.flyVz * p.flyVz);
            float dmg = crashDamage(speedBefore, speedAfter);
            if (dmg > 0.0F) {
                Shields.playSoundExceptSelf(p, level, (int) dmg > 4 ? "minecraft:entity.player.big_fall" : "minecraft:entity.player.small_fall", 1.0F, 1.0F);
                p.vitals().hurt(DamageType.byKey("fly_into_wall"), dmg);
            }
        }
        if (ground) p.setFallFlying(false);
    }
}
