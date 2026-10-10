// 그라디언트·무지개·전환·프라이드 색 테스트 — 알려진 입력의 정확한 색(공식은 손으로 계산: 선형 보간, HSV 시퀀스).
package org.domi.furnace.api.text.markup;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import org.domi.furnace.api.text.*;
import org.junit.jupiter.api.Test;

class MarkupEffectsTest {

    static final TextMarkup MM = TextMarkup.markup();

    static List<String> runs(String markup) { return Visual.runs(MM.deserialize(markup)); }

    @Test
    void gradientTwoColorsThreeChars() {
        // 위치 0, 0.5, 1 → 가운데는 (127.5 → 반올림 128)
        assertEquals(List.of("a[#FF0000]", "b[#800080]", "c[#0000FF]"), runs("<gradient:#ff0000:#0000ff>abc</gradient>"));
    }

    @Test
    void gradientTwoColorsFiveChars() {
        // 위치 0, .25, .5, .75, 1 : r = 255, 191.25, 127.5, 63.75, 0 ; b = 0, 63.75, 127.5, 191.25, 255
        assertEquals(List.of("a[#FF0000]", "b[#BF0040]", "c[#800080]", "d[#4000BF]", "e[#0000FF]"), runs("<gradient:#ff0000:#0000ff>abcde"));
    }

    @Test
    void gradientNamedColorsAndSingleChar() {
        assertEquals(List.of("a[red]"), runs("<gradient:red:blue>a"));
        assertEquals(List.of("a[white]", "b[#808080]", "c[black]"), runs("<gradient>abc"), "인자가 없으면 흰색 → 검정");
    }

    @Test
    void gradientThreeColors() {
        assertEquals(List.of("a[#FF0000]", "b[#808000]", "c[#00FF00]", "d[#008080]", "e[#0000FF]"),
                runs("<gradient:#ff0000:#00ff00:#0000ff>abcde"));
    }

    @Test
    void gradientPhase() {
        // phase .5 : 위치 .5, 1, 1.5 (끝에서 첫 색으로 되돌아 섞는다)
        assertEquals(List.of("a[#800080]", "b[#0000FF]", "c[#800080]"), runs("<gradient:#ff0000:#0000ff:0.5>abc"));
        // 음수 phase 는 색을 뒤집고 1+phase 만큼 민다
        assertEquals(List.of("a[#800080]", "b[#FF0000]", "c[#800080]"), runs("<gradient:#ff0000:#0000ff:-0.5>abc"));
        assertEquals(List.of("a[#0000FF]", "b[#800080]", "c[#FF0000]"), runs("<gradient:#ff0000:#0000ff:-1>abc"));
    }

    @Test
    void gradientCountsCodePointsNotUtf16Units() {
        List<String> r = runs("<gradient:#ff0000:#0000ff>a😀b");
        assertEquals(List.of("a[#FF0000]", "😀[#800080]", "b[#0000FF]"), r);
    }

    @Test
    void gradientMergesEqualAdjacentColors() {
        assertEquals(List.of("abc[red]"), runs("<gradient:red:red>abc"));
        Text t = MM.deserialize("<gradient:red:red>abc");
        assertEquals(1, t.children().size() + 1, "한 마디로 합쳐진다: " + t);
    }

    @Test
    void gradientKeepsExplicitColorsButAdvances() {
        // b 는 green 으로 고정, 위치는 흘러간다: a=0, b(건너뜀)=1, c=2
        assertEquals(List.of("a[#FF0000]", "b[green]", "c[#0000FF]"), runs("<gradient:#ff0000:#0000ff>a<green>b</green>c"));
    }

    @Test
    void gradientKeepsDecorationsAndOtherStyles() {
        assertEquals(List.of("a[#FF0000 +bold]", "b[#0000FF +bold]"), runs("<gradient:#ff0000:#0000ff><b>ab"));
        Text t = MM.deserialize("<gradient:#ff0000:#0000ff><click:run_command:/x>ab</click></gradient>cd");
        assertEquals("abcd", t.plainText());
        assertEquals(ClickEvent.runCommand("/x"), t.children().get(0).clickEvent());
    }

    @Test
    void gradientSpansSiblingsAndStopsAtClose() {
        List<String> r = runs("<gradient:#ff0000:#0000ff>a<b>b</b>c</gradient>d");
        assertEquals(List.of("a[#FF0000]", "b[#800080 +bold]", "c[#0000FF]", "d[-]"), r);
    }

    @Test
    void gradientColorsInsertedComponents() {
        TagResolver x = Placeholder.component("x", Text.text("XY"));
        assertEquals(List.of("a[#FF0000]", "X[#800080]", "Y[#0000FF]"), Visual.runs(MM.deserialize("<gradient:#ff0000:#0000ff>a<x>", x)).subList(0, 3));
        TagResolver k = Placeholder.component("k", Text.keybind("key.jump"));
        List<String> r = Visual.runs(MM.deserialize("<gradient:#ff0000:#0000ff>a<k>", k));
        assertEquals(List.of("a[#FF0000]", "{Keybind}[#0000FF]"), r, "글자가 아닌 내용은 한 칸");
    }

    @Test
    void gradientErrors() {
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<gradient:red>x"));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<gradient:red:blue:2>x"));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<gradient:red:nope>x"));
    }

    @Test
    void rainbowSixChars() {
        assertEquals(List.of("a[#FF0000]", "b[#FFFF00]", "c[#00FF00]", "d[#00FFFF]", "e[#0000FF]", "f[#FF00FF]"), runs("<rainbow>abcdef"));
    }

    @Test
    void rainbowReversedAndPhase() {
        assertEquals(List.of("a[#FF00FF]", "b[#0000FF]", "c[#00FFFF]", "d[#00FF00]", "e[#FFFF00]", "f[#FF0000]"), runs("<rainbow:!>abcdef"));
        // phase 5 → 색상 +0.5 : 두 글자면 [0.5, 1.0→0]
        assertEquals(List.of("a[#00FFFF]", "b[#FF0000]"), runs("<rainbow:5>ab"));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<rainbow:x>a"));
    }

    @Test
    void transitionPicksOneColor() {
        assertEquals(List.of("abc[#800080]"), runs("<transition:#ff0000:#0000ff:0.5>abc"));
        assertEquals(List.of("abc[#FF0000]"), runs("<transition:#ff0000:#0000ff:0>abc"));
        assertEquals(List.of("abc[#0000FF]"), runs("<transition:#ff0000:#0000ff:1>abc"));
        assertEquals(List.of("abc[#00FF00]"), runs("<transition:#ff0000:#00ff00:#0000ff:0.5>abc"));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<transition:red:blue:2>x"));
    }

    @Test
    void prideStripes() {
        assertEquals(List.of("a[#E40303]", "b[#FF8C00]", "c[#FFED00]", "d[#008026]", "e[#24408E]", "f[#732982]"), runs("<pride>abcdef"));
        // 글자 3개, 줄무늬 5개: 줄무늬 번호 = floor(i/3*5) = 0, 1, 3
        assertEquals(List.of("a[#5BCEFA]", "bc[#F5A9B8]"), runs("<pride:trans>abc"));
        assertThrows(MarkupParseException.class, () -> MM.deserialize("<pride:nope>x"));
    }
}
