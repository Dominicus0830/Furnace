// S→C server_data(0x4B, 바닐라 ClientboundServerDataPacket) — MOTD(텍스트 NBT)와 서버 아이콘 PNG(있으면).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record ServerData(Text motd, byte[] icon) implements Packet {

    public static final int ID = 0x4B;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBytes(motd.networkNbt());
        buf.writeBoolean(icon != null);
        if (icon != null) {
            Types.writeVarInt(buf, icon.length);
            buf.writeBytes(icon);
        }
    }
}
