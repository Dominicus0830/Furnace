// 바닐라 Entity.baseTick / move 의 불 부분을 플레이어에게: 불붙은 동안 20틱마다 1 피해, 물에 닿으면 꺼짐, 용암에 닿으면 15초 불 + 4 피해,
// 불·용암이 없으면 면역 틱(−20)으로 되돌리고, 비·물·가루눈·거품 기둥에서 불이 꺼지면 소리가 난다. 공유 플래그 0(불)은 Player.sharedFlags 가 isOnFire 로 만든다.
// 모두 소유 region 스레드에서 부른다. tick 은 매 틱(baseTick), afterMove 는 이동 패킷을 받은 틱(Entity.move 의 끝).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.FluidTicker;
import org.domi.furnace.server.block.Fluids;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.entity.damage.DamageType;
import org.domi.furnace.server.world.GameMode;

public final class PlayerFire {
    private PlayerFire() {}

    private static final int LAVA = B.id("lava"), BUBBLE_COLUMN = B.id("bubble_column"), POWDER_SNOW = B.id("powder_snow");

    /** baseTick: 물에 닿으면 끄고, 불붙었으면 피해·감소, 용암 속이면 lavaHurt. s_inWater 는 이번 틱에 잰 물 접촉. */
    public static void tick(Player p, ChunkLevel level, boolean inWater) {
        if (p.vitals().isDeadOrDying() || p.gameMode() == GameMode.SPECTATOR) return;
        if (inWater) p.clearFire();   // updateInWaterStateAndDoWaterCurrentPushing: 물에 닿으면 clearFire
        boolean inLava = level != null && isInLava(level, p);
        int rem = p.remainingFireTicks();
        if (rem > 0) {
            if (rem % 20 == 0 && !inLava) p.vitals().hurt(DamageType.byKey("on_fire"), 1.0F);
            p.setRemainingFireTicks(rem - 1);
            // Entity.baseTick: 불붙어 있으면 얼음이 녹는다(레벨 이벤트 1009 = 불 꺼지는 소리)
            if (p.vitals().thawByFire() && level != null) level.levelEvent(null, 1009, p.blockPosition(), 1);
        }
        if (inLava) {
            lavaHurt(p, level);
            p.vitals().setFallDistance(p.vitals().fallDistance() * 0.5F);
        }
    }

    /** Entity.lavaHurt: 15초 불붙이고 용암 피해 4(피해가 들어갔으면 타는 소리). */
    public static void lavaHurt(Player p, ChunkLevel level) {
        p.igniteForSeconds(15.0F);
        if (p.vitals().hurt(DamageType.byKey("lava"), 4.0F) && level != null) {
            Shields.playSoundExceptSelf(p, level, "minecraft:entity.generic.burn", 0.4F, 2.0F + level.random().nextFloat() * 0.4F);
        }
    }

    /** isInLava: 몸 상자(0.001 줄임)와 겹치는 용암 칸의 윗면이 상자 아래보다 높다(updateFluidHeightAndDoFluidPushing 의 높이 &gt; 0). */
    static boolean isInLava(ChunkLevel level, Player p) {
        AABB box = p.boundingBox().deflate(0.001);
        int x0 = Entity.floor(box.minX()), x1 = (int) Math.ceil(box.maxX()), y0 = Entity.floor(box.minY()), y1 = (int) Math.ceil(box.maxY());
        int z0 = Entity.floor(box.minZ()), z1 = (int) Math.ceil(box.maxZ());
        if (!level.hasChunksAt(x0, z0, x1 - 1, z1 - 1)) return false;
        for (int bx = x0; bx < x1; bx++) for (int by = y0; by < y1; by++) for (int bz = z0; bz < z1; bz++) {
            Pos pos = new Pos(bx, by, bz);
            int fluid = level.getFluidState(pos);
            if (!Fluids.isLava(fluid)) continue;
            double top = (float) by + FluidTicker.height(fluid, level, pos);
            if (top >= box.minY() && top - box.minY() > 0.0) return true;
        }
        return false;
    }

    /** BaseFireBlock.entityInside(플레이어): 불 틱 +1(0 이 되면 8초 붙임)과 불 속 피해(불 1, 영혼 불 2). */
    public static void fireInside(Player p, float fireDamage) {
        p.setRemainingFireTicks(p.remainingFireTicks() + 1);
        if (p.remainingFireTicks() == 0) p.igniteForSeconds(8.0F);
        p.vitals().hurt(DamageType.byKey("in_fire"), fireDamage);
    }

    /**
     * Entity.move 의 끝: 몸(1e-6 줄임)에 불·용암이 없으면 불 틱을 면역 값(−20)으로 두고, 불붙은 채 비·물·가루눈·거품 기둥에 있으면 꺼진다
     * (꺼질 때 소리). 이동 패킷을 처리한 틱에만 부른다.
     */
    public static void afterMove(Player p, ChunkLevel level, boolean inWater) {
        if (level == null || p.vitals().isDeadOrDying() || p.gameMode() == GameMode.SPECTATOR) return;
        AABB box = p.boundingBox().deflate(1.0E-6);
        int x0 = Entity.floor(box.minX()), y0 = Entity.floor(box.minY()), z0 = Entity.floor(box.minZ());
        int x1 = Entity.floor(box.maxX()), y1 = Entity.floor(box.maxY()), z1 = Entity.floor(box.maxZ());
        if (!level.hasChunksAt(x0, z0, x1, z1)) return;   // getBlockStatesIfLoaded: 안 올라온 곳이 있으면 건드리지 않는다
        boolean fireOrLava = false, powderSnow = p.takeInPowderSnow();   // entityInside 가 눈을 부쉈어도 이번 move 에서 들었던 것으로 센다
        for (int bx = x0; bx <= x1; bx++) for (int by = y0; by <= y1; by++) for (int bz = z0; bz <= z1; bz++) {
            int s = level.getBlockState(new Pos(bx, by, bz));
            if (B.isTag(s, "minecraft:fire") || B.is(s, LAVA)) fireOrLava = true;
            if (B.is(s, POWDER_SNOW)) powderSnow = true;
        }
        boolean wet = powderSnow || inWater || isInRain(level, p) || B.is(level.getBlockState(p.blockPosition()), BUBBLE_COLUMN);
        if (!fireOrLava) {
            if (p.remainingFireTicks() <= 0) p.setRemainingFireTicks(-Player.FIRE_IMMUNE_TICKS);
            if (p.isOnFire() && wet) extinguishedSound(p, level);
        }
        if (p.isOnFire() && wet) p.setRemainingFireTicks(-Player.FIRE_IMMUNE_TICKS);
    }

    /** Entity.isInRain: 발 칸이나 머리 위 칸에 비가 내린다. */
    static boolean isInRain(ChunkLevel level, Player p) {
        return level.isRainingAt(p.blockPosition()) || level.isRainingAt(new Pos(Entity.floor(p.x()), Entity.floor(p.boundingBox().maxY()), Entity.floor(p.z())));
    }

    private static void extinguishedSound(Player p, ChunkLevel level) {
        var r = level.random();
        Shields.playSoundExceptSelf(p, level, "minecraft:entity.generic.extinguish_fire", 0.7F, 1.6F + (r.nextFloat() - r.nextFloat()) * 0.4F);
    }
}
