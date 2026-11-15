// 추가 월드가 목록에 올라온 직후, WorldLoadEvent 바로 앞(Bukkit WorldInitEvent). 서버 시작 때의 기본 세 차원에는 오지 않는다.
// Paper 는 스폰 준비 전에 보내므로 플러그인이 여기서 그 월드의 설정을 바꾸곤 한다(예: setKeepSpawnInMemory).
package org.domi.furnace.api.event.world;

public final class WorldInitEvent extends WorldEvent {

    private final String name;

    public WorldInitEvent(String world, String name) {
        super(world);
        this.name = name;
    }

    /** 월드 이름(폴더 이름). */
    public String name() { return name; }
}
