// 텍스트 템플릿 테스트 — 채우기 결과가 직접 해석한 것과 같은지, 슬롯 없는 부분 트리 재사용, 스타일 슬롯, 호버/번역 인자 속 슬롯, 오류.
package org.domi.furnace.api.text.markup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Map;
import org.domi.furnace.api.text.*;
import org.junit.jupiter.api.Test;

class TextTemplateTest {

    static final TextMarkup MM = TextMarkup.markup();

    @Test
    void fillMatchesDirectDeserialize() {
        String src = "<gold>Hello <b><player></b>, you have <n> coins!";
        TextTemplate tpl = MM.template(src, "player", "n");
        Text filled = tpl.fill("player", Text.text("Steve", NamedTextColor.GREEN), "n", 42);
        Text direct = MM.deserialize(src, Placeholder.component("player", Text.text("Steve", NamedTextColor.GREEN)), Placeholder.unparsed("n", "42"));
        assertEquals(Visual.of(direct), Visual.of(filled));
        assertEquals("Hello Steve, you have 42 coins!", filled.plainText());
    }

    @Test
    void staticTemplateReturnsSameInstance() {
        TextTemplate tpl = MM.template("<red>no slots");
        assertTrue(tpl.isStatic());
        assertSame(tpl.fill(Map.of()), tpl.fill(Map.of()));
    }

    @Test
    void unchangedSubtreesAreShared() {
        TextTemplate tpl = MM.template("<red>static part</red> and <b>dynamic <name></b> then <blue>more static</blue>", "name");
        Text a = tpl.fill("name", "A");
        Text b = tpl.fill("name", "B");
        assertEquals("static part and dynamic A then more static", a.plainText());
        assertEquals("static part and dynamic B then more static", b.plainText());
        // 슬롯 없는 형제 마디는 두 결과가 같은 인스턴스를 공유한다
        assertSame(a.children().get(0), b.children().get(0));
        assertNotSame(a.children().get(2), b.children().get(2));
        assertSame(a.children().get(4), b.children().get(4));
        assertNotEquals(a, b);
    }

    @Test
    void valueKinds() {
        TextTemplate tpl = MM.template("[<v>]", "v");
        assertEquals("[text]", tpl.fill("v", "text").plainText());
        assertEquals("[<b>raw]", tpl.fill("v", "<b>raw").plainText(), "문자열은 해석하지 않는다");
        assertEquals("[7]", tpl.fill("v", 7).plainText());
        assertEquals("[true]", tpl.fill("v", true).plainText());
        assertEquals(java.util.List.of("[[-]", "bold[+bold]", "][-]"), Visual.runs(tpl.fill("v", TextTemplate.parsed("<b>bold"))));
        assertThrows(IllegalArgumentException.class, () -> tpl.fill("v", Style.empty()));
    }

    @Test
    void missingSlotValueThrows() {
        TextTemplate tpl = MM.template("<a> <b>", "a", "b");
        assertThrows(IllegalArgumentException.class, () -> tpl.fill("a", "x"));
        assertEquals(java.util.Set.of("a", "b"), tpl.slots());
    }

    @Test
    void slotsInsideHoverAndTranslatableArgs() {
        TextTemplate tpl = MM.template("<hover:show_text:'hi <p>'>x</hover> <lang:chat.type.text:'<p>':z>", "p");
        Text t = tpl.fill("p", Text.text("Steve", NamedTextColor.RED));
        Text hoverOwner = t.children().get(0);
        assertEquals("hi Steve", ((HoverEvent.ShowText) hoverOwner.hoverEvent()).text().plainText());
        Content.Translatable tr = (Content.Translatable) t.children().get(2).content();
        assertEquals("Steve", ((Text) tr.args().get(0)).plainText());
        Text direct = MM.deserialize("<hover:show_text:'hi <p>'>x</hover> <lang:chat.type.text:'<p>':z>", Placeholder.component("p", Text.text("Steve", NamedTextColor.RED)));
        assertEquals(direct.toJson(), t.toJson());
    }

    @Test
    void styleSlots() {
        TextTemplate tpl = TextTemplate.builder(MM).styleSlot("hl").slot("n").compile("a <hl>b <n></hl> c");
        Text t = tpl.fill(Map.of("hl", Style.style(NamedTextColor.YELLOW, TextDecoration.BOLD), "n", "X"));
        assertEquals(java.util.List.of("a [-]", "b X[yellow +bold]", " c[-]"), Visual.runs(t));
        assertThrows(IllegalArgumentException.class, () -> tpl.fill(Map.of("hl", "no", "n", "X")));
    }

    @Test
    void slotUnderGradientColorsAsOneUnit() {
        TextTemplate tpl = MM.template("<gradient:#ff0000:#0000ff>a<n>c", "n");
        Text t = tpl.fill("n", "BBBB");
        assertEquals("aBBBBc", t.plainText());
        assertEquals(java.util.List.of("a[#FF0000]", "BBBB[#800080]", "c[#0000FF]"), Visual.runs(t));
    }

    @Test
    void fixedResolversApplyToEveryFill() {
        TextTemplate tpl = TextTemplate.builder(MM).slot("n").resolvers(Placeholder.unparsed("server", "Furnace")).compile("<server>: <n>");
        assertEquals("Furnace: 5", tpl.fill("n", 5).plainText());
        TextTemplate p = TextTemplate.builder(MM).slot("n").resolvers(Placeholder.parsed("rd", "<red>")).compile("<rd><n>");
        assertEquals(java.util.List.of("x[red]"), Visual.runs(p.fill("n", "x")));
        assertEquals(java.util.List.of("<green>y[red]"), Visual.runs(p.fill("n", "<green>y")));
    }

    @Test
    void filledTextsKeepOwnEncodingCaches() {
        TextTemplate tpl = MM.template("hi <n>", "n");
        Text a = tpl.fill("n", "A");
        assertSame(a.networkNbt(), a.networkNbt());
        assertNotEquals(tpl.fill("n", "B").toJson(), a.toJson());
    }

    @Test
    void fillerBuilder() {
        TextTemplate tpl = MM.template("<a>-<b>", "a", "b");
        assertEquals("1-2", tpl.filler().put("a", 1).put("b", 2).build().plainText());
    }
}
