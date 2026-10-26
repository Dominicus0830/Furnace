// 바닐라 PoiType: 관심 지점 종류(레지스트리 이름, 최대 티켓 수, 유효 거리). 블록 상태 대응은 PoiTypes 가 들고 있다.
package org.domi.furnace.server.world.poi;

public final class PoiType {
    private final String name;
    private final int index;
    private final int maxTickets;
    private final int validRange;

    PoiType(String name, int index, int maxTickets, int validRange) {
        this.name = name;
        this.index = index;
        this.maxTickets = maxTickets;
        this.validRange = validRange;
    }

    /** 레지스트리 이름(minecraft:home 등). */
    public String name() { return name; }

    /** 레지스트리 순번(PoiTypes.bootstrap 등록 순서). */
    public int index() { return index; }

    public int maxTickets() { return maxTickets; }

    public int validRange() { return validRange; }

    @Override
    public String toString() { return name; }
}
