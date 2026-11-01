// 속성 갱신(clientbound update_attributes 0x75): 엔티티, [속성 번호, 기본값, [수정자 id, 양, 연산]...]...
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.attribute.AttributeInstance;
import org.domi.furnace.server.attribute.AttributeModifier;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record UpdateAttributes(int entityId, List<AttributeInstance> attributes) implements Packet {

    public static final int ID = 0x75;

    /** 보낼 때까지 값이 바뀌지 않도록 속성(기본값·수정자)을 복사한 패킷 — 소유 스레드가 만들고 다른 스레드(netty)가 쓴다. */
    public static UpdateAttributes snapshot(int entityId, List<AttributeInstance> src) {
        List<AttributeInstance> copy = new java.util.ArrayList<>(src.size());
        for (AttributeInstance i : src) {
            AttributeInstance c = new AttributeInstance(i.attribute(), () -> {});
            c.setBaseValue(i.baseValue());
            for (AttributeModifier m : i.modifiers().values()) c.addOrReplaceModifier(m);
            copy.add(c);
        }
        return new UpdateAttributes(entityId, copy);
    }

    @Override public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        Types.writeVarInt(buf, attributes.size());
        for (AttributeInstance i : attributes) {
            Types.writeVarInt(buf, i.attribute().id());
            buf.writeDouble(i.baseValue());
            Types.writeVarInt(buf, i.modifiers().size());
            for (AttributeModifier m : i.modifiers().values()) {
                Types.writeString(buf, m.id());
                buf.writeDouble(m.amount());
                buf.writeByte(m.operation());
            }
        }
    }
}
