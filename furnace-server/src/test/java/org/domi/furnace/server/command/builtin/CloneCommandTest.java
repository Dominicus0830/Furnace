// /clone: 실제 region 레벨에서 복사 결과(replace·masked·filtered)·겹침(normal/force/move)·크기 제한·미로드·블록 엔티티·예약 틱.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;
import java.util.function.Function;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.entity.ContainerBlockEntity;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.BlockPredicateArgument;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.RegionLevel;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CloneCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    int stone, gold, air;
    static final BlockPredicateArgument.Predicate ALL = (l, p) -> true;
    static final BlockPredicateArgument.Predicate MASKED = (l, p) -> !B.isAir(l.getBlockState(p));

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        stone = B.defaultState("minecraft:stone");
        gold = B.defaultState("minecraft:gold_block");
        air = B.defaultState("minecraft:air");
        // (0,0)·(1,0) 청크를 올려 둔다(블록을 놓으려면 로드돼 있어야 한다)
        for (int cx = 0; cx <= 1; cx++) {
            var cp = new org.domi.furnace.server.world.ChunkPos(cx, 0);
            var loaded = new CompletableFuture<CompletableFuture<?>>();
            core.world().runAtChunk(cp, r -> loaded.complete(r.loadChunk(cp)));
            loaded.get(10, TimeUnit.SECONDS).get(30, TimeUnit.SECONDS);
        }
    }

    @AfterEach
    void tearDown() { sched.close(); }

    /** (0,0) 청크를 가진 region 스레드에서 f 를 돌려 결과(또는 던진 오류)를 받는다. */
    private <T> T onRegion(Function<RegionLevel, T> f) throws Exception {
        var fut = new CompletableFuture<T>();
        core.world().runAt(0.5, 0.5, region -> {
            try {
                fut.complete(f.apply(region.level()));
            } catch (Throwable t) {
                fut.completeExceptionally(t);
            }
        });
        return fut.get(10, TimeUnit.SECONDS);
    }

    private int run(RegionLevel l, int[] src, int[] dst, BlockPredicateArgument.Predicate filter, CloneCommand.Mode mode) {
        try {
            return CloneCommand.doClone(CommandSource.console(core), l, src[0], src[1], src[2], src[3], src[4], src[5], dst[0], dst[1], dst[2], filter, mode);
        } catch (CloneCommand.Failed f) {
            throw new IllegalStateException(f.cause.getRawMessage().translationKey());
        }
    }

    private String fail(RegionLevel l, int[] src, int[] dst, BlockPredicateArgument.Predicate filter, CloneCommand.Mode mode) {
        try {
            run(l, src, dst, filter, mode);
            return null;
        } catch (IllegalStateException e) {
            return e.getMessage();
        }
    }

    private void put(RegionLevel l, int x, int y, int z, int state) { l.setBlock(new Pos(x, y, z), state, 2); }

    private int at(RegionLevel l, int x, int y, int z) { return l.getBlockState(new Pos(x, y, z)); }

    @Test
    void replaceCopiesAllIncludingAir() throws Exception {
        onRegion(l -> {
            put(l, 0, 100, 0, stone);
            put(l, 1, 100, 0, gold);
            put(l, 8, 100, 0, gold);
            put(l, 9, 100, 0, gold);
            put(l, 10, 100, 0, gold);
            // 원본 (0..2,100,0) = stone, gold, air → 대상 (8..10,100,0)
            int n = run(l, new int[]{0, 100, 0, 2, 100, 0}, new int[]{8, 100, 0}, ALL, CloneCommand.Mode.NORMAL);
            assertEquals(3, n);
            assertEquals(stone, at(l, 8, 100, 0));
            assertEquals(gold, at(l, 9, 100, 0));
            assertEquals(air, at(l, 10, 100, 0), "replace 는 공기도 덮는다");
            assertEquals(stone, at(l, 0, 100, 0), "normal 은 원본을 둔다");
            return null;
        });
    }

    @Test
    void maskedSkipsAirAndFilteredMatches() throws Exception {
        onRegion(l -> {
            put(l, 0, 100, 0, stone);
            put(l, 1, 100, 0, air);
            put(l, 2, 100, 0, gold);
            for (int x = 8; x <= 10; x++) put(l, x, 100, 0, gold);
            int n = run(l, new int[]{0, 100, 0, 2, 100, 0}, new int[]{8, 100, 0}, MASKED, CloneCommand.Mode.NORMAL);
            assertEquals(2, n, "masked 는 공기가 아닌 칸만 센다");
            assertEquals(stone, at(l, 8, 100, 0));
            assertEquals(gold, at(l, 9, 100, 0), "원본이 공기인 자리는 대상을 건드리지 않는다");
            assertEquals(gold, at(l, 10, 100, 0));
            // filtered: 금만
            BlockPredicateArgument.Predicate onlyGold = (lv, p) -> lv.getBlockState(p) == gold;
            for (int x = 8; x <= 10; x++) put(l, x, 100, 0, stone);
            n = run(l, new int[]{0, 100, 0, 2, 100, 0}, new int[]{8, 100, 0}, onlyGold, CloneCommand.Mode.NORMAL);
            assertEquals(1, n);
            assertEquals(stone, at(l, 8, 100, 0));
            assertEquals(stone, at(l, 9, 100, 0));
            assertEquals(gold, at(l, 10, 100, 0));
            // 아무것도 안 맞으면 commands.clone.failed
            BlockPredicateArgument.Predicate none = (lv, p) -> false;
            assertEquals("commands.clone.failed", fail(l, new int[]{0, 100, 0, 2, 100, 0}, new int[]{8, 100, 0}, none, CloneCommand.Mode.NORMAL));
            return null;
        });
    }

    @Test
    void overlapForceAndMove() throws Exception {
        onRegion(l -> {
            for (int x = 0; x < 4; x++) put(l, x, 100, 0, x % 2 == 0 ? stone : gold);   // s g s g
            int[] src = {0, 100, 0, 3, 100, 0};
            assertEquals("commands.clone.overlap", fail(l, src, new int[]{1, 100, 0}, ALL, CloneCommand.Mode.NORMAL));
            assertEquals(stone, at(l, 0, 100, 0), "거부하면 아무것도 바뀌지 않는다");
            // force: 겹쳐도 원본 상태를 먼저 모두 읽고 쓴다 → 한 칸 밀린 s g s g
            assertEquals(4, run(l, src, new int[]{1, 100, 0}, ALL, CloneCommand.Mode.FORCE));
            assertEquals(stone, at(l, 0, 100, 0));
            assertEquals(stone, at(l, 1, 100, 0));
            assertEquals(gold, at(l, 2, 100, 0));
            assertEquals(stone, at(l, 3, 100, 0));
            assertEquals(gold, at(l, 4, 100, 0));
            // move: 원본이 공기가 된다(겹치지 않는 자리로)
            assertEquals(2, run(l, new int[]{0, 100, 0, 1, 100, 0}, new int[]{20, 100, 0}, ALL, CloneCommand.Mode.MOVE));
            assertEquals(air, at(l, 0, 100, 0));
            assertEquals(air, at(l, 1, 100, 0));
            assertEquals(stone, at(l, 20, 100, 0));
            assertEquals(stone, at(l, 21, 100, 0));
            // 겹치는 move: 지운 뒤 놓는다 → 원본 일부는 새 블록이 차지
            assertEquals(3, run(l, new int[]{2, 100, 0, 4, 100, 0}, new int[]{3, 100, 0}, ALL, CloneCommand.Mode.MOVE));
            assertEquals(air, at(l, 2, 100, 0));
            assertEquals(gold, at(l, 3, 100, 0));
            assertEquals(stone, at(l, 4, 100, 0));
            assertEquals(gold, at(l, 5, 100, 0));
            return null;
        });
    }

    @Test
    void sizeLimitAndUnloaded() throws Exception {
        core.commands().getDispatcher().execute("gamerule commandModificationBlockLimit 4", CommandSource.console(core));
        onRegion(l -> {
            assertEquals("commands.clone.toobig", fail(l, new int[]{0, 100, 0, 4, 100, 0}, new int[]{8, 100, 0}, ALL, CloneCommand.Mode.NORMAL));
            assertEquals("argument.pos.unloaded", fail(l, new int[]{0, 100, 0, 1, 100, 0}, new int[]{1000000, 100, 0}, ALL, CloneCommand.Mode.NORMAL));
            return null;
        });
    }

    @Test
    void blockEntityAndScheduledTickCopied() throws Exception {
        onRegion(l -> {
            int chest = B.defaultState("minecraft:chest");
            put(l, 0, 100, 0, chest);
            var be = (ContainerBlockEntity) l.getBlockEntity(new Pos(0, 100, 0));
            assertNotNull(be);
            be.customName = "{\"text\":\"Boxy\"}";
            int sand = B.defaultState("minecraft:sand");
            l.scheduleTick(new Pos(1, 100, 0), B.block(sand), 5);
            put(l, 1, 100, 0, sand);
            assertEquals(2, run(l, new int[]{0, 100, 0, 1, 100, 0}, new int[]{8, 100, 0}, ALL, CloneCommand.Mode.NORMAL));
            var copy = (ContainerBlockEntity) l.getBlockEntity(new Pos(8, 100, 0));
            assertNotNull(copy);
            assertNotSame(be, copy);
            assertEquals("{\"text\":\"Boxy\"}", copy.customName, "블록 엔티티 내용이 복사된다");
            assertTrue(l.hasScheduledTick(new Pos(9, 100, 0), B.block(sand)), "예약 틱도 복사된다");
            return null;
        });
    }
}
