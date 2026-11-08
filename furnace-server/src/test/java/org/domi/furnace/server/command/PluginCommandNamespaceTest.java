// 플러그인 명령 등록의 이름 충돌 경고 문구(이름이 minecraft 인 플러그인) + send-namespaced=false 의 모든 단계 ':' 거르기.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import java.nio.file.Path;
import java.util.List;
import org.domi.furnace.api.command.BasicCommand;
import org.domi.furnace.api.command.CommandBuilder;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.api.plugin.FurnacePlugin;
import org.domi.furnace.api.plugin.PluginContext;
import org.domi.furnace.api.plugin.PluginDescription;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;



import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

class PluginCommandNamespaceTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private org.domi.furnace.server.plugin.PluginHost host;
    private ListAppender<ILoggingEvent> logs;
    private final BasicCommand noop = (CommandSender s, String[] a) -> {};

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        host = core.plugins();
        logs = new ListAppender<>();
        logs.start();
        ((Logger) LoggerFactory.getLogger("Furnace")).addAppender(logs);
    }

    @AfterEach
    void tearDown() throws Exception {
        ((Logger) LoggerFactory.getLogger("Furnace")).detachAppender(logs);
        host.close();
        sched.close();
        TestServers.drainIoPools();
    }

    private FurnacePlugin plugin(String name) {
        FurnacePlugin p = new FurnacePlugin() {};
        p.init(new PluginContext() {
            final PluginDescription d = new PluginDescription(name, "1", "x", "", List.of(), null, null, List.of(), List.of(), List.of(),
                    null, java.util.Map.of(), List.of(), null);
            public PluginDescription description() { return d; }
            public org.domi.furnace.api.Furnace server() { return host; }
            public java.util.logging.Logger logger() { return java.util.logging.Logger.getLogger(name); }
            public Path configFolder() { return tmp.resolve(name); }
            public Path dataFolder() { return tmp.resolve(name); }
            public Path file() { return tmp.resolve(name + ".jar"); }
            public ClassLoader classLoader() { return getClass().getClassLoader(); }
            public org.domi.furnace.api.registry.PluginRegistries registries() { return null; }
        });
        p.setEnabled(true);
        return p;
    }

    private List<String> warnings() {
        return logs.list.stream().filter(e -> e.getLevel().toInt() >= ch.qos.logback.classic.Level.WARN_INT).map(ILoggingEvent::getFormattedMessage).toList();
    }

    @Test
    void ordinaryPluginCollisionStillSaysItIsRegisteredUnderThePrefixedName() {
        var registered = host.commands().register(plugin("other"), "give", noop);
        assertEquals(List.of("other:give"), List.copyOf(registered));
        assertEquals(1, warnings().size(), warnings().toString());
        assertTrue(warnings().get(0).contains("'other:give' 로만 등록한다"), warnings().toString());
    }

    @Test
    void pluginNamedMinecraftCannotRegisterOverVanillaAndSaysNothingWasRegistered() {
        var registered = host.commands().register(plugin("minecraft"), "give", noop);
        assertTrue(registered.isEmpty(), registered.toString());
        assertEquals(1, warnings().size(), warnings().toString());
        String w = warnings().get(0);
        assertTrue(w.contains("등록하지 않는다") && !w.contains("로만 등록한다"), w);
        // 바닐라 /give 는 그대로
        assertNotNull(core.commands().getDispatcher().getRoot().getChild("minecraft:give"));
    }

    @Test
    void sendNamespacedFalseDropsColonSubLiteralsAtEveryLevel() {
        var b = CommandBuilder.literal("deep").executes(c -> 1)
                .then(CommandBuilder.literal("a:b").executes(c -> 1))
                .then(CommandBuilder.literal("ok").executes(c -> 1));
        host.commands().register(plugin("deeper"), b, "", List.of());
        var src = CommandSource.console(core);
        var root = core.commands().getDispatcher().getRoot();
        assertTrue(subNames(CommandTreePacket.forSource(root, src, true), "deep").contains("a:b"));
        var off = subNames(CommandTreePacket.forSource(root, src, false), "deep");
        assertTrue(off.contains("ok") && !off.contains("a:b"), off.toString());
    }

    private static List<String> subNames(CommandTreePacket p, String literal) {
        int[] rootOut = new int[1];
        var nodes = VanillaCommandTreeTest.decode(p, rootOut);
        for (int c : nodes.get(rootOut[0]).children()) {
            if (literal.equals(nodes.get(c).name())) {
                return java.util.Arrays.stream(nodes.get(c).children()).mapToObj(i -> nodes.get(i).name()).toList();
            }
        }
        fail("no root literal " + literal);
        return List.of();
    }
}
