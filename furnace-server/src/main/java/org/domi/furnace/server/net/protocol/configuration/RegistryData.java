// S→C 한 레지스트리의 엔트리 목록. known-packs 전략이라 데이터 없이 id만 보낸다(hasData=false).
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record RegistryData(String registryId, List<String> entries) implements Packet {

    public static final int ID = 0x07;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeString(buf, registryId);
        Types.writeVarInt(buf, entries.size());
        for (String entry : entries) {
            Types.writeString(buf, entry);
            buf.writeBoolean(false); // hasData: 클라가 알려진 팩(minecraft:core)에서 채운다
        }
    }
}
