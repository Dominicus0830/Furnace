// POI 조회 때 거르는 점유 상태(바닐라 PoiManager.Occupancy).
package org.domi.furnace.api.world;

public enum PoiOccupancy {
    /** 티켓이 남은 것. */
    HAS_SPACE,
    /** 티켓을 누가 잡고 있는 것(마을 중심 판정에 쓰는 조건). */
    IS_OCCUPIED,
    /** 상관없이 전부. */
    ANY
}
