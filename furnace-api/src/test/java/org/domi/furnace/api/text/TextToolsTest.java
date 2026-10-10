// 트리 도구 테스트 — replaceText(조건·횟수·정규식·호버), compact(보이는 모양 불변을 난수로 검증), JoinConfiguration, flatten, textOfChildren.
package org.domi.furnace.api.text;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;

class TextToolsTest {

    // ------------------------------------------------------------------ replaceText

    @Test
    void replaceLiteral() {
        Text t = Text.text("a X b X c", NamedTextColor.RED);
        Text r = t.replaceText(TextReplacement.literal("X", Text.text("Y", NamedTextColor.GREEN)));
        assertEquals("a Y b Y c", r.plainText());
        assertEquals(List.of("a [red]", "Y[green]", " b [red]", "Y[green]", " c[red]"), Visual.runs(r));
        assertNotSame(t, r);
        assertSame(t, t.replaceText(TextReplacement.literal("nothing", Text.text("z"))), "바뀐 게 없으면 같은 인스턴스");
    }

    @Test
    void replaceRegexWithFunction() {
        Text t = Text.text("id=12 and id=345");
        Text r = t.replaceText(b -> b.match("id=(\\d+)").replacement(m -> Text.text("[" + m.group(1) + "]", NamedTextColor.GOLD)));
        assertEquals("[12] and [345]", r.plainText());
        assertEquals(List.of("[12][gold]", " and [-]", "[345][gold]"), Visual.runs(r));
        Text s = Text.text("a-b").replaceText(b -> b.match(Pattern.compile("-")).replacement("+"));
        assertEquals("a+b", s.plainText());
    }

    @Test
    void replaceOnceAndTimes() {
        Text t = Text.text("x x x x");
        assertEquals("y x x x", t.replaceText(b -> b.matchLiteral("x").replacement("y").once()).plainText());
        assertEquals("y y x x", t.replaceText(b -> b.matchLiteral("x").replacement("y").times(2)).plainText());
        assertSame(t, t.replaceText(b -> b.matchLiteral("x").replacement("y").times(0)));
    }

    @Test
    void replaceCountIsSharedAcrossNodes() {
        Text t = Text.text("x").append(Text.text("x", NamedTextColor.RED)).append(Text.text("x"));
        Text r = t.replaceText(b -> b.matchLiteral("x").replacement("y").times(2));
        assertEquals("yyx", r.plainText());
    }

    @Test
    void replaceCondition() {
        Text t = Text.text("a a a a");
        Text skipFirst = t.replaceText(b -> b.matchLiteral("a").replacement("b").condition((m, count, done) -> count == 1 ? TextReplacement.Result.SKIP : TextReplacement.Result.REPLACE));
        assertEquals("a b b b", skipFirst.plainText());
        Text stop = t.replaceText(b -> b.matchLiteral("a").replacement("b").condition((m, count, done) -> count > 2 ? TextReplacement.Result.STOP : TextReplacement.Result.REPLACE));
        assertEquals("b b a a", stop.plainText());
    }

    @Test
    void replaceNullFunctionResultKeepsMatch() {
        Text r = Text.text("a b a").replaceText(b -> b.matchLiteral("a").replacement(m -> null));
        assertEquals("a b a", r.plainText());
    }

    @Test
    void replaceReachesHoverTranslationArgsAndChildren() {
        Text t = Text.text("top X").hoverText(Text.text("tip X")).append(Text.translatable("k", Text.text("arg X"))).append(Text.text("kid X", NamedTextColor.BLUE));
        Text r = t.replaceText(TextReplacement.literal("X", Text.text("Z")));
        assertEquals("tip Z", ((HoverEvent.ShowText) r.hoverEvent()).text().plainText());
        assertEquals("top Z" + "k" + "kid Z", r.plainText());
        Content.Translatable tr = (Content.Translatable) r.children().get(1).content();
        assertEquals("arg Z", ((Text) tr.args().get(0)).plainText());
    }

    @Test
    void replaceKeepsTailAndChildrenOrder() {
        Text t = Text.text("a X b").append(Text.text("child"));
        Text r = t.replaceText(TextReplacement.literal("X", Text.text("Y")));
        assertEquals("a Y bchild", r.plainText());
    }

