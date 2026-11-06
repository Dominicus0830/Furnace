// 플러그인 API: 살아 있는 Block 손잡이(소유 스레드 읽기·쓰기·이웃)와 BlockSnapshot, 블록 종류 표(BlockTypes)의 속성 정의.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.api.world.BlockProperties;
import org.domi.furnace.api.world.BlockType;
import org.domi.furnace.api.world.Direction;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PluginBlockHandleTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"), org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private org.domi.furnace.api.world.World api() { return host.world("minecraft:overworld"); }

    @Test
    void blockHandleIsCheapAndIdentityByPosition() {
        var w = api();
        var b = w.block(new BlockPos(3, 10, 4));
        assertEquals("minecraft:overworld", b.world());
        assertEquals(new BlockPos(3, 10, 4), b.pos());
        assertSame(w, b.worldHandle());
        assertEquals(b, w.block(new BlockPos(3, 10, 4)));
        assertEquals(new BlockPos(4, 10, 4), b.relative(1, 0, 0).pos());
        assertEquals(new BlockPos(3, 9, 4), b.relative(Direction.DOWN).pos());
        // 만들기만 해서는 소유 스레드 검사도 읽기도 하지 않는다 — 다른 region 의 칸이어도 만들 수 있다
        assertNotNull(w.block(new BlockPos(16 * 500, 10, 16 * 500)));
    }

    @Test
    void liveReadAndWriteOnOwnerThreadOnly() throws Exception {
        var w = api();
        BlockPos pos = new BlockPos(3, 10, 4);
        var b = w.block(pos);
        assertThrows(ThreadOwnershipException.class, b::data, "소유 스레드가 아니면 읽기 거부");
        assertThrows(ThreadOwnershipException.class, () -> b.set(BlockData.of("stone")), "소유 스레드가 아니면 쓰기 거부");
        Object[] out = w.scheduler(pos).submit(() -> {
            boolean placed = b.set(BlockData.parse("oak_stairs[facing=north,half=top]"));
            BlockData read = b.data();
            var snap = b.snapshot().orElseThrow();
            boolean flipped = b.setProperty(BlockProperties.FACING, Direction.SOUTH);
            return new Object[] {placed, read, snap, flipped, b.data(), b.type()};
        }).get(10, TimeUnit.SECONDS);
        assertEquals(true, out[0]);
        assertEquals(Direction.NORTH, ((BlockData) out[1]).get(BlockProperties.FACING));
        assertEquals("top", ((BlockData) out[1]).get(BlockProperties.HALF));
        assertEquals("minecraft:oak_stairs", ((org.domi.furnace.api.world.BlockSnapshot) out[2]).type());
        assertEquals(true, out[3]);
        assertEquals(Direction.SOUTH, ((BlockData) out[4]).get(BlockProperties.FACING), "쓴 값을 바로 읽는다");
        assertEquals("top", ((BlockData) out[4]).get(BlockProperties.HALF), "다른 속성은 그대로");
        assertEquals("minecraft:oak_stairs", out[5]);
        // 복사본은 그 뒤에 칸이 바뀌어도 그대로다
        var snapshot = (org.domi.furnace.api.world.BlockSnapshot) out[2];
        assertEquals(Direction.NORTH, snapshot.data().get(BlockProperties.FACING));
    }

    @Test
    void asyncReadWriteAndUnloadedChunk() throws Exception {
        var w = api();
        var b = w.block(new BlockPos(1, 10, 1));
        assertTrue(b.setAsync(BlockData.of("gold_block")).get(10, TimeUnit.SECONDS));
        assertEquals("minecraft:gold_block", b.snapshotAsync().get(10, TimeUnit.SECONDS).orElseThrow().type());
        var far = w.block(new BlockPos(16 * 500, 10, 16 * 500));
        assertTrue(far.snapshotAsync().get(10, TimeUnit.SECONDS).isEmpty(), "로드 안 된 청크는 읽지 않는다");
        assertFalse(far.setAsync(BlockData.of("stone")).get(10, TimeUnit.SECONDS));
        var ex = assertThrows(ExecutionException.class, () -> b.setAsync(BlockData.of("minecraft:not_a_block")).get());
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
    }

    @Test
    void readOfUnloadedChunkOnOwnerThreadIsIllegalState() throws Exception {
        var w = api();
        BlockPos ownPos = new BlockPos(1, 10, 1);
        var ex = assertThrows(ExecutionException.class, () -> w.scheduler(ownPos).submit(() -> w.block(new BlockPos(1, 9999, 1)).data()).get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalStateException.class, ex.getCause());
    }

    // ------------------------------------------------------------------ 블록 종류 표

    @Test
    void blockTypeDescribesPropertiesAndValues() {
        var types = host.blockTypes();
        BlockType stairs = types.get("oak_stairs").orElseThrow();
        assertEquals("minecraft:oak_stairs", stairs.id());
        var facing = stairs.property("facing").orElseThrow();
        assertEquals(BlockType.PropertyInfo.Kind.ENUM, facing.kind());
        assertEquals(java.util.Set.of("north", "south", "west", "east"), new java.util.HashSet<>(facing.values()));
        assertTrue(stairs.hasProperty(BlockProperties.HALF));
        assertEquals(BlockType.PropertyInfo.Kind.BOOLEAN, stairs.property("waterlogged").orElseThrow().kind());
        assertEquals(80, stairs.stateCount(), "4방향 × 상하 × 모양 5 × 물 2");
        assertTrue(facing.matches(BlockProperties.FACING));
        assertFalse(stairs.hasProperty(BlockProperties.AGE));

        BlockType wheat = types.get("minecraft:wheat").orElseThrow();
        var age = wheat.property("age").orElseThrow();
        assertEquals(BlockType.PropertyInfo.Kind.INTEGER, age.kind());
        assertEquals(java.util.List.of("0", "1", "2", "3", "4", "5", "6", "7"), age.values());
        assertTrue(age.matches(BlockProperties.AGE));
        assertEquals(0, wheat.defaultData().get(BlockProperties.AGE));
    }

    @Test
    void blockTypeValidatesData() {
        var types = host.blockTypes();
        BlockType stairs = types.get("oak_stairs").orElseThrow();
        assertTrue(stairs.isValid(BlockData.parse("oak_stairs[facing=east,half=bottom]")));
        assertFalse(stairs.isValid(BlockData.parse("oak_stairs[facing=up]")), "없는 값");
        assertFalse(stairs.isValid(BlockData.parse("oak_stairs[age=1]")), "없는 속성");
        assertFalse(stairs.isValid(BlockData.of("stone")), "다른 종류");
        assertTrue(types.get("stone").orElseThrow().properties().isEmpty());
        assertEquals(1, types.get("stone").orElseThrow().stateCount());
        assertTrue(types.get("minecraft:not_a_block").isEmpty());
        assertTrue(types.ids().contains("minecraft:oak_stairs"));
        assertSame(types.get("oak_stairs").orElseThrow(), types.get("minecraft:oak_stairs").orElseThrow(), "종류 표는 캐시된다");
    }

    @Test
    void worldReadsFillAllPropertiesSoTypedGetWorks() throws Exception {
        var w = api();
        BlockPos pos = new BlockPos(5, 10, 5);
        assertTrue(w.setBlock(pos, BlockData.of("lever")).get(10, TimeUnit.SECONDS));
        var d = w.blockAt(pos).get(10, TimeUnit.SECONDS).orElseThrow().data();
        assertFalse(d.get(BlockProperties.POWERED));
        assertEquals("wall", d.get(BlockProperties.FACE));
        assertNotNull(d.get(BlockProperties.FACING));
    }
}
