// 지도 색 계산(MapItem.update)과 바이옴 미리보기: 고정 지형 → 손으로 계산한 색 바이트(색 번호 << 2 | 밝기), 한 틱의 일량 상한.
package org.domi.furnace.server.map;

import static org.junit.jupiter.api.Assertions.*;

import java.util.function.IntBinaryOperator;
import org.domi.furnace.server.block.B;
import org.junit.jupiter.api.Test;

class MapRendererTest {

    @org.junit.jupiter.api.BeforeAll
    static void installItems() {   // 블록 동작(가마솥 상호작용 등)이 아이템 번호를 쓴다
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    static final int GRASS = B.defaultState("grass_block"), SAND = B.defaultState("sand"), WATER = B.defaultState("water");
    static final int GRASS_ID = 1, SAND_ID = 2;

    /** 열마다 (바닥 블록, 바닥 y, 물 윗면 y — 없으면 바닥과 같다)를 정하는 평평한 시험 지형. */
    static final class Terrain implements MapRenderer.Terrain {
        final IntBinaryOperator floorY;      // (x, z) → 맨 위 단단한 블록 y
        final IntBinaryOperator waterTopY;   // (x, z) → 물 윗면 y(바닥과 같으면 물 없음)
        final int floorState;
        int heightCalls;
        MapData.Banner banner;   // 이 시험 지형에 서 있는 깃발(없으면 null)
        java.util.function.BiPredicate<Integer, Integer> available = (cx, cz) -> true;

        Terrain(int floorState, IntBinaryOperator floorY, IntBinaryOperator waterTopY) {
            this.floorState = floorState;
            this.floorY = floorY;
            this.waterTopY = waterTopY;
        }

        static Terrain flat(int state, int y) { return new Terrain(state, (x, z) -> y, (x, z) -> y); }

        @Override public boolean hasCeiling() { return false; }

        @Override public int minY() { return -64; }

        @Override public MapData.Banner bannerAt(int x, int y, int z) {
            return banner != null && banner.x() == x && banner.y() == y && banner.z() == z ? banner : null;
        }

        @Override public boolean chunkAvailable(int cx, int cz) { return available.test(cx, cz); }

        @Override public int surfaceHeight(int x, int z) {
            heightCalls++;
            return Math.max(floorY.applyAsInt(x, z), waterTopY.applyAsInt(x, z)) + 1;
        }

        @Override public int blockState(int x, int y, int z) {
            int floor = floorY.applyAsInt(x, z), top = waterTopY.applyAsInt(x, z);
            if (y <= floor) return floorState;
            if (y <= top) return WATER;
            return 0;
        }
    }

    static MapData.Holding holding(MapData d) {
        return d.holding(new MapData.Viewer() {
            public String name() { return "p"; }
            public boolean removed() { return false; }
            public String dimension() { return MapData.OVERWORLD; }
            public double x() { return 0; }
            public double z() { return 0; }
            public float yaw() { return 0; }
            public boolean holdsMap(java.util.function.Predicate<org.domi.furnace.server.entity.ItemStack> p) { return true; }
        });
    }

    static MapData fresh() { return MapData.createFresh(0, 0, (byte) 0, true, false, MapData.OVERWORLD); }

    @Test
    void blockMapColorsComeFromTheVanillaTable() {
        assertEquals(GRASS_ID, MapRenderer.mapColor(GRASS));
        assertEquals(SAND_ID, MapRenderer.mapColor(SAND));
        assertEquals(MapColor.WATER, MapRenderer.mapColor(WATER));
        assertEquals(MapColor.NONE, MapRenderer.mapColor(0), "공기는 색이 없다");
        assertEquals(MapColor.STONE, MapRenderer.mapColor(B.defaultState("bedrock")));
    }

    @Test
    void flatGrassPaintsNormalBrightnessEverywhere() {
        MapData d = fresh();
        MapRenderer.update(Terrain.flat(GRASS, 63), MapData.OVERWORLD, 0, 0, d, holding(d));
        // 첫 틱(step 1)은 열 1 부터 시작하고 바뀐 열이 다음 열을 이어서 칠해 오른쪽 전체가 한 번에 찬다(열 0 은 step 16 차례).
        // 높이 차가 0 이면 밝기는 ((x+z)&1 - 0.5)*0.4 = ±0.2 → NORMAL
        byte expected = MapColor.packed(GRASS_ID, MapColor.NORMAL);
        assertEquals(5, expected);
        for (int x = 1; x < 128; x++) for (int z = 0; z < 128; z++) assertEquals(expected, d.color(x, z), "x=" + x + " z=" + z);
        for (int z = 0; z < 128; z++) assertEquals(0, d.color(0, z), "열 0 은 아직");
    }

    @Test
    void risingAndFallingGroundShadesHighAndLow() {
        // 월드 z >= 0(지도 줄 64)에서 땅이 7 높아진다 → 그 줄(z=64)만 (70-63)*4/4 = 7 > 0.6 → HIGH
        MapData up = fresh();
        MapRenderer.update(new Terrain(GRASS, (x, z) -> z >= 0 ? 70 : 63, (x, z) -> z >= 0 ? 70 : 63), MapData.OVERWORLD, 0, 0, up, holding(up));
        assertEquals(MapColor.packed(GRASS_ID, MapColor.NORMAL), up.color(3, 63));
        assertEquals(MapColor.packed(GRASS_ID, MapColor.HIGH), up.color(3, 64));
        assertEquals(6, up.color(3, 64));
        assertEquals(MapColor.packed(GRASS_ID, MapColor.NORMAL), up.color(3, 65));
        // 내려가면 -3 < -0.6 → LOW
        MapData down = fresh();
        MapRenderer.update(new Terrain(GRASS, (x, z) -> z >= 0 ? 60 : 63, (x, z) -> z >= 0 ? 60 : 63), MapData.OVERWORLD, 0, 0, down, holding(down));
        assertEquals(MapColor.packed(GRASS_ID, MapColor.LOW), down.color(3, 64));
        assertEquals(4, down.color(3, 64));
    }

    @Test
    void waterShadeFollowsDepth() {
        // 물 윗면 62. 바닥 y=60 (깊이 2), 57 (깊이 5), 50 (깊이 12): v = 깊이*0.1 + ((x+z)&1)*0.2
        for (int[] c : new int[][] {{60, MapColor.HIGH}, {57, MapColor.NORMAL}, {50, MapColor.LOW}}) {
            MapData d = fresh();
            int floor = c[0];
            MapRenderer.update(new Terrain(SAND, (x, z) -> floor, (x, z) -> 62), MapData.OVERWORLD, 0, 0, d, holding(d));
            for (int x : new int[] {10, 11}) {
                assertEquals(MapColor.packed(MapColor.WATER, c[1]), d.color(x, 20), "바닥 " + floor + " x=" + x);
            }
        }
        // 깊이 12 → 48, 깊이 2 → 50 (손으로 계산한 값)
        MapData deep = fresh();
        MapRenderer.update(new Terrain(SAND, (x, z) -> 50, (x, z) -> 62), MapData.OVERWORLD, 0, 0, deep, holding(deep));
        assertEquals(48, deep.color(10, 20));
        MapData shallow = fresh();
        MapRenderer.update(new Terrain(SAND, (x, z) -> 60, (x, z) -> 62), MapData.OVERWORLD, 0, 0, shallow, holding(shallow));
        assertEquals(50, shallow.color(10, 20));
    }

    @Test
    void unavailableChunksAreSkipped() {
        MapData d = fresh();
        Terrain t = Terrain.flat(GRASS, 63);
        t.available = (cx, cz) -> !(cx >= 0 && cz >= 0);   // 청크 (0..)x(0..) 쪽은 없는 셈 — 지도 가운데(64,64)부터 오른쪽 아래
        MapRenderer.update(t, MapData.OVERWORLD, 0, 0, d, holding(d));
        assertEquals(0, d.color(100, 100));
        assertEquals(5, d.color(10, 10));
    }

    @Test
    void otherDimensionAndCeilingRules() {
        MapData d = fresh();
        MapRenderer.update(Terrain.flat(GRASS, 63), "minecraft:the_nether", 0, 0, d, holding(d));
        assertEquals(0, d.color(10, 10), "지도 차원이 아니면 칠하지 않는다");
    }

    @Test
    void oneTickOnlyRecomputesOneColumnInSixteen() {
        MapData d = fresh();
        Terrain t = Terrain.flat(GRASS, 63);
        var h = holding(d);
        MapRenderer.update(t, MapData.OVERWORLD, 0, 0, d, h);
        int first = t.heightCalls;
        assertTrue(first > 127 * 128, "처음에는 바뀐 열이 줄줄이 이어져 전체를 칠한다: " + first);
        t.heightCalls = 0;
        MapRenderer.update(t, MapData.OVERWORLD, 0, 0, d, h);
        // 바뀐 게 없으면 열 격자 (x & 15) == (step & 15) 인 열만(128/16 = 8열 x 130줄)
        assertTrue(t.heightCalls <= 9 * 130, "두 번째 틱 일량: " + t.heightCalls);
        assertTrue(t.heightCalls < first / 10);
    }

    @Test
    void biomePreviewDrawsWaterOutlineAndHatching() {
        // 배율 2 지도(중심 192) — 칸 x 의 블록 좌표 (x-16)*4: x < 16 이 물
        MapData d = MapData.createFresh(0, 0, (byte) 2, true, true, MapData.OVERWORLD);
        MapRenderer.renderBiomePreview(d, (bx, bz) -> bx < 0);
        assertEquals(192, d.centerX());
        // 가장자리 물 칸(x=15): 이웃 물 5 → 물색(COLOR_ORANGE=15) LOW
        assertEquals(MapColor.packed(MapColor.COLOR_ORANGE, MapColor.LOW), d.color(15, 60));
        assertEquals(60, d.color(15, 60));
        // 첫 육지 칸(x=16): 이웃 물 3 → COLOR_BROWN(26) LOWEST
        assertEquals(MapColor.packed(MapColor.COLOR_BROWN, MapColor.LOWEST), d.color(16, 60));
        assertEquals(107, d.color(16, 60));
        // 물 한가운데(x=8): 짝수 줄은 해칭 — sin(60)*7 = -2 → (8-2)/8 % 5 = 0 → LOW, 홀수 줄은 비움
        assertEquals(60, d.color(8, 60));
        assertEquals(0, d.color(8, 61));
        // 바다에서 먼 육지는 비어 있다
        assertEquals(0, d.color(100, 60));
        // 맨 바깥 테두리는 칠하지 않는다
        assertEquals(0, d.color(0, 60));
    }

    @Test
    void toggleBannerAddsThenRemovesAndHonoursRange() {
        MapData d = fresh();
        MapData.Banner red = new MapData.Banner(10, 70, -5, "red", "\"Home\"");
        assertTrue(d.toggleBanner(10, -5, red));
        // 위치는 칸 중심: f = 10.5 → (byte)(10.5*2+0.5) = 21, g = -4.5 → (byte)(-9+0.5) = -8, 회전 (180+8)*16/360 = 8.35 → 8
        MapData.Decoration deco = d.decorations().get("banner-10,70,-5");
        assertEquals(MapDecorationType.byName("banner_red"), deco.type());
        assertEquals(21, deco.x());
        assertEquals(-8, deco.y());
        assertEquals(8, deco.rot());
        assertNotNull(deco.name());
        // 같은 깃발을 다시 대면 지운다
        assertTrue(d.toggleBanner(10, -5, red));
        assertNull(d.decorations().get("banner-10,70,-5"));
        // 지도 밖: 블록 63 → f = 63.5 > 63 실패, 블록 62 → 62.5 성공
        assertFalse(d.toggleBanner(63, 0, new MapData.Banner(63, 70, 0, "white", null)));
        assertTrue(d.toggleBanner(62, 0, new MapData.Banner(62, 70, 0, "white", null)));
        // 깃발 블록 엔티티가 없으면(fromWorld = null) 실패
        assertFalse(d.toggleBanner(5, 5, null));
    }

    @Test
    void mapUpdateDropsBannerMarkerWhenTheBannerChanged() {
        MapData d = fresh();
        MapData.Banner red = new MapData.Banner(1, 64, 1, "red", null);
        assertTrue(d.toggleBanner(1, 1, red));
        Terrain t = Terrain.flat(GRASS, 63);
        t.banner = red;
        MapData.Holding h = holding(d);
        for (int i = 0; i < 16; i++) MapRenderer.update(t, MapData.OVERWORLD, 0, 0, d, h);   // 16틱이면 모든 열을 한 번씩 훑는다
        assertNotNull(d.decorations().get("banner-1,64,1"));
        // 같은 칸의 깃발 이름이 달라지면(= 다른 MapBanner) 표식을 지운다
        t.banner = new MapData.Banner(1, 64, 1, "red", "\"New\"");
        for (int i = 0; i < 16; i++) MapRenderer.update(t, MapData.OVERWORLD, 0, 0, d, h);
        assertNull(d.decorations().get("banner-1,64,1"));
    }
}
