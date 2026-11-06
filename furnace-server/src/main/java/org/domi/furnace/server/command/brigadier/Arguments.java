// Brigadier 기본 인자 타입: bool, integer, long, float, double, string(단어·따옴표 구·나머지 전부).
// 네트워크 속성 형식은 바닐라 ArgumentTypeInfos(숫자: 플래그 바이트 0x01 최소·0x02 최대 + 값, 문자열: 종류 varint).
package org.domi.furnace.server.command.brigadier;

import io.netty.buffer.ByteBuf;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import org.domi.furnace.server.net.io.Types;

public final class Arguments {

    private Arguments() {}

    public static BoolArgumentType bool() { return new BoolArgumentType(); }

    public static IntegerArgumentType integer() { return new IntegerArgumentType(Integer.MIN_VALUE, Integer.MAX_VALUE); }

    public static IntegerArgumentType integer(int min) { return new IntegerArgumentType(min, Integer.MAX_VALUE); }

    public static IntegerArgumentType integer(int min, int max) { return new IntegerArgumentType(min, max); }

    public static LongArgumentType longArg(long min, long max) { return new LongArgumentType(min, max); }

    public static FloatArgumentType floatArg(float min, float max) { return new FloatArgumentType(min, max); }

    public static DoubleArgumentType doubleArg(double min, double max) { return new DoubleArgumentType(min, max); }

    public static StringArgumentType word() { return new StringArgumentType(StringArgumentType.Kind.SINGLE_WORD); }

    public static StringArgumentType string() { return new StringArgumentType(StringArgumentType.Kind.QUOTABLE_PHRASE); }

    public static StringArgumentType greedyString() { return new StringArgumentType(StringArgumentType.Kind.GREEDY_PHRASE); }

    public static final class BoolArgumentType implements ArgumentType<Boolean> {
        @Override
        public Boolean parse(StringReader reader) throws CommandSyntaxException { return reader.readBoolean(); }

        @Override
        public <S> CompletableFuture<Suggestions> listSuggestions(CommandContext<S> context, SuggestionsBuilder builder) {
            if ("true".startsWith(builder.getRemainingLowerCase())) builder.suggest("true");
            if ("false".startsWith(builder.getRemainingLowerCase())) builder.suggest("false");
            return builder.buildFuture();
        }

        @Override
        public Collection<String> getExamples() { return List.of("true", "false"); }

        @Override
        public String networkId() { return "brigadier:bool"; }
    }

    public record IntegerArgumentType(int minimum, int maximum) implements ArgumentType<Integer> {
        @Override
        public Integer parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            int result = reader.readInt();
            if (result < minimum) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooLow().createWithContext(reader, result, minimum);
            }
            if (result > maximum) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.integerTooHigh().createWithContext(reader, result, maximum);
            }
            return result;
        }

        @Override
        public Collection<String> getExamples() { return List.of("0", "123", "-123"); }

        @Override
        public String networkId() { return "brigadier:integer"; }

        @Override
        public void writeProperties(ByteBuf buf) {
            boolean hasMin = minimum != Integer.MIN_VALUE, hasMax = maximum != Integer.MAX_VALUE;
            buf.writeByte((hasMin ? 1 : 0) | (hasMax ? 2 : 0));
            if (hasMin) buf.writeInt(minimum);
            if (hasMax) buf.writeInt(maximum);
        }
    }

    public record LongArgumentType(long minimum, long maximum) implements ArgumentType<Long> {
        @Override
        public Long parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            long result = reader.readLong();
            if (result < minimum) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.longTooLow().createWithContext(reader, result, minimum);
            }
            if (result > maximum) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.longTooHigh().createWithContext(reader, result, maximum);
            }
            return result;
        }

        @Override
        public Collection<String> getExamples() { return List.of("0", "123", "-123"); }

        @Override
        public String networkId() { return "brigadier:long"; }

        @Override
        public void writeProperties(ByteBuf buf) {
            boolean hasMin = minimum != Long.MIN_VALUE, hasMax = maximum != Long.MAX_VALUE;
            buf.writeByte((hasMin ? 1 : 0) | (hasMax ? 2 : 0));
            if (hasMin) buf.writeLong(minimum);
            if (hasMax) buf.writeLong(maximum);
        }
    }

    public record FloatArgumentType(float minimum, float maximum) implements ArgumentType<Float> {
        @Override
        public Float parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            float result = reader.readFloat();
            if (result < minimum) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.floatTooLow().createWithContext(reader, result, minimum);
            }
            if (result > maximum) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.floatTooHigh().createWithContext(reader, result, maximum);
            }
            return result;
        }

        @Override
        public Collection<String> getExamples() { return List.of("0", "1.2", ".5", "-1", "-.5", "-1234.56"); }

        @Override
        public String networkId() { return "brigadier:float"; }

        @Override
        public void writeProperties(ByteBuf buf) {
            boolean hasMin = minimum != -Float.MAX_VALUE, hasMax = maximum != Float.MAX_VALUE;
            buf.writeByte((hasMin ? 1 : 0) | (hasMax ? 2 : 0));
            if (hasMin) buf.writeFloat(minimum);
            if (hasMax) buf.writeFloat(maximum);
        }
    }

    public record DoubleArgumentType(double minimum, double maximum) implements ArgumentType<Double> {
        @Override
        public Double parse(StringReader reader) throws CommandSyntaxException {
            int start = reader.getCursor();
            double result = reader.readDouble();
            if (result < minimum) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.doubleTooLow().createWithContext(reader, result, minimum);
            }
            if (result > maximum) {
                reader.setCursor(start);
                throw CommandSyntaxException.BUILT_IN_EXCEPTIONS.doubleTooHigh().createWithContext(reader, result, maximum);
            }
            return result;
        }

        @Override
        public Collection<String> getExamples() { return List.of("0", "1.2", ".5", "-1", "-.5", "-1234.56"); }

        @Override
        public String networkId() { return "brigadier:double"; }

        @Override
        public void writeProperties(ByteBuf buf) {
            boolean hasMin = minimum != -Double.MAX_VALUE, hasMax = maximum != Double.MAX_VALUE;
            buf.writeByte((hasMin ? 1 : 0) | (hasMax ? 2 : 0));
            if (hasMin) buf.writeDouble(minimum);
            if (hasMax) buf.writeDouble(maximum);
        }
    }

    public record StringArgumentType(Kind kind) implements ArgumentType<String> {
        public enum Kind { SINGLE_WORD, QUOTABLE_PHRASE, GREEDY_PHRASE }

        @Override
        public String parse(StringReader reader) throws CommandSyntaxException {
            if (kind == Kind.GREEDY_PHRASE) {
                String text = reader.getRemaining();
                reader.setCursor(reader.getTotalLength());
                return text;
            }
            return kind == Kind.SINGLE_WORD ? reader.readUnquotedString() : reader.readString();
        }

        @Override
        public Collection<String> getExamples() {
            return switch (kind) {
                case SINGLE_WORD -> List.of("word", "words_with_underscores");
                case QUOTABLE_PHRASE -> List.of("\"quoted phrase\"", "word", "\"\"");
                case GREEDY_PHRASE -> List.of("word", "words with spaces", "\"and symbols\"");
            };
        }

        @Override
        public String networkId() { return "brigadier:string"; }

        @Override
        public void writeProperties(ByteBuf buf) { Types.writeVarInt(buf, kind.ordinal()); }
    }
}