    @Test
    void replaceRejectsBadConfig() {
        assertThrows(IllegalArgumentException.class, () -> TextReplacement.builder().matchLiteral(""));
        assertThrows(IllegalStateException.class, () -> TextReplacement.builder().build());
        assertThrows(IllegalStateException.class, () -> TextReplacement.builder().matchLiteral("a").build());
    }

    // ------------------------------------------------------------------ compact

    /** 글자별 실제 스타일이 같고 평문이 같은지. */
    static void assertSameLook(Text before, Text after) {
        assertEquals(before.plainText(), after.plainText());
        assertEquals(perCharacter(before), perCharacter(after));
    }

    static List<String> perCharacter(Text t) {
        List<String> out = new ArrayList<>();
        var it = t.flatten();
        while (it.hasNext()) {
            Texts.Segment s = it.next();
            String text = Texts.plainContent(s.text(), Translators.SERVER_LOCALE);
            if (s.text().content() instanceof Content.Literal) {
                String st = Visual.style(s.style());
                text.codePoints().forEach(cp -> out.add(new String(Character.toChars(cp)) + "|" + st));
            } else if (!text.isEmpty() || !(s.text().content() instanceof Content.Literal)) {
                out.add("{" + s.text().content() + "}|" + Visual.style(s.style()));
            }
        }
        return out;
    }

    @Test
    void compactMergesRedundantStructure() {
        Text t = Text.empty()
                .append(Text.empty().append(Text.text("a")).append(Text.text("b")))
                .append(Text.text("c", NamedTextColor.RED).append(Text.text("d", NamedTextColor.RED)))
                .append(Text.empty());
        Text c = t.compact();
        assertSameLook(t, c);
        assertEquals(List.of("ab[-]", "cd[red]"), Visual.runs(c));
        assertTrue(c.children().size() <= 2, c.toJson());
    }

    @Test
    void compactDropsRedundantInheritedStyle() {
        Text t = Text.text("a", NamedTextColor.RED).append(Text.text("b", NamedTextColor.RED).decorate(TextDecoration.BOLD));
        Text c = t.compact();
        assertSameLook(t, c);
        assertEquals(NamedTextColor.RED, c.color());
        assertNull(c.children().get(0).color(), "부모와 같은 색은 중복이라 뺀다");
        assertEquals(TriState.TRUE, c.children().get(0).decoration(TextDecoration.BOLD));
    }

    @Test
    void compactKeepsExplicitFalseWhereParentIsUnset() {
        // FALSE 는 "정하지 않음" 과 다르다(예: 기본 기울임인 로어 줄)
        Text t = Text.text("a").append(Text.text("b").decoration(TextDecoration.ITALIC, false));
        Text c = t.compact();
        assertSameLook(t, c);
        assertEquals(TriState.FALSE, c.children().get(c.children().size() - 1).decoration(TextDecoration.ITALIC));
    }

    @Test
    void compactFoldsSingleChildContainer() {
        Text t = Text.empty().color(NamedTextColor.RED).append(Text.text("x").decorate(TextDecoration.BOLD));
        Text c = t.compact();
        assertSameLook(t, c);
        assertEquals("x", c.literal());
        assertEquals(NamedTextColor.RED, c.color());
    }

    @Test
    void compactDoesNotTouchNonLiteralContent() {
        Text t = Text.empty().append(Text.translatable("k", "a")).append(Text.keybind("key.jump"));
        assertSameLook(t, t.compact());
        assertEquals(t.plainText(), t.compact().plainText());
    }

    @Test
    void compactEmptyAndAlreadyCompact() {
        assertEquals(Text.empty(), Text.empty().append(Text.empty()).compact());
        Text t = Text.text("x", NamedTextColor.RED);
        assertEquals(t, t.compact());
    }

    @Test
    void compactNeverChangesLookOnRandomTrees() {
        for (int seed = 0; seed < 600; seed++) {
            Text t = new RandomText(seed, true).next(4);
            Text c = t.compact();
            assertSameLook(t, c);
            assertEquals(c, c.compact().compact(), "compact 는 한 번이면 충분하다(수렴)");
        }
    }

