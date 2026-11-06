// 플러그인 채널 메시지(서버→클라이언트). 채널은 "네임스페이스:이름", 본문은 최대 1048576바이트. 배열은 만들 때 복사하고 data() 도 사본을 준다.
package org.domi.furnace.api.packet.clientbound;

import java.util.Arrays;
import java.util.Objects;
import org.domi.furnace.api.packet.ClientboundPacket;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;

public record ClientboundCustomPayloadPacket(String channel, byte[] data) implements ClientboundPacket {

    public ClientboundCustomPayloadPacket {
        Objects.requireNonNull(channel, "channel");
        data = data.clone();
    }

    @Override
    public byte[] data() { return data.clone(); }

    @Override
    public boolean equals(Object o) { return o instanceof ClientboundCustomPayloadPacket p && channel.equals(p.channel) && Arrays.equals(data, p.data); }

    @Override
    public int hashCode() { return channel.hashCode() * 31 + Arrays.hashCode(data); }

    @Override
    public String toString() { return "ClientboundCustomPayloadPacket[channel=" + channel + ", " + data.length + " bytes]"; }

    @Override
    public PacketType<ClientboundCustomPayloadPacket> type() { return PacketTypes.Clientbound.CUSTOM_PAYLOAD; }
}
