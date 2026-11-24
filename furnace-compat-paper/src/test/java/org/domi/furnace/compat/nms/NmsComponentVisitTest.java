package org.domi.furnace.compat.nms;

import static org.junit.jupiter.api.Assertions.*;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Component;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.MutableComponent;
import net.minecraft.network.chat.Style;
import net.minecraft.network.chat.contents.KeybindContents;
import net.minecraft.network.chat.contents.PlainTextContents;
import net.minecraft.network.chat.contents.TranslatableContents;
import net.minecraft.util.FormattedCharSequence;
import org.junit.jupiter.api.Test;

/** Component 의 바닐라 규칙: 내용 객체·방문·평탄화·번역 조각 나누기. */
class NmsComponentVisitTest {

    private static List<String> pieces(Component c) {
        List<String> out = new ArrayList<>();
        c.visit((style, text) -> {
            out.add(text + "|" + (style.isBold() ? "b" : "-"));
            return Optional.empty();
        }, Style.EMPTY);
        return out;
    }

    @Test
    void contentsAreVanillaObjects() {
        assertInstanceOf(PlainTextContents.LiteralContents.class, Component.literal("hi").getContents());
        assertSame(PlainTextContents.EMPTY, Component.empty().getContents());
        MutableComponent t = Component.translatableWithFallback("no.such.key", "fb %s", "a");
        TranslatableContents tc = assertInstanceOf(TranslatableContents.class, t.getContents());
        assertEquals("no.such.key", tc.getKey());
        assertEquals("fb %s", tc.getFallback());
        assertArrayEquals(new Object[] {"a"}, tc.getArgs());
        assertSame(t.getContents(), t.getContents());
        assertInstanceOf(KeybindContents.class, Component.keybind("key.jump").getContents());
        assertEquals("literal{hi}", Component.literal("hi").getContents().toString());
    }

    @Test
    void createFromContentsRoundTrips() {
        MutableComponent c = MutableComponent.create(new TranslatableContents("k", null, new Object[] {Component.literal("x"), 3}));
        assertEquals("k", c.getString());
        TranslatableContents tc = (TranslatableContents) c.getContents();
        assertEquals(2, tc.getArgs().length);
        assertSame(PlainTextContents.EMPTY, MutableComponent.create(PlainTextContents.create("")).getContents());
    }

    @Test
    void translationDecompose() {
        MutableComponent c = Component.translatableWithFallback("no.such.key", "Hi %s and %2$s %%!", "a", Component.literal("b").withStyle(ChatFormatting.BOLD));
        assertEquals("Hi a and b %!", c.getString());
        assertEquals(List.of("Hi |-", "a|-", " and |-", "b|b", " |-", "%|-", "!|-"), pieces(c));
    }

    @Test
    void translationFormatErrorKeepsTemplate() {
        assertEquals("100% bad", Component.translatableWithFallback("no.such.key", "100% bad").getString());
        assertEquals("%d no", Component.translatableWithFallback("no.such.key", "%d no", 1).getString());
        assertEquals("%s %s", Component.translatableWithFallback("no.such.key", "%s %s", "only").getString());
    }

    @Test
    void nullArgumentIsNullText() {
        assertEquals("v=null", Component.translatableWithFallback("no.such.key", "v=%s", (Object) null).getString());
    }

    @Test
    void flatListStreamIterator() {
        MutableComponent root = Component.literal("a").append(Component.literal("b").withStyle(ChatFormatting.BOLD)).append(Component.literal("").append("c"));
        List<Component> flat = root.toFlatList();
        assertEquals(3, flat.size());
        assertEquals("a", flat.get(0).getString());
        assertTrue(flat.get(1).getStyle().isBold());
        assertEquals(4, root.stream().count());
        List<Component> it = new ArrayList<>();
        for (Component c : root) it.add(c);
        assertEquals(4, it.size());
        assertSame(root, it.get(0));
        assertEquals("abc", root.getString());
        assertEquals("ab", root.getString(2));
        assertEquals("", root.getString(0));
    }

    @Test
    void containsAndCollapse() {
        MutableComponent root = Component.literal("a").append("b");
        assertTrue(root.contains(root));
        assertTrue(root.contains(Component.literal("b")));
        assertFalse(root.contains(Component.literal("z")));
        assertEquals("a", Component.literal("a").tryCollapseToString());
        assertNull(root.tryCollapseToString());
        assertNull(Component.literal("a").withStyle(ChatFormatting.BOLD).tryCollapseToString());
        assertNull(Component.translatable("x").tryCollapseToString());
    }

    @Test
    void keybindResolvesToTranslation() {
        assertEquals("Jump", Component.keybind("key.jump").getString());
    }

    @Test
    void visualOrderAppliesLegacyCodes() {
        List<String> seen = new ArrayList<>();
        Component.literal("a§lb§rc").getVisualOrderText().accept((pos, style, cp) -> {
            seen.add(new String(Character.toChars(cp)) + (style.isBold() ? "B" : "-"));
            return true;
        });
        assertEquals(List.of("a-", "bB", "c-"), seen);
        assertTrue(FormattedCharSequence.EMPTY.accept((p, s, c) -> false));
        StringBuilder raw = new StringBuilder();
        FormattedCharSequence.forward("§ab", net.minecraft.network.chat.Style.EMPTY).accept((p, st, cp) -> { raw.appendCodePoint(cp); return true; });
        assertEquals("§ab", raw.toString());
        assertEquals(null, Component.translatable("k").tryCollapseToString());
        assertEquals("x", Component.literal("x").tryCollapseToString());
    }

    @Test
    void formattedTextComposite() {
        FormattedText t = FormattedText.composite(FormattedText.of("x"), FormattedText.of("y", Style.EMPTY.withBold(true)));
        assertEquals("xy", t.getString());
        assertEquals(Optional.of("x"), t.visit(s -> Optional.of(s)));
        assertEquals("", FormattedText.EMPTY.getString());
    }

    @Test
    void unmodelledContentsAreInvisibleButThrowOnGetContents() {
        MutableComponent score = MutableComponent.create(new org.domi.furnace.api.text.Content.Score("n", "o"));
        assertEquals("", score.getString());
        assertThrows(UnsupportedNmsCall.class, score::getContents);
    }
}
