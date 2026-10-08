// S→C 구성 단계 태그 동기화(0x0D). 이게 없으면 클라이언트의 블록/유체/아이템 태그가 비어 물·사다리·울타리 연결 등이 깨진다.
package org.domi.furnace.server.net.protocol.configuration;

import io.netty.buffer.ByteBuf;
import java.util.Map;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;
import org.domi.furnace.server.net.registry.TagSet;

public record UpdateTags(TagSet tags) implements Packet {

    public static final int ID = 0x0D;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, tags.registries().size());
        for (String registry : tags.registries()) {
            Types.writeString(buf, registry);
            Map<String, int[]> map = tags.tagsOf(registry);
            Types.writeVarInt(buf, map.size());
            for (var e : map.entrySet()) {
                Types.writeString(buf, e.getKey());
                Types.writeVarInt(buf, e.getValue().length);
                for (int id : e.getValue()) Types.writeVarInt(buf, id);
            }
        }
    }
}
