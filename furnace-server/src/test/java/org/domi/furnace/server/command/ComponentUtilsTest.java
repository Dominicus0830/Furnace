// ComponentUtils.resolve(바닐라 updateForEntity) 대조(xc·yh ScoreContents·yi SelectorContents·xc.formatList):
// 점수는 이름·목표로 점수판을 찾아 숫자 형식(목표 형식 또는 NO_STYLE), 없으면 빈 텍스트. 선택자는 대상 하나면 그 표시 이름(원래 스타일이 정한 값만 덮음),
// 여럿이면 빈 텍스트에 구분자로 잇는다. 표지판 소스(이름 "Sign", 권한 2, 칸 가운데). 저장 문자열은 키를 정렬한 JSON(FlatText.stable).
package org.domi.furnace.server.command;

import static org.junit.jupiter.api.Assertions.*;

import java.nio.file.Path;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.api.text.Content;
import org.domi.furnace.api.text.NamedTextColor;
import org.domi.furnace.api.text.Style;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.ServerCore;
import org.domi.furnace.server.entity.Player;
import org.domi.furnace.server.entity.Rider;
import org.domi.furnace.server.item.FlatText;
import org.domi.furnace.server.ConcurrentEmbeddedChannel;
import org.domi.furnace.server.scores.NumberFormat;
import org.domi.furnace.server.scores.ObjectiveCriteria;
import org.domi.furnace.server.scores.ScoreHolder;
import org.domi.furnace.server.sched.FurnaceSchedulerImpl;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ComponentUtilsTest {

    @TempDir Path tmp;
    private FurnaceSchedulerImpl sched;
    private ServerCore core;

    @BeforeEach
    void setUp() {
        sched = new FurnaceSchedulerImpl(50_000_000L, 1);
        core = VanillaCommandTreeTest.core(tmp, sched);
    }

    @AfterEach
    void tearDown() { sched.close(); }

    private CommandSource sign() { return CommandSource.sign(core, core.world(), 4, 70, -2); }

    private Text resolve(Text t) { return ComponentUtils.resolve(sign(), t, null).join(); }

    private static Text score(String name, String objective) { return Text.of(new Content.Score(name, objective), Style.empty(), List.of()); }

    @Test
    void signSourceIsNamedSignAtBlockCentre() {
        CommandSource s = sign();
        assertEquals("Sign", s.getTextName());
        assertEquals("Sign", s.getDisplayName().getString());
        assertTrue(s.hasPermission(2));
        assertFalse(s.hasPermission(3));
        assertEquals(4.5, s.x());
        assertEquals(70.5, s.y());
        assertEquals(-1.5, s.z());
        assertNull(s.entity());
        assertSame(core.world(), s.level());
    }

    @Test
    void scoreResolvesToStylelessNumberOrEmpty() throws Exception {
        var board = core.scoreboard();
        var pts = board.addObjective("pts", ObjectiveCriteria.DUMMY, Text.text("pts"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("Alice"), pts).set(7);

        assertEquals("\"7\"", FlatText.stable(resolve(score("Alice", "pts"))), "StyledFormat.NO_STYLE: 스타일 없는 숫자 글자");
        Text red = score("Alice", "pts").color(NamedTextColor.RED);
        assertEquals("{\"color\":\"red\",\"text\":\"7\"}", FlatText.stable(resolve(red)), "원래 텍스트의 스타일이 입혀진다");
        assertEquals("\"\"", FlatText.stable(resolve(score("Bob", "pts"))), "점수가 없으면 빈 텍스트");
        assertEquals("\"\"", FlatText.stable(resolve(score("Alice", "nope"))), "목표가 없으면 빈 텍스트");
        assertEquals("\"\"", FlatText.stable(resolve(score("*", "pts"))), "entity 없이 * 는 이름 \"*\" 그대로");
        assertEquals("\"\"", FlatText.stable(resolve(score("@p", "pts"))), "선택자가 아무도 못 찾으면 이름 그대로(\"@p\" 점수 없음)");

        pts.numberFormat(new NumberFormat.Fixed(Text.text("???")));
        assertEquals("\"???\"", FlatText.stable(resolve(score("Alice", "pts"))), "목표의 숫자 형식");
        // 자식은 풀린 결과 뒤에 이어 붙는다
        Text withChild = score("Alice", "pts").append(Text.text("!"));
        assertEquals("{\"extra\":[\"!\"],\"text\":\"???\"}", FlatText.stable(resolve(withChild)));
    }

    @Test
    void selectorWithoutTargetsIsEmptyAndBadPatternIsEmpty() throws Exception {
        Text sel = Text.of(new Content.Selector("@a", null), Style.empty(), List.of());
        assertEquals("\"\"", FlatText.stable(resolve(sel)));
        Text bad = Text.of(new Content.Selector("@q[", null), Style.empty(), List.of());
        assertEquals("\"\"", FlatText.stable(resolve(bad)));
    }

    @Test
    void translatableArgumentsAreResolvedInPlace() throws Exception {
        var board = core.scoreboard();
        var pts = board.addObjective("p", ObjectiveCriteria.DUMMY, Text.text("p"), ObjectiveCriteria.RenderType.INTEGER, false, null);
        board.getOrCreatePlayerScore(ScoreHolder.forNameOnly("A"), pts).set(3);
        Text tr = Text.of(new Content.Translatable("k", null, List.of(score("A", "p"), "s")), Style.empty(), List.of());
        assertEquals("{\"translate\":\"k\",\"with\":[\"3\",\"s\"]}", FlatText.stable(resolve(tr)));
    }

    private static Player player(int id, String name) {
        return new Player(id, new UUID(id, id), name, new ConcurrentEmbeddedChannel());
    }

    @Test
    void formatListSingleIsTheDisplayNameAndManyAreJoined() {
        Player a = player(1, "Ann"), b = player(2, "Ben");
        Text one = ComponentUtils.formatList(List.<Rider>of(a), ComponentUtils.defaultSeparator());
        assertEquals(a.displayName(), one, "하나면 빈 텍스트로 감싸지 않고 표시 이름 그대로");
        assertEquals("{\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/tell Ann \"},"
                + "\"hoverEvent\":{\"action\":\"show_entity\",\"contents\":{\"id\":[0,1,0,1],\"name\":\"Ann\",\"type\":\"minecraft:player\"}},"
                + "\"insertion\":\"Ann\",\"text\":\"Ann\"}", FlatText.stable(one), "바닐라 Player.getDisplayName: 클릭·엔티티 호버·삽입");
        Text many = ComponentUtils.formatList(List.<Rider>of(a, b), ComponentUtils.defaultSeparator());
        assertEquals("", many.literal());
        assertEquals(3, many.children().size());
        assertEquals("Ann, Ben", many.getString());
        assertEquals(NamedTextColor.GRAY, many.children().get(1).style().color());
        assertEquals("\"\"", FlatText.stable(ComponentUtils.formatList(List.<Rider>of(), ComponentUtils.defaultSeparator())));
    }

    @Test
    void singleSelectorResultMergesTheSelectorStyle() throws Exception {
        // 바닐라 withStyle(Style) = setStyle(원래.applyTo(풀린 것)): 원래 스타일에서 정한 값이 이기고 표시 이름의 클릭·호버·삽입은 남는다
        Player a = player(3, "Cid");
        CommandSource src = CommandSource.player(core, a).withPermission(2);
        Text sel = Text.of(new Content.Selector("@s", null), Style.empty().color(NamedTextColor.GOLD).insertion("x"), List.of());
        Text resolved = ComponentUtils.resolve(src, sel, null).join();
        assertEquals("{\"clickEvent\":{\"action\":\"suggest_command\",\"value\":\"/tell Cid \"},\"color\":\"gold\","
                + "\"hoverEvent\":{\"action\":\"show_entity\",\"contents\":{\"id\":[0,3,0,3],\"name\":\"Cid\",\"type\":\"minecraft:player\"}},"
                + "\"insertion\":\"x\",\"text\":\"Cid\"}", FlatText.stable(resolved));
    }
}
