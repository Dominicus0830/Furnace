// /execute E1 단계(as·at·positioned(as, over)·rotated(as)·facing·align·anchored·in·summon·on·run)가
// 실제 region 의 엔티티·블록을 대상으로 바닐라와 같은 소스·결과를 내는지, 조회성 하위 명령이 region 을 만들지 않는지.
package org.domi.furnace.server.command.exec;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.EntityAnchorArgument;
import org.domi.furnace.server.command.brigadier.LiteralArgumentBuilder;
import org.domi.furnace.server.entity.Cow;
import org.domi.furnace.server.entity.Entity;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.sched.GlobalThread;
import org.domi.furnace.server.world.ChunkColumn;
import org.domi.furnace.server.world.ChunkPos;
import org.domi.furnace.server.world.DimensionType;
import org.domi.furnace.server.world.Dimensions;
import org.domi.furnace.server.world.World;
import org.domi.furnace.server.world.gen.NoiseWorldSource;
import org.domi.furnace.server.world.gen.SuperflatWorldSource;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExecuteE1Test {

    /** 시험용 네더: 슈퍼플랫 높이를 쓰되 레벨 키·좌표 비(8)는 네더. */
    private static final DimensionType NETHER = new DimensionType("minecraft:the_nether", "minecraft:the_nether", -64, 384, 384,
            false, true, true, false, 8.0, 18000L, false, true, true, NoiseWorldSource.Dimension.NETHER);

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    ChunkColumn column;
    Cow[] cows;
    World nether;
    /** probe 가 받은 소스들(전역 스레드에서 쌓인다). */
    final List<CommandSource> probed = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        column = core.world().awaitChunk(new ChunkPos(0, 0));
        cows = DeferredResultTest.spawnCows(core, 2);
        var reg = org.domi.furnace.server.world.BlockRegistry.load("blocks/1.21.1.txt");
        nether = new World(NETHER, new SuperflatWorldSource(reg, 0), new org.domi.furnace.server.net.ChunkWorkerPool(1), sched, 1, 4, 1, 2, 2,
                reg.directBits(), 7, TestServers.ioPool(), TestServers.ioPool());
        Dimensions dims = new Dimensions();
        dims.register(core.world());
        dims.register(nether);
        core.dimensions(dims);
        core.commands().getDispatcher().register(LiteralArgumentBuilder.<CommandSource>literal("probe").executes(c -> {
            probed.add(c.getSource());
            return 1;
        }));
        // 소 0 은 태그 a, 소 1 은 태그 b + 회전
        inRegion(() -> {
            cows[0].addTag("a");
            cows[1].addTag("b");
            cows[1].moveTo(1.5, -60, 0.5, 33f, 12f);
        });
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private <T> T inRegion(java.util.function.Supplier<T> task) {
        return core.world().submitAtChunk(new ChunkPos(0, 0), r -> task.get()).orTimeout(10, TimeUnit.SECONDS).join();
    }

    private void inRegion(Runnable task) { inRegion(() -> { task.run(); return null; }); }

    private CommandResult run(String command) {
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).orTimeout(20, TimeUnit.SECONDS).join();
    }

    private Set<String> tags(Entity e) { return inRegion(e::getTags); }

    @Test
    void asEntitiesRunsOncePerEntityAndTagsThemInTheirRegion() {
        CommandResult r = run("execute as @e[type=cow] run tag @s add x");
        assertEquals(2, r.callbacks(), "소마다 한 번씩 실행");
        assertTrue(r.success());
        assertTrue(tags(cows[0]).contains("x"));
        assertTrue(tags(cows[1]).contains("x"));
        // 일치가 없으면 실행도 오류도 없다(바닐라 getOptionalEntities)
        assertEquals(CommandResult.NONE, run("execute as @e[type=cow,tag=none] run tag @s add y"));
    }

    @Test
    void atEntityRunsAtItsPositionAndSetblockLandsThere() {
        int stone = B.defaultState("minecraft:stone");
        CommandResult r = run("execute at @e[tag=b,limit=1] run setblock ~ ~-1 ~ minecraft:stone");
        assertEquals(1, r.callbacks());
        assertEquals(stone, column.getBlock(1, -61, 0), "소 1(x=1.5, y=-60) 의 한 칸 아래");
        assertNotEquals(stone, column.getBlock(0, -61, 0));
    }

    @Test
    void positionedAndAlignAndAnchoredAndFacingAreSynchronousSourceChanges() {
        run("execute positioned 1.5 2 3.5 run probe");
        assertEquals(1.5, probed.get(0).x(), 0);
        assertEquals(2, probed.get(0).y(), 0);
        assertEquals(3.5, probed.get(0).z(), 0);
        probed.clear();
        run("execute positioned 1.7 2.9 -3.2 align xz run probe");
        assertEquals(1.0, probed.get(0).x(), 0);
        assertEquals(2.9, probed.get(0).y(), 0);
        assertEquals(-4.0, probed.get(0).z(), 0);
        probed.clear();
        run("execute positioned 0 0 0 facing 10 0 0 run probe");
        assertEquals(-90, probed.get(0).yRot(), 0.05);
        assertEquals(0, probed.get(0).xRot(), 0.05);
        probed.clear();
        run("execute rotated 30 10 rotated ~15 ~ run probe");
        assertEquals(45, probed.get(0).yRot(), 1e-4);
        assertEquals(10, probed.get(0).xRot(), 1e-4);
        probed.clear();
        // anchored 는 positioned 가 다시 발로 되돌린다
        run("execute as @e[tag=b,limit=1] anchored eyes run probe");
        assertEquals(EntityAnchorArgument.Anchor.EYES, probed.get(0).getAnchor());
        probed.clear();
        run("execute as @e[tag=b,limit=1] anchored eyes positioned 1 1 1 run probe");
        assertEquals(EntityAnchorArgument.Anchor.FEET, probed.get(0).getAnchor());
    }

    @Test
    void entityDrivenSourceChanges() {
        run("execute positioned as @e[tag=b,limit=1] run probe");
        assertEquals(1.5, probed.get(0).x(), 0);
        assertEquals(-60, probed.get(0).y(), 0);
        assertEquals(0.5, probed.get(0).z(), 0);
        probed.clear();
        run("execute rotated as @e[tag=b,limit=1] run probe");
        assertEquals(33, probed.get(0).yRot(), 1e-4);
        assertEquals(12, probed.get(0).xRot(), 1e-4);
        probed.clear();
        // (1.5,-60,10.5) 에서 소(1.5,-60,0.5)를 보면 북쪽(-z): yaw ±180
        run("execute positioned 1.5 -60 10.5 facing entity @e[tag=b,limit=1] feet run probe");
        assertEquals(180f, Math.abs(probed.get(0).yRot()), 0.05f);
        assertEquals(0, probed.get(0).xRot(), 0.05);
        probed.clear();
        // as 는 위치를 바꾸지 않고 엔티티만 바꾼다
        run("execute positioned 5 6 7 as @e[tag=b,limit=1] run probe");
        assertEquals(5.5, probed.get(0).x(), 0);   // vec3 정수 x·z 는 칸 가운데
        assertSame(cows[1], probed.get(0).entity());
    }

    @Test
    void chainedForksKeepEntityOrderAndSnapshotPerSource() {
        // as 로 포크한 두 소스 각각에서 at @s → 소 위치(id 순), 이어서 상대 좌표
        CommandResult r = run("execute as @e[type=cow] at @s positioned ~ ~1 ~ run probe");
        assertEquals(2, r.callbacks());
        assertEquals(2, probed.size());
        assertEquals(0.5, probed.get(0).x(), 0);
        assertEquals(-59, probed.get(0).y(), 0);
        assertSame(cows[0], probed.get(0).entity());
        assertEquals(1.5, probed.get(1).x(), 0);
        assertSame(cows[1], probed.get(1).entity());
        assertEquals(33, probed.get(1).yRot(), 1e-4, "at 은 회전도 가져온다");
    }

    @Test
    void inDimensionScalesCoordinatesAndRejectsUnknownDimension() {
        run("execute positioned 80 64 160 in minecraft:the_nether run probe");
        assertEquals(80.5 / 8, probed.get(0).x(), 1e-9);   // 정수 x·z 는 +0.5 뒤 /8
        assertEquals(64, probed.get(0).y(), 0);
        assertEquals(160.5 / 8, probed.get(0).z(), 1e-9);
        assertEquals("minecraft:the_nether", probed.get(0).level().dimension());
        probed.clear();
        assertEquals(CommandResult.NONE, run("execute in minecraft:the_end run probe"));
        assertTrue(probed.isEmpty());
    }

    @Test
    void positionedOverReadsHeightInOwnerRegionAndDoesNotCreateRegionsForUnloadedColumns() {
        int expected = column.motionBlockingBlockY(0, 0) + 1;
        run("execute positioned 0.5 0 0.5 positioned over motion_blocking run probe");
        assertEquals(1, probed.size());
        assertEquals(expected, probed.get(0).y(), 0);
        assertEquals(0.5, probed.get(0).x(), 0);
        probed.clear();
        // 주인 region 이 없는 먼 칸: 바로 argument.pos.unloaded 로 끝나고 region 수는 그대로
        int before = core.world().regionCount();
        CommandResult r = run("execute positioned 1000000.5 0 1000000.5 positioned over world_surface run probe");
        assertEquals(CommandResult.NONE, r);
        assertTrue(probed.isEmpty());
        assertEquals(before, core.world().regionCount(), "조회성 하위 명령이 region 을 만들었다");
    }

    @Test
    void playerAndSelfSelectorsFinishInTheSameTick() throws Exception {
        var p = new Player(5, UUID.randomUUID(), "Steve", new org.domi.furnace.server.ConcurrentEmbeddedChannel());
        CommandSource src = CommandSource.player(core, p).withPermission(2);
        var g = (GlobalThread) sched.global();
        boolean doneInline = g.submit(() -> {
            core.commands().performPrefixedCommand(src, "execute as @s at @s positioned as @s run tag @s add z");
            return p.getTags().contains("z");   // 전역 스레드에서 이어 가는 일 없이 명령이 돌아오기 전에 끝나 있어야 한다
        }).orTimeout(10, TimeUnit.SECONDS).join();
        assertTrue(doneInline, "플레이어·@s 만 고르는 체인은 같은 틱 안에 끝나야 한다");
    }

    @Test
    void summonRedirectsToTheNewEntity() {
        CommandResult r = run("execute positioned 0.5 -60 0.5 summon minecraft:cow run tag @s add born");
        assertEquals(1, r.callbacks());
        assertTrue(r.success());
        long born = inRegion(() -> {
            long n = 0;
            for (Entity e : core.world().regionizer().ownerOf(0, 0).level().entities().all()) if (e.getTags().contains("born")) n++;
            return n;
        });
        assertEquals(1, born, "새로 소환된 소 하나만 태그를 받는다");
        assertEquals(CommandResult.NONE, run("execute summon minecraft:player run probe"), "player 는 소환할 수 없다");
    }

    @Test
    void onVehicleAndPassengersFollowRiding() {
        inRegion(() -> assertTrue(cows[0].startRiding(cows[1], true)));
        assertEquals(1, run("execute as @e[tag=a] on vehicle run tag @s add rode").callbacks());
        assertTrue(tags(cows[1]).contains("rode"));
        assertFalse(tags(cows[0]).contains("rode"));
        assertEquals(1, run("execute as @e[tag=b] on passengers run tag @s add pass").callbacks());
        assertTrue(tags(cows[0]).contains("pass"));
        // 길들인 주인·밧줄·표적이 없는 소
        assertEquals(CommandResult.NONE, run("execute as @e[tag=a] on owner run tag @s add o"));
        assertEquals(CommandResult.NONE, run("execute as @e[tag=a] on leasher run tag @s add l"));
        assertEquals(CommandResult.NONE, run("execute as @e[tag=a] on target run tag @s add t"));
        assertEquals(CommandResult.NONE, run("execute as @e[tag=a] on attacker run tag @s add k"));
    }
}
