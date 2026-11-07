package org.domi.furnace.server.plugin;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import org.domi.furnace.api.command.CommandBuilder;
import org.domi.furnace.api.command.CommandBuilder.ArgType;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.CommandTreePacket;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.TestServers;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/** C1: Brigadier 식 API 트리·BasicCommand 가 한 트리에 붙고, 권한으로 걸러지며, 런타임에 빼고 다시 보낸다. */
class CommandTreeApiTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private PluginHost host;
    private FurnacePlugin plugin;

    @BeforeEach
    void setUp() throws Exception {
        TestPlugins.LOG.clear();
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        Path dir = tmp.resolve("plugins");
        PluginSystemTest.jar(dir.resolve("a.jar"), "furnace-plugin.yml",
                "name: Alpha\nversion: 1\nmain: org.domi.furnace.server.plugin.TestPlugins$Alpha\n", TestPlugins.Alpha.class);
        host = new PluginHost(core, dir);
        host.pluginsImpl().loadAll();
        host.pluginsImpl().enableAll();
        plugin = host.plugins().plugin("Alpha");
    }

    @AfterEach
    void tearDown() {
        host.pluginsImpl().disableAll();
        host.pluginsImpl().closeAll();
        host.close();
        sched.close();
    }

    private void barrier() throws Exception { core.scheduler().global().submit(() -> null).join(); }

    @Test
    void treeCommandRunsWithTypedArguments() throws Exception {
        List<String> got = new CopyOnWriteArrayList<>();
        CommandBuilder root = CommandBuilder.literal("warpx")
                .executes(c -> { got.add("bare"); return 1; })
                .then(CommandBuilder.literal("go")
                        .then(CommandBuilder.argument("name", ArgType.word())
                                .suggests((s, p) -> List.of("spawn", "mine"))
                                .then(CommandBuilder.argument("n", ArgType.integer(1, 5))
                                        .executes(c -> { got.add(c.getString("name") + ":" + c.getInt("n") + ":" + c.sender().name()); return 1; }))));
        var names = host.commands().register(plugin, root);
        assertTrue(names.containsAll(List.of("warpx", "alpha:warpx")));
        host.dispatchCommand(host.console(), "warpx");
        host.dispatchCommand(host.console(), "warpx go spawn 3");
        host.dispatchCommand(host.console(), "alpha:warpx go spawn 9");   // 범위 밖 → 실행 안 됨
        barrier();
        assertEquals(List.of("bare", "spawn:3:Server"), got);
        var s = core.commands().suggestions(CommandSource.console(core), "/warpx go sp").get();
        assertEquals(List.of("spawn"), s.getList().stream().map(x -> x.getText()).toList());
    }

    @Test
    void requirementHidesNodeFromTree() {
        host.commands().register(plugin, CommandBuilder.literal("hidden").requires(s -> false).executes(c -> 1));
        host.commands().register(plugin, CommandBuilder.literal("shown").executes(c -> 1));
        var root = core.commands().getDispatcher().getRoot();
        CommandSource src = CommandSource.console(core);
        assertFalse(root.getChild("hidden").canUse(src));
        assertTrue(root.getChild("shown").canUse(src));
        // 패킷에서도 빠진다: hidden 이 없을 때와 있을 때 노드 수 차이는 2(hidden, alpha:hidden)
        int with = nodeCount(CommandTreePacket.forSource(root, src));
        host.commands().unregister(plugin, "hidden");
        host.commands().unregister(plugin, "alpha:hidden");
        assertNull(root.getChild("hidden"));
        assertNotNull(root.getChild("shown"));
        assertEquals(with, nodeCount(CommandTreePacket.forSource(root, src)), "숨겨진 노드는 원래 패킷에 없었다");
    }

    @Test
    void unregisterRemovesOnlyThatNameAndMixesWithBasicCommand() {
        host.commands().register(plugin, "plain", (sender, args) -> {});
        host.commands().register(plugin, CommandBuilder.literal("treed").executes(c -> 1));
        var root = core.commands().getDispatcher().getRoot();
        assertNotNull(root.getChild("plain"));
        assertNotNull(root.getChild("treed"));
        assertTrue(host.commands().unregister(plugin, "treed"));
        assertFalse(host.commands().unregister(plugin, "treed"));
        assertNull(root.getChild("treed"));
        assertNotNull(root.getChild("alpha:treed"));
        assertNotNull(root.getChild("plain"));
        host.commands().updateCommands();           // 접속자 없음 — 예외 없이 끝난다
        host.commands().updateCommands(host.console());
    }

    private static int nodeCount(CommandTreePacket p) {
        var buf = io.netty.buffer.Unpooled.buffer();
        p.write(buf);
        return org.domi.furnace.server.net.io.Types.readVarInt(buf);
    }
}
