package net.minecraft.world.level.saveddata.maps;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.map.CraftMapView;
import org.domi.furnace.api.internal.ChunkInternals;

/**
 * 지도 저장 데이터(Mojang 이름 shim). 읽을 때의 겉모양 사본이다 — 중심·차원·배율과 Paper 가 더한 {@code mapView} 필드만 있다(바닐라처럼 공개 final).
 * 색 바이트·표시·추적 등은 없다(없는 멤버 — 호출 위치에서 실패). 값 바꾸기는 서버 지도 쪽 일이라 이 사본에는 영향이 없다.
 */
public class MapItemSavedData {

    public final int centerX;
    public final int centerZ;
    public final ResourceKey<Level> dimension;
    public final byte scale;
    /** CraftBukkit 이 더한 필드. */
    public final CraftMapView mapView;

    private MapItemSavedData(int id, ChunkInternals.MapInfo info) {
        this.centerX = info.centerX();
        this.centerZ = info.centerZ();
        this.dimension = ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(info.dimension()));
        this.scale = info.scale();
        this.mapView = new CraftMapView(id);
    }

    /** 호환 모듈 전용 — 서버 지도의 겉모양으로 만든다. */
    public static MapItemSavedData furnaceOf(int id, ChunkInternals.MapInfo info) { return new MapItemSavedData(id, info); }

    /**
     * 지도 색 조각(바닐라 1.21.1 과 같은 record — 시작 x·y, 너비, 높이, 색 바이트). 패킷에 쓸 때 바닐라는 네 수를 바이트 하나씩(아래 8비트)만 쓴다.
     * 받은 지도에 칠하는 applyToMap 은 지도 데이터 shim 이 없어 두지 않았다.
     */
    public record MapPatch(int startX, int startY, int width, int height, byte[] mapColors) {}
}
