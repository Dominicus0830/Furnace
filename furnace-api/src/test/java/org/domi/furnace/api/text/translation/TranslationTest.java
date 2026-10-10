// 번역 저장소·전역 렌더러 테스트 — 로케일 대체 순서, 바닐라/MessageFormat 패턴, Translators 등록, 언어별 렌더링과 바닐라 키 보존.
package org.domi.furnace.api.text.translation;

import static org.junit.jupiter.api.Assertions.*;

import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.ResourceBundle;
import org.domi.furnace.api.text.*;
import org.junit.jupiter.api.Test;

class TranslationTest {

    static TranslationStore sample() {
        return TranslationStore.create(Locale.US)
                .register(Locale.US, "furnace.greet", "Hello, %s!")
                .register(Locale.KOREAN, "furnace.greet", "안녕, %s!")
                .register(Locale.KOREA, "furnace.only_kr", "한국만")
                .register(Locale.US, "furnace.swap", "%2$s <- %1$s")
                .register(Locale.US, "furnace.percent", "100%% sure");
    }

    @Test
    void localeFallbackChain() {
        TranslationStore s = sample();
        assertEquals("안녕, %s!", s.lookup("furnace.greet", Locale.KOREA), "ko-KR → ko");
        assertEquals("안녕, %s!", s.lookup("furnace.greet", Locale.KOREAN));
        assertEquals("Hello, %s!", s.lookup("furnace.greet", Locale.FRANCE), "모르는 언어는 기본 로케일");
        assertEquals("Hello, %s!", s.lookup("furnace.greet", Locale.UK), "en-GB → (en 없음) → 기본 en-US");
        assertEquals("한국만", s.lookup("furnace.only_kr", Locale.KOREA));
        assertNull(s.lookup("furnace.only_kr", Locale.US));
        assertNull(s.lookup("nope", Locale.US));
        assertTrue(s.contains("furnace.only_kr"));
        assertFalse(s.contains("nope"));
    }

    @Test
    void messageFormatPatternsAreConverted() {
        TranslationStore s = TranslationStore.create(Locale.US, TranslationStore.Format.MESSAGE_FORMAT);
        s.register(Locale.US, "a", "{1} before {0}");
        s.register(Locale.US, "b", "It''s '{0}' {0} 100%");
        s.register(Locale.US, "c", "{0,number,#.##} items");
        s.register(Locale.US, "d", "mix", TranslationStore.Format.VANILLA);
        assertEquals("%2$s before %1$s", s.lookup("a", Locale.US));
        assertEquals("It's {0} %1$s 100%%", s.lookup("b", Locale.US));
        assertEquals("%1$s items", s.lookup("c", Locale.US));
        assertEquals("mix", s.lookup("d", Locale.US));
    }

    @Test
    void registerAllFromMapAndBundle() {
        TranslationStore s = TranslationStore.create(Locale.US).registerAll(Locale.US, Map.of("x", "1", "y", "2"));
        assertEquals("1", s.lookup("x", Locale.US));
        ResourceBundle bundle = new java.util.ListResourceBundle() {
            @Override
            protected Object[][] getContents() { return new Object[][]{{"k1", "v1"}, {"k2", "v2"}}; }
        };
        s.registerAll(Locale.GERMAN, bundle);
        assertEquals("v2", s.lookup("k2", Locale.GERMANY));
        s.unregister("k1");
        assertNull(s.lookup("k1", Locale.GERMAN));
    }

    @Test
    void globalRegistrationFeedsTranslators() {
        TranslationStore s = sample();
        s.registerGlobally();
        s.registerGlobally(); // 중복 등록은 무시
        try {
            assertEquals("Hello, Steve!", Text.translatable("furnace.greet", "Steve").plainText());
            assertEquals("안녕, Steve!", Text.translatable("furnace.greet", "Steve").plainText(Locale.KOREA));
        } finally {
            s.unregisterGlobally();
        }
        assertEquals("furnace.greet", Text.translatable("furnace.greet", "Steve").plainText(), "해제하면 키 그대로");
    }

