package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.nio.file.Path;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.builtin.FurnaceCommands;
import org.domi.furnace.server.i18n.Lang;
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
            assertEquals("Plugins (0)", FurnaceCommands.pluginsLine(java.util.List.of(), core.plugins().plugins()));
            try {
                Lang.forceForTest("ko");
                assertEquals("플러그인 (0)", FurnaceCommands.pluginsLine(java.util.List.of(), core.plugins().plugins()), "the reply follows the server language");
                assertEquals("다시 읽음: A, B", FurnaceCommands.reloadLine("x", java.util.List.of("A", "B")));
            } finally {
                Lang.forceForTest("en");
            }
        } finally {
            sched.close();
        }
    }

    private static org.domi.furnace.api.plugin.PluginDescription desc(String name, String version, String description, String website, java.util.List<String> authors) {
        return new org.domi.furnace.api.plugin.PluginDescription(name, version, "a.B", description, authors, website, null, java.util.List.of(),
                java.util.List.of(), java.util.List.of(), null, java.util.Map.of(), java.util.List.of(), null);
    }

    private static java.util.List<String> plain(java.util.List<org.domi.furnace.api.text.Text> lines) {
        return lines.stream().map(t -> org.domi.furnace.api.text.serializer.PlainTextSerializer.plain().serialize(t)).toList();
    }

    /** Bukkit VersionCommand 와 같은 찾기·출력: 정확한 이름 우선, 없으면 부분 일치 전부, 없으면 안내 두 줄. */
    @Test
    void versionOfPluginFollowsBukkit() {
        var fawe = desc("FastAsyncWorldEdit", "2.16.2", "Fast edits", "https://example.org", java.util.List.of("A", "B", "C"));
        var lp = desc("LuckPerms", "5.5.91", "", null, java.util.List.of("Luck"));
        var edit2 = desc("WorldEditX", "1", "", null, java.util.List.of());
        var all = java.util.List.of(fawe, lp, edit2);
        assertEquals(java.util.List.of("FastAsyncWorldEdit version 2.16.2", "Fast edits", "Website: https://example.org", "Authors: A, B and C"),
                plain(FurnaceCommands.pluginInfo(all, "fastasyncworldedit")));
        assertEquals(java.util.List.of("LuckPerms version 5.5.91", "Author: Luck"), plain(FurnaceCommands.pluginInfo(all, "LuckPerms")));
        // 부분 일치는 전부 보여 준다(WorldEdit 은 두 플러그인 이름에 든다)
        assertEquals(java.util.List.of("FastAsyncWorldEdit version 2.16.2", "Fast edits", "Website: https://example.org", "Authors: A, B and C",
                "WorldEditX version 1"), plain(FurnaceCommands.pluginInfo(all, "worldedit")));
        // 정확한 이름이 있으면 부분 일치는 건너뛴다
        assertEquals(java.util.List.of("WorldEditX version 1"), plain(FurnaceCommands.pluginInfo(all, "WorldEditX")));
        assertEquals(java.util.List.of("This server is not running any plugin by that name.", "Use /plugins to get a list of plugins."),
                plain(FurnaceCommands.pluginInfo(all, "nope")));
    }
}
