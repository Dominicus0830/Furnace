// S→C 한 레지스트리의 엔트리 목록. known-packs 전략이라 바닐라 core 항목은 데이터 없이 id만 보내고(hasData=false),
// 데이터 팩·플러그인 항목(RegistrySet.data 가 있는 것)은 hasData=true 와 네트워크 NBT 를 싣는다(바닐라 RegistrySynchronization.packRegistry).
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.io.nbt.NbtCompound;
import org.domi.furnace.server.net.io.nbt.NbtWriter;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.RegistrySet;

public record RegistryData(String registryId, List<String> entries, RegistrySet source) implements Packet {

    public static final int ID = 0x07;

    public RegistryData(String registryId, List<String> entries) { this(registryId, entries, null); }

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
            NbtCompound body = source == null ? null : source.data(registryId, entry);
            if (body == null) {
                buf.writeBoolean(false); // hasData: 클라가 알려진 팩(minecraft:core)에서 채운다
            } else {
                buf.writeBoolean(true);
                new NbtWriter(buf).writeCompound(body);
            }
        }
    }
}
