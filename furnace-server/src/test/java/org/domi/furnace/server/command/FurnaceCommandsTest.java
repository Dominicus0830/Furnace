package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.builtin.FurnaceCommands;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** /version·/plugins 는 바닐라 트리(BuiltinCommands)에 없고 FurnaceCommands 로 따로 붙는다. */
class FurnaceCommandsTest {

    @TempDir Path tmp;

    @Test
    void registeredSeparatelyFromVanillaTree() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = TestServers.core(tmp, sched);
            var root = core.commands().getDispatcher().getRoot();
            assertNull(root.getChild("version"));
            assertNull(root.getChild("plugins"));
            FurnaceCommands.register(core.commands().getDispatcher(), core.plugins());
            assertNotNull(root.getChild("version"));
            assertNotNull(root.getChild("plugins"));
            assertEquals("Furnace " + core.plugins().version() + " (Minecraft 1.21.1)", FurnaceCommands.versionLine(core.plugins()));
            assertEquals("플러그인 (0)", FurnaceCommands.pluginsLine(java.util.List.of(), core.plugins().plugins()));
        } finally {
            sched.close();
        }
    }
}
