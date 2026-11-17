// S→C 쿠키 요청(구성 0x00): 키 문자열. 클라이언트는 구성 cookie_response(0x01)로 답한다.
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.cookie.ClientboundCookieRequestPacket")
public record CookieRequest(String key) implements Packet {

    public static final int ID = PacketIds.CONFIGURATION_CLIENTBOUND_COOKIE_REQUEST;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) { Types.writeString(buf, key); }
}
