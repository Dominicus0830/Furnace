package net.minecraft.network;

import io.netty.buffer.ByteBuf;

/**
 * 바닐라 VarInt(Paper 1.21.1 판 — 길이 표와 한·두 바이트 빠른 쓰기 포함, NMS shim 5e: voxy 가 먼 지형 묶음을 쓴다). 바이트 결과는 바닐라와 같다
 * (Paper jar 를 javap 로 대조).
 */
public class VarInt {

    private static final int MAX_VARINT_SIZE = 5;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;
    private static final int[] VARINT_EXACT_BYTE_LENGTHS = new int[33];

    static {
        for (int i = 0; i <= 32; i++) VARINT_EXACT_BYTE_LENGTHS[i] = (int) Math.ceil((31d - (i - 1)) / 7d);
        VARINT_EXACT_BYTE_LENGTHS[32] = 1;
    }

    public VarInt() {}

    /** 쓸 때 바이트 수(1~5). */
    public static int getByteSize(int value) { return VARINT_EXACT_BYTE_LENGTHS[Integer.numberOfLeadingZeros(value)]; }

    /** 바닐라 원래 셈(Paper 가 남겨 둔 것). */
    public static int getByteSizeOld(int value) {
        for (int i = 1; i < MAX_VARINT_SIZE; i++) {
            if ((value & -1 << i * DATA_BITS_PER_BYTE) == 0) return i;
        }
        return MAX_VARINT_SIZE;
    }

    public static boolean hasContinuationBit(byte b) { return (b & CONTINUATION_BIT_MASK) == CONTINUATION_BIT_MASK; }

    public static int read(ByteBuf buf) {
        int value = 0, size = 0;
        byte b;
        do {
            b = buf.readByte();
            value |= (b & DATA_BITS_MASK) << size++ * DATA_BITS_PER_BYTE;
            if (size > MAX_VARINT_SIZE) throw new RuntimeException("VarInt too big");
        } while (hasContinuationBit(b));
        return value;
    }

    public static ByteBuf write(ByteBuf buf, int value) {
        if ((value & -128) == 0) {
            buf.writeByte(value);
        } else if ((value & -16384) == 0) {
            buf.writeShort((value & DATA_BITS_MASK | CONTINUATION_BIT_MASK) << 8 | value >>> 7);
        } else {
            writeOld(buf, value);
        }
        return buf;
    }

    public static ByteBuf writeOld(ByteBuf buf, int value) {
        while ((value & -128) != 0) {
            buf.writeByte(value & DATA_BITS_MASK | CONTINUATION_BIT_MASK);
            value >>>= 7;
        }
        buf.writeByte(value);
        return buf;
    }
}
