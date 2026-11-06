// 지도 아이템: 만들기(번호 발급·저장 데이터), 제작 뒤처리(잠금·확대 → 새 번호), 들고 있는 플레이어·액자 표식.
package org.domi.furnace.server.map;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import java.util.function.Predicate;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class MapItemsTest {

    @TempDir Path dir;

    @BeforeEach
    void setUp() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        MapStore.install(dir);
    }

    @AfterEach
    void tearDown() { MapStore.uninstall(); }

    @Test
    void createIssuesIdsAndStoresFreshData() {
        ItemStack a = MapItems.create(MapData.OVERWORLD, 100, -300, (byte) 0, true, false);
        ItemStack b = MapItems.create(MapData.OVERWORLD, 100, -300, (byte) 2, true, true);
        assertTrue(MapItems.isFilledMap(a));
        assertEquals(0, MapItems.mapId(a));
        assertEquals(1, MapItems.mapId(b));
        MapData da = MapStore.of(a);
        assertEquals(128, da.centerX());   // 배율 0: (100+64)/128 = 1 → 128
        assertEquals(-256, da.centerZ());  // (-300+64)/128 = -2(내림) → -256
        assertEquals(0, da.scale());
        assertEquals(2, MapStore.of(b).scale());
        assertTrue(MapStore.of(b).unlimitedTracking());
        assertTrue(da.isDirty());
    }

    @Test
    void scalingMakesABiggerMapWithANewIdAndDropsTheMarker() {
        ItemStack base = MapItems.create(MapData.OVERWORLD, 0, 0, (byte) 1, true, false);   // 배율 1: 256 칸, 중심 (128-64=64 → 64,64)
        MapData orig = MapStore.of(base);
        assertEquals(64, orig.centerX());
        ItemStack marked = ItemTools.with(base, MapItems.POST_PROCESSING, "scale");
        ItemStack out = MapItems.postProcess(marked);
        assertNull(ItemTools.component(out, MapItems.POST_PROCESSING), "처리 표시는 떼어진다");
        assertNotEquals(MapItems.mapId(base), MapItems.mapId(out));
        MapData scaled = MapStore.of(out);
        assertEquals(2, scaled.scale());
        // createFresh(64, 64, 2): 512 칸 → (64+64)/512 = 0 → 중심 256-64 = 192
        assertEquals(192, scaled.centerX());
        assertEquals(192, scaled.centerZ());
        assertEquals(1, orig.scale(), "원래 지도는 그대로");
        // 표시가 없으면 아무것도 안 한다
        assertSame(base, MapItems.postProcess(base));
    }

    @Test
    void lockingCopiesColorsIntoALockedMapWithANewId() {
        ItemStack base = MapItems.create(MapData.OVERWORLD, 0, 0, (byte) 0, true, false);
        MapData orig = MapStore.of(base);
        orig.setColor(9, 9, (byte) 77);
        orig.addDecoration(MapDecorationType.TARGET_X, null, "x", 3, 3, 0, null);
        ItemStack out = MapItems.postProcess(ItemTools.with(base, MapItems.POST_PROCESSING, "lock"));
        assertNotEquals(MapItems.mapId(base), MapItems.mapId(out));
        MapData locked = MapStore.of(out);
        assertTrue(locked.locked());
        assertEquals(77, locked.color(9, 9));
        assertNotNull(locked.decorations().get("x"), "표식도 복사된다");
        assertFalse(orig.locked());
    }

    @Test
    void scaledMapCannotExceedFour() {
        MapData d = MapData.createFresh(0, 0, (byte) 4, true, false, MapData.OVERWORLD);
        assertEquals(4, d.scaled().scale());
    }

    @Test
    void carryingAddsThePlayerMarkerAndLosingTheMapRemovesIt() {
        ItemStack map = MapItems.create(MapData.OVERWORLD, 0, 0, (byte) 0, true, false);
        int id = MapItems.mapId(map);
        MapData d = MapStore.get(id);
        boolean[] holds = {true};
        var v = new MapPacketTest.Viewer() {
            @Override public boolean holdsMap(Predicate<ItemStack> p) { return holds[0] && p.test(map); }
        };
        v.x = 20;
        v.z = -10;
        v.yaw = 90;
        d.tickCarriedBy(v, map, id, null, 0L, Map.of());
        var deco = d.decorations().get("Dom");
        assertEquals(MapDecorationType.PLAYER, deco.type());
        assertEquals(40, deco.x());   // (byte)(20*2+0.5)
        assertEquals(4, deco.rot());
        // 가졌던 지도를 내려놓으면 표식이 사라진다
        holds[0] = false;
        d.tickCarriedBy(v, map, id, null, 0L, Map.of());
        assertNull(d.decorations().get("Dom"));
    }

    @Test
    void framedMapGetsAFrameMarkerButNoPlayerMarker() {
        ItemStack map = MapItems.create(MapData.OVERWORLD, 0, 0, (byte) 0, true, false);
        int id = MapItems.mapId(map);
        MapData d = MapStore.get(id);
        var v = new MapPacketTest.Viewer();
        d.tickCarriedBy(v, map, id, new MapData.Frame(5, 64, 6, 90, 33), 0L, Map.of());
        assertNull(d.decorations().get("Dom"));
        assertEquals(MapDecorationType.FRAME, d.decorations().get("frame-33").type());
        assertNotNull(d.frame(5, 64, 6));
    }
}
