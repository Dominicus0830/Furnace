package org.domi.furnace.compat.paper;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.bukkit.Bukkit;
import org.domi.furnace.compat.paper.fixtures.CommandProbe;
import org.domi.furnace.compat.paper.fixtures.JarCompiler;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.LoggerFactory;

/**
 * 명령 정합(P1, docs/feature-parity/paper-vs-spigot-2026-11-08.md 1장): Paper 1.21.1 과 같은 ① 인자 빈 칸 규칙(플레이어·콘솔·dispatchCommand 모두 같은 인자),
 * ② 없는 명령의 dispatchCommand 는 false, ③ 표지판 run_command 클릭은 콘솔 기록과 PlayerSignCommandPreprocessEvent(PlayerCommandPreprocessEvent 하위)를 거친다.
 */
class CommandParityTest {

    private static final String YML = """
            name: Parity
            version: '1'
            main: parity.ParityPlugin
            api-version: '1.21'
            """;

    private static final String PLUGIN = """
            package parity;
            import java.util.List;
            import org.bukkit.command.*;
            import org.bukkit.event.*;
            import org.bukkit.event.player.PlayerCommandPreprocessEvent;
            import org.bukkit.plugin.java.JavaPlugin;
            import io.papermc.paper.event.player.PlayerSignCommandPreprocessEvent;
            import org.domi.furnace.compat.paper.fixtures.CommandProbe;
            public final class ParityPlugin extends JavaPlugin implements Listener {
                @Override public void onEnable() {
                    getServer().getCommandMap().register("parity", new Command("echo") {
                        @Override public boolean execute(CommandSender s, String l, String[] a) {
                            CommandProbe.LOG.add("echo:" + l + "|" + String.join("|", a) + "|n=" + a.length);
                            return true;
                        }
                    });
                    getServer().getPluginManager().registerEvents(this, this);
                }
                @EventHandler(priority = EventPriority.LOW)
                public void onAnyCommand(PlayerCommandPreprocessEvent e) {
                    CommandProbe.LOG.add("pre:" + e.getClass().getSimpleName() + "|" + e.getMessage());
                }
                @EventHandler(priority = EventPriority.NORMAL)
                public void onSign(PlayerSignCommandPreprocessEvent e) {
                    CommandProbe.LOG.add("sign:" + e.getSide() + "|" + e.getMessage());
                    if (CommandProbe.cancelSign) e.setCancelled(true);
                    if (CommandProbe.rewriteSign != null) e.setMessage(CommandProbe.rewriteSign);
                }
            }
            """;

    @TempDir
    Path tmp;

    @BeforeEach
    void reset() { CommandProbe.reset(); }

    private FurnaceTestServer start() throws Exception {
        Path jar = JarCompiler.jar(tmp.resolve("parity.jar"), YML, Map.of("parity.ParityPlugin", PLUGIN));
        return FurnaceTestServer.builder().failFast(false).pluginJar(jar).start();
    }

    // ------------------------------------------------------------------ A1 인자 빈 칸

