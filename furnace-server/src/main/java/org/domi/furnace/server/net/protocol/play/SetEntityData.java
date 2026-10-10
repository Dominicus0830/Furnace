// S→C 엔티티 데이터(0x58) — (index byte, 직렬화기 id VarInt, 값) 목록, 0xFF 로 끝. 지금은 float·int(VarInt) 값만 쓴다.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

public record SetEntityData(int entityId, List<Item> items) implements Packet {

    public static final int ID = 0x58;
    /** EntityDataSerializers 순번. */
    public static final int SERIALIZER_BYTE = 0, SERIALIZER_INT = 1, SERIALIZER_FLOAT = 3;
    /** Entity.DATA_AIR_SUPPLY_ID, LivingEntity.DATA_HEALTH_ID, Player.DATA_SCORE_ID. */
    public static final int AIR_SUPPLY = 1, HEALTH = 9, SCORE = 16;

    public record Item(int index, int serializer, Number value) {}

    public static SetEntityData health(int entityId, float health) {
        return new SetEntityData(entityId, List.of(new Item(HEALTH, SERIALIZER_FLOAT, health)));
    }

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, entityId);
        for (Item it : items) {
            buf.writeByte(it.index());
            Types.writeVarInt(buf, it.serializer());
            switch (it.serializer()) {
                case SERIALIZER_BYTE -> buf.writeByte(it.value().byteValue());
                case SERIALIZER_INT -> Types.writeVarInt(buf, it.value().intValue());
                case SERIALIZER_FLOAT -> buf.writeFloat(it.value().floatValue());
                default -> throw new IllegalArgumentException("serializer " + it.serializer());
            }
        }
        buf.writeByte(0xFF);
    }
}
