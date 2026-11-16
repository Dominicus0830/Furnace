// S→C 로그인 단계 연결 종료(0x00). 사유는 JSON 텍스트 컴포넌트.
package org.domi.furnace.server.net.protocol.login;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.VanillaPort;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.protocol.PacketIds;

@VanillaPort("net.minecraft.network.protocol.login.ClientboundLoginDisconnectPacket")
public record LoginDisconnect(org.domi.furnace.api.text.Text reason) implements Packet {

    public LoginDisconnect(String literal) {
        this(org.domi.furnace.api.text.Text.text(literal));
    }

    public static final int ID = PacketIds.LOGIN_CLIENTBOUND_LOGIN_DISCONNECT;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, reason.toJson());
    }
}
