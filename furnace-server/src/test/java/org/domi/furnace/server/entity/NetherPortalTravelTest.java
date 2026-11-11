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
        pig.goalSelector().removeAllGoals(g -> true);   // 걸어 다니지 않게(목표를 비운다 — 바닐라 NoAI 몹은 move 하지 않아 포털에 들어가지 못한다)
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
        assertEquals(-60.0, arrived.y(), 1e-5);   // 몸 상자를 1e-6 늘려 찾는다(바닐라 inflate) — 오차 안
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
        pig.goalSelector().removeAllGoals(g -> true);   // 걸어 다니지 않게(목표를 비운다 — 바닐라 NoAI 몹은 move 하지 않아 포털에 들어가지 못한다)
        pig.moveTo(10.5, -60.0, 10.5, 0.0F, 0.0F);
        nether.addFreshEntity(pig);
        nether.tickEntities(3);
        Entity arrived = only(over, Pig.class);
        assertNotNull(arrived, "오버월드에 도착");
        assertEquals(85.5, arrived.x(), 1e-9);
        assertEquals(-60.0, arrived.y(), 1e-5);   // 몸 상자를 1e-6 늘려 찾는다(바닐라 inflate) — 오차 안
        assertEquals(85.5, arrived.z(), 1e-9);
        assertFalse(B.is(over.getBlockState(new Pos(84, -61, 84)), "minecraft:obsidian"), "새 지옥문을 만들지 않았다");
    }

    /** 산 것은 깊이 오프셋을 0 으로(LivingEntity.getRelativePortalPosition) — x 축 포털: 폭(x) 상대 위치는 보존, z 는 출구 가운데. */
    @Test
    void livingEntityArrivesAtPortalDepthCenter_xAxis() {
        var l = levels();
        var over = l[0];
        var nether = l[1];
        portalColumn(over, 85, -60, 85);
        portalColumn(over, 86, -60, 85);   // 2 폭 출구 (85..86, 85)
        portalColumn(nether, 10, -60, 10);
        portalColumn(nether, 11, -60, 10);
        portalColumn(nether, 12, -60, 10);
        portalColumn(nether, 13, -60, 10);   // 4 폭 원천 (10..13, 10)
        Pig pig = new Pig(nether, nether.nextEntityId());
        pig.goalSelector().removeAllGoals(g -> true);   // 걸어 다니지 않게(목표를 비운다 — 바닐라 NoAI 몹은 move 하지 않아 포털에 들어가지 못한다)
        pig.moveTo(11.0, -60.0, 10.85, 0.0F, 0.0F);   // 폭 (11 − 10.45)/3.1, 깊이 +0.35
        nether.addFreshEntity(pig);
        nether.tickEntities(3);
        Entity arrived = only(over, Pig.class);
        assertNotNull(arrived);
        double w = 0.9F;   // 몸 폭은 float(EntityDimensions)
        assertEquals(85 + w / 2 + (2 - w) * ((11.0 - (10 + w / 2)) / (4 - w)), arrived.x(), 1e-12);
        assertEquals(85.5, arrived.z(), 1e-9, "깊이는 가운데");
    }

    /** z 축 포털: 폭(z) 보존, 깊이(x) 가운데. */
    @Test
    void livingEntityArrivesAtPortalDepthCenter_zAxis() {
        var l = levels();
        var over = l[0];
        var nether = l[1];
        int pz = B.prop("axis").with(B.defaultState("minecraft:nether_portal"), "z");
        for (int z = 20; z <= 22; z++) for (int y = -60; y <= -58; y++) over.setBlock(new Pos(20, y, z), pz, 18);
        for (int z = 1; z <= 3; z++) for (int y = -60; y <= -58; y++) nether.setBlock(new Pos(2, y, z), pz, 18);
        Pig pig = new Pig(over, over.nextEntityId());
        pig.goalSelector().removeAllGoals(g -> true);   // 걸어 다니지 않게(목표를 비운다 — 바닐라 NoAI 몹은 move 하지 않아 포털에 들어가지 못한다)
        pig.moveTo(20.2, -60.0, 21.0, 0.0F, 0.0F);   // 폭 (21 − 20.45)/2.1, 깊이 −0.3
        over.addFreshEntity(pig);
        over.tickEntities(3);
        Entity arrived = only(nether, Pig.class);
        assertNotNull(arrived);
        assertEquals(2.5, arrived.x(), 1e-9, "깊이는 가운데");
        double w = 0.9F;
        assertEquals(1 + w / 2 + (3 - w) * ((21.0 - (20 + w / 2)) / (3 - w)), arrived.z(), 1e-12);
    }

    @Test
    void depthResetOnlyForLivingMinecartBoat() {
        var l = levels();
        assertTrue(Traveler.of(new Pig(l[0], 1)).resetPortalDepth());
        assertFalse(Traveler.of(new ItemEntity(l[0], 2)).resetPortalDepth(), "아이템은 깊이 오프셋 유지");
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
        pig.goalSelector().removeAllGoals(g -> true);   // 걸어 다니지 않게(목표를 비운다 — 바닐라 NoAI 몹은 move 하지 않아 포털에 들어가지 못한다)
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

    @Test
    void spectatorCannotTriggerPortals() {
        var p = new Player(1, java.util.UUID.randomUUID(), "A", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        assertTrue(org.domi.furnace.server.world.Region.canTriggerPortals(p));
        p.vitals().setGameMode(org.domi.furnace.server.world.GameMode.SPECTATOR);
        assertFalse(org.domi.furnace.server.world.Region.canTriggerPortals(p), "관전자는 포털을 탈 수 없다");
        p.vitals().setGameMode(org.domi.furnace.server.world.GameMode.CREATIVE);
        assertTrue(org.domi.furnace.server.world.Region.canTriggerPortals(p));
    }
}
