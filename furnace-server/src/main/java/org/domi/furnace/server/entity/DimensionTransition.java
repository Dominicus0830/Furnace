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
                                  float yRot, float xRot, boolean portalSound, Pos ticketPos, boolean missingRespawnBlock) {

    /** 도착 뒤 처리 없음(DO_NOTHING). */
    public DimensionTransition(ChunkLevel newLevel, double x, double y, double z, double vx, double vy, double vz, float yRot, float xRot) {
        this(newLevel, x, y, z, vx, vy, vz, yRot, xRot, false, null, false);
    }

    /** PLAY_PORTAL_SOUND.then(PLACE_PORTAL_TICKET): 소리 + 도착 칸 티켓(Entity.placePortalTicket(blockPosition)). */
    public static DimensionTransition portal(ChunkLevel newLevel, double x, double y, double z, double vx, double vy, double vz,
                                             float yRot, float xRot) {
        return new DimensionTransition(newLevel, x, y, z, vx, vy, vz, yRot, xRot, true,
                new Pos(Entity.floor(x), Entity.floor(y), Entity.floor(z)), false);
    }
}
