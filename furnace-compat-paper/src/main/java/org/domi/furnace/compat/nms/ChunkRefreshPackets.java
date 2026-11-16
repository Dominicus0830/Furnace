package org.domi.furnace.compat.nms;

import java.util.Collections;
import java.util.Map;
import java.util.WeakHashMap;
import net.minecraft.network.protocol.game.ClientboundLevelChunkWithLightPacket;
import net.minecraft.world.level.chunk.LevelChunk;

/**
 * 청크 보기로 만든 청크 패킷({@code new ClientboundLevelChunkWithLightPacket(LevelChunk, …)}, N2b-6) → 그 청크. 보낼 때
 * ({@link PacketDelivery}) 그 청크를 보고 있는 플레이어에게 서버가 지금 청크를 통째로 다시 보낸다(Paper World#refreshChunk).
 * 같은 패킷 객체를 여러 플레이어에게 보내도 다시 보내기는 한 번이다(처음 {@link #claim} 만 청크를 준다).
 *
 * <p>패킷 객체에 필드를 더하지 않으려고 패킷 밖 표에 둔다 — 패킷 필드는 바닐라 모양 그대로여야 패킷 라이브러리(ProtocolLib 등)의 필드 순번이 맞는다.
 * 키는 패킷 객체 정체성이고(패킷은 equals 를 덮어쓰지 않는다) 약하게 쥐어, 보내지 않고 버린 패킷은 저절로 빠진다.
 */
public final class ChunkRefreshPackets {

    private ChunkRefreshPackets() {}

    private static final Map<ClientboundLevelChunkWithLightPacket, LevelChunk> PENDING = Collections.synchronizedMap(new WeakHashMap<>());
    private static final Map<ClientboundLevelChunkWithLightPacket, Boolean> SENT = Collections.synchronizedMap(new WeakHashMap<>());

    /** 패킷 생성자가 부른다. */
    public static void register(ClientboundLevelChunkWithLightPacket packet, LevelChunk chunk) { PENDING.put(packet, chunk); }

    /** 청크 보기로 만든 패킷인가. */
    public static boolean isRefresh(ClientboundLevelChunkWithLightPacket packet) { return PENDING.containsKey(packet) || SENT.containsKey(packet); }

    /** 처음 부를 때만 그 청크, 그 뒤(또는 청크 보기로 만든 패킷이 아니면) null. */
    public static LevelChunk claim(ClientboundLevelChunkWithLightPacket packet) {
        synchronized (PENDING) {
            LevelChunk c = PENDING.remove(packet);
            if (c != null) SENT.put(packet, Boolean.TRUE);
            return c;
        }
    }
}
