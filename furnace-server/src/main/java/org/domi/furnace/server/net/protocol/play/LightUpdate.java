// S→C 빛 갱신(0x2A, 1.21.1): VarInt x, VarInt z, BitSet 하늘·블록·빈하늘·빈블록, 하늘 배열[], 블록 배열[](각 VarInt 2048 + 바이트)
// 바닐라 ClientboundLightUpdatePacket(+LightUpdatePacketData): 바뀐 빛 섹션만 담는다. 값이 모두 0 인 섹션은 빈 마스크(전체 청크 직렬화와 같은 규칙).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.light.ChunkLight;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record LightUpdate(int chunkX, int chunkZ, long skyMask, long blockMask, long emptySkyMask, long emptyBlockMask,
                          byte[][] skyData, byte[][] blockData) implements Packet {

    public static final int ID = 0x2A;

    /**
     * 빛 섹션 i(0 = 맨 아래 빛 섹션)가 바뀐 마스크로 패킷을 만든다. lightBase 는 ChunkLight 배열에서 빛 섹션 0 의 첨자.
     * 데이터는 복사한다(패킷은 다른 스레드에서 쓰인다).
     */
    public static LightUpdate of(int cx, int cz, ChunkLight light, long changedSky, long changedBlock, int lightBase, int count) {
        long sm = 0, bm = 0, es = 0, eb = 0;
        java.util.List<byte[]> sd = new java.util.ArrayList<>(), bd = new java.util.ArrayList<>();
        for (int i = 0; i < count; i++) {
            int a = lightBase + i;
            if (a < 0 || a >= ChunkLight.SECTIONS) continue;
            if ((changedSky & 1L << i) != 0) {
                if (isZero(light.sky(a))) es |= 1L << i;
                else { sm |= 1L << i; sd.add(light.sky(a).clone()); }
            }
            if ((changedBlock & 1L << i) != 0) {
                if (isZero(light.block(a))) eb |= 1L << i;
                else { bm |= 1L << i; bd.add(light.block(a).clone()); }
            }
        }
        return new LightUpdate(cx, cz, sm, bm, es, eb, sd.toArray(new byte[0][]), bd.toArray(new byte[0][]));
    }

    private static boolean isZero(byte[] a) {
        if (ChunkLight.isKnownZero(a)) return true;
        if (ChunkLight.isKnownFull(a)) return false;
        for (byte b : a) if (b != 0) return false;
        return true;
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, chunkX);
        Types.writeVarInt(buf, chunkZ);
        writeBitSet(buf, skyMask);
        writeBitSet(buf, blockMask);
        writeBitSet(buf, emptySkyMask);
        writeBitSet(buf, emptyBlockMask);
        writeArrays(buf, skyData);
        writeArrays(buf, blockData);
    }

    /** FriendlyByteBuf.writeBitSet: toLongArray(뒤쪽 0 long 은 빠진다 — 빈 집합은 길이 0). */
    private static void writeBitSet(ByteBuf buf, long mask) {
        if (mask == 0) {
            Types.writeVarInt(buf, 0);
        } else {
            Types.writeVarInt(buf, 1);
            buf.writeLong(mask);
        }
    }

    private static void writeArrays(ByteBuf buf, byte[][] arrays) {
        Types.writeVarInt(buf, arrays.length);
        for (byte[] a : arrays) {
            Types.writeVarInt(buf, a.length);
            buf.writeBytes(a);
        }
    }
}
