// /furnace pregen 명령: 권한, 중복 시작 거절, 상태·멈춤, 기본(없는) 월드 경계 거절.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import java.util.concurrent.TimeUnit;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.builtin.PregenCommands;
import org.domi.furnace.server.remote.RconConsole;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class PregenCommandsTest {

    @TempDir Path tmp;

    private static String run(ServerCore core, String cmd, int perm) throws Exception {
        var spawn = core.world().defaultSpawn();
        var sink = new RconConsole(() -> false, spawn.x(), spawn.y(), spawn.z());
        core.commands().performPrefixedCommandAsync(CommandSource.rcon(core, sink).withPermission(perm), cmd).get(20, TimeUnit.SECONDS);
        return sink.takeOutput();
    }

    @Test
    void startStatusStop_refusesDuplicateAndUnboundedBorder() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        ServerCore core = TestServers.core(tmp, sched);
        try {
            PregenCommands.register(core.commands().getDispatcher());
            assertFalse(run(core, "furnace pregen start 100", 3).contains("started"), "권한 4 미만은 못 쓴다");

            String border = run(core, "furnace pregen start border", 4);
            assertTrue(border.contains("The world border is at its default"), border);

            String start = run(core, "furnace pregen start 4000 0 0", 4);   // 500×500 청크 — 시험 동안 끝나지 않는다
            assertTrue(start.contains("Pregeneration started"), start);
            String dup = run(core, "furnace pregen start 100", 4);
            assertTrue(dup.contains("already running"), dup);
            String status = run(core, "furnace pregen status", 4);
            assertTrue(status.contains("[Pregen]") && status.contains("/250000 chunks"), status);
            String stop = run(core, "furnace pregen stop", 4);
            assertTrue(stop.contains("stopped"), stop);
            String again = run(core, "furnace pregen stop", 4);
            assertTrue(again.contains("No pregeneration is running"), again);
        } finally {
            core.pregen().shutdown();
            sched.close();
        }
    }
}
