// 지옥문: 좌표 비(오버월드 → 네더 1/8, 네더 → 오버월드 8), 목적지에 지옥문이 없으면 만들고(흑요석 틀), 있으면 가까운 것으로 간다.
// 도착 위치는 원천 포털 사각형 안의 상대 위치를 출구 사각형에 옮긴 것(바닐라 NetherPortalBlock.createDimensionTransition).
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockUtil;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.NetherPortalBlock;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class NetherPortalTravelTest {

    /** 시험용 네더: 슈퍼플랫 높이(-64..319)를 그대로 쓰되 좌표 비 8. */
    static final DimensionType TEST_NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    private static DimensionTestLevel[] levels() {
        var over = new DimensionTestLevel(NetherPortalBlock.OVERWORLD);
        var nether = new DimensionTestLevel(NetherPortalBlock.NETHER);
        nether.type = TEST_NETHER;
        DimensionTestLevel.link(over, nether);
        return new DimensionTestLevel[] {over, nether};
    }

    private static int portalX() { return B.prop("axis").with(B.defaultState("minecraft:nether_portal"), "x"); }

    /** 틀 없는 1×2 지옥문(모양 갱신 없이 — 플래그 18). */
    private static void portalColumn(DimensionTestLevel l, int x, int y, int z) {
        l.setBlock(new Pos(x, y, z), portalX(), 18);
        l.setBlock(new Pos(x, y + 1, z), portalX(), 18);
    }

    private static Entity only(DimensionTestLevel level, Class<?> c) {
        return level.entities().tickSnapshot().stream().filter(c::isInstance).filter(e -> !e.isRemoved()).findFirst().orElse(null);
    }

    @Test
    void teleportationScaleIsCoordinateScaleRatio() {
        assertEquals(0.125, DimensionType.teleportationScale(DimensionType.OVERWORLD, DimensionType.NETHER), 0.0);
        assertEquals(8.0, DimensionType.teleportationScale(DimensionType.NETHER, DimensionType.OVERWORLD), 0.0);
        assertEquals(1.0, DimensionType.teleportationScale(DimensionType.OVERWORLD, DimensionType.END), 0.0);
    }

    @Test
    void pigEntersOverworldPortal_netherPortalIsCreatedAtEighthCoordinates() {
        var l = levels();
        var over = l[0];
        var nether = l[1];
        portalColumn(over, 16, -60, 0);
        Pig pig = new Pig(over, over.nextEntityId());
        pig.setNoAi(true);   // 걸어 다니지 않게 — 상대 위치가 정확히 가운데
        pig.moveTo(16.5, -60.0, 0.5, 30.0F, 0.0F);
        over.addFreshEntity(pig);
        over.tickEntities(3);
        assertTrue(pig.isRemoved(), "돼지는 네더로 갔다");
        Entity arrived = only(nether, Pig.class);
        assertNotNull(arrived);
        // 출구 (16.5/8, −60, 0.5/8) → (2, −60, 0): 나선 중심에서 틀을 세운다 — 지옥문 (2..3, −60..−58, 0), 흑요석 틀
        for (int x = 2; x <= 3; x++) for (int y = -60; y <= -58; y++) {
            assertTrue(B.is(nether.getBlockState(new Pos(x, y, 0)), "minecraft:nether_portal"), "지옥문 " + x + "," + y);
        }
        for (int x = 1; x <= 4; x++) {
            assertTrue(B.is(nether.getBlockState(new Pos(x, -61, 0)), "minecraft:obsidian"));
            assertTrue(B.is(nether.getBlockState(new Pos(x, -57, 0)), "minecraft:obsidian"));
        }
        for (int y = -60; y <= -58; y++) {
            assertTrue(B.is(nether.getBlockState(new Pos(1, y, 0)), "minecraft:obsidian"));
            assertTrue(B.is(nether.getBlockState(new Pos(4, y, 0)), "minecraft:obsidian"));
        }
        // 원천 1×2 사각형 안 가로 0.5, 세로 0 → 출구 2×3 의 가운데 바닥: (2 + 0.45 + 1.1·0.5, −60, 0.5)
        assertEquals(3.0, arrived.x(), 1e-9);
        assertEquals(-60.0, arrived.y(), 1e-5);   // 몸 상자를 1e-6 줄여 찾으므로 1e-6 위
        assertEquals(0.5, arrived.z(), 1e-9);
        assertEquals(30.0F, arrived.yRot(), 1e-6, "같은 축이면 돌지 않는다");
        assertTrue(arrived.isOnPortalCooldown());
    }

    @Test
    void pigLeavesNether_findsClosestExistingOverworldPortal() {
        var l = levels();
        var over = l[0];
        var nether = l[1];
        portalColumn(over, 85, -60, 85);   // 출구 (84, −60, 84) 에서 가장 가까운 지옥문
        portalColumn(nether, 10, -60, 10);
        Pig pig = new Pig(nether, nether.nextEntityId());
        pig.setNoAi(true);   // 걸어 다니지 않게 — 상대 위치가 정확히 가운데
        pig.moveTo(10.5, -60.0, 10.5, 0.0F, 0.0F);
        nether.addFreshEntity(pig);
        nether.tickEntities(3);
        Entity arrived = only(over, Pig.class);
        assertNotNull(arrived, "오버월드에 도착");
        assertEquals(85.5, arrived.x(), 1e-9);
        assertEquals(-60.0, arrived.y(), 1e-5);   // 몸 상자를 1e-6 줄여 찾으므로 1e-6 위
        assertEquals(85.5, arrived.z(), 1e-9);
        assertFalse(B.is(over.getBlockState(new Pos(84, -61, 84)), "minecraft:obsidian"), "새 지옥문을 만들지 않았다");
    }

    @Test
    void perpendicularExitRotatesNinetyDegrees() {
        var l = levels();
        var over = l[0];
        var nether = l[1];
        int portalZ = B.prop("axis").with(B.defaultState("minecraft:nether_portal"), "z");
        nether.setBlock(new Pos(2, -60, 2), portalZ, 18);
        nether.setBlock(new Pos(2, -59, 2), portalZ, 18);
        portalColumn(over, 20, -60, 20);
        Pig pig = new Pig(over, over.nextEntityId());
        pig.setNoAi(true);   // 걸어 다니지 않게 — 상대 위치가 정확히 가운데
        pig.moveTo(20.5, -60.0, 20.5, 10.0F, 0.0F);
        over.addFreshEntity(pig);
        over.tickEntities(3);
        Entity arrived = only(nether, Pig.class);
        assertNotNull(arrived);
        assertEquals(100.0F, arrived.yRot(), 1e-4, "축이 다르면 +90도");
        assertEquals(2.5, arrived.x(), 1e-9);
        assertEquals(2.5, arrived.z(), 1e-9);
    }

    @Test
    void largestRectangleAroundFindsWholePortal() {
        var over = new DimensionTestLevel(NetherPortalBlock.OVERWORLD);
        for (int x = 0; x < 3; x++) for (int y = -60; y < -56; y++) over.setBlock(new Pos(x, y, 5), portalX(), 18);
        int s = over.getBlockState(new Pos(1, -58, 5));
        var r = BlockUtil.getLargestRectangleAround(new Pos(1, -58, 5), Dir.AXIS_X, 21, Dir.AXIS_Y, 21, p -> over.getBlockState(p) == s);
        assertEquals(new Pos(0, -60, 5), r.minCorner());
        assertEquals(3, r.axis1Size());
        assertEquals(4, r.axis2Size());
    }
}
