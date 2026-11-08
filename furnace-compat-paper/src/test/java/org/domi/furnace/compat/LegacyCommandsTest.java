// LegacyCommands/LegacyCommand: plugin.yml 선언 → 실행기 부착, 라벨·권한·사용법·예외·자동완성·dispatchCommand.
package org.domi.furnace.compat;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicReference;
import org.domi.furnace.api.command.CommandSender;
import org.domi.furnace.compat.command.LegacyCommand;
import org.domi.furnace.compat.thread.PrimaryThread;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.test.FurnaceTestServer;
import org.junit.jupiter.api.Test;

class LegacyCommandsTest {

    private static FurnaceTestServer start() { return FurnaceTestServer.builder().exposeCompatToNative(true).plugin(CompatTestPlugin.class).start(); }

    @Test
    void declaredCommandsCarryYamlSettingsAndCatchTypedLabel() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            LegacyCommand kit = p.commands.command(p, "kit");
            assertSame(kit, p.commands.command(p, "K"), "별칭으로도 찾는다(대소문자 무시)");
            assertEquals("compat.kit", kit.permission());
            assertEquals(List.of("k", "kits"), kit.aliases());
            assertEquals("Usage: /<command> <name>", kit.usage());
            assertEquals("/open", p.commands.command(p, "open").usage(), "usage 가 없으면 /이름");

