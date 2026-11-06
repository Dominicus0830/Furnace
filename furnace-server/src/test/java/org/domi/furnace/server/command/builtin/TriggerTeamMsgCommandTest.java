// /trigger(잠금·열기·add/set·오류 키·제안)와 /teammsg·/tm(팀원에게만, 팀 없음 오류)·/place 인자(회전·대칭·없는 id·미지원 월드).
package org.domi.furnace.server.command.builtin;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.channel.embedded.EmbeddedChannel;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.TestServers;
import org.domi.furnace.server.command.CommandSource;
import org.domi.furnace.server.command.arguments.TemplateArguments;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.command.brigadier.Suggestion;
import org.domi.furnace.server.command.brigadier.SuggestionsBuilder;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.play.DisguisedChatPacket;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.domi.furnace.server.scores.ScoreHolder;
import org.domi.furnace.server.world.gen.structure.template.BlockStateTable;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class TriggerTeamMsgCommandTest {

    @TempDir Path tmp;
    FurnaceSchedulerImpl sched;
    ServerCore core;
    final List<String> console = new ArrayList<>();

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = TestServers.core(tmp, sched);
        core.addConsoleListener(t -> console.add(t.getString()));
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private int consoleRun(String cmd) throws CommandSyntaxException {
        return core.commands().getDispatcher().execute(cmd, CommandSource.console(core));
    }

    private List<String> consoleAsync(String cmd) {
        console.clear();
        core.commands().performPrefixedCommandAsync(CommandSource.console(core), cmd).join();
        return List.copyOf(console);
    }

    final java.util.Map<Player, EmbeddedChannel> channels = new java.util.HashMap<>();

    private Player player(String name) {
        EmbeddedChannel ch = new ConcurrentEmbeddedChannel();
        Player p = new Player(core.world().entityIds().next(), UUID.randomUUID(), name, ch);
        channels.put(p, ch);
        p.scoreboard(core.scoreboard());
        return p;
    }

    private int score(String holder, String objective) {
        var b = core.scoreboard();
        return b.getPlayerScoreInfo(ScoreHolder.forNameOnly(holder), b.getObjective(objective)).value();
    }

    private static String key(CommandSyntaxException e) { return e.getRawMessage().translationKey(); }

    @Test
    void triggerNeedsEnableAndRelocks() throws Exception {
        consoleRun("scoreboard objectives add t trigger");
        consoleRun("scoreboard objectives add d dummy");
        Player p = player("Steve");
        CommandSource src = CommandSource.player(core, p);   // 권한 0 이어도 쓸 수 있다
        var d = core.commands().getDispatcher();
        // 점수가 없으면(아직 enable 안 함) unprimed
        assertEquals("commands.trigger.failed.unprimed", key(assertThrows(CommandSyntaxException.class, () -> d.execute("trigger t", src))));
        consoleRun("scoreboard players enable Steve t");
        assertEquals(List.of("t"), suggestions(src));
        assertEquals(1, d.execute("trigger t", src));
        assertEquals(1, score("Steve", "t"));
        // 쓰고 나면 다시 잠긴다
        assertEquals("commands.trigger.failed.unprimed", key(assertThrows(CommandSyntaxException.class, () -> d.execute("trigger t", src))));
        assertEquals(List.of(), suggestions(src));
        consoleRun("scoreboard players enable Steve t");
        assertEquals(6, d.execute("trigger t add 5", src), "add 는 새 값");
        consoleRun("scoreboard players enable Steve t");
        assertEquals(-3, d.execute("trigger t set -3", src), "set 은 준 값");
        assertEquals(-3, score("Steve", "t"));
        // trigger 기준이 아닌 목표·없는 목표·플레이어가 아닌 실행자
        consoleRun("scoreboard players enable Steve t");
        assertEquals("commands.trigger.failed.invalid", key(assertThrows(CommandSyntaxException.class, () -> d.execute("trigger d", src))));
        assertEquals("arguments.objective.notFound", key(assertThrows(CommandSyntaxException.class, () -> d.execute("trigger nope", src))));
        assertEquals("permissions.requires.player", key(assertThrows(CommandSyntaxException.class, () -> consoleRun("trigger t"))));
    }

    private List<String> suggestions(CommandSource src) {
        List<String> out = new ArrayList<>();
        for (Suggestion s : TriggerCommand.suggestObjectives(src, new SuggestionsBuilder("", 0)).join().getList()) out.add(s.getText());
        return out;
    }

    @Test
    void teamMsgGoesToTeamMembersOnly() throws Exception {
        Player a = player("Alice"), b = player("Bob"), c = player("Carol");
        for (Player p : List.of(a, b, c)) core.world().playerOnline(p);
        var d = core.commands().getDispatcher();
        CommandSource src = CommandSource.player(core, a);
        assertEquals("commands.teammsg.failed.noteam", key(assertThrows(CommandSyntaxException.class, () -> d.execute("teammsg hi", src))));
        assertEquals("permissions.requires.entity", key(assertThrows(CommandSyntaxException.class, () -> consoleRun("tm hi"))));
        consoleRun("team add red");
        consoleRun("team join red Alice");
        consoleRun("team join red Bob");
        drain(a); drain(b); drain(c);
        assertEquals(2, d.execute("teammsg hello team", src));
        assertEquals(1, chats(a), "보낸 사람도 (outgoing 형식으로) 받는다");
        assertEquals(1, chats(b));
        assertEquals(0, chats(c), "다른 팀이 아니면 받지 않는다");
        assertEquals(2, d.execute("tm again", src), "/tm 은 /teammsg 로 리다이렉트");
    }

    private int chats(Player p) {
        int n = 0;
        for (int id : drain(p)) if (id == DisguisedChatPacket.ID) n++;
        return n;
    }

    private List<Integer> drain(Player p) {
        EmbeddedChannel ch = channels.get(p);
        ch.runPendingTasks();
        List<Integer> out = new ArrayList<>();
        Object o;
        while ((o = ch.readOutbound()) != null) {
            if (o instanceof ByteBuf bb) {
                out.add(Types.readVarInt(bb));
                bb.release();
            }
        }
        return out;
    }

    @Test
    void placeArgumentsAndErrors() throws Exception {
        assertEquals(BlockStateTable.ROT_CW90, TemplateArguments.rotation().parse(new StringReader("clockwise_90")));
        assertEquals(BlockStateTable.ROT_CW180, TemplateArguments.rotation().parse(new StringReader("180")));
        assertEquals(BlockStateTable.FRONT_BACK, TemplateArguments.mirror().parse(new StringReader("front_back")));
        assertEquals("argument.enum.invalid", key(assertThrows(CommandSyntaxException.class, () -> TemplateArguments.mirror().parse(new StringReader("up")))));
        assertEquals(List.of("none", "clockwise_90"), List.copyOf(TemplateArguments.rotation().getExamples()));
        // 없는 id
        assertEquals(List.of("There is no feature with type \"minecraft:nope\""), consoleAsync("place feature nope"));
        assertEquals(List.of("There is no structure with type \"minecraft:nope\""), consoleAsync("place structure nope"));
        assertEquals(List.of("There is no template pool with type \"minecraft:nope\""), consoleAsync("place jigsaw nope minecraft:bottom 3"));
        // 평지 세계에는 월드젠 엔진이 없다 — 성공으로 위장하지 않는다
        assertEquals(List.of("Placing features in this world type is not supported yet"), consoleAsync("place feature minecraft:oak"));
        // 내장 리소스 목록(제안)
        assertTrue(PlaceCommand.resourceIds("worldgen/1.21.1/structure", ".nbt").contains("minecraft:igloo/top"));
        assertTrue(PlaceCommand.resourceIds("worldgen/1.21.1/worldgen/configured_feature", ".json").contains("minecraft:oak"));
    }
}