    @Test
    void rendersKnownKeysPerLocale() {
        GlobalRenderer r = GlobalRenderer.of(sample());
        Text t = Text.translatable("furnace.greet", Text.text("Steve", NamedTextColor.GREEN)).color(NamedTextColor.GOLD);
        Text ko = r.apply(t, Locale.KOREA);
        Text en = r.apply(t, Locale.US);
        assertEquals("안녕, Steve!", ko.plainText());
        assertEquals("Hello, Steve!", en.plainText());
        assertEquals(List.of("안녕, [gold]", "Steve[green]", "![gold]"), Visual.runs(ko));
        assertEquals(List.of("Hello, [gold]", "Steve[green]", "![gold]"), Visual.runs(en));
        assertTrue(ko.children().stream().noneMatch(c -> c.translationKey() != null));
    }

    @Test
    void positionalAndPercentPatterns() {
        GlobalRenderer r = GlobalRenderer.of(sample());
        assertEquals("b <- a", r.apply(Text.translatable("furnace.swap", "a", "b"), Locale.US).plainText());
        assertEquals("100% sure", r.apply(Text.translatable("furnace.percent"), Locale.US).plainText());
    }

    @Test
    void vanillaKeysStayTranslatableAndInstanceIsKept() {
        GlobalRenderer r = GlobalRenderer.of(sample());
        Text vanilla = Text.translatable("multiplayer.player.joined", Text.text("Steve")).color(NamedTextColor.YELLOW);
        assertSame(vanilla, r.apply(vanilla, Locale.US), "바꿀 게 없으면 같은 인스턴스(인코딩 캐시 유지)");
        Text mixed = Text.text("x").append(vanilla).append(Text.translatable("furnace.greet", "A"));
        Text out = r.apply(mixed, Locale.US);
        assertSame(vanilla, out.children().get(0), "바닐라 키는 그대로");
        assertEquals("multiplayer.player.joined", out.children().get(0).translationKey());
        assertEquals("xmultiplayer.player.joinedHello, A!", out.plainText().replace("Steve", ""));
    }

    @Test
    void rendersInsideArgsAndHover() {
        GlobalRenderer r = GlobalRenderer.of(sample());
        Text inner = Text.translatable("furnace.greet", "X");
        Text outer = Text.translatable("multiplayer.player.joined", inner).hoverText(inner);
        Text out = r.apply(outer, Locale.US);
        assertEquals("multiplayer.player.joined", out.translationKey());
        assertEquals("Hello, X!", ((Text) ((Content.Translatable) out.content()).args().get(0)).plainText());
        assertEquals("Hello, X!", ((HoverEvent.ShowText) out.hoverEvent()).text().plainText());
    }

    @Test
    void malformedPatternsAreShownVerbatimLikeVanilla() {
        TranslationStore s = TranslationStore.create(Locale.US).register(Locale.US, "bad.d", "%d broken").register(Locale.US, "bad.range", "%3$s")
                .register(Locale.US, "bad.tail", "50%");
        GlobalRenderer r = GlobalRenderer.of(s);
        assertEquals("%d broken", r.apply(Text.translatable("bad.d", 1), Locale.US).plainText());
        assertEquals("%3$s", r.apply(Text.translatable("bad.range", "a"), Locale.US).plainText());
        assertEquals("50%", r.apply(Text.translatable("bad.tail"), Locale.US).plainText());
    }

    @Test
    void globalRendererUsesGloballyRegisteredStores() {
        TranslationStore s = sample();
        s.registerGlobally();
        try {
            assertEquals("안녕, A!", GlobalRenderer.render(Text.translatable("furnace.greet", "A"), Locale.KOREA).plainText());
        } finally {
            s.unregisterGlobally();
        }
        Text t = Text.translatable("furnace.greet", "A");
        assertSame(t, GlobalRenderer.render(t, Locale.KOREA), "해제 뒤에는 그대로");
    }

    @Test
    void childrenAreKeptAfterRenderedPieces() {
        GlobalRenderer r = GlobalRenderer.of(sample());
        Text t = Text.translatable("furnace.greet", "A").append(Text.text(" tail", NamedTextColor.RED));
        Text out = r.apply(t, Locale.US);
        assertEquals("Hello, A! tail", out.plainText());
        assertEquals(List.of("Hello, A![-]", " tail[red]"), Visual.runs(out));
    }
}
