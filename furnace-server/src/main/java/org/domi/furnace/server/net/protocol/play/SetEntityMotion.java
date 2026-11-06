// S→C 엔티티 속도(set_entity_motion 0x5A): 축마다 ±3.9 로 자른 값 × 8000 을 short 로.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetEntityMotion(int entityId, double vx, double vy, double vz) implements Packet {

    public static final int ID = 0x5A;

    @Override
    public int id() { return ID; }

    /** (int)(clamp(v, -3.9, 3.9) * 8000). */
    public static int encode(double v) { return (int) (Math.clamp(v, -3.9, 3.9) * 8000.0); }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeShort(encode(vx));
        buf.writeShort(encode(vy));
        buf.writeShort(encode(vz));
    }
}
