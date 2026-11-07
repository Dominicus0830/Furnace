// 바닐라 LivingEntity.checkFallDamage 의 착지 입자: 땅에 닿은 순간 낙하 거리가 SAFE_FALL_DISTANCE 를 넘고 발밑이 공기가 아니면
// 발 밑 블록 입자(minecraft:block)를 개수 = (int)(150 × min(0.2F + ceil(낙하 - 안전)/15F, 2.5)), 퍼짐 0, 속도 0.15000000596046448 로 뿌린다.
// 위치는 엔티티 (x,y,z) 인데, 발밑 칸(getOnPosLegacy)이 엔티티가 선 칸과 x·z 가 다르면(모서리에 걸쳐 선 경우) 그 칸 위 가장자리로 당긴다.
// 몹(LivingEntity.checkFallDamage)과 플레이어(ServerPlayer.doCheckFallDamage → super.checkFallDamage)가 같은 식을 쓴다.
// 차이(미구현): 같은 메서드의 onChangedBlock(착지 때 위치 변경 효과 재실행)·updateInWaterStateAndDoWaterCurrentPushing 는 여기서 하지 않는다.
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.net.protocol.play.ParticleOptions;

public final class LandingParticles {
    private LandingParticles() {}

    /** 개수: (int)(150 × min((double)(0.2F + f/15F), 2.5)), f = ceil(낙하 - 안전) 을 float 로. */
    public static int count(float fallDistance, double safeFall) {
        float f = (float) Math.ceil((double) fallDistance - safeFall);
        double d = Math.min((double) (0.2F + f / 15.0F), 2.5);
        return (int) (150.0 * d);
    }

    /**
     * @param feet  엔티티가 선 칸(blockPosition)
     * @param on    발밑 칸(getOnPosLegacy)
     * @param state 발밑 칸의 블록 상태
     */
    public static void spawn(ChunkLevel level, double x, double y, double z, Pos feet, Pos on, int state, float fallDistance, double safeFall) {
        if (level == null || !((double) fallDistance > safeFall) || state < 0 || B.isAir(state)) return;
        if (on.x() != feet.x() || on.z() != feet.z()) {
            double dx = x - on.x() - 0.5, dz = z - on.z() - 0.5;
            double max = Math.max(Math.abs(dx), Math.abs(dz));
            x = on.x() + 0.5 + dx / max * 0.5;
            z = on.z() + 0.5 + dz / max * 0.5;
        }
        level.sendParticles(new ParticleOptions.Block("minecraft:block", state), x, y, z, count(fallDistance, safeFall), 0.0, 0.0, 0.0, 0.15000000596046448);
    }
}
