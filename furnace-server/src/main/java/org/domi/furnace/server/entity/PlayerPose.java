// 바닐라 Player.updatePlayerPose / Entity.updateSwimming: 서버가 수영·기어가기·웅크림 자세를 계산한다.
// 질주 중 물속이면 수영 플래그, 수영 크기(0.6)가 들어갈 때만 자세를 갱신하고, 서 있을 틈이 없으면 웅크림(1.5), 그것도 안 되면 수영 자세(기어가기).
// 소유 region 스레드에서 매 틱 부른다. 가정: 수영 시작 조건의 "발 위치 블록이 물" 은 "몸이 물에 닿음" 으로 근사한다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.world.GameMode;
import org.domi.furnace.server.world.PlayerProbe;

public final class PlayerPose {
    private PlayerPose() {}

    public static final int STANDING = 0, SWIMMING = 3, CROUCHING = 5;

    /** 자세별 키(EntityDimensions: 수영 0.6, 웅크림 1.5, 서기 1.8). */
    static double height(int pose) { return pose == SWIMMING ? 0.6 : pose == CROUCHING ? 1.5 : 1.8; }

    /** canPlayerFitWithinBlocksAndEntitiesWhen: 그 자세의 상자(1e-7 줄임)에 충돌이 없다. */
    static boolean canFit(ChunkLevel level, Player p, int pose) {
        double hw = 0.3;
        return level.noCollision(new AABB(p.x() - hw, p.y(), p.z() - hw, p.x() + hw, p.y() + height(pose), p.z() + hw).deflate(1.0E-7));
    }

    public static void update(Player p, ChunkLevel level, PlayerProbe probe, PlayerProbe.Blocks blocks) {
        boolean riding = p.vehicle() != null;
        boolean inWater = probe.inWater(blocks, p.x(), p.y(), p.z(), p.bbHeight());
        boolean underWater = inWater && probe.eyeInWater(blocks, p.x(), p.y(), p.z(), p.eyeY() - p.y());
        // Entity.updateSwimming
        p.swimming = p.swimming ? p.sprinting() && inWater && !riding : p.sprinting() && underWater && !riding;
        if (p.isSleeping() || !canFit(level, p, SWIMMING)) return;   // 수영 크기도 못 들어가면 자세를 그대로 둔다
        int desired;
        if (p.isFallFlying()) desired = 1;
        else if (p.swimming) desired = SWIMMING;
        else if (p.autoSpinAttackTicks > 0) desired = 4;
        else desired = p.sneaking() && !p.vitals().isFlying() ? CROUCHING : STANDING;
        boolean fits = p.gameMode() == GameMode.SPECTATOR || riding || desired == 1 || desired == 4 || canFit(level, p, desired);
        int fin = fits ? desired : canFit(level, p, CROUCHING) ? CROUCHING : SWIMMING;
        p.crawling = fin == SWIMMING && !p.swimming && desired != SWIMMING;
        p.forcedCrouch = fin == CROUCHING && !(desired == CROUCHING);
    }
}
