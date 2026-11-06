// 플러그인 채널 메시지(클라이언트→서버). 본문은 최대 32767바이트. 배열은 만들 때 복사하고 data() 도 사본을 준다.
package org.domi.furnace.api.packet.serverbound;

import java.util.Arrays;
import java.util.Objects;
import org.domi.furnace.api.packet.PacketType;
import org.domi.furnace.api.packet.PacketTypes;
import org.domi.furnace.api.packet.ServerboundPacket;

public record ServerboundCustomPayloadPacket(String channel, byte[] data) implements ServerboundPacket {

    public ServerboundCustomPayloadPacket {
        Objects.requireNonNull(channel, "channel");
        data = data.clone();
    }

    @Override
    public byte[] data() { return data.clone(); }

    @Override
    public boolean equals(Object o) { return o instanceof ServerboundCustomPayloadPacket p && channel.equals(p.channel) && Arrays.equals(data, p.data); }

    @Override
    public int hashCode() { return channel.hashCode() * 31 + Arrays.hashCode(data); }

    @Override
    public String toString() { return "ServerboundCustomPayloadPacket[channel=" + channel + ", " + data.length + " bytes]"; }

    @Override
    public PacketType<ServerboundCustomPayloadPacket> type() { return PacketTypes.Serverbound.CUSTOM_PAYLOAD; }
}
