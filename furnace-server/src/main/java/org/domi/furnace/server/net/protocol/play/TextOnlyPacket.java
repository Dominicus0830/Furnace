// 텍스트 하나만 담는 S→C 패킷 — 액션바·제목·부제. 텍스트가 품은 NBT 인코딩을 그대로 쓴다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.api.text.Text;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort({
    "net.minecraft.network.protocol.game.ClientboundSetActionBarTextPacket",
    "net.minecraft.network.protocol.game.ClientboundSetTitleTextPacket",
    "net.minecraft.network.protocol.game.ClientboundSetSubtitleTextPacket"})
public record TextOnlyPacket(int id, Text text) implements Packet {

    public static final int ACTION_BAR = PacketIds.PLAY_CLIENTBOUND_SET_ACTION_BAR_TEXT,
            TITLE = PacketIds.PLAY_CLIENTBOUND_SET_TITLE_TEXT, SUBTITLE = PacketIds.PLAY_CLIENTBOUND_SET_SUBTITLE_TEXT;

    @Override
    public void write(ByteBuf buf) { buf.writeBytes(text.networkNbt()); }
}
