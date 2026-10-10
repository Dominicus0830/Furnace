// S→C 탭 목록 머리말·꼬리말(0x6D).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.protocol.Packet;

public record TabListHeaderFooter(Text header, Text footer) implements Packet {

    public static final int ID = 0x6D;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBytes(header.networkNbt());
        buf.writeBytes(footer.networkNbt());
    }
}
