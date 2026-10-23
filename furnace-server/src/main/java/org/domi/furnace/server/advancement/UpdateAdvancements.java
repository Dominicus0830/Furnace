// S→C update_advancements(0x74, 바닐라 ClientboundUpdateAdvancementsPacket) — 초기화 여부, 추가된 발전 과제, 제거된 id, 진행.
package org.domi.furnace.server.advancement;

import io.netty.buffer.ByteBuf;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.domi.furnace.server.data.ResourceId;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record UpdateAdvancements(boolean reset, List<byte[]> added, Set<ResourceId> removed,
                                 Map<ResourceId, AdvancementProgress> progress) implements Packet {

    public static final int ID = 0x74;

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        buf.writeBoolean(reset);
        Types.writeVarInt(buf, added.size());
        for (byte[] a : added) buf.writeBytes(a);   // AdvancementManager 가 미리 만든 바닐라 AdvancementHolder 바이트
        Types.writeVarInt(buf, removed.size());
        for (ResourceId id : removed) Types.writeString(buf, id.toString());
        Types.writeVarInt(buf, progress.size());
        for (var e : progress.entrySet()) {
            Types.writeString(buf, e.getKey().toString());
            var criteria = e.getValue().criteria;
            Types.writeVarInt(buf, criteria.size());
            for (var c : criteria.entrySet()) {
                Types.writeString(buf, c.getKey());
                buf.writeBoolean(c.getValue().isDone());
                if (c.getValue().isDone()) buf.writeLong(c.getValue().obtained.toEpochMilli());
            }
        }
    }
}
