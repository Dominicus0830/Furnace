package net.minecraft.network;

import io.netty.buffer.ByteBuf;

/** 가변 길이 long(바닐라 VarLong 과 같은 바이트 형식). 한 값은 7비트씩 최대 10바이트. */
public class VarLong {
    private static final int MAX_VARLONG_SIZE = 10;
    private static final int DATA_BITS_MASK = 127;
    private static final int CONTINUATION_BIT_MASK = 128;
    private static final int DATA_BITS_PER_BYTE = 7;

    public static int getByteSize(long value) {
        for (int i = 1; i < MAX_VARLONG_SIZE; i++) {
            if ((value & -1L << i * DATA_BITS_PER_BYTE) == 0L) return i;
        }
        return MAX_VARLONG_SIZE;
    }

    public static boolean hasContinuationBit(byte b) { return (b & CONTINUATION_BIT_MASK) == CONTINUATION_BIT_MASK; }

    public static long read(ByteBuf buf) {
        long result = 0L;
        int i = 0;
        byte b;
        do {
            b = buf.readByte();
            result |= (long) (b & DATA_BITS_MASK) << i++ * DATA_BITS_PER_BYTE;
            if (i > MAX_VARLONG_SIZE) throw new RuntimeException("VarLong too big");
        } while (hasContinuationBit(b));
        return result;
    }

    public static ByteBuf write(ByteBuf buf, long value) {
        while ((value & -128L) != 0L) {
            buf.writeByte((int) (value & DATA_BITS_MASK) | CONTINUATION_BIT_MASK);
            value >>>= DATA_BITS_PER_BYTE;
        }
        buf.writeByte((int) value);
        return buf;
    }
}
