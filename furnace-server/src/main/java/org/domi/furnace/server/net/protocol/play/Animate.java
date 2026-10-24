// 엔티티 애니메이션(clientbound animate 0x03): 엔티티, 동작(0 주 손 휘두르기, 2 침대에서 깨기, 3 보조 손, 4 치명타, 5 마법 치명타).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record Animate(int entityId, int action) implements Packet {

    public static final int ID = 0x03;
    public static final int SWING_MAIN_HAND = 0, WAKE_UP = 2, SWING_OFF_HAND = 3, CRITICAL_HIT = 4, MAGIC_CRITICAL_HIT = 5;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        buf.writeByte(action);
    }
}
