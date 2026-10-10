// 플레이스홀더·포매터 테스트 — parsed/unparsed/component/styling, 태그 인자 속 치환, 숫자·날짜·선택·참거짓·목록.
package org.domi.furnace.api.text.markup;

import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalDate;
import java.util.List;
import java.util.Locale;
import org.domi.furnace.api.text.*;
import org.junit.jupiter.api.Test;

class PlaceholderTest {

    static final TextMarkup MM = TextMarkup.markup();

    @Test
    void parsedIsReparsedAsMarkup() {
        Text t = MM.deserialize("<red>hi <name>!", Placeholder.parsed("name", "<bold>Steve"));
        assertEquals(List.of("hi [red]", "Steve![red +bold]"), Visual.runs(t), "parsed 값의 <bold> 는 열린 채로 이어진다");
        assertEquals("hi Steve!", t.plainText());
    }

    @Test
    void unparsedIsLiteral() {
        Text t = MM.deserialize("<name>", Placeholder.unparsed("name", "<bold>x</bold>"));
        assertEquals("<bold>x</bold>", t.plainText());
        assertEquals(List.of("<bold>x</bold>[-]"), Visual.runs(t));
    }

    @Test
    void componentKeepsItsOwnStyle() {
        Text t = MM.deserialize("<blue>a<c>b", Placeholder.component("c", Text.text("X", NamedTextColor.GOLD).decorate(TextDecoration.BOLD)));
        assertEquals(List.of("a[blue]", "X[gold +bold]", "b[blue]"), Visual.runs(t));
    }

    @Test
    void componentInheritsSurroundingStyle() {
        Text t = MM.deserialize("<red><c>", Placeholder.component("c", Text.text("X")));
        assertEquals(List.of("X[red]"), Visual.runs(t));
    }

    @Test
    void stylingPlaceholderWrapsContent() {
        TagResolver hl = Placeholder.styling("hl", Style.style(NamedTextColor.YELLOW, TextDecoration.BOLD));
        assertEquals(List.of("a[-]", "b[yellow +bold]", "c[-]"), Visual.runs(MM.deserialize("a<hl>b</hl>c", hl)));
        TagResolver two = Placeholder.styling("s", NamedTextColor.RED, TextDecoration.ITALIC);
        assertEquals(List.of("x[red +italic]"), Visual.runs(MM.deserialize("<s>x", two)));
        TagResolver layered = Placeholder.styling("l", Style.style(NamedTextColor.RED), Style.style(NamedTextColor.GREEN));
        assertEquals(List.of("x[green]"), Visual.runs(MM.deserialize("<l>x", layered)), "뒤의 스타일이 앞을 덮는다");
    }

    @Test
    void parsedWorksInsideTagArguments() {
        Text t = MM.deserialize("<hover:show_text:'Hello <name>'>x</hover>", Placeholder.parsed("name", "Steve"));
        assertEquals(HoverEvent.showText(Text.text("Hello Steve")), t.hoverEvent());
        Text c = MM.deserialize("<click:run_command:'/msg <name> hi'>x", Placeholder.parsed("name", "Alex"));
        assertEquals(ClickEvent.runCommand("/msg Alex hi"), c.clickEvent());
    }

    @Test
    void unparsedAndComponentWorkInsideHoverText() {
        Text t = MM.deserialize("<hover:show_text:'Hello <name>'>x", Placeholder.unparsed("name", "<b>Steve"));
        assertEquals("Hello <b>Steve", ((HoverEvent.ShowText) t.hoverEvent()).text().plainText());
    }

    @Test
    void earlierResolversWinAndNamesAreCaseInsensitive() {
        Text t = MM.deserialize("<Name>", Placeholder.unparsed("name", "A"), Placeholder.unparsed("name", "B"));
        assertEquals("A", t.plainText());
        assertEquals("shadow", MM.deserialize("<red>", Placeholder.unparsed("red", "shadow")).plainText(), "사용자 해석기가 표준 태그보다 우선");
    }

    @Test
    void closingTagOfSelfClosingPlaceholderIsIgnored() {
        assertEquals("Ax", MM.deserialize("<p></p>x", Placeholder.unparsed("p", "A")).plainText());
    }

    @Test
    void invalidNamesAreRejected() {
        assertThrows(IllegalArgumentException.class, () -> Placeholder.parsed("Bad Name", "x"));
        assertThrows(IllegalArgumentException.class, () -> Placeholder.parsed("", "x"));
    }

    @Test
    void parsedRecursionIsBounded() {
        TagResolver self = Placeholder.parsed("a", "x<a>");
        assertDoesNotThrow(() -> MM.deserialize("<a>", self));
    }

    // ------------------------------------------------------------------ 포매터

    @Test
    void numberFormatter() {
        assertEquals("1,234.5", MM.deserialize("<n>", Formatter.number("n", 1234.5, Locale.US)).plainText());
        assertEquals("1,234.50", MM.deserialize("<n:'en-US':'#,##0.00'>", Formatter.number("n", 1234.5, Locale.GERMANY)).plainText());
        assertEquals("3.14", MM.deserialize("<n:'#.##'>", Formatter.number("n", 3.14159, Locale.US)).plainText());
        assertEquals("1.234,5", MM.deserialize("<n:'de-DE'>", Formatter.number("n", 1234.5, Locale.US)).plainText());
        assertEquals("1,234.5", MM.deserialize("<n:'ko-KR'>", Formatter.number("n", 1234.5, Locale.GERMANY)).plainText());
        assertEquals("007", MM.deserialize("<n:'000'>", Formatter.number("n", 7, Locale.US)).plainText());
    }

    @Test
    void dateFormatter() {
        TagResolver d = Formatter.date("d", LocalDate.of(2024, 3, 5), Locale.US);
        assertEquals("2024/03/05", MM.deserialize("<d:'yyyy/MM/dd'>", d).plainText());
        assertEquals("2024-03-05", MM.deserialize("<d>", d).plainText());
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<d:'qqqqqqqqq'>", d));
    }

    @Test
    void choiceFormatter() {
        String m = "<c:'0#no items|1#one item|1<{0} items'>";
        assertEquals("no items", MM.deserialize(m, Formatter.choice("c", 0)).plainText());
        assertEquals("one item", MM.deserialize(m, Formatter.choice("c", 1)).plainText());
        assertEquals("5 items", MM.deserialize(m, Formatter.choice("c", 5)).plainText());
    }

    @Test
    void booleanChoiceFormatter() {
        assertEquals(List.of("on[green]"), Visual.runs(MM.deserialize("<b:'<green>on':'<red>off'>", Formatter.booleanChoice("b", true))));
        assertEquals(List.of("off[red]"), Visual.runs(MM.deserialize("<b:'<green>on':'<red>off'>", Formatter.booleanChoice("b", false))));
    }

    @Test
    void joiningFormatter() {
        List<Text> items = List.of(Text.text("a"), Text.text("b", NamedTextColor.RED), Text.text("c"));
        assertEquals("a, b, c", MM.deserialize("<j>", Formatter.joining("j", items)).plainText());
        assertEquals("a b c", MM.deserialize("<j:' '>", Formatter.joining("j", items)).plainText());
        assertEquals("a, b and c", MM.deserialize("<j:', ':' and '>", Formatter.joining("j", items)).plainText());
        assertEquals("b[red]", Visual.runs(MM.deserialize("<j:''>", Formatter.joining("j", items))).get(1));
    }
}
