// 마인크래프트 프로토콜 와이어 데이터타입(VarInt/String/UUID 등) 읽기·쓰기 유틸
package org.domi.furnace.server.net.io;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import org.domi.furnace.server.net.protocol.ProtocolException;

public final class Types {

    private static final int SEGMENT_BITS = 0x7F;
    private static final int CONTINUE_BIT = 0x80;

    private Types() {}

    public static int readVarInt(ByteBuf buf) {
        int value = 0;
        int position = 0;
        byte current;
        do {
            current = buf.readByte();
            value |= (current & SEGMENT_BITS) << position;
            position += 7;
            if (position > 35) {
                throw new ProtocolException("VarInt too long");
            }
        } while ((current & CONTINUE_BIT) != 0);
        return value;
    }

    public static void writeVarInt(ByteBuf buf, int value) {
        while ((value & ~SEGMENT_BITS) != 0) {
            buf.writeByte((value & SEGMENT_BITS) | CONTINUE_BIT);
            value >>>= 7;
        }
        buf.writeByte(value);
    }

    public static void writeVarLong(ByteBuf buf, long value) {
        while ((value & ~((long) SEGMENT_BITS)) != 0L) {
            buf.writeByte((int) ((value & SEGMENT_BITS) | CONTINUE_BIT));
            value >>>= 7;
        }
        buf.writeByte((int) value);
    }

    public static String readString(ByteBuf buf, int maxLen) {
        int len = readVarInt(buf);
        if (len < 0 || len > maxLen * 4) {
            throw new ProtocolException("String length out of bounds: " + len);
        }
        byte[] bytes = new byte[len];
        buf.readBytes(bytes);
        String s = new String(bytes, StandardCharsets.UTF_8);
        if (s.length() > maxLen) {
            throw new ProtocolException("String longer than max: " + s.length());
        }
        return s;
    }

    public static void writeString(ByteBuf buf, String value) {
        byte[] bytes = value.getBytes(StandardCharsets.UTF_8);
        writeVarInt(buf, bytes.length);
        buf.writeBytes(bytes);
    }

    public static UUID readUuid(ByteBuf buf) {
        return new UUID(buf.readLong(), buf.readLong());
    }

    public static void writeUuid(ByteBuf buf, UUID value) {
        buf.writeLong(value.getMostSignificantBits());
        buf.writeLong(value.getLeastSignificantBits());
    }

    /** 회전 각도를 256분할 byte로. degrees * 256 / 360. */
    public static void writeAngle(ByteBuf buf, float degrees) {
        buf.writeByte((int) (degrees * 256.0f / 360.0f) & 0xFF);
    }

    /** 블록 좌표를 바닐라 Position long으로 패킹. */
    public static void writeBlockPos(ByteBuf buf, int x, int y, int z) {
        buf.writeLong(((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFF));
    }

    /** Position long 언패킹 → {x, y, z}. 각 필드 부호 확장. */
    public static int[] readBlockPos(ByteBuf buf) {
        long v = buf.readLong();
        int x = (int) (v >> 38);
        int y = (int) (v << 52 >> 52);
        int z = (int) (v << 26 >> 38);
        return new int[]{x, y, z};
    }
}
