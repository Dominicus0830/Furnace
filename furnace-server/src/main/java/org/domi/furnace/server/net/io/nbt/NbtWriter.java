// 네트워크 NBT(1.20.2+, 루트 무명) write. heightmap 전송용 최소 태그 + 범용 compound write(블록엔티티 tag).
package org.domi.furnace.server.net.io.nbt;

import io.netty.buffer.ByteBuf;
import java.nio.charset.StandardCharsets;

public final class NbtWriter {

    private static final byte TAG_END = 0x00;
    private static final byte TAG_BYTE = 0x01;
    private static final byte TAG_SHORT = 0x02;
    private static final byte TAG_INT = 0x03;
    private static final byte TAG_LONG = 0x04;
    private static final byte TAG_FLOAT = 0x05;
    private static final byte TAG_DOUBLE = 0x06;
    private static final byte TAG_BYTE_ARRAY = 0x07;
    private static final byte TAG_STRING = 0x08;
    private static final byte TAG_LIST = 0x09;
    private static final byte TAG_COMPOUND = 0x0a;
    private static final byte TAG_INT_ARRAY = 0x0b;
    private static final byte TAG_LONG_ARRAY = 0x0c;

    private final ByteBuf buf;

    public NbtWriter(ByteBuf buf) {
        this.buf = buf;
    }

    /** 루트 compound 시작(이름 없음). */
    public void beginRootCompound() {
        buf.writeByte(TAG_COMPOUND);
    }

    public void writeLongArray(String name, long[] values) {
        buf.writeByte(TAG_LONG_ARRAY);
        writeName(name);
        writeLongArrayPayload(values);
    }

    public void endCompound() {
        buf.writeByte(TAG_END);
    }

    /**
     * 무명 루트 compound 전체 write(타입바이트+엔트리+TAG_END, NbtIo.writePayload와 동형 로직을 네트워크
     * 프레이밍으로 이식). 빈 태그를 TAG_END 1바이트로 취급하는 규약(§2.1)은 호출부 책임.
     */
    public void writeCompound(NbtCompound compound) {
        beginRootCompound();
        writeCompoundEntries(compound);
        endCompound();
    }

    private void writeCompoundEntries(NbtCompound compound) {
        for (var e : compound.tags().entrySet()) {
            Object v = e.getValue();
            byte type = typeOf(v);
            buf.writeByte(type);
            writeName(e.getKey());
            writePayload(type, v);
        }
    }

    private void writePayload(byte type, Object v) {
        switch (type) {
            case TAG_BYTE -> buf.writeByte((Byte) v);
            case TAG_SHORT -> buf.writeShort((Short) v);
            case TAG_INT -> buf.writeInt((Integer) v);
            case TAG_LONG -> buf.writeLong((Long) v);
            case TAG_FLOAT -> buf.writeFloat((Float) v);
            case TAG_DOUBLE -> buf.writeDouble((Double) v);
            case TAG_BYTE_ARRAY -> { byte[] a = (byte[]) v; buf.writeInt(a.length); buf.writeBytes(a); }
            case TAG_STRING -> writeName((String) v);
            case TAG_INT_ARRAY -> { int[] a = (int[]) v; buf.writeInt(a.length); for (int x : a) buf.writeInt(x); }
            case TAG_LONG_ARRAY -> writeLongArrayPayload((long[]) v);
            case TAG_LIST -> {
                NbtList l = (NbtList) v;
                buf.writeByte(l.size() == 0 ? TAG_END : l.elementType());
                buf.writeInt(l.size());
                for (Object item : l.items()) writePayload(l.elementType(), item);
            }
            case TAG_COMPOUND -> { writeCompoundEntries((NbtCompound) v); buf.writeByte(TAG_END); }
            default -> throw new IllegalArgumentException("미지원 태그: " + type);
        }
    }

    private void writeLongArrayPayload(long[] values) {
        buf.writeInt(values.length);
        for (long v : values) buf.writeLong(v);
    }

    private static byte typeOf(Object v) {
        if (v instanceof Byte) return TAG_BYTE;
        if (v instanceof Short) return TAG_SHORT;
        if (v instanceof Integer) return TAG_INT;
        if (v instanceof Long) return TAG_LONG;
        if (v instanceof Float) return TAG_FLOAT;
        if (v instanceof Double) return TAG_DOUBLE;
        if (v instanceof byte[]) return TAG_BYTE_ARRAY;
        if (v instanceof String) return TAG_STRING;
        if (v instanceof int[]) return TAG_INT_ARRAY;
        if (v instanceof long[]) return TAG_LONG_ARRAY;
        if (v instanceof NbtList) return TAG_LIST;
        if (v instanceof NbtCompound) return TAG_COMPOUND;
        throw new IllegalArgumentException("미지원 값: " + v.getClass());
    }

    private void writeName(String name) {
        byte[] bytes = name.getBytes(StandardCharsets.UTF_8);
        buf.writeShort(bytes.length);
        buf.writeBytes(bytes);
    }
}
