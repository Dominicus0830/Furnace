// 빠른 블록 쓰기(E7): World.editBlocks 가 같은 옵션의 바닐라 setBlock 을 칸마다 부른 것과 같은 월드를 만드는지, region 이 여럿이거나
// 틱을 나눠도 다 적용되는지, 로드 안 된 청크를 만들지 않는지, 청크째 다시 보내기·동기판 소유권 규칙을 확인한다.
package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.api.concurrent.ThreadOwnershipException;
import org.domi.furnace.api.world.BlockData;
import org.domi.furnace.api.world.BlockEditOptions;
import org.domi.furnace.api.world.BlockEditOptions.ClientUpdates;
import org.domi.furnace.api.world.BlockEditOptions.NeighborUpdates;
import org.domi.furnace.api.world.BlockEditResult;
import org.domi.furnace.api.world.BlockPos;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.ItemEntity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.LevelChunkWithLight;
import org.domi.furnace.server.net.protocol.play.SectionBlocksUpdate;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.Region;
import org.domi.furnace.server.world.RegionLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class BlockEditApiTest {

    /** 비교용 두 자리: 같은 평지, 서로 다른 region(청크 40 떨어짐). */
    private static final int A = 0, B_OFF = 16 * 40;

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
        core.world().setRegistries(org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt"),
                org.domi.furnace.server.world.ItemRegistry.load("items/1.21.1.txt"));
        for (int off : new int[] {A, B_OFF}) {
            for (int dx = -1; dx <= 2; dx++) {
                for (int dz = -1; dz <= 1; dz++) {
                    ChunkPos cp = new ChunkPos((off >> 4) + dx, dz);
                    core.world().submitAtChunk(cp, r -> r.chunkAtBlocking(cp)).get(30, TimeUnit.SECONDS);
                }
            }
        }
    }

    @AfterEach
    void tearDown() throws Exception {
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private org.domi.furnace.api.world.World api() { return host.world("minecraft:overworld"); }

    private <T> T atRegion(int blockX, java.util.function.Function<Region, T> f) throws Exception {
        return core.world().submitAtChunk(ChunkPos.ofBlock(blockX, 0), f).get(30, TimeUnit.SECONDS);
    }

    // ------------------------------------------------------------------ 바닐라 setBlock 과 같은 결과

    /** 모양·이웃 갱신이 서로 얽히는 블록들(계단·울타리·유리판·레드스톤·레일·모래·물·횃불·상자) — 고정 시드로 무작위 배치. */
    private static final String[] PALETTE = {
        "stone", "air", "air", "oak_stairs[facing=east]", "oak_fence", "glass_pane", "redstone_wire", "redstone_torch",
        "lever[face=floor,powered=true]", "rail", "sand", "water", "torch", "chest", "observer[facing=up]", "redstone_block",
        "oak_door[half=lower]", "cobblestone_wall", "piston[facing=up]", "tnt"
    };

    /** 편집 순서(청크 하나 안: y·z·x 오름차순)대로 적은 목록. */
    private record Put(int x, int y, int z, BlockData data) {}

    private static List<Put> randomScene(long seed) {
        Random rnd = new Random(seed);
        List<Put> out = new ArrayList<>();
        for (int y = -60; y <= -54; y++)
            for (int z = 1; z <= 14; z++)
                for (int x = 1; x <= 14; x++)
                    if (rnd.nextInt(3) != 0) out.add(new Put(x, y, z, BlockData.parse(PALETTE[rnd.nextInt(PALETTE.length)])));
        return out;
    }

    /** 바닐라 쪽: 같은 순서로 Level.setBlock(flags), AFTER_EDIT 이면 /fill 처럼 놓은 뒤 blockUpdated. */
    private void vanilla(int off, List<Put> puts, int flags, boolean afterEdit, boolean clear) throws Exception {
        var reg = core.world().blockRegistry();
        atRegion(off, r -> {
            RegionLevel lv = r.level();
            List<Pos> placed = new ArrayList<>();
            for (Put p : puts) {
                Pos pos = new Pos(off + p.x, p.y, p.z);
                int s = ApiConvert.stateId(p.data, reg);
                if (clear && lv.getBlockState(pos) != s) org.domi.furnace.server.block.entity.Clearables.tryClear(lv.getBlockEntity(pos));
                if (lv.setBlock(pos, s, flags)) placed.add(pos);
            }
            if (afterEdit) for (Pos pos : placed) lv.blockUpdated(pos, B.block(lv.getBlockState(pos)));
            lv.broadcastChanges();
            return null;
        });
    }

    private BlockEditResult edit(int off, List<Put> puts, BlockEditOptions o) throws Exception {
        return api().editBlocks(o, ed -> {
            for (Put p : puts) ed.set(off + p.x, p.y, p.z, p.data);
        }).get(30, TimeUnit.SECONDS);
    }

    /** 두 자리의 상자(이웃 한 칸 포함) 블록이 모두 같은가. */
    private void assertSameWorld(String what) throws Exception {
        int[] a = box(A), b = box(B_OFF);
        var reg = core.world().blockRegistry();
        for (int i = 0; i < a.length; i++) {
            if (a[i] != b[i]) fail(what + ": 칸 " + i + " 다름 — 편집 " + reg.nameOf(a[i]) + " / 바닐라 " + reg.nameOf(b[i]));
        }
    }

    private int[] box(int off) throws Exception {
        return atRegion(off, r -> {
            int[] out = new int[16 * 10 * 16];
            int i = 0;
            for (int y = -61; y <= -52; y++) for (int z = 0; z <= 15; z++) for (int x = 0; x <= 15; x++) out[i++] = r.getBlock(off + x, y, z);
            return out;
        });
    }

    private int items(int off) throws Exception {
        return atRegion(off, r -> {
            int n = 0;
            for (var e : r.level().entities().all()) if (e instanceof ItemEntity && !e.isRemoved() && e.x() >= off && e.x() < off + 16) n++;
            return n;
        });
    }

    @Test
    void defaultOptionsMatchSetBlockPerBlock() throws Exception {
        List<Put> scene = randomScene(11);
        BlockEditResult res = edit(A, scene, BlockEditOptions.DEFAULT);
        vanilla(B_OFF, scene, 11, false, false);
        assertSameWorld("DEFAULT");
        assertEquals(items(B_OFF), items(A), "쏟아진 아이템 수도 같다");
        assertEquals(scene.size(), res.changed() + res.unchanged(), "적은 칸은 모두 바뀌었거나 그대로");
        assertEquals(0, res.skipped());
        assertTrue(res.changed() > 0);
    }

    @Test
    void afterEditMatchesFillCommandOrder() throws Exception {
        List<Put> scene = randomScene(22);
        var o = BlockEditOptions.DEFAULT.neighborUpdates(NeighborUpdates.AFTER_EDIT).clearContainers(true);
        edit(A, scene, o);
        vanilla(B_OFF, scene, 2, true, true);
        assertSameWorld("AFTER_EDIT");
    }

    @Test
    void noUpdateOptionsMatchVanillaFlags() throws Exception {
        List<Put> scene = randomScene(33);
        edit(A, scene, BlockEditOptions.DEFAULT.neighborUpdates(NeighborUpdates.NONE).shapeUpdates(false));
        vanilla(B_OFF, scene, 2 | 16, false, false);
        assertSameWorld("NONE + 모양 끔");
        // 두 번째 편집을 같은 자리에 겹쳐도 같다(이미 놓인 블록 위에서 바뀌는 길)
        List<Put> scene2 = randomScene(34);
        edit(A, scene2, BlockEditOptions.FAST.clientUpdates(ClientUpdates.BLOCKS));
        vanilla(B_OFF, scene2, 2 | 16, false, true);
        assertSameWorld("FAST 겹치기");
    }

    @Test
    void fastKeepsBlocksAsGivenAndDoesNotSpillContainers() throws Exception {
        var w = api();
        BlockPos chest = new BlockPos(5, -60, 5);
        assertTrue(w.setBlock(chest, BlockData.of("chest")).get(10, TimeUnit.SECONDS));
        atRegion(A, r -> {
            var be = (org.domi.furnace.server.block.entity.ContainerBlockEntity) r.level().getBlockEntity(new Pos(5, -60, 5));
            be.items[0] = new org.domi.furnace.server.entity.ItemStack(core.world().itemRegistry().idOf("minecraft:diamond"), 3);
            return null;
        });
        var res = w.editBlocks(BlockEditOptions.FAST, ed -> {
            ed.set(chest, BlockData.of("stone"));
            ed.set(5, -59, 5, BlockData.of("torch"));                 // 돌 위 횃불
            ed.set(8, -60, 8, BlockData.of("oak_fence"));             // 옆에 돌을 두어도 잇지 않는다(모양 갱신 끔)
            ed.set(9, -60, 8, BlockData.of("stone"));
            ed.set(5, -60, 5, BlockData.of("air"));                   // 같은 칸 다시 — 마지막 값(공기)이 남는다
        }).get(10, TimeUnit.SECONDS);
        assertEquals(4, res.changed() + res.unchanged(), "같은 칸은 한 번");
        assertEquals("minecraft:air", w.blockAt(chest).get().orElseThrow().type());
        assertEquals("minecraft:torch", w.blockAt(new BlockPos(5, -59, 5)).get().orElseThrow().type(), "지지대가 사라져도 그대로(이웃 갱신 끔)");
        assertEquals("false", w.blockAt(new BlockPos(8, -60, 8)).get().orElseThrow().data().properties().get("east"));
        assertEquals(0, items(A), "clearContainers: 상자를 덮어써도 쏟지 않는다");

        // 끄면 바닐라 setBlock 처럼 쏟는다
        assertTrue(w.setBlock(chest, BlockData.of("chest")).get(10, TimeUnit.SECONDS));
        atRegion(A, r -> {
            var be = (org.domi.furnace.server.block.entity.ContainerBlockEntity) r.level().getBlockEntity(new Pos(5, -60, 5));
            be.items[0] = new org.domi.furnace.server.entity.ItemStack(core.world().itemRegistry().idOf("minecraft:diamond"), 3);
            return null;
        });
        w.editBlocks(BlockEditOptions.FAST.clearContainers(false), ed -> ed.set(chest, BlockData.of("stone"))).get(10, TimeUnit.SECONDS);
        assertEquals(1, items(A), "clearContainers=false: 바닐라처럼 쏟는다");
    }

    // ------------------------------------------------------------------ region·로드·틱 나누기

    @Test
    void spansRegionsSkipsUnloadedAndOutOfHeight() throws Exception {
        var w = api();
        int before = core.world().loadedChunkCount();
        var res = w.editBlocks(ed -> {
            ed.fill(new BlockPos(0, -60, 0), new BlockPos(31, -60, 15), BlockData.of("gold_block"));        // region A, 청크 2개
            ed.fill(new BlockPos(B_OFF, -60, 0), new BlockPos(B_OFF + 15, -60, 15), BlockData.of("gold_block"));   // region B
            ed.set(16 * 900, -60, 0, BlockData.of("gold_block"));     // 로드 안 된 청크
            ed.set(3, 5000, 3, BlockData.of("gold_block"));           // 높이 밖
            ed.set(3, -65, 3, BlockData.of("gold_block"));
        }).get(30, TimeUnit.SECONDS);
        assertEquals(256 * 3, res.changed());
        assertEquals(3, res.skipped(), "로드 안 된 청크 1칸 + 높이 밖 2칸");
        assertEquals(before, core.world().loadedChunkCount(), "청크를 만들거나 불러오지 않는다");
        assertEquals("minecraft:gold_block", w.blockAt(new BlockPos(31, -60, 15)).get().orElseThrow().type());
        assertEquals("minecraft:gold_block", w.blockAt(new BlockPos(B_OFF + 15, -60, 15)).get().orElseThrow().type());
        assertTrue(core.world().regionizer().ownerOf(0, 0) != core.world().regionizer().ownerOf(B_OFF >> 4, 0), "두 region 에 걸친 시험");
    }

    @Test
    void chunksPerTickSplitsAcrossTicksAndFinishes() throws Exception {
        var w = api();
        var res = w.editBlocks(BlockEditOptions.DEFAULT.chunksPerTick(1), ed ->
                ed.fill(new BlockPos(-16, -59, -16), new BlockPos(47, -59, 31), BlockData.of("diamond_block"))).get(30, TimeUnit.SECONDS);
        assertEquals(64 * 48, res.changed(), "청크 12개를 한 틱에 하나씩");
        for (int x = -16; x < 48; x += 7) for (int z = -16; z < 32; z += 5)
            assertEquals("minecraft:diamond_block", w.blockAt(new BlockPos(x, -59, z)).get().orElseThrow().type());
    }

    @Test
    void unknownBlockFailsWithoutChangesAndEditorClosesAfterwards() throws Exception {
        var w = api();
        org.domi.furnace.api.world.BlockEditor[] leaked = new org.domi.furnace.api.world.BlockEditor[1];
        var ex = assertThrows(ExecutionException.class, () -> w.editBlocks(ed -> {
            ed.set(1, -60, 1, BlockData.of("stone"));
            ed.set(2, -60, 1, BlockData.of("minecraft:not_a_block"));
        }).get(10, TimeUnit.SECONDS));
        assertInstanceOf(IllegalArgumentException.class, ex.getCause());
        assertTrue(w.blockAt(new BlockPos(1, -60, 1)).get().orElseThrow().data().isAir(), "아무 칸도 바꾸지 않는다");
        w.editBlocks(ed -> leaked[0] = ed).get(10, TimeUnit.SECONDS);
        assertThrows(IllegalStateException.class, () -> leaked[0].set(1, -60, 1, BlockData.of("stone")), "함수가 끝나면 닫힌다");
        assertEquals(BlockEditResult.EMPTY, w.editBlocks(ed -> {}).get(10, TimeUnit.SECONDS));
    }

    // ------------------------------------------------------------------ 동기판

    @Test
    void editBlocksNowRequiresOwnerThreadForEveryChunk() throws Exception {
        var w = api();
        assertThrows(ThreadOwnershipException.class, () -> w.editBlocksNow(BlockEditOptions.DEFAULT, ed -> ed.set(1, -60, 1, BlockData.of("stone"))));
        var res = w.scheduler(new BlockPos(1, -60, 1)).submit(() ->
                w.editBlocksNow(BlockEditOptions.DEFAULT, ed -> ed.fill(new BlockPos(0, -60, 0), new BlockPos(3, -60, 3), BlockData.of("stone"))))
                .get(10, TimeUnit.SECONDS);
        assertEquals(16, res.changed());
        // 남의 region 칸이 하나라도 섞이면 아무것도 바꾸지 않는다
        var ex = assertThrows(ExecutionException.class, () -> w.scheduler(new BlockPos(1, -60, 1)).submit(() ->
                w.editBlocksNow(BlockEditOptions.DEFAULT, ed -> {
                    ed.set(7, -60, 7, BlockData.of("stone"));
                    ed.set(B_OFF + 1, -60, 1, BlockData.of("stone"));
                })).get(10, TimeUnit.SECONDS));
        assertInstanceOf(ThreadOwnershipException.class, ex.getCause());
        assertTrue(w.blockAt(new BlockPos(7, -60, 7)).get().orElseThrow().data().isAir());
    }

    // ------------------------------------------------------------------ 클라이언트

    private static List<Integer> ids(EmbeddedChannel ch) {
        ch.runPendingTasks();
        List<Integer> ids = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            ByteBuf b = (ByteBuf) o;
            ids.add(Types.readVarInt(b));
            b.release();
        }
        return ids;
    }

    @Test
    void chunksModeResendsWholeChunkInsteadOfSectionUpdates() throws Exception {
        var w = api();
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(1, UUID.randomUUID(), "P", ch);
        ChunkPos c00 = new ChunkPos(0, 0);
        org.domi.furnace.server.world.WorldTestAccess.subscribeReady(core.world(), c00, p);
        ids(ch);

        w.editBlocks(BlockEditOptions.DEFAULT, ed -> ed.fill(new BlockPos(0, -60, 0), new BlockPos(15, -57, 15), BlockData.of("stone"))).get(10, TimeUnit.SECONDS);
        List<Integer> blocks = ids(ch);
        assertTrue(blocks.contains(SectionBlocksUpdate.ID), "BLOCKS: 바닐라처럼 섹션 갱신: " + blocks);

        w.editBlocks(BlockEditOptions.FAST, ed -> ed.fill(new BlockPos(0, -60, 0), new BlockPos(15, -57, 15), BlockData.of("glowstone"))).get(10, TimeUnit.SECONDS);
        List<Integer> seen = new ArrayList<>();
        for (int i = 0; i < 40 && !seen.contains(LevelChunkWithLight.ID); i++) {
            Thread.sleep(50);
            atRegion(A, r -> null);
            seen.addAll(ids(ch));
        }
        assertTrue(seen.contains(LevelChunkWithLight.ID), "CHUNKS: 청크째 다시 보낸다: " + seen);
        assertFalse(seen.contains(SectionBlocksUpdate.ID), "CHUNKS: 그 청크의 섹션 갱신은 보내지 않는다: " + seen);
        for (int i = 0; i < 40 && !org.domi.furnace.server.world.WorldTestAccess.isReady(core.world(), c00, p); i++) {   // 전달 표시는 region 작업으로 돈다
            Thread.sleep(25);
            atRegion(A, r -> null);
        }
        assertTrue(org.domi.furnace.server.world.WorldTestAccess.isReady(core.world(), c00, p), "다시 보낸 뒤 구독은 ready");
        // 빛도 이미 계산돼 있다(청크 패킷에 실린다)
        assertTrue(atRegion(A, r -> r.level().getBlockLight(new Pos(0, -56, 0))) > 0);
    }
}
