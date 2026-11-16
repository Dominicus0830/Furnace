// S→C 시스템 채팅(0x6C) — 명령 결과·서버 공지. overlay=true 면 액션바.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.game.ClientboundSystemChatPacket")
public record SystemChat(Text content, boolean overlay) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_SYSTEM_CHAT;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBytes(content.networkNbt());
        buf.writeBoolean(overlay);
    }
}
