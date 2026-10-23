// 텍스트 직렬화가 바닐라 1.21.1 과 같은지: JSON 문자열과 플레이 단계 NBT 바이트를 바닐라 코드로 뽑은 정답과 비교한다
// (정답 파일: 바닐라 ComponentSerialization.CODEC 을 JsonOps·NbtOps 로 인코딩한 결과). 번역 포맷·스타일 상속·색 파싱도 본다.
package org.domi.furnace.api.text;

import static org.junit.jupiter.api.Assertions.*;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class TextEncodingTest {

    /** 정답 파일과 같은 순서의 사례. */
    static List<Text> cases() {
        return List.of(
                Text.text("hello"),
                Text.text(""),
                Text.text("red", NamedTextColor.RED),
                Text.text("hex", TextColor.color(0x123456), TextDecoration.BOLD, TextDecoration.ITALIC),
                Text.translatable("multiplayer.player.joined", Text.text("Steve")).color(NamedTextColor.YELLOW),
                Text.translatable("commands.list.players", 1, 20, Text.text("Steve")),
                Text.translatable("k", 1.5, true, 7L, "s", Text.text("x", NamedTextColor.RED)),
                Text.translatableWithFallback("my.key", "Fallback %s", "a"),
                Text.empty().append(Text.text("a")).append(Text.text("b", NamedTextColor.GREEN)),
                Text.text("a").append(Text.text("b")).append(Text.text("c")),
                Text.text("click").clickEvent(ClickEvent.runCommand("/say hi")).hoverText(Text.text("tip", NamedTextColor.GRAY)).insertion("ins"),
                Text.text("e").hoverEvent(HoverEvent.showEntity("minecraft:pig", new UUID(0x0123456789abcdefL, 0xfedcba9876543210L), Text.text("Pig"))),
                Text.text("f").font("minecraft:uniform").decoration(TextDecoration.UNDERLINED, false)
                        .decoration(TextDecoration.STRIKETHROUGH, true).decoration(TextDecoration.OBFUSCATED, true),
                Text.keybind("key.jump"),
                Text.score("@p", "obj"),
                Text.selector("@a", Text.text(", ")),
                Text.nbt("Pos", true, null, new Content.NbtSource.Entity("@s")),
                Text.text("esc \" \\ \n \t \u0001 <>&='  한글"),
                Text.translatable("chat.type.admin", Text.text("Server"), Text.translatable("commands.time.set", 1000))
                        .color(NamedTextColor.GRAY).decoration(TextDecoration.ITALIC, true));
    }

    private static String hex(byte[] b) {
        StringBuilder s = new StringBuilder();
        for (byte x : b) s.append(String.format("%02x", x));
        return s.toString();
    }

    @Test
    void jsonAndNbtMatchVanilla() throws Exception {
        List<String[]> golden = new ArrayList<>();
        try (var r = new BufferedReader(new InputStreamReader(getClass().getResourceAsStream("/text/vanilla-1.21.1-golden.tsv"), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) golden.add(line.split("\t"));
        }
        List<Text> cases = cases();
        assertEquals(golden.size(), cases.size());
        for (int i = 0; i < cases.size(); i++) {
            assertEquals(golden.get(i)[0], cases.get(i).toJson(), "JSON #" + (i + 1));
            assertEquals(golden.get(i)[1], hex(cases.get(i).networkNbt()), "NBT #" + (i + 1));
        }
    }

    @Test
    void allStyleFieldsUseVanillaJsonOrder() {
        Text t = Text.translatableWithFallback("k", "f", "a").color(NamedTextColor.RED)
                .decorate(TextDecoration.BOLD, TextDecoration.ITALIC, TextDecoration.UNDERLINED, TextDecoration.STRIKETHROUGH, TextDecoration.OBFUSCATED)
                .clickEvent(ClickEvent.runCommand("/x")).hoverText(Text.text("h")).insertion("i").font("minecraft:default").append(Text.text("e"));
        assertEquals("{\"translate\":\"k\",\"fallback\":\"f\",\"with\":[\"a\"],\"extra\":[\"e\"],\"hoverEvent\":{\"contents\":\"h\",\"action\":\"show_text\"},"
                + "\"insertion\":\"i\",\"font\":\"minecraft:default\",\"obfuscated\":true,\"clickEvent\":{\"action\":\"run_command\",\"value\":\"/x\"},"
                + "\"italic\":true,\"underlined\":true,\"strikethrough\":true,\"color\":\"red\",\"bold\":true}", t.toJson());
    }

    @Test
    void encodingIsCachedAndTextIsImmutable() {
        Text a = Text.text("x");
        assertSame(a.networkNbt(), a.networkNbt(), "한 번 인코딩해 재사용");
        Text b = a.color(NamedTextColor.RED);
        assertNotSame(a, b);
        assertNull(a.color(), "원본은 그대로");
        assertEquals(Text.text("x"), a);
        assertEquals(Text.text("x").hashCode(), a.hashCode());
    }

    @Test
    void translationFormatFollowsVanilla() {
        Translator t = (key, locale) -> switch (key) {
            case "test.a" -> "%s and %s";
            case "test.b" -> "%2$s before %1$s %%";
            case "test.bad" -> "%d broken";
            case "test.range" -> "%3$s";
            default -> null;
        };
        Translators.register(t);
        try {
            assertEquals("x and y", Text.translatable("test.a", "x", "y").plainText());
            assertEquals("y before x %", Text.translatable("test.b", "x", Text.text("y")).plainText());
            assertEquals("%d broken", Text.translatable("test.bad", 1).plainText(), "지원하지 않는 형식은 패턴 그대로");
            assertEquals("%3$s", Text.translatable("test.range", "a").plainText(), "범위 밖 인자도 패턴 그대로");
            assertEquals("unknown.key", Text.translatable("unknown.key").plainText(), "모르는 키는 키 그대로");
            assertEquals("Fb 1", Text.translatableWithFallback("unknown.key", "Fb %s", 1).plainText(), "fallback 사용");
            assertEquals("ab", Text.text("a").append(Text.text("b")).plainText());
            assertEquals("", Text.score("@p", "o").plainText(), "해석 전 점수는 빈 문자열");
        } finally {
            Translators.unregister(t);
        }
    }

    @Test
    void stylesInheritAndColorsParse() {
        Style parent = Style.style(NamedTextColor.RED, TextDecoration.BOLD);
        Style child = Style.empty().decoration(TextDecoration.BOLD, false).insertion("i");
        Style merged = child.applyTo(parent);
        assertEquals(NamedTextColor.RED, merged.color());
        assertEquals(TriState.FALSE, merged.decoration(TextDecoration.BOLD));
        assertEquals("i", merged.insertion());

        assertSame(NamedTextColor.RED, TextColor.color(0xFF5555), "16색 값은 이름 있는 색으로 정규화");
        assertSame(NamedTextColor.GRAY, TextColor.parse("gray"));
        assertEquals(0x00FF7F, TextColor.parse("#00ff7f").value());
        assertNull(TextColor.parse("#12345"));
        assertEquals("#00FF7F", TextColor.color(0x00FF7F).serialize());
        assertSame(NamedTextColor.GRAY, NamedTextColor.byName("grey"));
        assertEquals(NamedTextColor.GOLD, NamedTextColor.nearestTo(TextColor.color(0xFFA500)));
        assertEquals(TextDecoration.BOLD, TextDecoration.byName("b"));
        assertEquals(Locale.US, Translators.SERVER_LOCALE);
    }

    @Test
    void translatableRejectsUnsupportedArgs() {
        assertThrows(IllegalArgumentException.class, () -> Text.translatable("k", new Object()));
    }
}
