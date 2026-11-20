package net.minecraft.network;

import io.netty.buffer.ByteBuf;

/**
 * 바닐라 VarInt 겉모양(NMS shim 5e: voxy 가 먼 지형 묶음을 쓴다). 구현은 Furnace 가 새로 썼고, 바닐라 규격만 맞춘다:
 * 7비트씩 낮은 쪽부터, 이어짐 비트 0x80, 최대 5바이트, 6번째 바이트를 읽으면 "VarInt too big". 공개 메서드 이름·모양은 Paper 1.21.1(javap)과 같다
 * ({@code getByteSizeOld}·{@code writeOld} 는 Paper 가 남긴 옛 이름이라 같은 결과를 낸다).
 */
public class VarInt {

    public VarInt() {}

    /** 쓸 때 바이트 수(1~5): 남는 유효 비트를 7로 나눈 칸 수. */
    public static int getByteSize(int value) { return (31 - Integer.numberOfLeadingZeros(value | 1)) / 7 + 1; }

    public static int getByteSizeOld(int value) { return getByteSize(value); }

    public static boolean hasContinuationBit(byte b) { return (b & 0x80) != 0; }

    public static int read(ByteBuf buf) {
        int value = 0;
        for (int shift = 0; ; shift += 7) {
            if (shift == 35) {   // 다섯 바이트 뒤에도 이어짐 — 바닐라처럼 한 바이트를 더 읽고 거절한다
                buf.readByte();
                throw new RuntimeException("VarInt too big");
            }
            byte b = buf.readByte();
            value |= (b & 0x7F) << shift;
            if (!hasContinuationBit(b)) return value;
        }
    }

    public static ByteBuf write(ByteBuf buf, int value) {
        for (int rest = value; ; rest >>>= 7) {
            if ((rest & ~0x7F) == 0) {
                buf.writeByte(rest);
                return buf;
            }
            buf.writeByte(rest & 0x7F | 0x80);
        }
    }

    public static ByteBuf writeOld(ByteBuf buf, int value) { return write(buf, value); }
}
