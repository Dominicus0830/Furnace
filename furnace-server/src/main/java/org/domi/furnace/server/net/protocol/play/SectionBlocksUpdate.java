// 한 구역(16³)의 여러 블록 변경(clientbound section_blocks_update). 구역 좌표 long + VarInt 개수 + VarLong(상태 << 12 | 칸).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SectionBlocksUpdate(int sectionX, int sectionY, int sectionZ, short[] positions, int[] states) implements Packet {

    public static final int ID = 0x49;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        // SectionPos.asLong: x 22비트 << 42 | z 22비트 << 20 | y 20비트
        buf.writeLong(((long) sectionX & 0x3FFFFF) << 42 | ((long) sectionZ & 0x3FFFFF) << 20 | (long) sectionY & 0xFFFFF);
        Types.writeVarInt(buf, positions.length);
        for (int i = 0; i < positions.length; i++) Types.writeVarLong(buf, (long) states[i] << 12 | positions[i]);
    }
}
