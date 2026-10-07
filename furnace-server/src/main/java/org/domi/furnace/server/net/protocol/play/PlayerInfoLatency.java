// S→C 플레이어 정보 갱신 중 update_latency만(바닐라가 600틱마다 전원에게 보내는 핑 갱신).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.UUID;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record PlayerInfoLatency(List<Entry> entries) implements Packet {

    private static final int ACTIONS = 0x10; // update_latency

    public record Entry(UUID uuid, int latencyMs) {}

    @Override
    public int id() {
        return PlayerInfoUpdate.ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeByte(ACTIONS);
        Types.writeVarInt(buf, entries.size());
        for (Entry e : entries) {
            Types.writeUuid(buf, e.uuid());
            Types.writeVarInt(buf, e.latencyMs());
        }
    }
}
