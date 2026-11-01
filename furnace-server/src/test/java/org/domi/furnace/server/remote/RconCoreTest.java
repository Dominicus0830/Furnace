package org.domi.furnace.server.remote;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** ServerCore.runRconCommand: IO 스레드에서 불러도 명령은 전역 스레드에서 돌고 출력이 평문으로 모인다. */
class RconCoreTest {

    @TempDir Path tmp;

    @Test
    void commandOutputIsCaptured() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            assertTrue(core.runRconCommand("scoreboard objectives add rc dummy").contains("rc"));
            assertTrue(core.runRconCommand("scoreboard objectives list").contains("rc"));
            // 실패 메시지도 응답으로(바닐라 RconConsoleSource 는 실패도 받는다)
            assertFalse(core.runRconCommand("nosuchcommand").isEmpty());
        } finally {
            sched.close();
        }
    }

    @Test
    void ioThreadCallRunsCommandOnGlobalThread() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            String[] result = new String[1];
            Throwable[] err = new Throwable[1];
            Thread io = new Thread(() -> {
                try {
                    result[0] = core.runRconCommand("scoreboard objectives add io dummy");
                } catch (Throwable t) {
                    err[0] = t;
                }
            }, "RCON Client test");
            io.start();
            io.join(10_000);
            assertNull(err[0]);
            assertTrue(result[0].contains("io"), result[0]);
        } finally {
            sched.close();
        }
    }
}
