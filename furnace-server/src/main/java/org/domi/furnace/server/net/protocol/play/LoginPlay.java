// S→C play 진입 패킷. 엔티티 id·차원·게임모드 등 세션 초기 상태를 알린다(1.20.5+ 포맷, NBT 없음).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/**
 * @param entityId        플레이어 엔티티 id
 * @param dimensionName   차원 식별자(예: "minecraft:overworld")
 * @param dimensionType   dimension_type 레지스트리 내 인덱스(VarInt)
 */
public record LoginPlay(int entityId, String dimensionName, int dimensionType) implements Packet {

    public static final int ID = 0x2B;

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(entityId);          // Entity ID
        buf.writeBoolean(false);         // Is hardcore
        Types.writeVarInt(buf, 1);       // Dimension count
        Types.writeString(buf, dimensionName); // Dimension names[0]
        Types.writeVarInt(buf, 20);      // Max players (무시됨)
        Types.writeVarInt(buf, 10);      // View distance
        Types.writeVarInt(buf, 10);      // Simulation distance
        buf.writeBoolean(false);         // Reduced debug info
        buf.writeBoolean(true);          // Enable respawn screen
        buf.writeBoolean(false);         // Do limited crafting
        Types.writeVarInt(buf, dimensionType); // Dimension type (registry index)
        Types.writeString(buf, dimensionName); // Dimension name
        buf.writeLong(0L);               // Hashed seed
        buf.writeByte(1);                // Game mode (1 = creative)
        buf.writeByte(-1);               // Previous game mode (-1 = none)
        buf.writeBoolean(false);         // Is debug
        buf.writeBoolean(true);          // Is flat
        buf.writeBoolean(false);         // Has death location
        Types.writeVarInt(buf, 0);       // Portal cooldown
        buf.writeBoolean(false);         // Enforces secure chat
    }
}
