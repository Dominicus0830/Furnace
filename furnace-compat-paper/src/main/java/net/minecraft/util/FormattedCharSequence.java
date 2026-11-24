package net.minecraft.util;

import java.util.List;
import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;

/**
 * 스타일 있는 글자열(Mojang 이름 shim). 바닐라처럼 §서식 코드는 스타일로 바뀌어 소비자에게 간다.
 * 오른쪽에서 왼쪽 글 재배열(bidi)은 서버 기본 Language 도 하지 않으므로 하지 않는다.
 * {@code backward}·{@code decorateOutput} 과 Int2IntFunction 을 받는 {@code forward} 는 아직 없다(없는 멤버 — 호출 위치에서 실패).
 */
@FunctionalInterface
public interface FormattedCharSequence {

    FormattedCharSequence EMPTY = sink -> true;

    boolean accept(FormattedCharSink sink);

    static FormattedCharSequence codepoint(int codePoint, Style style) { return sink -> sink.accept(0, style, codePoint); }

    static FormattedCharSequence forward(String text, Style style) {
        return text.isEmpty() ? EMPTY : sink -> iterateFormatted(text, style, sink);
    }

    static FormattedCharSequence composite() { return EMPTY; }

    static FormattedCharSequence composite(FormattedCharSequence a) { return a; }

    static FormattedCharSequence composite(FormattedCharSequence a, FormattedCharSequence b) { return fromPair(a, b); }

    static FormattedCharSequence composite(FormattedCharSequence... parts) { return fromList(List.of(parts)); }

    static FormattedCharSequence composite(List<FormattedCharSequence> parts) {
        return switch (parts.size()) {
            case 0 -> EMPTY;
            case 1 -> parts.get(0);
            case 2 -> fromPair(parts.get(0), parts.get(1));
            default -> fromList(List.copyOf(parts));
        };
    }

    static FormattedCharSequence fromPair(FormattedCharSequence a, FormattedCharSequence b) { return sink -> a.accept(sink) && b.accept(sink); }

    static FormattedCharSequence fromList(List<FormattedCharSequence> parts) {
        return sink -> {
            for (FormattedCharSequence p : parts) {
                if (!p.accept(sink)) return false;
            }
            return true;
        };
    }

    /** 바닐라 StringDecomposer.iterateFormatted: §코드는 스타일을 바꾸고(RESET 은 처음 스타일로), 짝 없는 대리쌍은 U+FFFD. */
    private static boolean iterateFormatted(String text, Style style, FormattedCharSink sink) {
        int len = text.length();
        Style current = style;
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (c == '§') {
                if (i + 1 >= len) break;
                ChatFormatting f = ChatFormatting.getByCode(text.charAt(i + 1));
                if (f != null) current = f == ChatFormatting.RESET ? style : current.applyLegacyFormat(f);
                i++;
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 >= len) {
                    if (!sink.accept(i, current, 65533)) return false;
                    break;
                }
                char c2 = text.charAt(i + 1);
                if (Character.isLowSurrogate(c2)) {
                    if (!sink.accept(i, current, Character.toCodePoint(c, c2))) return false;
                    i++;
                } else if (!sink.accept(i, current, 65533)) {
                    return false;
                }
            } else if (!sink.accept(i, current, Character.isSurrogate(c) ? 65533 : c)) {
                return false;
            }
        }
        return true;
    }
}
