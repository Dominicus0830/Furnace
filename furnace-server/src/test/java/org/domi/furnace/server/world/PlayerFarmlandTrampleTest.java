// 플레이어 낙하로 경작지 밟기(바닐라 ServerPlayer.doCheckFallDamage → FarmBlock.fallOn). 봇 시험(2026-11-03)에서 5칸 낙하에도 그대로였다.
package org.domi.furnace.server.world;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.channel.embedded.EmbeddedChannel;
import java.nio.file.Path;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.block.B;
import org.domi.furnace.server.block.Pos;
import org.domi.furnace.server.data.TagRegistries;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.PlayerInventory;
import org.domi.furnace.server.item.ItemContext;
import org.domi.furnace.server.net.registry.RegistrySet;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PlayerFarmlandTrampleTest {

    private static final ChunkPos C00 = new ChunkPos(0, 0);

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;

    @BeforeAll
    static void install() {
        ItemContext.install(new TagRegistries(RegistrySet.load("registries/1.21.1.txt")));
    }

    @BeforeEach
    void setUp() throws Exception {
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

    /** 경작지(8,-61,8) 위로 from 높이에서 떨어져 내려앉힌 뒤 그 칸 블록 이름. */
    private String landOnFarmland(double fromY) throws Exception {
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), "A", new EmbeddedChannel());
        p.inventory(new PlayerInventory());
        p.applyMove(8.5, fromY, 8.5, 0, 0, fromY <= -60.0625);
        return core.world().submitAtChunk(C00, r -> {
            var level = r.level();
            Pos at = new Pos(8, -61, 8);
            level.setBlock(at, B.defaultState("minecraft:farmland"), 3);
            r.adopt(p);
            boolean ground = fromY <= -60.0625;
            p.pushMove(8.5, fromY, 8.5, ground);   // 기준점
            for (double y = fromY - 1; y > -60.0625; y -= 1) p.pushMove(8.5, y, 8.5, false);
            p.pushMove(8.5, -60.0625, 8.5, true);   // 경작지 윗면(15/16)
            r.tick();
            int st = level.getBlockState(at);
            return B.is(st, "minecraft:dirt") ? "minecraft:dirt" : B.is(st, "minecraft:farmland") ? "minecraft:farmland" : "기타 " + st;
        }).get(30, TimeUnit.SECONDS);
    }

    @Test
    void fallingFiveBlocksTramplesFarmland() throws Exception {
        assertEquals("minecraft:dirt", landOnFarmland(-55.0625), "낙하 거리 5 - 0.5 > 1 이라 반드시 흙");
    }

    @Test
    void walkingOnFarmlandKeepsIt() throws Exception {
        assertEquals("minecraft:farmland", landOnFarmland(-60.0625), "낙하 거리 0 이면 fallOn 을 부르지 않는다");
    }
}
