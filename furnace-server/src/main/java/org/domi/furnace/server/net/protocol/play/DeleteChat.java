// S→C 채팅 지우기(0x1C): MessageSignature.Packed — VarInt(id+1), id 가 없으면(0) 서명 256바이트가 이어진다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

/** @param cacheId 서명 캐시 번호(없으면 -1)  @param signature cacheId 가 -1 일 때의 256바이트 서명 */
@VanillaPort("net.minecraft.network.protocol.game.ClientboundDeleteChatPacket")
public record DeleteChat(int cacheId, byte[] signature) implements Packet {

    public static final int ID = PacketIds.PLAY_CLIENTBOUND_DELETE_CHAT;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        if (cacheId >= 0) {
            Types.writeVarInt(buf, cacheId + 1);
        } else {
            if (signature == null || signature.length != 256) throw new IllegalArgumentException("the signature must be 256 bytes");
            Types.writeVarInt(buf, 0);
            buf.writeBytes(signature);
        }
    }
}
