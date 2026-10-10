// 레거시 직렬화기 테스트 — 색/꾸밈/초기화 규칙, 두 가지 16진 형식, 16색 줄이기, 주소 추출, 왕복.
package org.domi.furnace.api.text.serializer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Random;
import java.util.regex.Pattern;
import org.domi.furnace.api.text.*;
import org.junit.jupiter.api.Test;

class LegacySerializerTest {

    static final LegacyTextSerializer AMP = LegacyTextSerializer.ampersand();
    static final LegacyTextSerializer SEC = LegacyTextSerializer.section();

    @Test
    void plainStringStaysPlain() {
        assertEquals(Text.text("hello"), AMP.deserialize("hello"));
        assertEquals(Text.empty(), AMP.deserialize(""));
        assertEquals("hello", AMP.serialize(Text.text("hello")));
    }

    @Test
    void colorsAndDecorations() {
        assertEquals(List.of("Hello [green]", "World[green +bold]"), Visual.runs(AMP.deserialize("&aHello &lWorld")));
        assertEquals(List.of("a[+obfuscated]", "b[+obfuscated +strikethrough]", "c[+obfuscated +strikethrough +underlined]", "d[+obfuscated +strikethrough +underlined +italic]"),
                Visual.runs(AMP.deserialize("&ka&mb&nc&od")));
        assertEquals(List.of("x[red]"), Visual.runs(AMP.deserialize("&Cx")), "대문자 코드");
        for (TextColor c : NamedTextColor.VALUES) {
            char code = Character.forDigit(NamedTextColor.VALUES.indexOf(c), 16);
            assertEquals(List.of("x[" + c.name() + "]"), Visual.runs(AMP.deserialize("&" + code + "x")));
        }
    }

    @Test
    void colorResetsDecorationsAndRResetsAll() {
        assertEquals(List.of("X[green]"), Visual.runs(AMP.deserialize("&l&aX")), "색 코드가 앞선 굵게를 지운다");
        assertEquals(List.of("X[green +bold]", "Y[-]"), Visual.runs(AMP.deserialize("&a&lX&rY")));
        assertEquals(List.of("X[red +bold]", "Y[blue]"), Visual.runs(AMP.deserialize("&c&lX&9Y")));
        assertEquals(List.of("Y[-]"), Visual.runs(AMP.deserialize("&c&l&rY")));
    }

    @Test
    void unknownCodesAndTrailingCharacterStayText() {
        assertEquals("&zhi", AMP.deserialize("&zhi").plainText());
        assertEquals("trailing&", AMP.deserialize("trailing&").plainText());
        assertEquals("a&&", AMP.deserialize("a&&").plainText());
        assertEquals("§chi", AMP.deserialize("§chi").plainText(), "& 직렬화기는 § 를 코드로 보지 않는다");
    }

    @Test
    void hexBothFormats() {
        assertEquals(List.of("x[#FF00FF]"), Visual.runs(AMP.deserialize("&#ff00ffx")));
        assertEquals(List.of("x[#FF00FF]"), Visual.runs(AMP.deserialize("&x&f&f&0&0&f&fx")));
        assertEquals(List.of("x[#FF00FF]"), Visual.runs(SEC.deserialize("§#ff00ffx")));
        assertEquals(List.of("x[#FF00FF]"), Visual.runs(SEC.deserialize("§x§f§f§0§0§f§fx")));
        assertEquals(List.of("&#ff0x[-]"), Visual.runs(AMP.deserialize("&#ff0x")), "자릿수가 모자라면 글자");
        assertEquals(List.of("&xhi[-]"), Visual.runs(AMP.deserialize("&xhi")));
        // 16진이 바닐라 16색 값이면 이름 있는 색으로 정규화
        assertEquals(List.of("x[red]"), Visual.runs(AMP.deserialize("&#ff5555x")));
        // 16진 꾸밈 결합
        assertEquals(List.of("x[#123456 +bold]"), Visual.runs(AMP.deserialize("&#123456&lx")));
    }

    @Test
    void hexDisabledTreatsHexAsText() {
        LegacyTextSerializer noHex = LegacyTextSerializer.builder().character('&').build();
        assertEquals("&#ff00ffx", noHex.deserialize("&#ff00ffx").plainText());
    }

    @Test
    void serializeBasics() {
        assertEquals("&cx", AMP.serialize(Text.text("x", NamedTextColor.RED)));
        assertEquals("&c&lx", AMP.serialize(Text.text("x", NamedTextColor.RED, TextDecoration.BOLD)));
        assertEquals("&lx", AMP.serialize(Text.text("x").decorate(TextDecoration.BOLD)));
        // 굵게가 사라지면 &r 로 초기화한 뒤 다시 쓴다
        Text t = Text.text("a", NamedTextColor.RED, TextDecoration.BOLD).append(Text.text("b").decoration(TextDecoration.BOLD, false));
        assertEquals("&c&la&r&cb", AMP.serialize(t));
        // 색이 사라질 때도 &r
        assertEquals("&ca&rb", AMP.serialize(Text.join(Text.text("a", NamedTextColor.RED), Text.text("b"))));
        // 같은 색 연속은 코드를 반복하지 않는다
        assertEquals("&cab", AMP.serialize(Text.join(Text.text("a", NamedTextColor.RED), Text.text("b", NamedTextColor.RED))));
        // 색 바꾸면 꾸밈이 지워지므로 다시 쓴다
        assertEquals("&c&lx&9&ly", AMP.serialize(Text.join(Text.text("x", NamedTextColor.RED, TextDecoration.BOLD), Text.text("y", NamedTextColor.BLUE, TextDecoration.BOLD))));
        assertEquals("§cx", SEC.serialize(Text.text("x", NamedTextColor.RED)));
    }

