// S→C 리스폰(0x47) — CommonPlayerSpawnInfo(LoginPlay 뒷부분과 같은 구조) + 유지할 데이터 플래그(1 속성, 2 엔티티 데이터).
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/** @param lastDeath 마지막 사망 위치 {x,y,z}(차원은 lastDeathDimension), 없으면 null */
public record Respawn(int dimensionType, String dimensionName, long hashedSeed, int gameMode, int previousGameMode,
                      boolean flat, String lastDeathDimension, int[] lastDeath, int portalCooldown, byte dataToKeep) implements Packet {

    public static final int ID = 0x47;
    public static final byte KEEP_ATTRIBUTES = 1;
    public static final byte KEEP_ENTITY_DATA = 2;
    public static final byte KEEP_ALL = 3;

    @Override
    public int id() { return ID; }

    @Override
    public void write(ByteBuf buf) {
        Types.writeVarInt(buf, dimensionType);
        Types.writeString(buf, dimensionName);
        buf.writeLong(hashedSeed);
        buf.writeByte(gameMode);
        buf.writeByte(previousGameMode);
        buf.writeBoolean(false);         // is debug
        buf.writeBoolean(flat);
        buf.writeBoolean(lastDeath != null);
        if (lastDeath != null) {
            Types.writeString(buf, lastDeathDimension);
            buf.writeLong(packBlockPos(lastDeath[0], lastDeath[1], lastDeath[2]));
        }
        Types.writeVarInt(buf, portalCooldown);
        buf.writeByte(dataToKeep);
    }

    /** BlockPos.asLong: x 26비트 | z 26비트 | y 12비트. */
    static long packBlockPos(int x, int y, int z) {
        return ((long) (x & 0x3FFFFFF) << 38) | ((long) (z & 0x3FFFFFF) << 12) | (y & 0xFFFL);
    }
}
