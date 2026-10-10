// 바닐라 MinMaxBounds(Doubles·Ints) — "1", "1..", "..5", "1..5" 형식 범위.
package org.domi.furnace.server.command.arguments;

import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.api.text.Text;

public record MinMaxBounds(Double min, Double max) {

    public static final SimpleCommandExceptionType ERROR_EMPTY = new SimpleCommandExceptionType(Text.translatable("argument.range.empty"));
    public static final SimpleCommandExceptionType ERROR_SWAPPED = new SimpleCommandExceptionType(Text.translatable("argument.range.swapped"));
    public static final SimpleCommandExceptionType ERROR_INTS_ONLY = new SimpleCommandExceptionType(Text.translatable("argument.range.ints"));
    public static final MinMaxBounds ANY = new MinMaxBounds(null, null);

    public boolean isAny() { return min == null && max == null; }

    public boolean matches(double v) { return (min == null || min <= v) && (max == null || v <= max); }

    public boolean matchesSqr(double sq) {
        return (min == null || min * min <= sq) && (max == null || sq <= max * max);
    }

    public static MinMaxBounds fromReader(StringReader reader, boolean intsOnly) throws CommandSyntaxException {
        if (!reader.canRead()) throw ERROR_EMPTY.createWithContext(reader);
        int start = reader.getCursor();
        try {
            Double min = readNumber(reader, intsOnly);
            Double max;
            if (reader.canRead(2) && reader.peek() == '.' && reader.peek(1) == '.') {
                reader.skip();
                reader.skip();
                max = readNumber(reader, intsOnly);
                if (min == null && max == null) throw ERROR_EMPTY.createWithContext(reader);
            } else {
                max = min;
            }
            if (min == null && max == null) throw ERROR_EMPTY.createWithContext(reader);
            if (min != null && max != null && min > max) throw ERROR_SWAPPED.createWithContext(reader);
            return new MinMaxBounds(min, max);
        } catch (CommandSyntaxException e) {
            reader.setCursor(start);
            throw new CommandSyntaxException(e.getType(), e.getRawMessage(), e.getInput(), start);
        }
    }

    private static Double readNumber(StringReader reader, boolean intsOnly) throws CommandSyntaxException {
        int start = reader.getCursor();
        while (reader.canRead() && isAllowedInputChar(reader)) reader.skip();
        String s = reader.getString().substring(start, reader.getCursor());
        if (s.isEmpty()) return null;
        try {
            if (intsOnly) return (double) Integer.parseInt(s);
            return Double.parseDouble(s);
        } catch (NumberFormatException e) {
            if (intsOnly) throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidInt().createWithContext(reader, s);
            throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.readerInvalidDouble().createWithContext(reader, s);
        }
    }

    private static boolean isAllowedInputChar(StringReader reader) {
        char c = reader.peek();
        if (c >= '0' && c <= '9' || c == '-') return true;
        if (c != '.') return false;
        return !reader.canRead(2) || reader.peek(1) != '.';
    }
}
