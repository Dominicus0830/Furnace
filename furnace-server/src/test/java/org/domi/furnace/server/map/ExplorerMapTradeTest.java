// 제도사 탐험 지도 거래(바다 탐험·삼림 탐험·시련 지도): 가짜 구조물 위치로 만든 지도가 구조물 방향 표식·바이옴 바탕·지도 색·이름을 가진다.
package org.domi.furnace.server.map;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.Map;
import org.domi.furnace.server.block.LegacyRandomSource;
import org.domi.furnace.server.entity.ItemStack;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.item.ItemTools;
import org.domi.furnace.server.trade.MerchantOffer;
import org.domi.furnace.server.trade.VillagerTrades;
import org.domi.furnace.server.trade.VillagerTrades.TradeContext;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExplorerMapTradeTest {

    @TempDir Path dir;

    @BeforeEach
    void setUp() {
        ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
        MapStore.install(dir);
    }

    @AfterEach
    void tearDown() { MapStore.uninstall(); }

    /** 구조물이 (1000, 2000) 에 하나 있는 가짜 월드. 물 윤곽은 x<0. */
    static final class FakeBackend implements ExplorerMaps.Backend {
        String lastTag;
        int[] origin;
        boolean found = true;

        public int[] findNearestMapStructure(String tag, int x, int y, int z, int radius) {
            lastTag = tag;
            origin = new int[] {x, y, z, radius};
            return found ? new int[] {1000, 2000} : null;
        }

        public boolean waterOnMapOutlines(int bx, int bz) { return bx < 1000; }

        public String dimension() { return MapData.OVERWORLD; }
    }

    @Test
    void cartographerMapPointsAtTheStructure() {
        FakeBackend backend = new FakeBackend();
        var ctx = new TradeContext("plains", null, 10, 70, -20, backend);
        MerchantOffer offer = VillagerTrades.TRADES.get("cartographer")[1][1].getOffer(ctx, new LegacyRandomSource(1));   // 레벨 2: 바다 탐험 지도
        assertNotNull(offer);
        assertEquals("minecraft:on_ocean_explorer_maps", backend.lastTag);
        assertArrayEquals(new int[] {10, 70, -20, 100}, backend.origin);
        assertEquals("minecraft:emerald", offer.baseCostA().item());
        assertEquals(13, offer.baseCostA().count());
        assertEquals("minecraft:compass", offer.costB().orElseThrow().item());
        assertEquals(12, offer.maxUses());
        assertEquals(5, offer.xp());
        ItemStack map = offer.result();
        assertTrue(MapItems.isFilledMap(map));
        assertEquals("{\"translate\":\"filled_map.monument\"}", ItemTools.component(map, "minecraft:item_name"));
        assertEquals(3830373, ItemTools.component(map, "minecraft:map_color"));
        // 목표 표식 "+" — 위치는 구조물 좌표, 회전 180
        Map<?, ?> decos = (Map<?, ?>) ItemTools.component(map, MapItems.DECORATIONS);
        Map<?, ?> target = (Map<?, ?>) decos.get("+");
        assertEquals("minecraft:monument", target.get("type"));
        assertEquals(1000.0, ((Number) target.get("x")).doubleValue());
        assertEquals(2000.0, ((Number) target.get("z")).doubleValue());
        // 지도 데이터: 배율 2, 구조물을 담는 512 칸 격자(중심 (1216, 2240)), 위치·무제한 추적
        MapData data = MapStore.of(map);
        assertEquals(2, data.scale());
        assertEquals(1216, data.centerX());
        assertEquals(2240, data.centerZ());
        assertTrue(data.trackingPosition() && data.unlimitedTracking());
        // 바이옴 바탕: 물 윤곽(x<1000 → 지도 칸 x < (1000-1216)/4+64 = 10)이 칠해져 있고 가장자리 갈색선이 있다
        assertEquals(MapColor.packed(MapColor.COLOR_BROWN, MapColor.LOWEST), data.color(10, 60));
        assertEquals(MapColor.packed(MapColor.COLOR_ORANGE, MapColor.LOW), data.color(9, 60));
        // 처음 들고 있을 때 스택의 목표 표식이 지도 칸으로 환산돼 구조물 방향을 가리킨다: x = (byte)((1000-1216)/4*2+0.5) = -107
        var v = new MapPacketTest.Viewer();
        v.x = 1216;
        v.z = 2240;
        data.tickCarriedBy(v, map, MapItems.mapId(map), null, 0L, MapItems.markers(map));
        var plus = data.decorations().get("+");
        assertEquals(MapDecorationType.MONUMENT, plus.type());
        assertEquals(-107, plus.x());
        assertEquals(-119, plus.y());
        assertEquals(8, plus.rot());   // (180+8)*16/360 = 8.35
        assertTrue(data.isExplorationMap());
    }

    @Test
    void mansionAndTrialChamberTradesAndMissingStructure() {
        FakeBackend backend = new FakeBackend();
        var ctx = new TradeContext("plains", null, 0, 64, 0, backend);
        MerchantOffer mansion = VillagerTrades.TRADES.get("cartographer")[2][1].getOffer(ctx, new LegacyRandomSource(1));
        assertEquals("minecraft:on_woodland_explorer_maps", backend.lastTag);
        assertEquals(14, mansion.baseCostA().count());
        assertEquals(10, mansion.xp());
        Map<?, ?> t = (Map<?, ?>) ((Map<?, ?>) ItemTools.component(mansion.result(), MapItems.DECORATIONS)).get("+");
        assertEquals("minecraft:mansion", t.get("type"));
        MerchantOffer trial = VillagerTrades.TRADES.get("cartographer")[2][2].getOffer(ctx, new LegacyRandomSource(1));
        assertEquals("minecraft:on_trial_chambers_maps", backend.lastTag);
        assertEquals(12, trial.baseCostA().count());
        assertEquals(12741452, ItemTools.component(trial.result(), "minecraft:map_color"));
        // 구조물을 못 찾으면 거래가 없다
        backend.found = false;
        assertNull(VillagerTrades.TRADES.get("cartographer")[2][1].getOffer(ctx, new LegacyRandomSource(1)));
        // 탐험 지도 지원이 없으면(월드 생성기 없음) 거래가 없다
        assertNull(VillagerTrades.TRADES.get("cartographer")[2][1].getOffer(new TradeContext("plains", null), new LegacyRandomSource(1)));
    }
}
