package net.minecraft.network.chat;

import java.util.List;
import java.util.Optional;
import net.minecraft.util.Unit;

/**
 * 서식 글 방문자(Mojang 이름 shim). 글을 (스타일, 글자) 조각으로 훑는 바닐라 규약이다: 소비자가 값을 돌려주면 거기서 멈춘다.
 * 구현은 Furnace 가 새로 썼고 바닐라(Paper 1.21.1) 바이트코드의 동작을 따른다.
 */
public interface FormattedText {

    Optional<Unit> STOP_ITERATION = Optional.of(Unit.INSTANCE);

    FormattedText EMPTY = new FormattedText() {
        @Override
        public <T> Optional<T> visit(ContentConsumer<T> consumer) { return Optional.empty(); }

        @Override
        public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) { return Optional.empty(); }
    };

    <T> Optional<T> visit(ContentConsumer<T> consumer);

    <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style);

    static FormattedText of(String text) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(ContentConsumer<T> consumer) { return consumer.accept(text); }

            @Override
            public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) { return consumer.accept(style, text); }
        };
    }

    static FormattedText of(String text, Style textStyle) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(ContentConsumer<T> consumer) { return consumer.accept(text); }

            @Override
            public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) { return consumer.accept(textStyle.applyTo(style), text); }
        };
    }

    static FormattedText composite(FormattedText... parts) { return composite(List.of(parts)); }

    static FormattedText composite(List<? extends FormattedText> parts) {
        return new FormattedText() {
            @Override
            public <T> Optional<T> visit(ContentConsumer<T> consumer) {
                for (FormattedText p : parts) {
                    Optional<T> r = p.visit(consumer);
                    if (r.isPresent()) return r;
                }
                return Optional.empty();
            }

            @Override
            public <T> Optional<T> visit(StyledContentConsumer<T> consumer, Style style) {
                for (FormattedText p : parts) {
                    Optional<T> r = p.visit(consumer, style);
                    if (r.isPresent()) return r;
                }
                return Optional.empty();
            }
        };
    }

    default String getString() {
        StringBuilder sb = new StringBuilder();
        visit(s -> {
            sb.append(s);
            return Optional.empty();
        });
        return sb.toString();
    }

    interface ContentConsumer<T> {
        Optional<T> accept(String text);
    }

    interface StyledContentConsumer<T> {
        Optional<T> accept(Style style, String text);
    }
}
