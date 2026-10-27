// /forceload: add/remove/query 의 결과·오류 키, 256 청크 한도, chunks.dat 저장·복원, 붙잡은 청크는 티켓으로 남는다.
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.brigadier.CommandDispatcher;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.world.ForcedChunks;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ForceLoadCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    CommandDispatcher<CommandSource> d;
    CommandSource src;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        d = core.commands().getDispatcher();
        src = CommandSource.console(core);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private String err(String cmd) {
        return assertThrows(CommandSyntaxException.class, () -> d.execute(cmd, src)).getRawMessage().translationKey();
    }

    @Test
    void addQueryRemove() throws Exception {
        var forced = core.world().forcedChunks();
        assertEquals(1, d.execute("forceload add 0 0", src));
        assertTrue(forced.contains(0, 0));
        assertEquals(1, d.execute("forceload query 5 5", src), "블록 좌표 (5,5) 는 청크 (0,0)");
        assertEquals("commands.forceload.added.failure", err("forceload add 3 3"));
        assertEquals("commands.forceload.query.failure", err("forceload query 16 0"));
        // 범위: 청크 (0,0)..(1,1) 중 (0,0) 은 이미 있어 셋만 늘어난다
        assertEquals(3, d.execute("forceload add 0 0 20 20", src));
        assertEquals(4, forced.size());
        assertEquals(4, d.execute("forceload query", src));
        assertEquals(1, d.execute("forceload remove 20 20", src));
        assertFalse(forced.contains(1, 1));
        assertEquals("commands.forceload.removed.failure", err("forceload remove 20 20"));
        assertEquals(0, d.execute("forceload remove all", src));
        assertEquals(0, forced.size());
        assertEquals(0, d.execute("forceload query", src), "없으면 실패 메시지를 보내고 0");
    }

    @Test
    void limitsAndBounds() {
        assertEquals("commands.forceload.toobig", err("forceload add 0 0 300 300"));
        assertEquals("argument.pos.outofworld", err("forceload add 30000000 0"));
        assertEquals("argument.pos.outofworld", err("forceload add -30000001 0"));
        assertEquals(0, core.world().forcedChunks().size());
        // 정확히 256 청크(16x16)는 된다
        assertDoesNotThrow(() -> d.execute("forceload add 0 0 255 255", src));
        assertEquals(256, core.world().forcedChunks().size());
        // 순서: 부호 있는 long 오름차순(바닐라 ChunkPos.asLong, x 가 아래 32비트)
        long[] keys = core.world().forcedChunks().sortedKeys();
        for (int i = 1; i < keys.length; i++) assertTrue(keys[i - 1] < keys[i]);
    }

    @Test
    void permissionTwoRequired() {
        assertThrows(CommandSyntaxException.class, () -> d.execute("forceload add 0 0", src.withPermission(1)));
    }

    @Test
    void persistsToChunksDat() {
        Path dir = tmp.resolve("dim");
        var f = ForcedChunks.open(dir);
        assertTrue(f.set(3, -4, true));
        assertTrue(f.set(-1, 7, true));
        assertFalse(f.set(3, -4, true));
        f.saveIfDirty();
        var again = ForcedChunks.open(dir);
        assertTrue(again.contains(3, -4));
        assertTrue(again.contains(-1, 7));
        assertEquals(2, again.size());
        assertTrue(again.set(3, -4, false));
        again.saveIfDirty();
        assertEquals(1, ForcedChunks.open(dir).size());
        // 바닐라 ChunkPos.asLong 배치
        assertEquals(0xFFFFFFFFL & 3 | (long) -4 << 32, ForcedChunks.asLong(3, -4));
        assertEquals(-4, ForcedChunks.zOf(ForcedChunks.asLong(3, -4)));
        assertEquals(3, ForcedChunks.xOf(ForcedChunks.asLong(3, -4)));
    }
}
