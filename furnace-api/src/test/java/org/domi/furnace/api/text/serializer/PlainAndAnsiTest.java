// 평문·ANSI 직렬화기 테스트 — 평문 옵션(번역/키/키바인딩/동적 내용)과 ANSI 세 가지 색 방식·꾸밈·초기화.
package org.domi.furnace.api.text.serializer;

import static org.junit.jupiter.api.Assertions.*;

import java.util.Locale;
import java.util.Map;
import org.domi.furnace.api.text.*;
import org.junit.jupiter.api.Test;

class PlainAndAnsiTest {

    static final String ESC = "\u001B[";

    // ------------------------------------------------------------------ 평문

    @Test
    void defaultsMatchTextPlainText() {
        for (int seed = 0; seed < 300; seed++) {
            Text t = new RandomText(seed, true).next(3);
            assertEquals(t.plainText(), PlainTextSerializer.plain().serialize(t), "seed " + seed);
        }
    }

    @Test
    void translatableModes() {
        Translator tr = (key, locale) -> key.equals("p.k") ? "%2$s then %1$s" : null;
        Translators.register(tr);
        try {
            Text t = Text.translatable("p.k", "a", Text.text("b", NamedTextColor.RED)).append(Text.text("!"));
            assertEquals("b then a!", PlainTextSerializer.plain().serialize(t));
            assertEquals("p.k!", PlainTextSerializer.builder().translate(false).build().serialize(t));
            assertEquals("p.k[a, b]!", PlainTextSerializer.builder().translatables(PlainTextSerializer.TranslatableMode.KEY_WITH_ARGS).build().serialize(t));
            assertEquals("p.k!", PlainTextSerializer.builder().translatables(PlainTextSerializer.TranslatableMode.KEY_WITH_ARGS).build().serialize(Text.translatable("p.k").append("!")));
        } finally {
            Translators.unregister(tr);
        }
    }

    @Test
    void localeOptionPicksTranslation() {
        Translator tr = (key, locale) -> key.equals("hi.k") ? (locale.getLanguage().equals("ko") ? "안녕" : "hello") : null;
        Translators.register(tr);
        try {
            assertEquals("안녕", PlainTextSerializer.builder().locale(Locale.KOREAN).build().serialize(Text.translatable("hi.k")));
            assertEquals("hello", PlainTextSerializer.plain().serialize(Text.translatable("hi.k")));
        } finally {
            Translators.unregister(tr);
        }
    }

    @Test
    void keybindAndDynamicContentModes() {
        assertEquals("key.jump", PlainTextSerializer.builder().keybinds(PlainTextSerializer.KeybindMode.KEY).build().serialize(Text.keybind("key.jump")));
        assertEquals("[key.jump]", PlainTextSerializer.builder().keybinds(PlainTextSerializer.KeybindMode.BRACKETED).build().serialize(Text.keybind("key.jump")));
        PlainTextSerializer d = PlainTextSerializer.builder().dynamicContent(PlainTextSerializer.DynamicMode.DESCRIBE).build();
        assertEquals("@p:obj", d.serialize(Text.score("@p", "obj")));
        assertEquals("@a", d.serialize(Text.selector("@a")));
        assertEquals("Pos", d.serialize(Text.nbt("Pos", false, null, new Content.NbtSource.Entity("@s"))));
        assertEquals("", PlainTextSerializer.plain().serialize(Text.score("@p", "obj")));
    }

    // ------------------------------------------------------------------ ANSI

    @Test
    void trueColor() {
        AnsiTextSerializer a = AnsiTextSerializer.trueColor();
        assertEquals(ESC + "38;2;18;52;86mhi" + ESC + "0m", a.serialize(Text.text("hi", TextColor.color(0x123456))));
        assertEquals("plain", a.serialize(Text.text("plain")));
    }

    @Test
    void namedColorsUsePalette() {
        AnsiTextSerializer a = AnsiTextSerializer.trueColor();
        assertEquals(ESC + "91mhi" + ESC + "0m", a.serialize(Text.text("hi", NamedTextColor.RED)));
        assertEquals(ESC + "31mhi" + ESC + "0m", a.serialize(Text.text("hi", NamedTextColor.DARK_RED)));
        assertEquals(ESC + "90mhi" + ESC + "0m", a.serialize(Text.text("hi", NamedTextColor.DARK_GRAY)));
        AnsiTextSerializer exact = AnsiTextSerializer.builder().colorMode(AnsiTextSerializer.ColorMode.TRUE_COLOR).exactNamedColors(true).build();
        assertEquals(ESC + "38;2;255;85;85mhi" + ESC + "0m", exact.serialize(Text.text("hi", NamedTextColor.RED)));
    }