    @Test
    void everyRouteDropsEmptyArgumentsLikePaper() throws Exception {
        try (FurnaceTestServer server = start()) {
            server.execute("echo  a   b ");                                     // 서버 명령 줄(플레이어·콘솔이 치는 길)
            server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "echo  a   b "));
            server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "  echo a  b"));   // 앞 빈 칸(Paper 도 버린다)
            server.onGlobal(() -> Bukkit.getCommandMap().dispatch(Bukkit.getConsoleSender(), "ECHO   a    b   "));
            assertEquals(List.of("echo:echo|a|b|n=2", "echo:echo|a|b|n=2", "echo:echo|a|b|n=2", "echo:echo|a|b|n=2"), CommandProbe.LOG);
        }
    }

    @Test
    void blankOnlyLineIsNotACommand() throws Exception {
        try (FurnaceTestServer server = start()) {
            assertFalse(server.onGlobal(() -> Bukkit.getCommandMap().dispatch(Bukkit.getConsoleSender(), "   ")));
            assertFalse(server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "")));
            assertTrue(CommandProbe.LOG.isEmpty());
        }
    }

    // ------------------------------------------------------------------ A5 없는 명령은 false

    @Test
    void dispatchCommandReturnsFalseOnlyForNamesTheTreeDoesNotKnow() throws Exception {
        try (FurnaceTestServer server = start()) {
            assertTrue(server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "echo x")), "옛 명령");
            assertTrue(server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "say hi")), "바닐라 명령(큐에 접수)");
            assertTrue(server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "/say hi")), "앞 / 는 뗀다");

            int from = server.consoleMessages().size();
            assertFalse(server.onGlobal(() -> Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "no-such-command x")), "없는 명령");
            server.tick();
            var after = server.consoleMessages().subList(from, server.consoleMessages().size());
            assertTrue(after.stream().anyMatch(t -> t.plainText().contains("Unknown or incomplete command")),
                    "보낸 사람에게는 바닐라 오류 문장이 간다: " + after);

            // 다른 스레드(시험 스레드)에서 불러도 같다 — 이름 검사는 스레드와 상관없다
            assertTrue(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "say again"));
            assertFalse(Bukkit.dispatchCommand(Bukkit.getConsoleSender(), "also-not-a-command"));
        }
    }

    // ------------------------------------------------------------------ A6 표지판 명령

    private static org.domi.furnace.server.entity.Player firstPlayer(FurnaceTestServer server) {
        return server.internals().players().iterator().next();
    }

    private boolean signClick(FurnaceTestServer server, boolean front, String command) {
        var pos = new org.domi.furnace.api.world.BlockPos(2, 70, 2);
        var player = firstPlayer(server);
        return server.onRegion(pos, () -> server.internals().world().levelServices().runSignCommand(player, 2, 70, 2, front, command));
    }

    @Test
    void signClickCommandFiresThePreprocessEventAndLogsLikePaper() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("Server");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (FurnaceTestServer server = start()) {
            server.join("Alice");
            server.tick(2);

            assertTrue(signClick(server, true, "echo hi"));
            server.tick(2);
            assertEquals(List.of("pre:PlayerSignCommandPreprocessEvent|/echo hi", "sign:FRONT|/echo hi", "echo:echo|hi|n=1"), CommandProbe.LOG,
                    "PlayerCommandPreprocessEvent 처리기도 하위 이벤트를 받고(Paper 와 같다), 앞에 / 가 붙은 채 온다. 그 뒤 명령이 돈다");
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().equals("Alice issued server command: /echo hi")),
                    "콘솔에 기록: " + appender.list);

            CommandProbe.LOG.clear();
            assertTrue(signClick(server, false, "/echo slash"));   // 이미 / 로 시작하면 그대로
            server.tick(2);
            assertEquals(List.of("pre:PlayerSignCommandPreprocessEvent|/echo slash", "sign:BACK|/echo slash", "echo:echo|slash|n=1"), CommandProbe.LOG);
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void cancelledSignCommandDoesNotRunButIsStillLogged() throws Exception {
        Logger logger = (Logger) LoggerFactory.getLogger("Server");
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try (FurnaceTestServer server = start()) {
            server.join("Alice");
            server.tick(2);
            CommandProbe.cancelSign = true;
            assertFalse(signClick(server, true, "echo blocked"), "취소하면 false(Paper: 표지판 클릭이 명령을 돌리지 않은 것으로 끝난다)");
            server.tick(2);
            assertEquals(List.of("pre:PlayerSignCommandPreprocessEvent|/echo blocked", "sign:FRONT|/echo blocked"), CommandProbe.LOG, "명령은 안 돈다");
            assertTrue(appender.list.stream().anyMatch(e -> e.getFormattedMessage().equals("Alice issued server command: /echo blocked")),
                    "기록은 이벤트보다 먼저라 취소돼도 남는다(Paper 와 같다)");
        } finally {
            logger.detachAppender(appender);
        }
    }

    @Test
    void signCommandRewrittenByAPluginRunsAsRewritten() throws Exception {
        try (FurnaceTestServer server = start()) {
            server.join("Alice");
            server.tick(2);
            CommandProbe.rewriteSign = "/echo changed";
            assertTrue(signClick(server, true, "echo original"));
            server.tick(2);
            assertTrue(CommandProbe.LOG.contains("echo:echo|changed|n=1"), CommandProbe.LOG.toString());
            assertFalse(CommandProbe.LOG.stream().anyMatch(s -> s.contains("original|")), CommandProbe.LOG.toString());
        }
    }
}
