// S→C play 진입 패킷(1.20.5+ 포맷) — 바닐라 PlayerList.placeNewPlayer 가 채우는 값들.
package org.domi.furnace.server.net.protocol.play;

import io.netty.buffer.ByteBuf;
import java.util.List;
import org.domi.furnace.server.net.io.Types;
import org.domi.furnace.server.net.protocol.Packet;

/**
 * @param levels        서버의 모든 차원 이름(바닐라 levelKeys: overworld, the_nether, the_end)
 * @param viewDistance  서버 view-distance(클라 렌더 거리 상한)
 * @param hashedSeed    BiomeManager.obfuscateSeed(시드) — 클라 바이옴 블렌딩용
 */
public record LoginPlay(int entityId, boolean hardcore, List<String> levels, int maxPlayers, int viewDistance,
                        int simulationDistance, boolean reducedDebugInfo, boolean showDeathScreen, boolean doLimitedCrafting,
                        int dimensionType, String dimensionName, long hashedSeed, int gameMode, int previousGameMode,
                        boolean debug, boolean flat, String lastDeathDimension, int[] lastDeath, boolean enforcesSecureChat) implements Packet {

    public static final int ID = 0x2B;

    /** 예전 호출부(테스트)용: 기본값으로 채운다. */
    public LoginPlay(int entityId, String dimensionName, int dimensionType, int viewDistance) {
        this(entityId, false, List.of(dimensionName), 20, viewDistance, viewDistance, false, true, false,
                dimensionType, dimensionName, 0L, 1, -1, false, false, null, null, false);
    }

    /** BiomeManager.obfuscateSeed: SHA-256(시드, little-endian long) 앞 8바이트를 little-endian long 으로. */
    public static long obfuscateSeed(long seed) {
        return org.domi.furnace.server.world.BiomeZoom.obfuscateSeed(seed);
    }

    @Override
    public int id() {
        return ID;
    }

    @Override
    public void write(ByteBuf buf) {
        buf.writeInt(entityId);
        buf.writeBoolean(hardcore);
        Types.writeVarInt(buf, levels.size());
        for (String l : levels) Types.writeString(buf, l);
        Types.writeVarInt(buf, maxPlayers);
        Types.writeVarInt(buf, viewDistance);
        Types.writeVarInt(buf, simulationDistance);
        buf.writeBoolean(reducedDebugInfo);
        buf.writeBoolean(showDeathScreen);
        buf.writeBoolean(doLimitedCrafting);
        Types.writeVarInt(buf, dimensionType);
        Types.writeString(buf, dimensionName);
        buf.writeLong(hashedSeed);
        buf.writeByte(gameMode);
        buf.writeByte(previousGameMode);
        buf.writeBoolean(debug);         // is debug (월드 생성기가 minecraft:debug)
        buf.writeBoolean(flat);
        buf.writeBoolean(lastDeath != null);   // 마지막 사망 위치(나침반 recovery_compass)
        if (lastDeath != null) {
            Types.writeString(buf, lastDeathDimension);
            buf.writeLong(Respawn.packBlockPos(lastDeath[0], lastDeath[1], lastDeath[2]));
        }
        Types.writeVarInt(buf, 0);       // portal cooldown
        buf.writeBoolean(enforcesSecureChat);
    }
}
