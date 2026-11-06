// 포털 이벤트 취소(이벤트 취소 계약, docs/api/event-cancel-contract.md): EntityPortalEvent·PortalCreateEvent 를 취소하면 상태가 그대로다.
// 서버 차원 이동 경로는 대역(DimensionTravel)으로 갈아 끼워 "넘어가려 했는가"만 센다. 구독자가 없을 때(허용)와 짝으로 본다.
package org.domi.furnace.server.entity;

import static org.junit.jupiter.api.Assertions.*;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.ChunkLevel;
import org.domi.furnace.server.block.Dir;
import org.domi.furnace.server.block.LevelServices;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.types.NetherPortalBlock;
import org.domi.furnace.server.block.types.PortalShape;
import org.domi.furnace.server.plugin.GameEvents;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;

class PortalCancelTest {

    @BeforeAll
    static void install() {
        org.domi.furnace.server.item.ItemContext.install(new org.domi.furnace.server.data.TagRegistries(
                org.domi.furnace.server.net.registry.RegistrySet.load("registries/1.21.1.txt")));
    }

    /** 넘어가려는 횟수만 센다. */
    static final class CountingTravel implements LevelServices.DimensionTravel {
        final AtomicInteger travels = new AtomicInteger();
        @Override public boolean isLevelEnabled(String dimension) { return true; }
        @Override public void travel(PortalTravel plan, BiConsumer<ChunkLevel, DimensionTransition> arrive) { travels.incrementAndGet(); }
        @Override public void runAtSource(int blockX, int blockZ, Consumer<ChunkLevel> task) { task.accept(null); }
    }

    private static int portalX() { return B.prop("axis").with(B.defaultState("minecraft:nether_portal"), "x"); }

    private static int walkPigIntoPortal(boolean allow, CountingTravel travel, int[] asked) {
        var over = new DimensionTestLevel(NetherPortalBlock.OVERWORLD);
        var nether = new DimensionTestLevel(NetherPortalBlock.NETHER);
        nether.type = NetherPortalTravelTest.TEST_NETHER;
        DimensionTestLevel.link(over, nether);
        over.travel = travel;
        over.events = new GameEvents() {
            @Override public boolean entityPortal(Entity e, String toDimension, int cause) {
                asked[0]++;
                assertEquals(NetherPortalBlock.NETHER, toDimension);
                assertEquals(GameEvents.PORTAL_NETHER, cause);
                return allow;
            }
        };
        over.setBlock(new Pos(16, -60, 0), portalX(), 18);
        over.setBlock(new Pos(16, -59, 0), portalX(), 18);
        Pig pig = new Pig(over, over.nextEntityId());
        pig.setNoAi(true);
        pig.moveTo(16.5, -60.0, 0.5, 30.0F, 0.0F);
        over.addFreshEntity(pig);
        over.tickEntities(3);
        assertFalse(pig.isRemoved() && !allow, "취소했으면 돼지는 그대로");
        return travel.travels.get();
    }

    @Test
    void entityPortalCancelStopsTheTravel() {
        var travel = new CountingTravel();
        int[] asked = {0};
        assertEquals(0, walkPigIntoPortal(false, travel, asked), "취소하면 차원 이동을 시작하지 않는다");
        assertTrue(asked[0] >= 1, "이벤트는 불렸다");
    }

    @Test
    void entityPortalAllowedStartsTheTravel() {
        var travel = new CountingTravel();
        int[] asked = {0};
        assertEquals(1, walkPigIntoPortal(true, travel, asked), "대조: 허용하면 한 번 넘어간다");
        assertEquals(1, asked[0]);
    }

    private static DimensionTestLevel frame(boolean allow, int[] asked) {
        var level = new DimensionTestLevel(NetherPortalBlock.OVERWORLD);
        level.events = new GameEvents() {
            @Override public boolean portalCreate(String dimension, java.util.List<Pos> blocks) {
                asked[0] += blocks.size();
                return allow;
            }
        };
        int obsidian = B.defaultState("minecraft:obsidian");
        for (int x = 0; x <= 3; x++) { level.setBlock(new Pos(x, -61, 0), obsidian, 18); level.setBlock(new Pos(x, -57, 0), obsidian, 18); }
        for (int y = -60; y <= -58; y++) { level.setBlock(new Pos(0, y, 0), obsidian, 18); level.setBlock(new Pos(3, y, 0), obsidian, 18); }
        return level;
    }

    @Test
    void portalCreateCancelLeavesTheFrameEmpty() {
        int[] asked = {0};
        var level = frame(false, asked);
        var shape = PortalShape.findEmpty(level, new Pos(1, -60, 0), Dir.AXIS_X);
        assertNotNull(shape, "2×3 틀");
        shape.createPortalBlocks();
        assertEquals(6, asked[0], "틀 안 6칸이 이벤트에 실린다");
        for (int x = 1; x <= 2; x++) for (int y = -60; y <= -58; y++)
            assertTrue(B.isAir(level.getBlockState(new Pos(x, y, 0))), "취소했으면 안은 공기 " + x + "," + y);
    }

    @Test
    void portalCreateAllowedFillsTheFrame() {
        int[] asked = {0};
        var level = frame(true, asked);
        PortalShape.findEmpty(level, new Pos(1, -60, 0), Dir.AXIS_X).createPortalBlocks();
        for (int x = 1; x <= 2; x++) for (int y = -60; y <= -58; y++)
            assertTrue(B.is(level.getBlockState(new Pos(x, y, 0)), "minecraft:nether_portal"), "대조: 허용하면 채워진다 " + x + "," + y);
    }
}
