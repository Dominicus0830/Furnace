package net.minecraft.network.chat.contents;

import java.util.Optional;
import net.minecraft.network.chat.ComponentContents;
import net.minecraft.network.chat.FormattedText;
import net.minecraft.network.chat.Style;

/** 글자 그대로의 내용(Mojang 이름 shim). 빈 문자열은 {@link #EMPTY} 하나를 쓴다(바닐라 create 와 같다). */
public interface PlainTextContents extends ComponentContents {

    PlainTextContents EMPTY = new PlainTextContents() {
        @Override
        public String text() { return ""; }

        @Override
        public String toString() { return "empty"; }
    };

    static PlainTextContents create(String text) { return text.isEmpty() ? EMPTY : new LiteralContents(text); }

    String text();

    @Override
    default <T> Optional<T> visit(FormattedText.ContentConsumer<T> consumer) { return consumer.accept(text()); }

    @Override
    default <T> Optional<T> visit(FormattedText.StyledContentConsumer<T> consumer, Style style) { return consumer.accept(style, text()); }

    record LiteralContents(String text) implements PlainTextContents {

        @Override
        public String toString() { return "literal{" + text + "}"; }
    }
}
