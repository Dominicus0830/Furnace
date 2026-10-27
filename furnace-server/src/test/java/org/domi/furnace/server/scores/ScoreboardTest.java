// 점수판·팀 — 연산 9종, 저장 왕복, 팀 멤버십, 패킷 바이트, 명령 오류 키, 선택자 team=/scores=, 접속 시 전송.
package org.domi.furnace.server.scores;

import static org.junit.jupiter.api.Assertions.*;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.api.text.TextDecoration;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.command.CommandSource;

import org.domi.furnace.server.command.arguments.ScoreboardArguments;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.play.ResetScore;
import org.domi.furnace.server.net.protocol.play.SetDisplayObjective;
import org.domi.furnace.server.net.protocol.play.SetObjective;
import org.domi.furnace.server.net.protocol.play.SetPlayerTeam;
import org.domi.furnace.server.net.protocol.play.SetScore;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ScoreboardTest {

    @TempDir Path tmp;

    private static String hex(Packet p) {
        ByteBuf b = Unpooled.buffer();
        p.write(b);
        byte[] a = new byte[b.readableBytes()];
        b.readBytes(a);
        return HexFormat.of().formatHex(a);
    }

    private static Objective dummy(Scoreboard b, String name) {
        return b.addObjective(name, ObjectiveCriteria.DUMMY, Text.text(name), ObjectiveCriteria.RenderType.INTEGER, false, null);
    }

    private static int apply(String op, int a, int bv) throws CommandSyntaxException {
        Scoreboard b = new Scoreboard();
        Objective o = dummy(b, "o");
        ScoreAccess t = b.getOrCreatePlayerScore(ScoreHolder.forNameOnly("t"), o);
        ScoreAccess s = b.getOrCreatePlayerScore(ScoreHolder.forNameOnly("s"), o);
        t.set(a);
        s.set(bv);
        ScoreboardArguments.operation().parse(new StringReader(op)).apply(t, s);
        return op.equals("><") ? t.get() * 1000 + s.get() : t.get();
    }

    @Test
    void nineOperations() throws Exception {
        assertEquals(3, apply("=", 7, 3));
        assertEquals(10, apply("+=", 7, 3));
        assertEquals(4, apply("-=", 7, 3));
        assertEquals(21, apply("*=", 7, 3));
        assertEquals(-3, apply("/=", -7, 3));   // floorDiv
        assertEquals(2, apply("%=", -7, 3));    // floorMod
        assertEquals(3, apply("<", 7, 3));
        assertEquals(7, apply(">", 7, 3));
        assertEquals(3007, apply("><", 7, 3));
        CommandSyntaxException e = assertThrows(CommandSyntaxException.class, () -> apply("/=", 1, 0));
        assertEquals("arguments.operation.div0", e.getRawMessage().translationKey());
        e = assertThrows(CommandSyntaxException.class, () -> ScoreboardArguments.operation().parse(new StringReader("**")));
        assertEquals("arguments.operation.invalid", e.getRawMessage().translationKey());
    }

    @Test
    void saveRoundTrip() {
        ServerScoreboard b = new ServerScoreboard(List::of, null);
        Objective o = dummy(b, "kills");
        Objective h = b.addObjective("hp", ObjectiveCriteria.HEALTH, Text.text("HP", NamedTextColor.RED), ObjectiveCriteria.RenderType.HEARTS, true,
                new NumberFormat.Styled(Style.style(NamedTextColor.GOLD, TextDecoration.BOLD)));
        b.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Alex"), o).set(5);
        ScoreAccess a = b.getOrCreatePlayerScore(ScoreHolder.forNameOnly("#fake"), o);
        a.set(-3);
        a.display(Text.text("Fake"));
        a.numberFormatOverride(new NumberFormat.Fixed(Text.text("??")));
        b.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Alex"), h, true).set(20);
        b.setDisplayObjective(DisplaySlot.SIDEBAR, o);
        b.setDisplayObjective(DisplaySlot.LIST, h);
        PlayerTeam t = b.addPlayerTeam("red");
        t.color(ChatFormatting.RED);
        t.playerPrefix(Text.text("[R] "));
        t.allowFriendlyFire(false);
        t.nameTagVisibility(PlayerTeam.Visibility.HIDE_FOR_OTHER_TEAMS);
        t.collisionRule(PlayerTeam.CollisionRule.PUSH_OWN_TEAM);
        b.addPlayerToTeam("Alex", t);
        b.addPlayerTeam("plain");

        NbtCompound saved = b.save();
        ServerScoreboard c = new ServerScoreboard(List::of, null);
        c.load(saved);
        Objective o2 = c.getObjective("kills"), h2 = c.getObjective("hp");
        assertSame(ObjectiveCriteria.HEALTH, h2.criteria());
        assertEquals(ObjectiveCriteria.RenderType.HEARTS, h2.renderType());
        assertTrue(h2.displayAutoUpdate());
        assertEquals(Text.text("HP", NamedTextColor.RED), h2.displayName());
        assertEquals(new NumberFormat.Styled(Style.style(NamedTextColor.GOLD, TextDecoration.BOLD)), h2.numberFormat());
        assertEquals(5, c.getPlayerScoreInfo(ScoreHolder.forNameOnly("Alex"), o2).value());
        Score fake = c.getPlayerScoreInfo(ScoreHolder.forNameOnly("#fake"), o2);
        assertEquals(-3, fake.value());
        assertEquals(Text.text("Fake"), fake.display());
        assertEquals(new NumberFormat.Fixed(Text.text("??")), fake.numberFormat());
        assertTrue(fake.isLocked());
        assertSame(o2, c.getDisplayObjective(DisplaySlot.SIDEBAR));
        assertSame(h2, c.getDisplayObjective(DisplaySlot.LIST));
        PlayerTeam t2 = c.getPlayerTeam("red");
        assertEquals(ChatFormatting.RED, t2.color());
        assertEquals(Text.text("[R] "), t2.playerPrefix());
        assertFalse(t2.isAllowFriendlyFire());
        assertEquals(PlayerTeam.Visibility.HIDE_FOR_OTHER_TEAMS, t2.nameTagVisibility());
        assertEquals(PlayerTeam.CollisionRule.PUSH_OWN_TEAM, t2.collisionRule());
        assertSame(t2, c.getPlayersTeam("Alex"));
        assertEquals(ChatFormatting.RESET, c.getPlayerTeam("plain").color());
        assertFalse(((NbtCompound) ((org.domi.furnace.server.net.io.nbt.NbtList) saved.tags().get("Teams")).items().get(1)).contains("TeamColor"));
        assertEquals(saved.tags().keySet(), c.save().tags().keySet());
    }

    @Test
    void fileRoundTrip() {
        ServerScoreboard b = ServerScoreboard.open(List::of, tmp);
        dummy(b, "x");
        assertTrue(b.isDirty());
        b.saveIfDirty();
        assertTrue(java.nio.file.Files.isRegularFile(tmp.resolve("data").resolve("scoreboard.dat")));
        ServerScoreboard c = ServerScoreboard.open(List::of, tmp);
        assertNotNull(c.getObjective("x"));
        assertFalse(c.isDirty());
    }

    @Test
    void teamMembership() {
        ServerScoreboard b = new ServerScoreboard(List::of, null);
        PlayerTeam a = b.addPlayerTeam("a"), c = b.addPlayerTeam("c");
        assertTrue(b.addPlayerToTeam("Steve", a));
        assertTrue(b.addPlayerToTeam("Steve", a));   // 바닐라: 같은 팀이어도 먼저 빼고 다시 넣는다
        assertTrue(b.addPlayerToTeam("Steve", c));   // 다른 팀에서 옮긴다
        assertFalse(a.hasPlayer("Steve"));
        assertSame(c, b.getPlayersTeam("Steve"));
        assertThrows(IllegalStateException.class, () -> b.removePlayerFromTeam("Steve", a));
        assertTrue(b.removePlayerFromTeam("Steve"));
        assertFalse(b.removePlayerFromTeam("Steve"));
        b.addPlayerToTeam("Alex", a);
        b.removePlayerTeam(a);
        assertNull(b.getPlayersTeam("Alex"));
        assertSame(c, b.addPlayerTeam("c"));   // 같은 이름은 기존 팀
        c.color(ChatFormatting.BLUE);
        c.playerPrefix(Text.text("<"));
        assertEquals(Text.empty().append(Text.text("<"), Text.text("Steve"), Text.empty()).color(NamedTextColor.BLUE), c.formatName(Text.text("Steve")));
    }

    @Test
    void readOnlyAndLockedScores() {
        Scoreboard b = new Scoreboard();
        Objective h = b.addObjective("h", ObjectiveCriteria.HEALTH, Text.text("h"), ObjectiveCriteria.RenderType.HEARTS, false, null);
        assertThrows(IllegalStateException.class, () -> b.getOrCreatePlayerScore(ScoreHolder.forNameOnly("x"), h).set(1));
        b.getOrCreatePlayerScore(ScoreHolder.forNameOnly("x"), h, true).set(1);
        Objective t = b.addObjective("t", ObjectiveCriteria.TRIGGER, Text.text("t"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        ScoreAccess a = b.getOrCreatePlayerScore(ScoreHolder.forNameOnly("x"), t);
        assertTrue(a.locked());
        a.unlock();
        assertFalse(b.getPlayerScoreInfo(ScoreHolder.forNameOnly("x"), t).isLocked());
        b.resetSinglePlayerScore(ScoreHolder.forNameOnly("x"), t);
        assertEquals(1, b.listPlayerScores(ScoreHolder.forNameOnly("x")).size());
        b.removeObjective(h);
        assertTrue(b.trackedNames().contains("x"));   // 빈 맵은 남는다(바닐라 removeObjective)
    }

    @Test
    void criteriaNames() {
        assertSame(ObjectiveCriteria.DUMMY, ObjectiveCriteria.byName("dummy").orElseThrow());
        assertSame(ObjectiveCriteria.TEAM_KILL[12], ObjectiveCriteria.byName("teamkill.red").orElseThrow());
        assertTrue(ObjectiveCriteria.byName("nope").isEmpty());
        assertTrue(ObjectiveCriteria.HEALTH.isReadOnly());
        assertEquals(ObjectiveCriteria.RenderType.HEARTS, ObjectiveCriteria.HEALTH.defaultRenderType());
    }

    @Test
    void packetBytes() {
        assertEquals("01036f626a", hex(new SetDisplayObjective(DisplaySlot.SIDEBAR, "obj")));
        assertEquals("014100", hex(new ResetScore("A", null)));
        assertEquals("014101016f", hex(new ResetScore("A", "o")));
        assertEquals("0141016f050000", hex(new SetScore("A", "o", 5, null, null)));
        assertEquals("0141016f05000102080001" + "78", hex(new SetScore("A", "o", 5, null, new NumberFormat.Fixed(Text.text("x")))));
        assertEquals("0141016f05000100", hex(new SetScore("A", "o", 5, null, NumberFormat.BLANK)));
        assertEquals("0141016f050001010a00", hex(new SetScore("A", "o", 5, null, new NumberFormat.Styled(Style.empty()))));
        assertEquals("0141016f05010800014100", hex(new SetScore("A", "o", 5, Text.text("A"), null)));
        assertEquals("016f01", hex(new SetObjective("o", SetObjective.METHOD_REMOVE, Text.text("o"), ObjectiveCriteria.RenderType.INTEGER, null)));
        assertEquals("016f000800016f0100", hex(new SetObjective("o", SetObjective.METHOD_ADD, Text.text("o"), ObjectiveCriteria.RenderType.HEARTS, null)));

        Scoreboard b = new Scoreboard();
        PlayerTeam t = b.addPlayerTeam("t");
        String always = "06616c77617973";
        assertEquals("017400" + "08000174" + "03" + always + always + "15" + "080000" + "080000" + "00", hex(SetPlayerTeam.addOrModify(t, true)));
        b.addPlayerToTeam("A", t);
        t.color(ChatFormatting.RED);
        assertEquals("017402" + "08000174" + "03" + always + always + "0c" + "080000" + "080000", hex(SetPlayerTeam.addOrModify(t, false)));
        assertEquals("017401", hex(SetPlayerTeam.remove(t)));
        assertEquals("0174030101" + "41", hex(SetPlayerTeam.player(t, "A", true)));
        assertEquals("0174040101" + "41", hex(SetPlayerTeam.player(t, "A", false)));
    }

    // ------------------------------------------------------------------ 명령

    private List<String> run(ServerCore core, List<String> out, String cmd) {
        out.clear();
        core.commands().performPrefixedCommand(CommandSource.console(core), cmd);
        return List.copyOf(out);
    }

    @Test
    void commandsAndErrorKeys() {
        FurnaceSchedulerImpl sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        try {
            ServerCore core = org.domi.furnace.server.TestServers.core(tmp, sched);
            List<String> out = new ArrayList<>();
            core.addConsoleListener(t -> out.add(t.getString()));
            assertEquals(List.of("Created new objective [kills]"), run(core, out, "scoreboard objectives add kills dummy"));
            assertEquals(List.of("An objective already exists by that name"), run(core, out, "scoreboard objectives add kills dummy"));
            assertEquals(List.of("Unknown scoreboard objective 'nope'"), run(core, out, "scoreboard players set A nope 1"));
            assertEquals(List.of("Set [kills] for A to 4"), run(core, out, "scoreboard players set A kills 4"));
            assertEquals(List.of("A has 4 [kills]"), run(core, out, "scoreboard players get A kills"));
            assertEquals(List.of("Can't get value of kills for B; none is set"), run(core, out, "scoreboard players get B kills"));
            run(core, out, "scoreboard players set B kills 3");
            assertEquals(List.of("Set [kills] for A to 12"), run(core, out, "scoreboard players operation A kills *= B kills"));
            assertEquals(List.of("Cannot divide by zero"), run(core, out, "scoreboard players operation A kills /= #zero kills"));
            run(core, out, "scoreboard objectives add hp health");
            assertEquals(List.of("Scoreboard objective 'hp' is read-only"), run(core, out, "scoreboard players set A hp 1"));
            assertEquals(List.of("Enable only works on trigger-objectives"), run(core, out, "scoreboard players enable A kills"));
            run(core, out, "scoreboard objectives add tr trigger");
            assertEquals(List.of("Enabled trigger [tr] for A"), run(core, out, "scoreboard players enable A tr"));
            assertEquals(List.of("Nothing changed. That trigger is already enabled"), run(core, out, "scoreboard players enable A tr"));
            assertEquals(List.of("Set display slot sidebar to show objective kills"), run(core, out, "scoreboard objectives setdisplay sidebar kills"));
            assertEquals(List.of("Nothing changed. That display slot is already showing that objective"),
                    run(core, out, "scoreboard objectives setdisplay sidebar kills"));

            assertEquals(List.of("Created team [red]"), run(core, out, "team add red"));
            assertEquals(List.of("A team already exists by that name"), run(core, out, "team add red"));
            assertEquals(List.of("Unknown team 'blue'"), run(core, out, "team join blue A"));
            assertEquals(List.of("Nothing changed. That team is already empty"), run(core, out, "team empty red"));
            assertEquals(List.of("Added A to team [red]"), run(core, out, "team join red A"));
            assertEquals(List.of("Team [red] has 1 member(s): A"), run(core, out, "team list red"));
            assertEquals(List.of("Updated the color for team [red] to red"), run(core, out, "team modify red color red"));
            assertEquals(ChatFormatting.RED, core.scoreboard().getPlayersTeam("A").color());
        } finally {
            sched.close();
        }
    }
}
