package ca.spottedleaf.moonrise.patches.chunk_system.scheduling;

import net.minecraft.server.level.TicketType;

/**
 * Paper(Moonrise) 청크 자리 관리자 자리(N2b-6 — 플러그인이 쓰는 상수만). {@link #UNLOAD_COOLDOWN} 은 Paper 그대로 100틱 뒤 풀리는 티켓 종류
 * (청크를 잠깐 더 붙잡을 때 — FAWE 가 편집할 청크에 건다). 거는 길은 {@code ServerChunkCache#addRegionTicket}.
 */
public final class ChunkHolderManager {

    private ChunkHolderManager() {}

    public static final TicketType<net.minecraft.util.Unit> UNLOAD_COOLDOWN = TicketType.create("unload_cooldown", (a, b) -> 0, 100);
}
