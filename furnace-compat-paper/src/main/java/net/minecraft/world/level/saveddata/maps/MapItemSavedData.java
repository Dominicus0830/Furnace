package net.minecraft.world.level.saveddata.maps;

/**
 * 지도 저장 데이터(Mojang 이름 shim). 아직 내포 타입 {@link MapPatch} 만 있다(easy-nms E4c-2) — 지도 데이터 자체의 멤버는 없다.
 */
public class MapItemSavedData {

    private MapItemSavedData() {}

    /**
     * 지도 색 조각(바닐라 1.21.1 과 같은 record — 시작 x·y, 너비, 높이, 색 바이트). 패킷에 쓸 때 바닐라는 네 수를 바이트 하나씩(아래 8비트)만 쓴다.
     * 받은 지도에 칠하는 applyToMap 은 지도 데이터 shim 이 없어 두지 않았다.
     */
    public record MapPatch(int startX, int startY, int width, int height, byte[] mapColors) {}
}
