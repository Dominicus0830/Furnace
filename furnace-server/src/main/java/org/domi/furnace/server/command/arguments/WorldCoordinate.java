// 바닐라 WorldCoordinate — 좌표 한 칸: 절대값 또는 '~' 상대값(실행 위치 기준 오프셋).
package org.domi.furnace.server.command.arguments;

import org.domi.furnace.server.command.brigadier.CommandSyntaxException;
import org.domi.furnace.server.command.brigadier.SimpleCommandExceptionType;
import org.domi.furnace.server.command.brigadier.StringReader;
import org.domi.furnace.server.net.protocol.Text;

public record WorldCoordinate(boolean relative, double value) {

    public static final SimpleCommandExceptionType ERROR_EXPECTED_DOUBLE = new SimpleCommandExceptionType(Text.translatable("argument.pos.missing.double"));
    public static final SimpleCommandExceptionType ERROR_EXPECTED_INT = new SimpleCommandExceptionType(Text.translatable("argument.pos.missing.int"));
    public static final SimpleCommandExceptionType ERROR_MIXED_TYPE = new SimpleCommandExceptionType(Text.translatable("argument.pos.mixed"));

    public double get(double origin) { return relative ? value + origin : value; }

    /** 실수 좌표. centerCorrect 면 소수점 없는 절대값에 0.5 를 더한다(블록 중앙). '^'(로컬 좌표)는 섞을 수 없다. */
    public static WorldCoordinate parseDouble(StringReader reader, boolean centerCorrect) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') throw ERROR_MIXED_TYPE.createWithContext(reader);
        if (!reader.canRead()) throw ERROR_EXPECTED_DOUBLE.createWithContext(reader);
        boolean relative = isRelative(reader);
        int start = reader.getCursor();
        double v = reader.canRead() && reader.peek() != ' ' ? reader.readDouble() : 0.0;
        String text = reader.getString().substring(start, reader.getCursor());
        if (relative && text.isEmpty()) return new WorldCoordinate(true, 0.0);
        if (!text.contains(".") && !relative && centerCorrect) v += 0.5;
        return new WorldCoordinate(relative, v);
    }

    /** 정수(블록) 좌표. 상대값이면 실수도 허용한다. */
    public static WorldCoordinate parseInt(StringReader reader) throws CommandSyntaxException {
        if (reader.canRead() && reader.peek() == '^') throw ERROR_MIXED_TYPE.createWithContext(reader);
        if (!reader.canRead()) throw ERROR_EXPECTED_INT.createWithContext(reader);
        boolean relative = isRelative(reader);
        double v;
        if (reader.canRead() && reader.peek() != ' ') v = relative ? reader.readDouble() : (double) reader.readInt();
        else v = 0.0;
        return new WorldCoordinate(relative, v);
    }

    public static boolean isRelative(StringReader reader) {
        if (reader.peek() == '~') {
            reader.skip();
            return true;
        }
        return false;
    }
}
