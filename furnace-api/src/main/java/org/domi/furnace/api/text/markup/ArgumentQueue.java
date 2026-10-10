// 태그 인자 큐 — "<tag:a:'b c':d>" 의 a, b c, d 를 차례로 꺼낸다(따옴표·이스케이프는 이미 풀린 값). 태그 하나를 해석하는 동안만 쓴다.
package org.domi.furnace.api.text.markup;

import java.util.OptionalDouble;
import java.util.OptionalInt;

public interface ArgumentQueue {

    /** 인자 하나. */
    interface Argument {
        String value();

        /** 소문자 값. */
        default String lowerValue() { return value().toLowerCase(java.util.Locale.ROOT); }

        default OptionalInt asInt() {
            try {
                return OptionalInt.of(Integer.parseInt(value().trim()));
            } catch (NumberFormatException e) {
                return OptionalInt.empty();
            }
        }

        default OptionalDouble asDouble() {
            try {
                return OptionalDouble.of(Double.parseDouble(value().trim()));
            } catch (NumberFormatException e) {
                return OptionalDouble.empty();
            }
        }

        default boolean isTrue() { return "true".equalsIgnoreCase(value()); }

        default boolean isFalse() { return "false".equalsIgnoreCase(value()); }
    }

    /** 남은 인자가 있나. */
    boolean hasNext();

    /** 남은 인자 수. */
    int remaining();

    /** 다음 인자를 꺼내지 않고 본다(없으면 null). */
    Argument peek();

    /** 다음 인자를 꺼낸다. 없으면 {@link MarkupParseException}. */
    Argument pop();

    /** 다음 인자를 꺼낸다. 없으면 errorMessage 로 {@link MarkupParseException}. */
    Argument popOr(String errorMessage);

    /** 남은 인자를 ':' 로 다시 이어 꺼낸다(URL·명령처럼 ':' 가 들어간 값용). 없으면 errorMessage 로 예외. */
    String popRestOr(String errorMessage);

    /** 처음 위치로 되감는다. */
    void reset();

    /** 빈 큐(태그 존재 확인용). */
    static ArgumentQueue empty() { return Impl.EMPTY; }

    /** 구현 — 값 배열 하나를 감싼다. */
    final class Impl implements ArgumentQueue {
        static final Impl EMPTY = new Impl(new String[0], 0);

        private record Arg(String value) implements Argument {}

        private final String[] values;
        private final int count;
        private int pos;

        Impl(String[] values, int count) {
            this.values = values;
            this.count = count;
        }

        @Override public boolean hasNext() { return pos < count; }

        @Override public int remaining() { return count - pos; }

        @Override public Argument peek() { return pos < count ? new Arg(values[pos]) : null; }

        @Override public Argument pop() { return popOr("인자가 더 필요하다"); }

        @Override
        public Argument popOr(String errorMessage) {
            if (pos >= count) throw new MarkupParseException(errorMessage);
            return new Arg(values[pos++]);
        }

        @Override
        public String popRestOr(String errorMessage) {
            if (pos >= count) throw new MarkupParseException(errorMessage);
            if (count - pos == 1) return values[pos++];
            StringBuilder sb = new StringBuilder(values[pos++]);
            while (pos < count) sb.append(':').append(values[pos++]);
            return sb.toString();
        }

        @Override public void reset() { pos = 0; }
    }
}
