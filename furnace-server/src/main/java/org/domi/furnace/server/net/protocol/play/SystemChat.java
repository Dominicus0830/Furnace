// S→C 시스템 채팅(0x6C) — 명령 결과·서버 공지. overlay=true 면 액션바.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.Text;

public record SystemChat(Text content, boolean overlay) implements Packet {

    public static final int ID = 0x6C;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        new NbtWriter(buf).writeCompound(content.toNbt());
        buf.writeBoolean(overlay);
    }
}
