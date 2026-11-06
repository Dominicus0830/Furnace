// S→C chunks_biomes(0x0E): 청크의 바이옴만 다시 보낸다(/fillbiome). 항목 = [ChunkPos long][VarInt 길이][섹션마다 바이옴 컨테이너].
// 컨테이너 모양은 청크 패킷의 바이옴 부분과 같다(ChunkSerializer).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.Unpooled;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.world.Palette;

public record ChunksBiomes(List<Entry> chunks, int biomeDirectBits) implements Packet {

    public static final int ID = 0x0E;

    /** 한 청크: 위치와 섹션별 바이옴 id 64개(quart 4×4×4). */
    public record Entry(int chunkX, int chunkZ, int[][] sectionBiomes) { }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, chunks.size());
        for (Entry e : chunks) {
            buf.writeLong(((long) e.chunkX() & 0xFFFFFFFFL) | (((long) e.chunkZ() & 0xFFFFFFFFL) << 32));
            ByteBuf data = Unpooled.buffer();
            for (int[] biomes : e.sectionBiomes()) Palette.write(data, biomes, biomeDirectBits, 1, 3);
            Types.writeVarInt(buf, data.readableBytes());
            buf.writeBytes(data);
            data.release();
        }
    }
}
