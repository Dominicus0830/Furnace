package net.minecraft.world.level.saveddata.maps;

import net.minecraft.resources.ResourceKey;
import net.minecraft.resources.ResourceLocation;
import net.minecraft.world.level.Level;
import org.bukkit.craftbukkit.map.CraftMapView;
import org.domi.furnace.api.internal.ChunkInternals;

/**
 * 지도 저장 데이터(Mojang 이름 shim). 서버 지도({@code MapStore})의 **살아 있는 보기**다: {@link #colors} 는 서버 지도가 쓰는 배열 그 자체라 쓰면 바로 서버 지도가 바뀌고
 * (바닐라와 같다 — 플러그인이 공개 배열에 직접 그린다), 직접 고친 칸은 {@link #setColorsDirty}로 알려 저장 대상에 올리고 지도를 든 플레이어에게 보낸다.
 * 중심·차원·배율·추적·잠금은 만들 때 정해진 값이다(바닐라도 final). 표시·깃발·추적 목록은 없다(없는 멤버 — 호출 위치에서 실패).
 * <p>{@link #createFresh} 로 만든 지도는 아직 서버에 없는(번호 없는) 객체이고 {@code Level#setMapData} 로 서버에 둔 뒤 같은 배열을 공유한다.
 * {@code Level#getMapData} 는 부를 때마다 새 보기 객체를 주므로(같은 번호여도 객체가 다르다) 객체 동일성 비교는 하지 않는다.
 */
public class MapItemSavedData {

    public final int centerX;
    public final int centerZ;
    public final ResourceKey<Level> dimension;
    public final byte scale;
    public final boolean trackingPosition;
    public final boolean unlimitedTracking;
    public final boolean locked;
    /** 바닐라 공개 필드: 128*128 색 바이트(서버 지도의 배열 그대로). */
    public byte[] colors;
    /** CraftBukkit 이 더한 필드. */
    public final CraftMapView mapView;

    /** 서버 지도의 번호(아직 서버에 없으면 -1). */
    private volatile int furnaceId;

    private MapItemSavedData(int id, int centerX, int centerZ, ResourceKey<Level> dimension, byte scale, boolean tracking, boolean unlimited, boolean locked, byte[] colors) {
        this.furnaceId = id;
        this.centerX = centerX;
        this.centerZ = centerZ;
        this.dimension = dimension;
        this.scale = scale;
        this.trackingPosition = tracking;
        this.unlimitedTracking = unlimited;
        this.locked = locked;
        this.colors = colors;
        this.mapView = new CraftMapView(id);
    }

    private static ResourceKey<Level> dimensionOf(String name) {
        return ResourceKey.create(net.minecraft.core.registries.Registries.DIMENSION, ResourceLocation.parse(name));
    }

    /** 번호마다 보기 하나(같은 번호는 늘 같은 객체 — 플러그인이 객체를 열쇠로 기억해도 되고, 옛 객체 쓰기가 새 지도로 가지 않는다). */
    private static final java.util.concurrent.ConcurrentHashMap<Integer, MapItemSavedData> VIEWS = new java.util.concurrent.ConcurrentHashMap<>();

    /**
     * 호환 모듈 전용 — 그 번호의 보기(없는 번호면 null). 서버 지도의 색 배열이 바뀌었으면(지도가 새로 놓임) 새 보기를 만들어 바꾼다.
     * {@code Level#setMapData} 로 넣은 객체는 그대로 돌려준다.
     */
    public static MapItemSavedData furnaceOf(int id, ChunkInternals.MapInfo info) {
        byte[] colors = org.domi.furnace.compat.nms.NmsChunks.bridge().mapColors(id);
        if (colors == null) {
            VIEWS.remove(id);
            return null;
        }
        MapItemSavedData cached = VIEWS.get(id);
        if (cached != null && cached.colors == colors) return cached;
        MapItemSavedData made = new MapItemSavedData(id, info.centerX(), info.centerZ(), dimensionOf(info.dimension()), info.scale(), info.trackingPosition(),
                info.unlimitedTracking(), info.locked(), colors);
        VIEWS.put(id, made);
        return made;
    }

    /**
     * 바닐라 createFresh: 위치를 배율 격자에 맞춘 중심의 빈 지도. 색은 모두 0(투명). 번호가 없다 — {@code Level#setMapData} 로 서버에 둔다.
     */
    public static MapItemSavedData createFresh(double x, double z, byte scale, boolean trackingPosition, boolean unlimitedTracking, ResourceKey<Level> dimension) {
        int size = 128 * (1 << scale);
        int ix = (int) Math.floor((x + 64.0) / size);
        int iz = (int) Math.floor((z + 64.0) / size);
        int cx = ix * size + size / 2 - 64;
        int cz = iz * size + size / 2 - 64;
        return new MapItemSavedData(-1, cx, cz, dimension, scale, trackingPosition, unlimitedTracking, false, new byte[128 * 128]);
    }

    /** 호환 모듈 전용 — {@code Level#setMapData} 가 서버에 둘 값(중심·차원·배율·추적·잠금). */
    public ChunkInternals.MapInfo furnaceInfo() {
        return new ChunkInternals.MapInfo(centerX, centerZ, dimension.location().toString(), scale, trackingPosition, unlimitedTracking, locked);
    }

    /** 호환 모듈 전용 — 서버에 둔 뒤 그 번호에 붙는다(이후 직접 고친 칸을 서버에 알린다). */
    public void furnaceAttach(int id) {
        this.furnaceId = id;
        VIEWS.put(id, this);
    }

    /** 바닐라 setColor: 칸 하나를 쓰고 바뀐 칸으로 알린다(0 <= x, y < 128). */
    public void setColor(int x, int y, byte color) {
        colors[x + y * 128] = color;
        setColorsDirty(x, y);
    }

    /** 바닐라 updateColor: 다르면 쓰고 참. */
    public boolean updateColor(int x, int y, byte color) {
        if (colors[x + y * 128] == color) return false;
        setColor(x, y, color);
        return true;
    }

    /** 바닐라 setColorsDirty: 배열을 직접 고친 칸을 알린다. 서버에 없는 지도(번호 없음)는 둘 때 통째로 올라가므로 할 일이 없다. */
    public void setColorsDirty(int x, int y) {
        int id = furnaceId;
        if (id >= 0) org.domi.furnace.compat.nms.NmsChunks.bridge().mapMarkColorsDirty(id, x, y);
    }

    /** 바닐라 SavedData#setDirty: 저장 표시만 한다(플레이어에게 다시 보내지 않는다). 번호가 없는 지도는 둘 때 통째로 올라가므로 할 일이 없다. */
    public void setDirty() {
        int id = furnaceId;
        if (id >= 0) org.domi.furnace.compat.nms.NmsChunks.bridge().mapMarkDirty(id);
    }

    /**
     * 지도 색 조각(바닐라 1.21.1 과 같은 record — 시작 x·y, 너비, 높이, 색 바이트). 패킷에 쓸 때 바닐라는 네 수를 바이트 하나씩(아래 8비트)만 쓴다.
     * 받은 지도에 칠하는 applyToMap 은 지도 데이터 shim 이 없어 두지 않았다.
     */
    public record MapPatch(int startX, int startY, int width, int height, byte[] mapColors) {}
}
