// S→C 바라보게 하기(0x3F): VarInt 앵커(0 발, 1 눈), double x,y,z, boolean 엔티티 있음, [VarInt 엔티티 번호, VarInt 엔티티 앵커].
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/** @param entityId 엔티티를 보게 하면 그 번호, 아니면 -1 */
public record PlayerLookAt(int anchor, double x, double y, double z, int entityId, int entityAnchor) implements Packet {

    public static final int ID = 0x3F;

    public static PlayerLookAt atPosition(int anchor, double x, double y, double z) {
        return new PlayerLookAt(anchor, x, y, z, -1, 0);
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, anchor);
        buf.writeDouble(x);
        buf.writeDouble(y);
        buf.writeDouble(z);
        boolean ent = entityId >= 0;
        buf.writeBoolean(ent);
        if (ent) {
            Types.writeVarInt(buf, entityId);
            Types.writeVarInt(buf, entityAnchor);
        }
    }
}
