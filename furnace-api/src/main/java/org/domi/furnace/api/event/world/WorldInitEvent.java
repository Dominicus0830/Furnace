// 추가 월드가 목록에 올라온 직후, WorldLoadEvent 바로 앞(Bukkit WorldInitEvent). 서버 시작 때의 기본 세 차원에는 오지 않는다.
// Paper 는 스폰 준비 전에 보내므로 플러그인이 여기서 그 월드의 설정을 바꾸곤 한다(예: setKeepSpawnInMemory).
// Furnace 는 월드가 이미 틱을 돌기 시작한 뒤에 보낸다(비동기 createWorld 면 몇 틱 뒤). 설정을 바꿀 수는 있지만 그 사이의 틱은 이미 지나갔다.
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