    @Test
    void ansi256AndAnsi16() {
        assertEquals(ESC + "38;5;196mx" + ESC + "0m", AnsiTextSerializer.ansi256().serialize(Text.text("x", TextColor.color(0xFF0000))));
        assertEquals(ESC + "38;5;244mx" + ESC + "0m", AnsiTextSerializer.ansi256().serialize(Text.text("x", TextColor.color(0x808080))), "회색 램프");
        assertEquals(ESC + "33mx" + ESC + "0m", AnsiTextSerializer.ansi16().serialize(Text.text("x", TextColor.color(0xFFA500))), "가장 가까운 16색(금색)");
        assertEquals(ESC + "93mx" + ESC + "0m", AnsiTextSerializer.ansi16().serialize(Text.text("x", NamedTextColor.YELLOW)));
    }

    @Test
    void decorations() {
        AnsiTextSerializer a = AnsiTextSerializer.trueColor();
        assertEquals(ESC + "1mx" + ESC + "0m", a.serialize(Text.text("x").decorate(TextDecoration.BOLD)));
        assertEquals(ESC + "3mx" + ESC + "0m", a.serialize(Text.text("x").decorate(TextDecoration.ITALIC)));
        assertEquals(ESC + "4mx" + ESC + "0m", a.serialize(Text.text("x").decorate(TextDecoration.UNDERLINED)));
        assertEquals(ESC + "9mx" + ESC + "0m", a.serialize(Text.text("x").decorate(TextDecoration.STRIKETHROUGH)));
        assertEquals(ESC + "5mx" + ESC + "0m", a.serialize(Text.text("x").decorate(TextDecoration.OBFUSCATED)));
        assertEquals(ESC + "91;1mx" + ESC + "0m", a.serialize(Text.text("x", NamedTextColor.RED, TextDecoration.BOLD)), "한 시퀀스로 합친다");
    }

    @Test
    void transitionsEmitOnlyDifferencesAndResets() {
        AnsiTextSerializer a = AnsiTextSerializer.trueColor();
        Text t = Text.join(Text.text("a", NamedTextColor.RED), Text.text("b"));
        assertEquals(ESC + "91ma" + ESC + "39mb", a.serialize(t));
        Text b = Text.join(Text.text("a").decorate(TextDecoration.BOLD), Text.text("b"));
        assertEquals(ESC + "1ma" + ESC + "22mb", a.serialize(b));
        Text same = Text.join(Text.text("a", NamedTextColor.RED), Text.text("b", NamedTextColor.RED));
        assertEquals(ESC + "91mab" + ESC + "0m", a.serialize(same), "같은 스타일은 코드 반복 없음");
        Text swap = Text.join(Text.text("a", NamedTextColor.RED), Text.text("b", NamedTextColor.BLUE));
        assertEquals(ESC + "91ma" + ESC + "94mb" + ESC + "0m", a.serialize(swap));
        AnsiTextSerializer noReset = AnsiTextSerializer.builder().resetAtEnd(false).build();
        assertEquals(ESC + "91mx", noReset.serialize(Text.text("x", NamedTextColor.RED)));
    }

    @Test
    void childInheritsParentStyle() {
        Text t = Text.text("a", NamedTextColor.RED).append(Text.text("b").decorate(TextDecoration.BOLD));
        assertEquals(ESC + "91ma" + ESC + "1mb" + ESC + "0m", AnsiTextSerializer.trueColor().serialize(t));
    }

    @Test
    void noneModeIsPlain() {
        AnsiTextSerializer none = AnsiTextSerializer.builder().colorMode(AnsiTextSerializer.ColorMode.NONE).build();
        assertEquals("ab", none.serialize(Text.join(Text.text("a", NamedTextColor.RED), Text.text("b").decorate(TextDecoration.BOLD))));
    }

    @Test
    void detectFromEnvironment() {
        assertEquals(AnsiTextSerializer.ColorMode.NONE, AnsiTextSerializer.ColorMode.detect(Map.of("NO_COLOR", "1")::get));
        assertEquals(AnsiTextSerializer.ColorMode.TRUE_COLOR, AnsiTextSerializer.ColorMode.detect(Map.of("COLORTERM", "truecolor", "TERM", "xterm")::get));
        assertEquals(AnsiTextSerializer.ColorMode.TRUE_COLOR, AnsiTextSerializer.ColorMode.detect(Map.of("COLORTERM", "24bit")::get));
        assertEquals(AnsiTextSerializer.ColorMode.ANSI_256, AnsiTextSerializer.ColorMode.detect(Map.of("TERM", "xterm-256color")::get));
        assertEquals(AnsiTextSerializer.ColorMode.NONE, AnsiTextSerializer.ColorMode.detect(Map.of("TERM", "dumb")::get));
        assertEquals(AnsiTextSerializer.ColorMode.ANSI_16, AnsiTextSerializer.ColorMode.detect(Map.of("TERM", "xterm")::get));
        assertEquals(AnsiTextSerializer.ColorMode.ANSI_16, AnsiTextSerializer.ColorMode.detect(k -> null));
    }

    @Test
    void translatesContent() {
        assertEquals("unknown.k", AnsiTextSerializer.trueColor().serialize(Text.translatable("unknown.k")));
    }
}
