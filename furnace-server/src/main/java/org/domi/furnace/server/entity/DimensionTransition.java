// 바닐라 DimensionTransition: 차원 이동의 목적지(레벨·위치·속도·회전)와 도착 뒤 처리(PLAY_PORTAL_SOUND · PLACE_PORTAL_TICKET).
package org.domi.furnace.server.entity;

import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Pos;

/**
 * @param portalSound         도착 뒤 플레이어에게 level_event 1032(지옥문 소리)
 * @param ticketPos           도착 뒤 포털 티켓을 둘 칸(없으면 null) — PLACE_PORTAL_TICKET 은 도착 위치, 찾은 지옥문이면 그 칸
 * @param missingRespawnBlock 리스폰 블록이 없어 월드 스폰으로 간다(no_respawn_block_available)
 */
public record DimensionTransition(ChunkLevel newLevel, double x, double y, double z, double vx, double vy, double vz,
                                  float yRot, float xRot, boolean portalSound, Pos ticketPos, boolean missingRespawnBlock, boolean ticketAtArrival) {

    /**
     * ticketAtArrival 이 거짓인 판(티켓이 없거나 찾은 지옥문 칸처럼 도착 칸과 무관한 칸).
     * @param ticketAtArrival 티켓이 바닐라 PLACE_PORTAL_TICKET(도착한 엔티티의 칸)이다 — 도착 위치가 바뀌면 티켓도 따라간다
     */
    public DimensionTransition(ChunkLevel newLevel, double x, double y, double z, double vx, double vy, double vz,
                               float yRot, float xRot, boolean portalSound, Pos ticketPos, boolean missingRespawnBlock) {
        this(newLevel, x, y, z, vx, vy, vz, yRot, xRot, portalSound, ticketPos, missingRespawnBlock, false);
    }

    /** 도착 뒤 처리 없음(DO_NOTHING). */
    public DimensionTransition(ChunkLevel newLevel, double x, double y, double z, double vx, double vy, double vz, float yRot, float xRot) {
        this(newLevel, x, y, z, vx, vy, vz, yRot, xRot, false, null, false);
    }

    /**
     * Paper Entity#changeDimension 이 플러그인 이벤트로 바뀐 도착 위치로 새로 만드는 전이(new DimensionTransition(to 월드, to 위치, 속도, to.yaw,
     * to.pitch, missingRespawnBlock, postDimensionTransition 그대로)). 도착 뒤 처리는 그대로이고, 티켓이 도착 칸 기준이면 새 도착 칸으로 옮긴다.
     */
    public DimensionTransition redirected(ChunkLevel level, double nx, double ny, double nz, double nvx, double nvy, double nvz, float nyRot, float nxRot) {
        Pos ticket = ticketAtArrival ? new Pos(Entity.floor(nx), Entity.floor(ny), Entity.floor(nz)) : ticketPos;
        return new DimensionTransition(level, nx, ny, nz, nvx, nvy, nvz, nyRot, nxRot, portalSound, ticket, missingRespawnBlock, ticketAtArrival);
    }

    /** PLAY_PORTAL_SOUND.then(PLACE_PORTAL_TICKET): 소리 + 도착 칸 티켓(Entity.placePortalTicket(blockPosition)). */
    public static DimensionTransition portal(ChunkLevel newLevel, double x, double y, double z, double vx, double vy, double vz,
                                             float yRot, float xRot) {
        return new DimensionTransition(newLevel, x, y, z, vx, vy, vz, yRot, xRot, true,
                new Pos(Entity.floor(x), Entity.floor(y), Entity.floor(z)), false, true);
    }
}
