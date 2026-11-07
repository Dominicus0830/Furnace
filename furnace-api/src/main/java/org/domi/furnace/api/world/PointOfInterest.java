// 관심 지점(POI) 한 칸 — 침대·직업 블록·종·포털 같은 주민 AI 의 목적지. 잰 순간의 값(불변).
package org.domi.furnace.api.world;

/**
 * @param type         POI 종류 키(minecraft:home, minecraft:farmer, minecraft:meeting …)
 * @param freeTickets  남은 티켓(주민이 점유하면 줄어든다)
 * @param maxTickets   그 종류의 최대 티켓
 */
public record PointOfInterest(String type, BlockPos pos, int freeTickets, int maxTickets) {

    /** 누군가 티켓을 잡고 있는가(바닐라 IS_OCCUPIED). */
    public boolean occupied() { return freeTickets != maxTickets; }

    /** 티켓이 남았는가(바닐라 HAS_SPACE). */
    public boolean hasSpace() { return freeTickets > 0; }
}