    @Test
    void serializeHexFormats() {
        Text t = Text.text("x", TextColor.color(0x123456));
        assertEquals("&#123456x", AMP.serialize(t));
        assertEquals("§x§1§2§3§4§5§6x", SEC.serialize(t));
        LegacyTextSerializer ampX = LegacyTextSerializer.builder().character('&').hexColors().useUnusualXRepeatedCharacterHexFormat().build();
        assertEquals("&x&1&2&3&4&5&6x", ampX.serialize(t));
        LegacyTextSerializer custom = LegacyTextSerializer.builder().character('&').hexCharacter('@').hexColors().build();
        assertEquals("&@123456x", custom.serialize(t));
        assertEquals(List.of("x[#123456]"), Visual.runs(custom.deserialize("&@123456x")));
    }

    @Test
    void serializeDownsamplesWithoutHex() {
        LegacyTextSerializer noHex = LegacyTextSerializer.builder().character('&').build();
        assertEquals("&6x", noHex.serialize(Text.text("x", TextColor.color(0xFFA500))));
        assertEquals("&cx", noHex.serialize(Text.text("x", NamedTextColor.RED)));
    }

    @Test
    void serializeDropsUnrepresentableStyleAndTranslates() {
        Text t = Text.text("x").clickEvent(ClickEvent.runCommand("/a")).hoverText(Text.text("t")).insertion("i");
        assertEquals("x", AMP.serialize(t));
        assertEquals("unknown.key", AMP.serialize(Text.translatable("unknown.key")));
        assertEquals("", AMP.serialize(Text.empty()));
    }

    @Test
    void roundTripPreservesLook() {
        for (String s : new String[]{"&aHello &lWorld", "&c&lbold red&r plain &9blue", "&#ff00ffhex &ostill hex", "plain", "&kx&my"}) {
            Text a = AMP.deserialize(s);
            Text b = AMP.deserialize(AMP.serialize(a));
            assertEquals(Visual.of(a), Visual.of(b), s);
            assertEquals(a.plainText(), b.plainText());
        }
        for (String s : new String[]{"§aHello §lWorld", "§x§1§2§3§4§5§6hex"}) {
            Text a = SEC.deserialize(s);
            assertEquals(s, SEC.serialize(a), "§ 직렬화기는 바닐라와 같은 문자열을 되만든다");
        }
    }

    @Test
    void randomLegacyStringsRoundTrip() {
        Random r = new Random(7);
        String codes = "0123456789abcdefklmnor";
        for (int i = 0; i < 300; i++) {
            StringBuilder sb = new StringBuilder();
            for (int k = 0; k < 8; k++) {
                if (r.nextInt(3) > 0) sb.append('&').append(codes.charAt(r.nextInt(codes.length())));
                if (r.nextInt(6) == 0) sb.append("&#").append(String.format("%06x", r.nextInt(0x1000000)));
                sb.append("ab".charAt(r.nextInt(2)));
            }
            Text a = AMP.deserialize(sb.toString());
            Text b = AMP.deserialize(AMP.serialize(a));
            assertEquals(Visual.of(a), Visual.of(b), sb.toString());
        }
    }

    @Test
    void urlExtraction() {
        LegacyTextSerializer urls = LegacyTextSerializer.builder().character('&').extractUrls().build();
        Text t = urls.deserialize("&aVisit example.com now");
        assertEquals("Visit example.com now", t.plainText());
        Text link = t.children().get(1);
        assertEquals("example.com", link.literal());
        assertEquals(ClickEvent.openUrl("https://example.com"), link.clickEvent());
        assertEquals(NamedTextColor.GREEN, link.color());
        Text full = urls.deserialize("go https://ab.org/path?q=1 ok");
        assertEquals(ClickEvent.openUrl("https://ab.org/path?q=1"), full.children().get(1).clickEvent());
        assertNull(AMP.deserialize("Visit example.com").clickEvent(), "추출을 켜지 않으면 링크가 없다");

        LegacyTextSerializer styled = LegacyTextSerializer.builder().character('&').extractUrls(Style.style(NamedTextColor.AQUA, TextDecoration.UNDERLINED)).build();
        Text s = styled.deserialize("see ab.io");
        Text l = s.children().get(1);
        assertEquals(NamedTextColor.AQUA, l.color());
        assertTrue(l.hasDecoration(TextDecoration.UNDERLINED));

        LegacyTextSerializer custom = LegacyTextSerializer.builder().character('&').extractUrls(Pattern.compile("(?:(furnace))://\\S+"), Style.empty()).build();
        assertEquals(ClickEvent.openUrl("furnace://x"), custom.deserialize("a furnace://x b").children().get(1).clickEvent());
    }
}
