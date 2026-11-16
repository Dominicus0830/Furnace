// S→C 압축 활성 패킷. 이후 모든 프레임은 압축 포맷을 쓴다(이 패킷 자체는 비압축으로 전송).
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.login.ClientboundLoginCompressionPacket")
public record SetCompression(int threshold) implements Packet {

    public static final int ID = PacketIds.LOGIN_CLIENTBOUND_LOGIN_COMPRESSION;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, threshold);
    }
}
