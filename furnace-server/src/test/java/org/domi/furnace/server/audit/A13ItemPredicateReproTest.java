// A13-01 재현 시험(docs/audit/findings/A13-repro/A13ReproTest.java.txt 의 아이템 술어 두 개): 명령에서 하위 술어(custom_data~·enchantments~)를
// 바닐라 1.21.1 처럼 받는다. 기준 커밋에서는 'Not supported yet' 로 실패했다.
package org.domi.furnace.server.audit;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ChunkPos;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class A13ItemPredicateReproTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    Player steve;
    final List<String> out = Collections.synchronizedList(new ArrayList<>());

    @BeforeEach
    void setUp() throws Exception {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.world().awaitChunk(new ChunkPos(0, 0));
        core.addConsoleListener(t -> out.add(t.getString()));
        steve = new Player(10, UUID.nameUUIDFromBytes("Steve".getBytes()), "Steve", new ConcurrentEmbeddedChannel());
        steve.applyMove(0.5, 70, 0.5, 0, 0, true);
        core.world().playerOnline(steve);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private int run(String cmd) throws Exception {
        out.clear();
        return core.commands().performPrefixedCommandAsync(CommandSource.console(core).withEntity(steve), cmd).get(10, TimeUnit.SECONDS).result();
    }

    @Test
    void itemPredicateCustomDataPartialMatch() throws Exception {
        run("give @s diamond_sword[custom_data={a:1,b:2}]");
        assertEquals(1, run("execute if items entity @s container.0 diamond_sword[custom_data~{a:1}]"), out.toString());
        assertEquals(1, run("clear @s diamond_sword[custom_data~{a:1}] 0"), out.toString());
    }

    @Test
    void itemPredicateEnchantmentsPartialMatch() throws Exception {
        run("give @s diamond_sword[enchantments={levels:{sharpness:3}}]");
        assertEquals(1, run("execute if items entity @s container.0 diamond_sword[enchantments~[{enchantments:\"minecraft:sharpness\",levels:{min:3}}]]"), out.toString());
    }

    /** 어긋나는 값은 0(오류 아님), 타입이 다른 수(1b 대 1)는 바닐라 compareNbt 처럼 다르다. */
    @Test
    void itemPredicateMismatchCountsZero() throws Exception {
        run("give @s diamond_sword[custom_data={a:1,b:2}]");
        assertEquals(0, run("execute if items entity @s container.0 diamond_sword[custom_data~{a:2}]"), out.toString());
        assertEquals(0, run("execute if items entity @s container.0 diamond_sword[custom_data~{a:1b}]"), out.toString());
        assertEquals(0, run("execute if items entity @s container.0 diamond_sword[enchantments~[{enchantments:\"minecraft:sharpness\",levels:{min:4}}]]"), out.toString());
    }
}
