// 마크업 해석 테스트 — 모든 표준 태그, 중첩·닫는 순서, reset, 부정, 이스케이프, strict/lenient, 인자 따옴표.
package org.domi.furnace.api.text.markup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.UUID;
import org.domi.furnace.api.text.*;
import org.junit.jupiter.api.Test;

class MarkupParseTest {

    static final TextMarkup MM = TextMarkup.markup();
    static final TextMarkup STRICT = TextMarkup.builder().strict(true).build();

    static String v(String markup) { return Visual.of(MM.deserialize(markup)); }

    @Test
    void plainTextStaysPlain() {
        assertEquals(Text.text("hello"), MM.deserialize("hello"));
        assertEquals(Text.empty(), MM.deserialize(""));
        assertEquals("a < b > c", MM.deserialize("a < b > c").plainText());
        assertEquals("<3 love", MM.deserialize("<3 love").plainText());
    }

    @Test
    void namedColorsAndAliases() {
        assertEquals("hi[red]", v("<red>hi"));
        assertEquals("hi[dark_gray]", v("<dark_gray>hi"));
        assertEquals("hi[gray]", v("<grey>hi"));
        assertEquals("hi[dark_gray]", v("<dark_grey>hi"));
        assertEquals("hi[red]", v("<RED>hi"));
        for (TextColor c : NamedTextColor.VALUES) assertEquals("x[" + c.name() + "]", v("<" + c.name() + ">x"));
    }

