// 추가 월드가 올라와 틱을 돌기 시작한 직후(Bukkit WorldLoadEvent). 서버 시작 때의 기본 세 차원에는 오지 않는다 — 그때는 ServerLoadEvent.
package org.domi.furnace.api.event.world;

public final class WorldLoadEvent extends WorldEvent {

    private final String name;

    public WorldLoadEvent(String world, String name) {
        super(world);
        this.name = name;
    }

    /** 월드 이름(폴더 이름). */
    public String name() { return name; }
}
