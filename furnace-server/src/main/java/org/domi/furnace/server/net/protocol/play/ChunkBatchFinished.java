// S→C 청크 배치 종료. 배치에 담긴 청크 수를 알려 클라가 처리율을 추정하게 한다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundChunkBatchFinishedPacket")
public record ChunkBatchFinished(int batchSize) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_CHUNK_BATCH_FINISHED;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, batchSize);
    }
}
