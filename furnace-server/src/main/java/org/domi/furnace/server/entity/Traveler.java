// 포털을 지나는 것(엔티티·플레이어)의 원천 쪽 상태: 위치·속도·회전·몸 크기·충돌 문맥. 목적 차원 계산은 이 값만 읽는다(원천 스레드에서 떠 둔다).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.CollisionContext;
import org.domi.furnace.server.block.Pos;

/**
 * @param enderPearl 엔더 진주(엔드 관문은 진주를 아래로 (0, -1, 0) 보낸다).
 * @param collision 출발 자리의 충돌 문맥 스냅샷(CollisionContext.snapshot) — 네더 포털 출구 자리 찾기(findFreePosition(entity))가 쓴다.
 */
public record Traveler(double x, double y, double z, double vx, double vy, double vz, float yRot, float xRot,
                       double width, double height, boolean isPlayer, boolean resetPortalDepth, boolean enderPearl,
                       CollisionContext collision) {


    public Pos blockPosition() { return new Pos(Entity.floor(x), Entity.floor(y), Entity.floor(z)); }

    /**
     * resetPortalDepth: LivingEntity·AbstractMinecart·Boat 의 getRelativePortalPosition 재정의
     * (resetForwardDirectionOfRelativePortalPosition) — 포털 깊이 오프셋을 0 으로 해 출구 포털 가운데로 간다.
     */
    public static Traveler of(Entity e) {
        return new Traveler(e.x(), e.y(), e.z(), e.vx(), e.vy(), e.vz(), e.yRot(), e.xRot(), e.bbWidth(), e.bbHeight(), false,
                e instanceof LivingEntity || e instanceof AbstractMinecart || e instanceof Boat, e instanceof ThrownEnderpearl,
                CollisionContext.of(e).snapshot());
    }

    /** 플레이어: 서버 쪽 속도는 차원 이동에 쓰이지 않는다(위치 패킷만 간다). 몸 크기는 자세(bbHeight). */
    public static Traveler of(Player p) {
        return new Traveler(p.x(), p.y(), p.z(), 0, 0, 0, p.yaw(), p.pitch(), 0.6, p.bbHeight(), true, true, false, CollisionContext.of(p).snapshot());
    }
}
