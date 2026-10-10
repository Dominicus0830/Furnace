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
                        boolean flat, boolean enforcesSecureChat) implements Packet {

    public static final int ID = 0x2B;

    /** 예전 호출부(테스트)용: 기본값으로 채운다. */
    public LoginPlay(int entityId, String dimensionName, int dimensionType, int viewDistance) {
        this(entityId, false, List.of(dimensionName), 20, viewDistance, viewDistance, false, true, false,
                dimensionType, dimensionName, 0L, 1, -1, false, false);
    }

    /** BiomeManager.obfuscateSeed: SHA-256(시드, little-endian long) 앞 8바이트를 little-endian long 으로. */
    public static long obfuscateSeed(long seed) {
        try {
            byte[] in = java.nio.ByteBuffer.allocate(8).order(java.nio.ByteOrder.LITTLE_ENDIAN).putLong(seed).array();
            byte[] h = java.security.MessageDigest.getInstance("SHA-256").digest(in);
            return java.nio.ByteBuffer.wrap(h, 0, 8).order(java.nio.ByteOrder.LITTLE_ENDIAN).getLong();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
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
        buf.writeBoolean(false);         // is debug
        buf.writeBoolean(flat);
        buf.writeBoolean(false);         // death location 없음
        Types.writeVarInt(buf, 0);       // portal cooldown
        buf.writeBoolean(enforcesSecureChat);
    }
}
