// 플레이어 이동 방출(바닐라 Entity.move 의 MovementEmission 부분을 ServerPlayer.move 에 맞춰): 걸음(step)·헤엄(swim) 게임 이벤트와
// 조각 감지기·비명체 밟기. 플레이어는 Entity 가 아니라 위치 패킷 하나마다 그 변위로 부른다(Region.processMoves).
// 차이(가정): 발밑 칸은 지지 블록(mainSupportingBlockPos) 없이 바닥 좌표로 본다 — 모서리에 걸쳐 설 때 다른 칸이 될 수 있다.
// TODO(소리): 걸음·헤엄 소리, TODO(낙하): 플레이어 hit_ground 이벤트.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.world.gameevent.GameEvent;
import org.domi.furnace.server.world.gameevent.GameEventContext;

public final class PlayerMovementEmission {

    public float walkDist, moveDist, flyDist, nextStep = 1.0F;

    private static final int POWDER_SNOW = B.id("powder_snow"), SCULK_SENSOR = B.id("sculk_sensor"),
            CALIBRATED = B.id("calibrated_sculk_sensor"), SHRIEKER = B.id("sculk_shrieker");

    /** 한 이동(변위 dx,dy,dz 후 위치 x,y,z). */
    public void onMove(ChunkLevel level, Player p, double x, double y, double z, double dx, double dy, double dz, boolean onGround, boolean inWater) {
        if (level == null || p.vehicle() != null) return;
        Pos legacy = new Pos(Entity.floor(x), Entity.floor(y - 0.2F), Entity.floor(z));
        int legacyState = level.getBlockState(legacy);
        if (onGround) stepOn(level, legacy, legacyState, p);
        boolean flying = p.vitals().isFlying();
        // Player.getMovementEmission: 날거나 땅에서 웅크리면 NONE
        if (flying || onGround && p.sneaking()) return;
        flyDist += (float) (Math.sqrt(dx * dx + dy * dy + dz * dz) * 0.6);
        Pos onPos = new Pos(Entity.floor(x), Entity.floor(y - 1.0E-5F), Entity.floor(z));
        int onState = level.getBlockState(onPos);
        double ey = climbable(onState) ? dy : 0.0;
        walkDist += (float) Math.sqrt(dx * dx + dz * dz) * 0.6F;
        moveDist += (float) Math.sqrt(dx * dx + ey * ey + dz * dz) * 0.6F;
        if (moveDist > nextStep && !B.isAir(onState)) {
            boolean same = onPos.equals(legacy);
            boolean any = effects(level, p, x, y, z, legacy, legacyState, same, onGround, dy);
            if (!same) any |= effects(level, p, x, y, z, onPos, onState, true, onGround, dy);
            if (any) {
                nextStep = (int) moveDist + 1;
            } else if (inWater) {
                nextStep = (int) moveDist + 1;
                level.gameEvent(GameEvent.SWIM, new Vec3(x, y, z), GameEventContext.of(p));
            }
        }
    }

    private static boolean climbable(int state) { return B.isTag(state, "minecraft:climbable") || B.is(state, POWDER_SNOW); }

    private static boolean effects(ChunkLevel level, Player p, double x, double y, double z, Pos pos, int state, boolean event, boolean onGround, double dy) {
        if (B.isAir(state)) return false;
        boolean crouching = p.sneaking() && !p.vitals().isFlying();
        if (onGround || climbable(state) || crouching && dy == 0.0) {
            if (event) level.gameEvent(GameEvent.STEP, new Vec3(x, y, z), GameEventContext.of(p, state));
            return true;
        }
        return false;
    }

    /** 발밑 블록의 stepOn 중 플레이어에게 의미 있는 것(조각 감지기 STEP 진동·비명체 비명). */
    private static void stepOn(ChunkLevel level, Pos pos, int state, Player p) {
        int b = B.block(state);
        if (b == SCULK_SENSOR || b == CALIBRATED) {
            org.domi.furnace.server.block.types.SculkSensorBlock.stepOnBy(level, pos, state, p, new Vec3(p.x(), p.y(), p.z()));
        } else if (b == SHRIEKER) {
            org.domi.furnace.server.block.types.SculkShriekerBlock.stepOnBy(level, pos, state, p);
        }
    }
}
