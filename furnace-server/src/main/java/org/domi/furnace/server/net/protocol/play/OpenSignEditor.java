// open_sign_editor(clientbound 0x34): 표지판 위치 + 앞면인가.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record OpenSignEditor(int x, int y, int z, boolean front) implements Packet {

    public static final int ID = 0x34;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeBlockPos(buf, x, y, z);
        buf.writeBoolean(front);
    }
}
