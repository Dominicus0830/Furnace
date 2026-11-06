// 마크업 직렬화 테스트 — 알려진 출력 모양, 이스케이프, 직렬화 → 해석 왕복(글자와 글자별 스타일이 같아야 한다), 난수 왕복.
package org.domi.furnace.api.text.markup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.api.text.*;
import org.junit.jupiter.api.Test;

class MarkupSerializeTest {

    static final TextMarkup MM = TextMarkup.markup();
    static final TextMarkup STRICT_CLOSED = TextMarkup.builder().strict(true).closeAllTags(true).build();

    static void roundTrip(Text t) {
        String s = MM.serialize(t);
        assertEquals(Visual.of(t), Visual.of(MM.deserialize(s)), "왕복 실패: " + s);
        String closed = STRICT_CLOSED.serialize(t);
        assertEquals(Visual.of(t), Visual.of(STRICT_CLOSED.deserialize(closed)), "strict 왕복 실패: " + closed);
        assertEquals(t.plainText(), MM.deserialize(s).plainText());
    }

    @Test
    void knownOutputShapes() {
        assertEquals("hello", MM.serialize(Text.text("hello")));
        assertEquals("<red>hello", MM.serialize(Text.text("hello", NamedTextColor.RED)));
        assertEquals("<#123456><bold>x", MM.serialize(Text.text("x", TextColor.color(0x123456), TextDecoration.BOLD)));
        assertEquals("<red>a</red>b", MM.serialize(Text.join(Text.text("a", NamedTextColor.RED), Text.text("b"))));
        assertEquals("<!italic>x", MM.serialize(Text.text("x").decoration(TextDecoration.ITALIC, false)));
        assertEquals("<click:run_command:'/say hi'>x", MM.serialize(Text.text("x").clickEvent(ClickEvent.runCommand("/say hi"))));
        assertEquals("<key:key.jump>", MM.serialize(Text.keybind("key.jump")));
        assertEquals("<lang:multiplayer.player.joined:Steve>", MM.serialize(Text.translatable("multiplayer.player.joined", "Steve")));
        assertEquals("<red>a</red>", STRICT_CLOSED.serialize(Text.text("a", NamedTextColor.RED)));
    }

    @Test
    void escapesLiteralText() {
        assertEquals("a\\<b>", MM.serialize(Text.text("a<b>")));
        assertEquals("a\\\\", MM.serialize(Text.text("a\\")));
        assertEquals("a\\b", MM.serialize(Text.text("a\\b")));
        assertEquals("\\\\\\<x", MM.serialize(Text.text("\\<x")));
    }

    @Test
    void roundTripsHandWrittenCases() {
        roundTrip(Text.text("plain"));
        roundTrip(Text.text("red", NamedTextColor.RED).append(Text.text(" and ")).append(Text.text("bold", NamedTextColor.GREEN, TextDecoration.BOLD)));
        roundTrip(Text.text("a<b>c\\d'e\"f:g"));
        roundTrip(Text.text("x").decoration(TextDecoration.ITALIC, false).decoration(TextDecoration.BOLD, true));
        roundTrip(Text.text("click").clickEvent(ClickEvent.openUrl("https://example.com/a?b=c:d")).hoverText(Text.text("tip <x>", NamedTextColor.GRAY)));
        roundTrip(Text.text("ins").insertion("it's").font("minecraft:uniform"));
        roundTrip(Text.text("hi").hoverEvent(HoverEvent.showItem("minecraft:diamond", 3)));
        roundTrip(Text.text("hi").hoverEvent(HoverEvent.showEntity("minecraft:pig", UUID.randomUUID(), Text.text("Pig", NamedTextColor.GOLD))));
        roundTrip(Text.text("hi").hoverEvent(HoverEvent.showEntity("minecraft:pig", UUID.randomUUID(), null)));
        roundTrip(Text.translatable("k.v", "a:b", 5, true, Text.text("t", NamedTextColor.RED)));
        roundTrip(Text.translatableWithFallback("k.v", "fallback's %s", "x"));
        roundTrip(Text.score("@p", "obj").color(NamedTextColor.RED));
        roundTrip(Text.selector("@a[team=red]", Text.text(", ")));
        roundTrip(Text.nbt("a.b", true, Text.text("|"), new Content.NbtSource.Storage("ns:path")));
        roundTrip(Text.keybind("key.attack").decorate(TextDecoration.UNDERLINED));
        roundTrip(Text.text("한글 😀 <3 \n줄바꿈"));
        roundTrip(Text.empty().append(Text.text("a", NamedTextColor.RED).append(Text.text("b", NamedTextColor.BLUE))).append(Text.text("c")));
    }

    @Test
    void deserializedGradientRoundTripsPerCharacter() {
        Text t = MM.deserialize("<gradient:#ff0000:#0000ff><b>hello</b> world</gradient>");
        roundTrip(t);
    }

    @Test
    void decorationFalseSurvivesAsExplicitOff() {
        Text t = MM.deserialize("<!italic>a");
        Text back = MM.deserialize(MM.serialize(t));
        assertEquals(TriState.FALSE, back.decoration(TextDecoration.ITALIC));
    }

    @Test
    void randomTreesRoundTrip() {
        for (int seed = 0; seed < 400; seed++) {
            roundTrip(new RandomText(seed, true).next(3));
        }
    }
}
