// 바닐라 명령의 "minecraft:이름" 별칭(Paper 1.21.1 Commands 생성자): 모든 바닐라 루트 명령에 있고, 실행·권한·자동완성이 원래 명령과 같다.
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.TreeSet;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.VanillaCommandTreeTest.Node;
import org.domi.furnace.server.command.brigadier.CommandNode;
import org.domi.furnace.server.config.OperatorOptions;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class NamespacedCommandsTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;
    private final List<String> out = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = VanillaCommandTreeTest.core(tmp, sched);
        core.addConsoleListener(t -> out.add(t.getString()));
    }

    @AfterEach
    void tearDown() {
        OperatorOptions.install(null);
        sched.close();
    }

    private List<String> run(String command) {
        out.clear();
        core.commands().performPrefixedCommandAsync(CommandSource.console(core), command).join();
        return List.copyOf(out);
    }

    private static void sendNamespaced(boolean on) {
        var d = OperatorOptions.DEFAULT;
        OperatorOptions.install(new OperatorOptions(d.strictChatOrder(), d.unsignedChatAsSystem(), d.serverBrand(), d.announceSleepStatus(),
                d.confirmInterrupt(), d.interruptConfirmSeconds(), d.motdFormat(), d.gravityBlockPortalDupe(), on));
    }

    /** 접속자가 보낸 자동완성 요청처럼(send-namespaced 거르기 대상). */
    private List<String> suggest(String text) throws Exception {
        return core.commands().suggestions(CommandSource.console(core), text, true).get().getList().stream().map(s -> s.getText()).toList();
    }

    private static CommandNode<CommandSource> flatten(CommandNode<CommandSource> n) {
        while (n.getRedirect() != null) n = n.getRedirect();
        return n;
    }

    @Test
    void everyVanillaRootCommandHasAnAliasThatMirrorsItsTarget() {
        var root = core.commands().getDispatcher().getRoot();
        int plain = 0;
        for (CommandNode<CommandSource> node : new ArrayList<>(root.getChildren())) {
            if (node.getName().contains(":")) continue;
            plain++;
            var alias = root.getChild("minecraft:" + node.getName());
            assertNotNull(alias, "minecraft:" + node.getName());
            var target = flatten(node);
            // 별칭의 별칭(tp → teleport)은 끝까지 따라간 노드를 가리킨다(Paper 와 같다 — 중첩 리다이렉트는 풀리지 않는다)
            assertSame(target, alias.getRedirect(), alias.getName());
            assertSame(target.getCommand(), alias.getCommand(), alias.getName());
            assertSame(target.getRequirement(), alias.getRequirement(), alias.getName());
        }
        assertTrue(plain > 60, "바닐라 루트 명령 수 " + plain);
        assertEquals(plain * 2, root.getChildren().size(), "별칭은 이름당 하나, 별칭의 별칭은 없다");
        assertSame(root.getChild("teleport"), root.getChild("minecraft:tp").getRedirect());
        assertSame(root.getChild("msg"), root.getChild("minecraft:tell").getRedirect(), "tell 도 msg 노드로");
    }

    @Test
    void prefixedCommandsRunLikeTheOriginals() {
        assertEquals(run("list"), run("minecraft:list"));
        assertEquals(List.of("Set the time to 1000"), run("minecraft:time set day"));
        assertEquals(List.of("Set the time to 6000"), run("/minecraft:time set noon"));
        assertEquals(run("say hello"), run("minecraft:say hello"));
        assertTrue(run("minecraft:say hello").get(0).endsWith("[Server] hello"));
        assertEquals(run("gamerule doDaylightCycle"), run("minecraft:gamerule doDaylightCycle"));
        assertEquals(run("difficulty"), run("minecraft:difficulty"));
        // execute 안에서도 루트 명령이므로 접두 이름이 통한다
        assertTrue(run("execute run minecraft:say nested").get(0).endsWith("[Server] nested"));
        // 틀린 인자는 원래 명령과 같은 오류
        assertEquals(run("time set").get(0), run("minecraft:time set").get(0));   // 둘째 줄은 친 명령이 보이는 위치 표시라 다르다
        assertFalse(run("minecraft:time bogus").isEmpty());
    }

    @Test
    void treeShowsAliasesWithTheTargetsPermissionAndRedirect() {
        int[] root = new int[1];
        List<Node> guest = VanillaCommandTreeTest.decode(core.commands().treeFor(CommandSource.forProfile(core, "Guest", 0)), root);
        TreeSet<String> names = new TreeSet<>();
        for (int c : guest.get(root[0]).children()) names.add(guest.get(c).name());
        assertTrue(names.containsAll(List.of("list", "minecraft:list", "minecraft:help", "minecraft:msg", "minecraft:tell", "minecraft:trigger")), names.toString());
        assertFalse(names.contains("minecraft:stop"));
        assertFalse(names.contains("minecraft:gamemode"));

        List<Node> op = VanillaCommandTreeTest.decode(core.commands().treeFor(CommandSource.forProfile(core, "Op", 4)), root);
        Node tp = null, list = null;
        for (int c : op.get(root[0]).children()) {
            Node n = op.get(c);
            if (n.name().equals("minecraft:tp")) tp = n;
            if (n.name().equals("minecraft:list")) list = n;
        }
        assertNotNull(tp);
        assertNotNull(list);
        assertTrue(list.executable(), "list 는 인자 없이 실행되는 명령이라 별칭도 실행 가능");
        assertFalse(tp.executable());
        assertEquals("teleport", op.get(tp.redirect()).name());
        // 레벨 3 이상만 보이는 명령은 별칭도 같다
        assertFalse(rootNames(2).contains("minecraft:kick"));
        assertTrue(rootNames(3).contains("minecraft:kick"));
    }

    private TreeSet<String> rootNames(int level) {
        int[] root = new int[1];
        List<Node> nodes = VanillaCommandTreeTest.decode(core.commands().treeFor(CommandSource.forProfile(core, "L" + level, level)), root);
        TreeSet<String> names = new TreeSet<>();
        for (int c : nodes.get(root[0]).children()) names.add(nodes.get(c).name());
        return names;
    }

    @Test
    void tabCompletionWorksOnTheNameAndOnArguments() throws Exception {
        List<String> names = suggest("/minecraft:ti");
        assertTrue(names.containsAll(List.of("minecraft:time", "minecraft:tick")), names.toString());
        assertEquals(suggest("/gamemode "), suggest("/minecraft:gamemode "));
        assertEquals(suggest("/time set "), suggest("/minecraft:time set "));
        // 접두 없이 /ti 를 완성하면 이름공간 명령은 섞이지 않는다
        assertFalse(suggest("/ti").stream().anyMatch(s -> s.startsWith("minecraft:")));
        // "/mi" 처럼 접두 낱말로 시작하면 모든 별칭이 후보
        assertTrue(suggest("/mi").contains("minecraft:give"));
    }

    @Test
    void helpListingKeepsOneLinePerVanillaCommand() {
        List<String> help = run("help");
        assertFalse(help.isEmpty());
        assertTrue(help.stream().noneMatch(l -> l.startsWith("/minecraft:")), help.toString());
        assertTrue(help.stream().anyMatch(l -> l.startsWith("/give")));
        // 리다이렉트 노드의 /help 는 바닐라 /help tp 처럼 비어 있다(별칭이라고 달라지지 않는다)
        assertEquals(run("help tp"), run("help minecraft:tp"));
    }

    @Test
    void sendNamespacedFalseHidesAliasesFromTheClientButNotFromTheServer() throws Exception {
        sendNamespaced(false);
        TreeSet<String> names = rootNames(4);
        assertTrue(names.contains("give") && names.contains("list"));
        assertTrue(names.stream().noneMatch(n -> n.contains(":")), names.toString());
        // 이름 자리 자동완성에서도 빠지고, 이름을 다 친 뒤의 인자 완성은 그대로
        assertTrue(suggest("/mi").stream().noneMatch(s -> s.contains(":")));
        assertEquals(suggest("/gamemode "), suggest("/minecraft:gamemode "));
        // 실행은 된다
        assertEquals(List.of("Set the time to 1000"), run("minecraft:time set day"));
        // 콘솔 자동완성은 거르지 않는다(Paper 는 플레이어 패킷 응답에서만 거른다)
        assertTrue(core.commands().suggestions(CommandSource.console(core), "minecraft:gi").get().getList().stream()
                .anyMatch(x -> x.getText().equals("minecraft:give")));
        // 켜면 다시 보인다
        sendNamespaced(true);
        assertTrue(rootNames(4).contains("minecraft:give"));
    }
}
