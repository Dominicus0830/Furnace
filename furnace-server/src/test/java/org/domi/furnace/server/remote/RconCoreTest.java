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

    @Test
    void remoteServerCommandEventCanRewriteOrCancel() throws Exception {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            var plugin = new org.domi.furnace.api.plugin.FurnacePlugin() {};
            org.domi.furnace.server.TestPluginContexts.init(plugin, core.plugins(), "rce", tmp);
            plugin.setEnabled(true);
            java.util.List<String> seen = new java.util.concurrent.CopyOnWriteArrayList<>();
            core.plugins().eventsImpl().subscribe(plugin, org.domi.furnace.api.event.server.RemoteServerCommandEvent.class, e -> {
                seen.add(e.sender().name() + ":" + e.sender().isOp() + ":" + e.command());
                if (e.command().equals("blocked")) {
                    e.sender().sendMessage(org.domi.furnace.api.text.Text.text("nope"));
                    e.setCancelled(true);
                } else if (e.command().equals("make")) {
                    e.command("scoreboard objectives add rw dummy");
                }
            });
            org.junit.jupiter.api.Assertions.assertEquals("", core.runRconCommand("blocked"), "취소면 실행하지 않고 빈 응답(Paper DedicatedServer.runCommand)");
            assertTrue(core.runRconCommand("make").contains("rw"), "바뀐 명령을 실행한다");
            assertTrue(core.runRconCommand("scoreboard objectives list").contains("rw"));
            org.junit.jupiter.api.Assertions.assertEquals(java.util.List.of("Rcon:true:blocked", "Rcon:true:make", "Rcon:true:scoreboard objectives list"), seen);
            core.plugins().eventsImpl().unregisterAll(plugin);
        } finally {
            sched.close();
        }
    }
}