            List<String> seen = new CopyOnWriteArrayList<>();
            AtomicReference<Boolean> primary = new AtomicReference<>();
            kit.setExecutor((sender, cmd, label, args) -> {
                seen.add(label + "|" + String.join(",", args) + "|" + sender.name());
                primary.set(PrimaryThread.isPrimary());
                return true;
            });
            server.execute("kit a b");
            server.execute("k x");
            server.execute("compattest:kit y");
            server.execute("compattest:kits z");
            assertEquals(List.of("kit|a,b|Server", "k|x|Server", "compattest:kit|y|Server", "compattest:kits|z|Server"), seen,
                    "친 이름 그대로 label 로 간다(Bukkit 과 같다)");
            assertEquals(Boolean.TRUE, primary.get());
        }
    }

    @Test
    void falseResultSendsUsageWithLabelAndMultilines() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            p.commands.command(p, "kit").setExecutor((s, c, l, a) -> false).setUsage("Usage: /<command> <name>\nSecond line");
            var out = server.execute("k");
            assertEquals(List.of("Usage: /k <name>", "Second line"), out.lines().stream().map(Object::toString).toList());
        }
    }

    @Test
    void emptyUsageStaysSilentAndNullExecutorShowsUsage() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            p.commands.command(p, "boom").setExecutor((s, c, l, a) -> false);
            assertTrue(server.execute("boom").lines().isEmpty(), "usage 가 빈 문자열이면 말없이");
            var out = server.execute("open");   // 실행기 없음(false 취급) → "/open"
            assertEquals(List.of("/open"), out.lines().stream().map(Object::toString).toList());
        }
    }

    @Test
    void exceptionReportsInternalErrorToSender() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            p.commands.command(p, "boom").setExecutor((s, c, l, a) -> { throw new IllegalStateException("시험용 예외"); });
            var out = server.execute("boom");
            assertEquals(List.of("An internal error occurred while attempting to perform this command"),
                    out.lines().stream().map(Object::toString).toList());
        }
    }

    @Test
    void permissionHidesCommandFromPlayersWithoutItAndLetsHoldersRun() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            List<String> seen = new CopyOnWriteArrayList<>();
            p.commands.command(p, "kit").setExecutor((s, c, l, a) -> { seen.add(s.name()); return true; });
            var steve = server.join("Steve");
            steve.command("kit");
            server.tick();
            assertTrue(seen.isEmpty(), "권한이 없으면 실행되지 않는다");
            steve.player().addAttachment(p).setPermission("compat.kit", true);
            steve.command("kit");
            server.tick();
            assertEquals(List.of("Steve"), seen, "권한을 받으면 실행된다");
        }
    }

    @Test
    void testPermissionSendsDefaultAndCustomMessages() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            var cmd = new LegacyCommand(p, "secret").setPermission("a.b;c.d");
            List<String> got = new ArrayList<>();
            CommandSender nobody = fakeSender(got, false);
            assertFalse(cmd.testPermissionSilent(nobody));
            assertFalse(cmd.testPermission(nobody));
            assertEquals(1, got.size());
            assertTrue(got.get(0).startsWith("I'm sorry, but you do not have permission"));
            got.clear();
            cmd.setPermissionMessage("need <permission>\nplease");
            cmd.testPermission(nobody);
            assertEquals(List.of("need a.b;c.d", "please"), got);
            got.clear();
            cmd.setPermissionMessage("");
            assertFalse(cmd.testPermission(nobody));
            assertTrue(got.isEmpty(), "빈 문장이면 아무 말도 안 한다");
            assertTrue(cmd.testPermissionSilent(fakeSender(got, true)));
        }
    }

    @Test
    void tabCompletePrefersCompleterThenExecutorThenOnlinePlayers() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            server.join("Alice");
            server.join("alfred");
            server.join("Bob");
            LegacyCommand open = p.commands.command(p, "open");
            CommandSender console = server.api().console();
            assertEquals(List.of("alfred", "Alice"), open.tabComplete(console, "open", new String[]{"al"}), "기본: 접속자 이름(대소문자 무시, 정렬)");
            assertEquals(List.of(), open.tabComplete(console, "open", new String[0]));
            open.setExecutor(new TabExec());
            assertEquals(List.of("fromExecutor"), open.tabComplete(console, "open", new String[]{"x"}));
            open.setCompleter((s, c, a, args) -> List.of("fromCompleter"));
            assertEquals(List.of("fromCompleter"), open.tabComplete(console, "open", new String[]{"x"}));
            open.setCompleter((s, c, a, args) -> null);
            assertEquals(List.of("fromExecutor"), open.tabComplete(console, "open", new String[]{"x"}), "null 이면 다음으로");
        }
    }

    @Test
    void tabCompleteThroughServerHopsToGlobalThread() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            AtomicReference<Boolean> primary = new AtomicReference<>();
            p.commands.command(p, "kit").setCompleter((s, c, alias, args) -> {
                primary.set(PrimaryThread.isPrimary());
                return List.of("diamond", "dirt", "stone").stream().filter(x -> x.startsWith(args[args.length - 1])).toList();
            });
            var s = server.internals().commands().suggestions(CommandSource.console(server.internals()), "/k di").get();
            assertEquals(List.of("diamond", "dirt"), s.getList().stream().map(x -> x.getText()).toList());
            assertEquals(Boolean.TRUE, primary.get(), "시험 스레드에서 물어도 완성기는 주 스레드에서 돈다");
        }
    }

    @Test
    void dispatchCommandRunsLegacyCommandSynchronouslyOnPrimary() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            List<String> seen = new CopyOnWriteArrayList<>();
            p.commands.command(p, "kit").setExecutor((s, c, l, a) -> { seen.add(l + ":" + a.length); return a.length > 0; });
            // 주 스레드에서: 바로 실행하고 성공 여부를 돌려준다
            assertEquals(List.of(true, false), server.onGlobal(() -> List.of(
                    p.commands.dispatch(server.api().console(), "kit a"),
                    p.commands.dispatch(server.api().console(), "/k"))));
            assertEquals(List.of("kit:1", "k:0"), seen);
            // 다른 스레드에서: 전역으로 넘기고 접수 true
            assertTrue(p.commands.dispatch(server.api().console(), "kit zz"));
            server.tick();
            assertEquals(List.of("kit:1", "k:0", "kit:1"), seen);
        }
    }

    @Test
    void unregisterRemovesFromTreeAndLookup() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            LegacyCommand open = p.commands.command(p, "open");
            assertTrue(p.commands.unregister(open));
            assertNull(p.commands.command(p, "open"));
            assertFalse(server.execute("open").success());
            LegacyCommand again = new LegacyCommand(p, "again").setExecutor((s, c, l, a) -> true);
            assertTrue(p.commands.register(p, again).contains("again"));
            assertTrue(server.execute("again").success());
        }
    }

    private static final class TabExec implements org.domi.furnace.compat.command.LegacyExecutor, org.domi.furnace.compat.command.LegacyTabCompleter {
        @Override
        public boolean onCommand(CommandSender sender, LegacyCommand command, String label, String[] args) { return true; }

        @Override
        public List<String> onTabComplete(CommandSender sender, LegacyCommand command, String alias, String[] args) { return List.of("fromExecutor"); }
    }

    private static CommandSender fakeSender(List<String> sink, boolean allowed) {
        return new CommandSender() {
            @Override
            public String name() { return "fake"; }

            @Override
            public boolean hasPermission(String node) { return allowed; }

            @Override
            public boolean isOp() { return allowed; }

            @Override
            public boolean isPermissionSet(String node) { return allowed; }

            @Override
            public org.domi.furnace.api.permission.PermissionAttachment addAttachment(org.domi.furnace.api.plugin.FurnacePlugin plugin) {
                throw new UnsupportedOperationException();
            }

            @Override
            public void sendMessage(org.domi.furnace.api.text.Text message) { sink.add(message.plainText()); }
        };
    }

    @Test
    void executionArgsDropEmptyTokensLikePaper() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            List<String> seen = new CopyOnWriteArrayList<>();
            p.commands.command(p, "kit").setExecutor((s, c, l, a) -> { seen.add(l + ":" + String.join("|", a)); return true; });
            server.execute("kit  a   b ");
            server.onGlobal(() -> p.commands.dispatch(server.api().console(), "KIT  x  y "));
            assertEquals(List.of("kit:a|b", "kit:x|y"), seen, "빈 인자는 버리고, dispatch 의 label 은 소문자");
        }
    }

    @Test
    void defaultTabCompletionHidesPlayersTheSenderCannotSee() {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            var alice = server.join("Alice");
            server.join("Alvin");
            server.tick(10);
            var viewer = alice.player();
            var vanished = server.api().player("Alvin");
            server.api().entityViews().hide(p, viewer, vanished);
            LegacyCommand open = p.commands.command(p, "open");
            assertEquals(List.of("Alice"), open.tabComplete(viewer, "open", new String[]{"al"}), "숨긴 플레이어 이름은 새지 않는다");
            assertEquals(List.of("Alice", "Alvin"), open.tabComplete(server.api().console(), "open", new String[]{"al"}));
        }
    }

    @Test
    void slowGlobalThreadMakesTabCompletionTimeOutToEmptyList() throws Exception {
        try (var server = start()) {
            var p = server.plugin(CompatTestPlugin.class);
            var fast = new org.domi.furnace.compat.command.LegacyCommands(server.api(), 100);
            var cmd = new LegacyCommand(p, "slowtab").setCompleter((s, c, a, args) -> List.of("never"));
            fast.register(p, cmd);
            java.util.concurrent.CountDownLatch block = new java.util.concurrent.CountDownLatch(1);
            java.util.concurrent.CountDownLatch started = new java.util.concurrent.CountDownLatch(1);
            server.api().scheduler().global().execute(() -> {
                started.countDown();
                try {
                    block.await(5, java.util.concurrent.TimeUnit.SECONDS);
                } catch (InterruptedException ignored) {
                    Thread.currentThread().interrupt();
                }
            });
            started.await(5, java.util.concurrent.TimeUnit.SECONDS);
            try {
                var s = server.internals().commands().suggestions(CommandSource.console(server.internals()), "/slowtab ne").get();
                assertTrue(s.getList().isEmpty(), "전역이 막혀 한도(100ms)를 넘으면 빈 목록");
            } finally {
                block.countDown();
            }
        }
    }
}
