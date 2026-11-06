// S→C 한 레지스트리의 엔트리 목록. known-packs 전략이라 바닐라 항목은 데이터 없이 id만 보내고(hasData=false),
// 데이터 팩·플러그인 항목(클라가 모르는 팩)은 네트워크 NBT 를 싣는다(바닐라 RegistrySynchronization.packRegistry).
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record RegistryData(String registryId, List<String> entries,
                           java.util.function.Function<String, org.domi.furnace.server.net.io.nbt.NbtCompound> data) implements Packet {

    public RegistryData(String registryId, List<String> entries) { this(registryId, entries, e -> null); }

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
            var d = data.apply(entry);
            buf.writeBoolean(d != null); // 없으면 클라가 알려진 팩(minecraft:core)에서 채운다
            if (d != null) new org.domi.furnace.server.net.io.nbt.NbtWriter(buf).writeCompound(d);
        }
    }
}
