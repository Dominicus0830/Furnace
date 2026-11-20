package net.minecraft.network;

import io.netty.buffer.ByteBuf;
import io.netty.handler.codec.DecoderException;
import io.netty.handler.codec.EncoderException;
import java.nio.charset.StandardCharsets;

/**
 * 프로토콜 문자열(가변 정수 바이트 길이 + UTF-8) 읽기·쓰기(Mojang 이름, 바닐라 1.21.1 모양). {@link FriendlyByteBuf#readUtf(int)}·
 * {@code writeUtf} 가 이것을 부르는 것도 바닐라와 같다. 길이 상한은 문자 수(UTF-16 코드 유닛), 바이트 상한은 그 3배.
 */
public class Utf8String {

    public Utf8String() {}

    public static String read(ByteBuf buf, int maxLength) {
        int maxBytes = maxLength * 3;   // ByteBufUtil.utf8MaxBytes(int)
        int bytes = readVarInt(buf);
        if (bytes > maxBytes) throw new DecoderException("The received encoded string buffer length is longer than maximum allowed (" + bytes + " > " + maxBytes + ")");
        if (bytes < 0) throw new DecoderException("The received encoded string buffer length is less than zero! Weird string!");
        int readable = buf.readableBytes();
        if (bytes > readable) throw new DecoderException("Not enough bytes in buffer, expected " + bytes + ", but got " + readable);
        String s = buf.toString(buf.readerIndex(), bytes, StandardCharsets.UTF_8);
        buf.readerIndex(buf.readerIndex() + bytes);
        if (s.length() > maxLength) throw new DecoderException("The received string length is longer than maximum allowed (" + s.length() + " > " + maxLength + ")");
        return s;
    }

    public static void write(ByteBuf buf, CharSequence value, int maxLength) {
        if (value.length() > maxLength) throw new EncoderException("String too big (was " + value.length() + " characters, max " + maxLength + ")");
        // 바닐라는 ByteBufUtil.writeUtf8 로 임시 버퍼에 쓴다 — 짝 없는 대리 문자를 '?' 로 바꾸는 것은 String#getBytes(UTF_8) 와 같다
        byte[] bytes = value.toString().getBytes(StandardCharsets.UTF_8);
        int maxBytes = maxLength * 3;
        if (bytes.length > maxBytes) throw new EncoderException("String too big (was " + bytes.length + " bytes encoded, max " + maxBytes + ")");
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    // 바닐라 VarInt.read·write 와 같은 바이트(감싼 버퍼를 새로 만들지 않으려고 여기 둔다)
    private static int readVarInt(ByteBuf buf) {
        int result = 0;
        for (int shift = 0; shift < 35; shift += 7) {
            byte b = buf.readByte();
            result |= (b & 0x7F) << shift;
            if ((b & 0x80) != 128) return result;
        }
        throw new RuntimeException("VarInt too big");
    }

    private static void writeVarInt(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & 0x7F | 0x80);
            value >>>= 7;
        }
        buf.writeByte(value);
    }
}
