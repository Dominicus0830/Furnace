// S→C 활성 기능 플래그(0x0C). 바닐라 서버는 minecraft:vanilla를 보낸다.
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record FeatureFlags(List<String> flags) implements Packet {

    public static final int ID = 0x0C;

    public static FeatureFlags vanilla() {
        return new FeatureFlags(List.of("minecraft:vanilla"));
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, flags.size());
        for (String f : flags) Types.writeString(buf, f);
    }
}
