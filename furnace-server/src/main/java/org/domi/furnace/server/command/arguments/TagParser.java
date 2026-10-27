// 바닐라 TagParser(1.21.1) — 명령 인자용 SNBT 읽기. 오류는 바닐라 키(argument.nbt.*)로 던진다.
// 값 모형은 NbtCompound 와 같다. [B; …] 는 바이트 태그만, [L; …] 는 long 태그만, [I; …] 는 int 태그만 받는다(바닐라와 같다).
package org.domi.furnace.server.command.arguments;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.Dynamic2CommandExceptionType;
import org.domi.furnace.server.command.brigadier.DynamicCommandExceptionType;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtIo;
import org.domi.furnace.server.net.io.nbt.NbtList;
import org.domi.furnace.server.net.io.nbt.NbtTags;

public final class TagParser {

    public static final SimpleCommandExceptionType ERROR_TRAILING_DATA = new SimpleCommandExceptionType(Text.translatable("argument.nbt.trailing"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_KEY = new SimpleCommandExceptionType(Text.translatable("argument.nbt.expected.key"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_VALUE = new SimpleCommandExceptionType(Text.translatable("argument.nbt.expected.value"));
    public static final Dynamic2CommandExceptionType ERROR_INSERT_MIXED_LIST =
            new Dynamic2CommandExceptionType((a, b) -> Text.translatable("argument.nbt.list.mixed", a, b));
    public static final Dynamic2CommandExceptionType ERROR_INSERT_MIXED_ARRAY =
            new Dynamic2CommandExceptionType((a, b) -> Text.translatable("argument.nbt.array.mixed", a, b));
    public static final DynamicCommandExceptionType ERROR_INVALID_ARRAY = new DynamicCommandExceptionType(a -> Text.translatable("argument.nbt.array.invalid", a));

    private static final Pattern DOUBLE_NS = Pattern.compile("[-+]?(?:[0-9]+[.]|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?", Pattern.CASE_INSENSITIVE);
    private static final Pattern DOUBLE = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?d", Pattern.CASE_INSENSITIVE);
    private static final Pattern FLOAT = Pattern.compile("[-+]?(?:[0-9]+[.]?|[0-9]*[.][0-9]+)(?:e[-+]?[0-9]+)?f", Pattern.CASE_INSENSITIVE);
    private static final Pattern BYTE = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)b", Pattern.CASE_INSENSITIVE);
    private static final Pattern LONG = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)l", Pattern.CASE_INSENSITIVE);
    private static final Pattern SHORT = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)s", Pattern.CASE_INSENSITIVE);
    private static final Pattern INT = Pattern.compile("[-+]?(?:0|[1-9][0-9]*)");

    private final StringReader reader;

    public TagParser(StringReader reader) { this.reader = reader; }

    /** TagParser.parseTag: compound 하나, 뒤에 남는 글자가 있으면 오류. */
    public static NbtCompound parseTag(String s) throws CommandSyntaxException {
        TagParser p = new TagParser(new StringReader(s));
        NbtCompound c = p.readStruct();
        p.reader.skipWhitespace();
        if (p.reader.canRead()) throw ERROR_TRAILING_DATA.createWithContext(p.reader);
        return c;
    }

    private String readKey() throws CommandSyntaxException {
        reader.skipWhitespace();
        if (!reader.canRead()) throw ERROR_EXPECTED_KEY.createWithContext(reader);
        return reader.readString();
    }

    private Object readTypedValue() throws CommandSyntaxException {
        reader.skipWhitespace();
        int start = reader.getCursor();
        if (StringReader.isQuotedStringStart(reader.peek())) return reader.readQuotedString();
        String s = reader.readUnquotedString();
        if (s.isEmpty()) {
            reader.setCursor(start);
            throw ERROR_EXPECTED_VALUE.createWithContext(reader);
        }
        return type(s);
    }

    static Object type(String s) {
        try {
            if (FLOAT.matcher(s).matches()) return Float.parseFloat(s.substring(0, s.length() - 1));
            if (BYTE.matcher(s).matches()) return Byte.parseByte(s.substring(0, s.length() - 1));
            if (LONG.matcher(s).matches()) return Long.parseLong(s.substring(0, s.length() - 1));
            if (SHORT.matcher(s).matches()) return Short.parseShort(s.substring(0, s.length() - 1));
            if (INT.matcher(s).matches()) return Integer.parseInt(s);
            if (DOUBLE.matcher(s).matches()) return Double.parseDouble(s.substring(0, s.length() - 1));
            if (DOUBLE_NS.matcher(s).matches()) return Double.parseDouble(s);
            if ("true".equalsIgnoreCase(s)) return (byte) 1;
            if ("false".equalsIgnoreCase(s)) return (byte) 0;
        } catch (NumberFormatException ignored) {
            // 범위를 넘는 수는 문자열
        }
        return s;
    }

    /** readValue: 태그 하나. */
    public Object readValue() throws CommandSyntaxException {
        reader.skipWhitespace();
        if (!reader.canRead()) throw ERROR_EXPECTED_VALUE.createWithContext(reader);
        char c = reader.peek();
        if (c == '{') return readStruct();
        return c == '[' ? readList() : readTypedValue();
    }

    private Object readList() throws CommandSyntaxException {
        return reader.canRead(3) && !StringReader.isQuotedStringStart(reader.peek(1)) && reader.peek(2) == ';' ? readArrayTag() : readListTag();
    }

    public NbtCompound readStruct() throws CommandSyntaxException {
        expect('{');
        NbtCompound c = new NbtCompound();
        reader.skipWhitespace();
        while (reader.canRead() && reader.peek() != '}') {
            int start = reader.getCursor();
            String key = readKey();
            if (key.isEmpty()) {
                reader.setCursor(start);
                throw ERROR_EXPECTED_KEY.createWithContext(reader);
            }
            expect(':');
            c.put(key, readValue());
            if (!hasElementSeparator()) break;
            if (!reader.canRead()) throw ERROR_EXPECTED_KEY.createWithContext(reader);
        }
        expect('}');
        return c;
    }

    private Object readListTag() throws CommandSyntaxException {
        expect('[');
        reader.skipWhitespace();
        if (!reader.canRead()) throw ERROR_EXPECTED_VALUE.createWithContext(reader);
        NbtList list = new NbtList(NbtIo.TAG_END);
        int type = -1;
        while (reader.peek() != ']') {
            int start = reader.getCursor();
            Object v = readValue();
            byte t = NbtTags.typeOf(v);
            if (type < 0) type = t;
            else if (t != type) {
                reader.setCursor(start);
                throw ERROR_INSERT_MIXED_LIST.createWithContext(reader, NbtTags.prettyName(t), NbtTags.prettyName((byte) type));
            }
            list.addTag(list.size(), v, t);
            if (!hasElementSeparator()) break;
            if (!reader.canRead()) throw ERROR_EXPECTED_VALUE.createWithContext(reader);
        }
        expect(']');
        return list;
    }

    private Object readArrayTag() throws CommandSyntaxException {
        expect('[');
        int start = reader.getCursor();
        char t = reader.read();
        reader.read();
        reader.skipWhitespace();
        if (!reader.canRead()) throw ERROR_EXPECTED_VALUE.createWithContext(reader);
        if (t == 'B') {
            List<Number> l = readArray(NbtIo.TAG_BYTE_ARRAY, NbtIo.TAG_BYTE);
            byte[] a = new byte[l.size()];
            for (int i = 0; i < a.length; i++) a[i] = l.get(i).byteValue();
            return a;
        } else if (t == 'L') {
            List<Number> l = readArray(NbtIo.TAG_LONG_ARRAY, NbtIo.TAG_LONG);
            long[] a = new long[l.size()];
            for (int i = 0; i < a.length; i++) a[i] = l.get(i).longValue();
            return a;
        } else if (t == 'I') {
            List<Number> l = readArray(NbtIo.TAG_INT_ARRAY, NbtIo.TAG_INT);
            int[] a = new int[l.size()];
            for (int i = 0; i < a.length; i++) a[i] = l.get(i).intValue();
            return a;
        }
        reader.setCursor(start);
        throw ERROR_INVALID_ARRAY.createWithContext(reader, String.valueOf(t));
    }

    private List<Number> readArray(byte arrayType, byte elementType) throws CommandSyntaxException {
        List<Number> out = new ArrayList<>();
        while (reader.peek() != ']') {
            int start = reader.getCursor();
            Object v = readValue();
            byte t = NbtTags.typeOf(v);
            if (t != elementType) {
                reader.setCursor(start);
                throw ERROR_INSERT_MIXED_ARRAY.createWithContext(reader, NbtTags.prettyName(t), NbtTags.prettyName(arrayType));
            }
            out.add((Number) v);
            if (!hasElementSeparator()) break;
            if (!reader.canRead()) throw ERROR_EXPECTED_VALUE.createWithContext(reader);
        }
        expect(']');
        return out;
    }

    private boolean hasElementSeparator() {
        reader.skipWhitespace();
        if (reader.canRead() && reader.peek() == ',') {
            reader.skip();
            reader.skipWhitespace();
            return true;
        }
        return false;
    }

    private void expect(char c) throws CommandSyntaxException {
        reader.skipWhitespace();
        reader.expect(c);
    }
}
