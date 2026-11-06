// 명령 블록 체인이 바닐라처럼 한 틱(그 예약 틱 호출 하나) 안에 끝난다: 명령은 전역 스레드에서 돌지만 region 이 결과를 기다리며
// 명령이 보낸 왕복을 그 자리에서 돌린다. @e 처럼 모든 region 을 도는 명령도 쉬는 다른 region 이 곧바로 응답해 같은 호출 안에 끝난다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.BlockBehaviors;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.block.Prop;
import org.domi.furnace.server.block.entity.CommandBlockEntity;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class CommandBlockOneTickTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);
    private static final ChunkPos FAR = new ChunkPos(200, 200);   // 3200칸 — 다른 region
    private static final Prop FACING = B.prop("facing");

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
        Files.writeString(tmp.resolve("server.properties"), "enable-command-block=true\n");
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().setRegistries(BlockRegistry.load("blocks/1.21.1.txt"), ItemRegistry.load("items/1.21.1.txt"));
        core.world().submitAtChunk(C00, r -> r.chunkAtBlocking(C00)).get(30, TimeUnit.SECONDS);
    }

    @AfterEach
    void tearDown() throws Exception {
        core.plugins().close();
        sched.close();
        TestServers.drainIoPools();
    }

    private static CommandBlockEntity put(org.domi.furnace.server.block.ChunkLevel level, Pos p, String block, String cmd) {
        level.setBlock(p, FACING.with(B.defaultState(block), "east"), 3);
        var be = (CommandBlockEntity) level.getBlockEntity(p);
        be.logic().setCommand(cmd);
        return be;
    }

    /** 임펄스 + 체인 둘을 놓고 예약 틱 하나를 직접 부른 뒤, 그 호출이 돌아온 직후(같은 region 작업 안)의 결과. */
    private String runChain(String c0, String c1, String c2, Pos... check) throws Exception { return runChain(1, c0, c1, c2, check); }

    private String runChain(int z, String c0, String c1, String c2, Pos... check) throws Exception {
        return core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Pos p0 = new Pos(1, -60, z);
            var a = put(level, p0, "minecraft:command_block", c0);
            var b = put(level, p0.relative(org.domi.furnace.server.block.Dir.EAST), "minecraft:chain_command_block", c1);
            var c = put(level, p0.relative(org.domi.furnace.server.block.Dir.EAST).relative(org.domi.furnace.server.block.Dir.EAST),
                    "minecraft:chain_command_block", c2);
            a.markConditionMet(level);
            int s = level.getBlockState(p0);
            BlockBehaviors.of(s).tick(s, level, p0, level.random());   // CommandBlock.tick → execute → executeChain
            StringBuilder out = new StringBuilder();
            out.append(a.logic().successCount()).append(b.logic().successCount()).append(c.logic().successCount());
            for (Pos q : check) out.append(' ').append(B.is(level.getBlockState(q), "minecraft:stone") ? "stone" : "-");
            return out.toString();
        }).get(30, TimeUnit.SECONDS);
    }

    /** 첫 명령은 명령 트리·클래스 로딩으로 한 틱 예산을 넘길 수 있다 — 한 번 데워 둔다. */
    private void warmUp() throws Exception {
        runChain(12, "setblock 1 -60 14 dirt", "say a", "execute if entity @e run say b");
        Thread.sleep(500);
    }

    @Test
    void chainFinishesInsideTheSameTick() throws Exception {
        warmUp();
        String got = runChain("setblock 1 -60 5 stone", "setblock 2 -60 5 stone", "setblock 3 -60 5 stone",
                new Pos(1, -60, 5), new Pos(2, -60, 5), new Pos(3, -60, 5));
        assertEquals("111 stone stone stone", got, "세 명령 모두 그 틱 안에 끝나고 성공 횟수도 바로 보인다(비교기)");
    }

    @Test
    void entitySelectorAcrossRegionsFinishesInsideTheSameTick() throws Exception {
        core.world().submitAtChunk(FAR, r -> r.chunkAtBlocking(FAR)).get(30, TimeUnit.SECONDS);
        assertNotSame(core.world().submitAtChunk(C00, r -> r).get(10, TimeUnit.SECONDS),
                core.world().submitAtChunk(FAR, r -> r).get(10, TimeUnit.SECONDS), "두 region");
        core.world().submitAtChunk(FAR, r -> {
            var pig = org.domi.furnace.server.entity.Entities.create(r.level(), "minecraft:pig");
            pig.moveTo(3208.5, -60, 3208.5, 0, 0);
            r.level().addFreshEntity(pig);
            return null;
        }).get(10, TimeUnit.SECONDS);
        warmUp();
        // 첫 명령은 모든 region 을 도는 @e(멀리 있는 돼지), 체인은 그 성공에 기대지 않는 명령
        String got = runChain("execute if entity @e[type=minecraft:pig] run setblock 1 -60 6 stone",
                "execute as @e[type=minecraft:pig] run setblock 2 -60 6 stone", "setblock 3 -60 6 stone",
                new Pos(1, -60, 6), new Pos(2, -60, 6), new Pos(3, -60, 6));
        assertEquals("111 stone stone stone", got);
    }
}