    @Test
    void compactNeverGrows() {
        for (int seed = 0; seed < 300; seed++) {
            Text t = new RandomText(1000 + seed, false).next(4);
            assertTrue(countNodes(t.compact()) <= countNodes(t), "seed " + seed);
        }
    }

    static int countNodes(Text t) {
        int n = 1;
        for (Text c : t.children()) n += countNodes(c);
        return n;
    }

    // ------------------------------------------------------------------ join · flatten

    static final List<Text> ITEMS = List.of(Text.text("a"), Text.text("b"), Text.text("c"));

    @Test
    void joinConfigurationSeparators() {
        assertEquals("a, b, c", JoinConfiguration.commas(true).join(ITEMS).plainText());
        assertEquals("a,b,c", JoinConfiguration.commas(false).join(ITEMS).plainText());
        assertEquals("a b c", JoinConfiguration.spaces().join(ITEMS).plainText());
        assertEquals("a\nb\nc", JoinConfiguration.newlines().join(ITEMS).plainText());
        assertEquals("abc", JoinConfiguration.noSeparators().join(ITEMS).plainText());
        assertEquals("[a, b, c]", JoinConfiguration.arrayLike().join(ITEMS).plainText());
    }

    @Test
    void joinConfigurationLastSeparators() {
        JoinConfiguration cfg = JoinConfiguration.builder().separator(Text.text(", ")).lastSeparator(Text.text(" and ")).build();
        assertEquals("a and b", cfg.join(ITEMS.subList(0, 2)).plainText());
        assertEquals("a, b and c", cfg.join(ITEMS).plainText());
        JoinConfiguration serial = cfg.toBuilder().lastSeparatorIfSerial(Text.text(", and ")).build();
        assertEquals("a and b", serial.join(ITEMS.subList(0, 2)).plainText(), "둘일 땐 일반 마지막 구분자");
        assertEquals("a, b, and c", serial.join(ITEMS).plainText());
    }

    @Test
    void joinConfigurationPrefixSuffixConvertorAndEmpty() {
        JoinConfiguration cfg = JoinConfiguration.builder().separator(Text.text("|")).prefix(Text.text("<")).suffix(Text.text(">"))
                .convertor(t -> t.color(NamedTextColor.RED)).build();
        Text j = cfg.join(ITEMS);
        assertEquals("<a|b|c>", j.plainText());
        assertEquals(NamedTextColor.RED, j.children().get(1).color());
        assertEquals(Text.empty(), cfg.join(List.of()));
        assertEquals(ITEMS.get(0).color(NamedTextColor.RED), JoinConfiguration.builder().convertor(t -> t.color(NamedTextColor.RED)).build().join(ITEMS.subList(0, 1)));
        assertEquals("a-b-c", Text.join(JoinConfiguration.separator("-"), ITEMS).plainText());
    }

    @Test
    void textOfChildrenKeepsPartsAsChildren() {
        Text t = Text.textOfChildren(Text.text("a"), Text.text("b", NamedTextColor.RED));
        assertEquals(2, t.children().size());
        assertEquals("ab", t.plainText());
    }

    @Test
    void flattenYieldsEffectiveStylesInPreOrder() {
        Text t = Text.text("a", NamedTextColor.RED).append(Text.text("b").decorate(TextDecoration.BOLD).append(Text.text("c", NamedTextColor.BLUE))).append(Text.text("d"));
        List<String> got = new ArrayList<>();
        t.flatten().forEachRemaining(s -> got.add(s.text().literal() + "|" + Visual.style(s.style())));
        assertEquals(List.of("a|red", "b|red +bold", "c|blue +bold", "d|red"), got);
        List<String> visited = new ArrayList<>();
        t.forEachSegment((n, st) -> visited.add(n.literal() + "|" + Visual.style(st)));
        assertEquals(got, visited);
        assertFalse(Text.empty().flatten().next().text().literal().length() > 0);
    }

    @Test
    void plainContentUsesLocaleAndSkipsChildren() {
        Text t = Text.translatable("unknown.k").append(Text.text("child"));
        assertEquals("unknown.k", Texts.plainContent(t, java.util.Locale.US));
        assertEquals("unknown.kchild", t.plainText(java.util.Locale.KOREA));
    }
}