    @Test
    void hexColors() {
        assertEquals("x[#FF00FF]", v("<#ff00ff>x"));
        assertEquals("x[#FF00FF]", v("<color:#ff00ff>x"));
        assertEquals("x[#FF00FF]", v("<c:#ff00ff>x"));
        assertEquals("x[#FF00FF]", v("<colour:#ff00ff>x"));
        assertEquals("x[red]", v("<color:red>x"));
        assertEquals("x[red]", v("<#ff5555>x"), "16색 값은 이름 있는 색으로 정규화");
        assertEquals("<#xyz>x", MM.deserialize("<#xyz>x").plainText(), "잘못된 16진수는 태그가 아니다");
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<color:nope>x"));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<color>x"));
    }

    @Test
    void decorationsAndAliases() {
        assertEquals("x[+bold]", v("<bold>x"));
        assertEquals("x[+bold]", v("<b>x"));
        assertEquals("x[+italic]", v("<i>x"));
        assertEquals("x[+italic]", v("<em>x"));
        assertEquals("x[+italic]", v("<italic>x"));
        assertEquals("x[+underlined]", v("<u>x"));
        assertEquals("x[+underlined]", v("<underlined>x"));
        assertEquals("x[+strikethrough]", v("<st>x"));
        assertEquals("x[+strikethrough]", v("<strikethrough>x"));
        assertEquals("x[+obfuscated]", v("<obf>x"));
        assertEquals("x[+obfuscated]", v("<obfuscated>x"));
    }

    @Test
    void negationForms() {
        assertEquals("x[-bold]", v("<!bold>x"));
        assertEquals("x[-bold]", v("<bold:false>x"));
        assertEquals("x[+bold]", v("<bold:true>x"));
        assertEquals("x[-italic]", v("<!i>x"));
        assertEquals(java.util.List.of("a[+bold]", " b[-bold]", " c[+bold]"), Visual.runs(MM.deserialize("<bold>a<!bold> b</!bold> c")));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<bold:maybe>x"));
    }

    @Test
    void nestingAndInheritance() {
        Text t = MM.deserialize("<red>a<bold>b</bold>c</red>d");
        assertEquals(java.util.List.of("a[red]", "b[red +bold]", "c[red]", "d[-]"), Visual.runs(t));
    }

    @Test
    void closingOrderLenientClosesInnerTags() {
        // </red> 가 안쪽 <bold> 까지 닫는다
        Text t = MM.deserialize("<red><bold>x</red>y</bold>z");
        assertEquals(java.util.List.of("x[red +bold]", "yz[-]"), Visual.runs(t));
    }

    @Test
    void unclosedTagsAutoCloseInLenient() {
        assertEquals("abc[red]", v("<red>abc"));
        assertEquals(java.util.List.of("a[red]", "b[red +bold]"), Visual.runs(MM.deserialize("<red>a<bold>b")));
    }

    @Test
    void unmatchedClosingTagDroppedOrKept() {
        assertEquals("ab", MM.deserialize("a</red>b").plainText(), "아는 태그의 짝 없는 닫기는 버린다");
        assertEquals("a</foo>b", MM.deserialize("a</foo>b").plainText(), "모르는 태그는 글자로 남긴다");
    }

    @Test
    void unknownTagsStayText() {
        assertEquals("<unknown>x", MM.deserialize("<unknown>x").plainText());
        assertEquals("<foo:bar>", MM.deserialize("<foo:bar>").plainText());
    }

    @Test
    void resetClosesEverything() {
        Text t = MM.deserialize("<red><bold>a<reset>b");
        assertEquals(java.util.List.of("a[red +bold]", "b[-]"), Visual.runs(t));
        Text t2 = MM.deserialize("<red>a<reset>b</red>c");
        assertEquals(java.util.List.of("a[red]", "bc[-]"), Visual.runs(t2));
    }

    @Test
    void escapes() {
        assertEquals("<red>x", MM.deserialize("\\<red>x").plainText());
        assertEquals("\\", MM.deserialize("\\\\").plainText());
        assertEquals("C:\\Users", MM.deserialize("C:\\Users").plainText(), "이스케이프 대상이 아닌 \\ 는 그대로");
        // \\ 다음의 <red> 는 진짜 태그
        assertEquals(java.util.List.of("\\[-]", "x[red]"), Visual.runs(MM.deserialize("\\\\<red>x")));
        assertEquals("a\\", MM.deserialize("a\\").plainText());
    }

    @Test
    void preTagDisablesParsing() {
        Text t = MM.deserialize("<pre><red>hello \\<x></pre><blue>b");
        assertEquals("<red>hello \\<x>b", t.plainText());
        assertEquals(java.util.List.of("<red>hello \\<x>[-]", "b[blue]"), Visual.runs(t));
        assertEquals("<red>x", MM.deserialize("<pre><red>x").plainText(), "닫히지 않은 pre 는 끝까지");
    }

    @Test
    void clickTags() {
        Text t = MM.deserialize("<click:run_command:/say hi>go</click>");
        assertEquals(ClickEvent.runCommand("/say hi"), t.clickEvent());
        assertEquals(ClickEvent.openUrl("https://example.com/a?b=c"), MM.deserialize("<click:open_url:https://example.com/a?b=c>x").clickEvent());
        assertEquals(ClickEvent.openUrl("https://example.com/"), MM.deserialize("<click:open_url:'https://example.com/'>x").clickEvent());
        assertEquals(ClickEvent.suggestCommand("/msg a:b"), MM.deserialize("<click:suggest_command:'/msg a:b'>x").clickEvent());
        assertEquals(ClickEvent.copyToClipboard("t"), MM.deserialize("<click:copy_to_clipboard:t>x").clickEvent());
        assertEquals(ClickEvent.changePage(2), MM.deserialize("<click:change_page:2>x").clickEvent());
        assertEquals(ClickEvent.openFile("/tmp/x"), MM.deserialize("<click:open_file:/tmp/x>x").clickEvent());
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<click:fly:x>y"));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<click:run_command>y"));
    }

    @Test
    void hoverShowText() {
        Text t = MM.deserialize("<hover:show_text:'<red>tip'>hi</hover> after");
        assertEquals(HoverEvent.showText(Text.text("tip", NamedTextColor.RED)), t.children().get(0).hoverEvent());
        assertEquals("hi after", t.plainText());
        Text d = MM.deserialize("<hover:show_text:\"a 'quoted' b\">x");
        assertEquals(HoverEvent.showText(Text.text("a 'quoted' b")), d.hoverEvent());
        Text e = MM.deserialize("<hover:show_text:'it\\'s'>x");
        assertEquals(HoverEvent.showText(Text.text("it's")), e.hoverEvent());
    }

    @Test
    void hoverShowItemAndEntity() {
        assertEquals(new HoverEvent.ShowItem("minecraft:diamond", 1, null), MM.deserialize("<hover:show_item:diamond>x").hoverEvent());
        assertEquals(new HoverEvent.ShowItem("minecraft:diamond", 3, null), MM.deserialize("<hover:show_item:minecraft:diamond:3>x").hoverEvent());
        assertEquals(new HoverEvent.ShowItem("minecraft:diamond", 3, "{a:1}"), MM.deserialize("<hover:show_item:diamond:3:'{a:1}'>x").hoverEvent());
        UUID id = UUID.fromString("12345678-1234-1234-1234-123456789abc");
        assertEquals(new HoverEvent.ShowEntity("minecraft:pig", id, null), MM.deserialize("<hover:show_entity:pig:" + id + ">x").hoverEvent());
        assertEquals(new HoverEvent.ShowEntity("minecraft:pig", id, Text.text("Piggy", NamedTextColor.GOLD)),
                MM.deserialize("<hover:show_entity:pig:" + id + ":'<gold>Piggy'>x").hoverEvent());
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<hover:show_entity:pig:nope:x>y"));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<hover:teleport:x>y"));
    }

    @Test
    void keybindAndLang() {
        assertEquals(Text.keybind("key.jump"), MM.deserialize("<key:key.jump>"));
        Text t = MM.deserialize("press <key:key.jump> now");
        assertEquals("press ", ((Content.Literal) t.children().get(0).content()).text());
        assertEquals(Text.translatable("block.minecraft.dirt"), MM.deserialize("<lang:block.minecraft.dirt>"));
        Text l = MM.deserialize("<lang:chat.type.text:Steve:'<red>hello'>");
        assertEquals("chat.type.text", l.translationKey());
        Content.Translatable tr = (Content.Translatable) l.content();
        assertEquals(Text.text("Steve"), tr.args().get(0));
        assertEquals(Text.text("hello", NamedTextColor.RED), tr.args().get(1));
        assertEquals(l, MM.deserialize("<tr:chat.type.text:Steve:'<red>hello'>"));
        assertEquals(l, MM.deserialize("<translate:chat.type.text:Steve:'<red>hello'>"));
    }

    @Test
    void langOrHasFallback() {
        Text t = MM.deserialize("<lang_or:my.key:'Fallback %s':arg>");
        Content.Translatable tr = (Content.Translatable) t.content();
        assertEquals("my.key", tr.key());
        assertEquals("Fallback %s", tr.fallback());
        assertEquals(1, tr.args().size());
        assertEquals(t, MM.deserialize("<tr_or:my.key:'Fallback %s':arg>"));
        assertEquals(t, MM.deserialize("<translate_or:my.key:'Fallback %s':arg>"));
        assertEquals("Fallback arg", t.plainText());
    }

    @Test
    void insertionAndFont() {
        assertEquals("ins", MM.deserialize("<insert:ins>x").insertion());
        assertEquals("a b", MM.deserialize("<insertion:'a b'>x").insertion());
        assertEquals("minecraft:uniform", MM.deserialize("<font:minecraft:uniform>x").font());
        assertEquals("minecraft:uniform", MM.deserialize("<font:uniform>x").font());
        assertEquals("my:font", MM.deserialize("<font:my:font>x").font());
    }

    @Test
    void newlineAndSelectorScoreNbt() {
        assertEquals("a\nb", MM.deserialize("a<newline>b").plainText());
        assertEquals("a\nb", MM.deserialize("a<br>b").plainText());
        assertEquals("a\nb", MM.deserialize("a<br/>b").plainText());
        assertEquals("a\n\nb", MM.deserialize("a<newline:2>b").plainText());
        assertEquals(Text.selector("@a[team=red]"), MM.deserialize("<selector:'@a[team=red]'>"));
        assertEquals(Text.selector("@a", Text.text(", ")), MM.deserialize("<sel:@a:', '>"));
        assertEquals(Text.score("Steve", "kills"), MM.deserialize("<score:Steve:kills>"));
        assertEquals(Text.nbt("Pos", false, null, new Content.NbtSource.Entity("@s")), MM.deserialize("<nbt:entity:'@s':Pos>"));
        assertEquals(Text.nbt("Items", true, Text.text("|"), new Content.NbtSource.Block("1 2 3")), MM.deserialize("<data:block:'1 2 3':Items:'|':interpret>"));
        assertEquals(Text.nbt("a.b", false, null, new Content.NbtSource.Storage("my:store")), MM.deserialize("<nbt:storage:'my:store':a.b>"));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<nbt:biome:x:y>"));
    }

    @Test
    void quotedAndColonArguments() {
        assertEquals("a:b", MM.deserialize("<insert:'a:b'>x").insertion());
        assertEquals("a>b", MM.deserialize("<insert:'a>b'>x").insertion());
        assertEquals("a'b", MM.deserialize("<insert:\"a'b\">x").insertion());
        assertEquals("a\\b", MM.deserialize("<insert:'a\\\\b'>x").insertion());
    }

    @Test
    void strictModeRejectsSloppyMarkup() {
        assertThrows(MarkupParseException.class, () -> STRICT.deserialize("<red>abc"));
        assertThrows(MarkupParseException.class, () -> STRICT.deserialize("abc</red>"));
        assertThrows(MarkupParseException.class, () -> STRICT.deserialize("<red><bold>x</red></bold>"));
        assertThrows(MarkupParseException.class, () -> STRICT.deserialize("<pre>abc"));
        assertEquals("x", STRICT.deserialize("<red><bold>x</bold></red>").plainText());
        assertEquals("a\nb", STRICT.deserialize("a<newline>b").plainText(), "자기 닫는 태그는 strict 에서도 된다");
        assertEquals("a<unknown>", STRICT.deserialize("a<unknown>").plainText());
    }

    @Test
    void errorCarriesPosition() {
        MarkupParseException e = assertThrows(MarkupParseException.class, () -> MM.deserialize("ab<color:nope>"));
        assertEquals(2, e.position());
        assertTrue(e.getMessage().contains("ab<color:nope>"));
    }

    @Test
    void selfClosingStylingProducesNothing() {
        assertEquals("ab", MM.deserialize("a<red/>b").plainText());
        assertEquals(java.util.List.of("ab[-]"), Visual.runs(MM.deserialize("a<red/>b")));
    }

    @Test
    void stripAndEscape() {
        assertEquals("hello world", MM.stripTags("<red>hello <bold>world</bold></red>"));
        assertEquals("<unknown> x", MM.stripTags("<unknown> <red>x"));
        assertEquals("<red>", MM.stripTags("\\<red>"));
        assertEquals("<red>x", MM.stripTags("<pre><red>x</pre>"));
        assertEquals("a\\<red>b", MM.escapeTags("a<red>b"));
        for (String s : new String[]{"plain", "<red>x</red>", "a\\<b>", "back\\", "\\\\<bold>", "<3 <hover:show_text:'x'>y", "한글 <b>"}) {
            assertEquals(s, MM.deserialize(MM.escapeTags(s)).plainText(), s);
        }
    }

    @Test
    void cacheReturnsSameInstanceForPlaceholderFreeInput() {
        TextMarkup cached = TextMarkup.builder().cache(2).build();
        Text a = cached.deserialize("<red>x");
        assertSame(a, cached.deserialize("<red>x"));
        cached.deserialize("b");
        cached.deserialize("c"); // 최대 2개: <red>x 가 밀려난다
        assertNotSame(a, cached.deserialize("<red>x"));
        assertNotSame(cached.deserialize("<red>y", Placeholder.unparsed("p", "v")), cached.deserialize("<red>y", Placeholder.unparsed("p", "v")));
    }

    @Test
    void preAndPostProcessors() {
        TextMarkup m = TextMarkup.builder().preProcessor(s -> s.replace("{p}", "<red>")).postProcessor(t -> t.decoration(TextDecoration.ITALIC, false)).build();
        Text t = m.deserialize("{p}x");
        assertEquals(TriState.FALSE, t.decoration(TextDecoration.ITALIC));
        assertEquals("x", t.plainText());
    }

    @Test
    void customTagViaFunctionalInterface() {
        TagResolver shout = TagResolver.resolver("shout", (args, ctx) -> Tag.styling(s -> s.color(NamedTextColor.RED).decorate(TextDecoration.BOLD)));
        assertEquals("hey[red +bold]", Visual.of(MM.deserialize("<shout>hey</shout>", shout)));
        TagResolver greet = TagResolver.resolver("greet", (args, ctx) -> Tag.selfClosingInserting(Text.text("Hello " + args.popOr("name").value())));
        assertEquals("Hello Bob!", MM.deserialize("<greet:Bob>!", greet).plainText());
        TextMarkup withTags = TextMarkup.builder().editTags(b -> b.tag("shout", Tag.styling(s -> s.color(NamedTextColor.GREEN)))).build();
        assertEquals("a[green]", Visual.of(withTags.deserialize("<shout>a")));
        assertEquals("a[red]", Visual.of(withTags.deserialize("<red>a")), "표준 태그도 유지");
    }

    @Test
    void deeplyNestedHoverIsBounded() {
        TagResolver loop = Placeholder.parsed("loop", "<hover:show_text:'<loop>'>x</hover>");
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<loop>", loop));
    }
}
