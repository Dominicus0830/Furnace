package net.minecraft.util;

import net.minecraft.ChatFormatting;
import net.minecraft.network.chat.Style;

/** 바닐라 StringDecomposer: 글자열을 코드포인트로 쪼개 소비자에게 준다. */
public class StringDecomposer {
    private static final char REPLACEMENT_CHAR = '\uFFFD';

    public static final java.util.Optional<net.minecraft.util.Unit> STOP_ITERATION = net.minecraft.network.chat.FormattedText.STOP_ITERATION;

    public StringDecomposer() {}

    private static boolean feedChar(Style style, FormattedCharSink sink, int pos, char c) {
        return Character.isSurrogate(c) ? sink.accept(pos, style, REPLACEMENT_CHAR) : sink.accept(pos, style, c);
    }

    public static boolean iterate(String text, Style style, FormattedCharSink sink) {
        int len = text.length();
        for (int i = 0; i < len; i++) {
            char c = text.charAt(i);
            if (Character.isHighSurrogate(c)) {
                if (i + 1 >= len) {
                    if (!sink.accept(i, style, REPLACEMENT_CHAR)) return false;
                    break;
                }
                char c2 = text.charAt(i + 1);
                if (Character.isLowSurrogate(c2)) {
                    if (!sink.accept(i, style, Character.toCodePoint(c, c2))) return false;
                    i++;
                } else if (!sink.accept(i, style, REPLACEMENT_CHAR)) {
                    return false;
                }
            } else if (!feedChar(style, sink, i, c)) {
                return false;
            }
        }
        return true;
    }

    public static boolean iterateBackwards(String text, Style style, FormattedCharSink sink) {
        int len = text.length();
        for (int i = len - 1; i >= 0; i--) {
            char c = text.charAt(i);
            if (Character.isLowSurrogate(c)) {
                if (i - 1 < 0) {
                    if (!sink.accept(0, style, REPLACEMENT_CHAR)) return false;
                    break;
                }
                char c2 = text.charAt(--i);
                if (Character.isHighSurrogate(c2)) {
                    if (!sink.accept(i, style, Character.toCodePoint(c2, c))) return false;
                } else if (!sink.accept(i, style, REPLACEMENT_CHAR)) {
                    return false;
                }
            } else if (!feedChar(style, sink, i, c)) {
                return false;
            }
        }
        return true;
    }

    public static String filterBrokenSurrogates(String text) {
        StringBuilder sb = new StringBuilder();
        iterate(text, Style.EMPTY, (pos, st, cp) -> { sb.appendCodePoint(cp); return true; });
        return sb.toString();
    }

    public static String getPlainText(net.minecraft.network.chat.FormattedText text) {
        StringBuilder sb = new StringBuilder();
        iterateFormatted(text, Style.EMPTY, (pos, st, cp) -> { sb.appendCodePoint(cp); return true; });
        return sb.toString();
    }

    public static boolean iterateFormatted(String text, int start, Style style, FormattedCharSink sink) {
        return iterateFormatted(text, start, style, style, sink);
    }

    public static boolean iterateFormatted(String text, Style style, FormattedCharSink sink) {
        return iterateFormatted(text, 0, style, style, sink);
    }

    public static boolean iterateFormatted(String text, int start, Style startStyle, Style resetStyle, FormattedCharSink sink) {
        int len = text.length();
        Style style = startStyle;
        for (int i = start; i < len; i++) {
            char c = text.charAt(i);
            if (c == '\u00a7') {
                if (i + 1 >= len) break;
                ChatFormatting f = ChatFormatting.getByCode(text.charAt(i + 1));
                if (f != null) style = f == ChatFormatting.RESET ? resetStyle : style.applyLegacyFormat(f);
                i++;
            } else if (Character.isHighSurrogate(c)) {
                if (i + 1 >= len) {
                    if (!sink.accept(i, style, REPLACEMENT_CHAR)) return false;
                    break;
                }
                char c2 = text.charAt(i + 1);
                if (Character.isLowSurrogate(c2)) {
                    if (!sink.accept(i, style, Character.toCodePoint(c, c2))) return false;
                    i++;
                } else if (!sink.accept(i, style, REPLACEMENT_CHAR)) {
                    return false;
                }
            } else if (!feedChar(style, sink, i, c)) {
                return false;
            }
        }
        return true;
    }

    public static boolean iterateFormatted(net.minecraft.network.chat.FormattedText text, Style style, FormattedCharSink sink) {
        return text.visit((st, s) -> iterateFormatted(s, 0, st, st, sink) ? java.util.Optional.empty() : net.minecraft.network.chat.FormattedText.STOP_ITERATION, style).isEmpty();
    }
}
