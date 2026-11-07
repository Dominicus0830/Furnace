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

    /** 서버 월드에 켜진 기능 플래그(등록 순서) — 바닐라 서버도 enabledFeatures 를 보낸다. 클라이언트는 꺼진 기능의 아이템은 사용 패킷을 보내지 않는다. */
    public static FeatureFlags enabled() {
        List<String> out = new java.util.ArrayList<>();
        for (String f : org.domi.furnace.server.data.FeatureFlags.ALL) if (org.domi.furnace.server.data.FeatureFlags.isEnabled(f)) out.add(f);
        return new FeatureFlags(out);
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
