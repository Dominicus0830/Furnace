package net.minecraft.server.level;

import net.minecraft.world.level.ChunkPos;
import net.minecraft.world.level.chunk.ChunkAccess;
import net.minecraft.world.level.chunk.ChunkSource;
import net.minecraft.world.level.chunk.LevelChunk;
import net.minecraft.world.level.chunk.status.ChunkStatus;
import org.domi.furnace.compat.nms.NmsChunks;

/**
 * 서버 청크 공급원 shim(Mojang 이름, N2a): {@code ServerLevel#getChunkSource()}. 돌려주는 청크는 서버 청크의 보기({@link LevelChunk})이고,
 * 로드 여부는 아무 스레드에서나 본다. 주인 region 이 아닌 스레드에서는 주인이 틱 끝에 낸 공개 스냅샷을 읽는다(N2b-3).
 * Paper 와 다른 점: 로드돼 있지 않은 청크를 동기로 불러오지 않는다 — create=true 로 그런 청크를 달라고 하면 IllegalStateException.
 * 생성 단계가 FULL 아닌 청크(생성 중)는 보이지 않는다. 청크 지도(자리 찾기·받는 플레이어)·티켓·빛 다시 계산 부탁은 N2b-6, 엔티티 추적기는 N3a.
 */
public class ServerChunkCache extends ChunkSource {

    private final ServerLevel level;
    /** 청크 지도(N2b-6) — 청크 자리 찾기와 그 청크를 받는 플레이어. */
    public final ChunkMap chunkMap;
    /** 바닐라 주 스레드 실행기 — Furnace 는 전역 스레드에서 돈다(N2b-6). */
    public final MainThreadExecutor mainThreadProcessor = new MainThreadExecutor();
    private final ThreadedLevelLightEngine lightEngine;

    /** 호환 모듈 전용(ServerLevel 이 만든다). */
    ServerChunkCache(ServerLevel level) {
        this.level = level;
        this.chunkMap = new ChunkMap(level);
        this.lightEngine = new ThreadedLevelLightEngine(level);
    }

    /** 바닐라 ServerChunkCache.MainThreadExecutor 자리: 맡긴 일을 Furnace 전역 스레드에서 돌린다. */
    public static final class MainThreadExecutor implements java.util.concurrent.Executor {
        private MainThreadExecutor() {}

        @Override
        public void execute(Runnable task) { org.domi.furnace.api.Furnace.server().scheduler().global().execute(task); }
    }

    /**
     * 바닐라(주 스레드 전용) 청크 미래 — Furnace: 로드돼 있으면 끝난 미래, 아니면 그 청크를 가진 region 이 불러온 뒤(create 면 만들어서) 끝난다.
     * 못 얻으면 {@link ChunkHolder#UNLOADED_LEVEL_CHUNK}. FAWE 어댑터가 리플렉션으로 이 메서드가 있는지 본다.
     */
    private java.util.concurrent.CompletableFuture<ChunkResult<ChunkAccess>> getChunkFutureMainThread(int x, int z, ChunkStatus status, boolean create) {
        ChunkAccess now = getChunk(x, z, status, false);
        if (now != null) return java.util.concurrent.CompletableFuture.completedFuture(ChunkResult.of(now));
        return level.getWorld().furnaceHandle().loadChunkAsync(x, z, create).thenApply(ok -> {
            ChunkAccess c = ok ? getChunkNow(x, z) : null;
            return c != null ? ChunkResult.of(c) : ChunkResult.<ChunkAccess>error("Unloaded level chunk");
        });
    }

    /** 서버 빛 엔진(N2b-6 — 빛 다시 계산 부탁만 받는다, {@link ThreadedLevelLightEngine}). */
    public ThreadedLevelLightEngine getLightEngine() { return lightEngine; }

    /** 바닐라 addRegionTicket: 반경 r 티켓 = 수준 33−r. Furnace 청크 티켓으로 옮긴다({@code NmsTickets}). 아무 스레드. */
    public <T> void addRegionTicket(TicketType<T> type, ChunkPos pos, int radius, T value) {
        org.domi.furnace.compat.nms.NmsTickets.add(level, type, pos, 33 - radius, value);
    }

    public <T> void removeRegionTicket(TicketType<T> type, ChunkPos pos, int radius, T value) {
        org.domi.furnace.compat.nms.NmsTickets.remove(level, type, pos, 33 - radius, value);
    }

    /** 바닐라 addTicketAtLevel(수준을 바로 준다). */
    public <T> void addTicketAtLevel(TicketType<T> type, ChunkPos pos, int ticketLevel, T value) {
        org.domi.furnace.compat.nms.NmsTickets.add(level, type, pos, ticketLevel, value);
    }

    public <T> void removeTicketAtLevel(TicketType<T> type, ChunkPos pos, int ticketLevel, T value) {
        org.domi.furnace.compat.nms.NmsTickets.remove(level, type, pos, ticketLevel, value);
    }

    /**
     * 바닐라는 주 스레드가 아니면 주 스레드에 맡기고 기다린다 — 여기서는 로드된 청크에 공개본이 아직 없을 때만 주인 region 을 기다린다(region 이 아닌
     * 스레드, 상한 30초). 다른 region 의 스레드와 전역 스레드는 기다리지 않는다(교착) — create=true 면 IllegalStateException, false 면 null.
     */
    @Override
    public ChunkAccess getChunk(int x, int z, ChunkStatus status, boolean create) {
        LevelChunk c;
        try {
            c = NmsChunks.awaitLoadedChunk(level, x, z);   // 이번 로드 동안 같은 객체
        } catch (IllegalStateException e) {
            if (create) throw e;
            return null;
        }
        if (c != null) return c;
        if (create) throw NmsChunks.notLoaded(level.getWorld().furnaceHandle(), x, z, "ServerChunkCache#getChunk");
        return null;
    }

    /** 기다리지 않는다(아무 스레드) — 주인이 아닌 스레드에서 공개본이 아직 없으면 null(주인에게 부탁해 두어 다음 틱부터 보인다). */
    @Override
    public LevelChunk getChunkNow(int x, int z) { return NmsChunks.loadedChunk(level, x, z); }

    /** Paper: 로드된 다 만든 청크면 그 청크, 아니면 null. 아무 스레드. */
    public LevelChunk getChunkAtIfLoadedImmediately(int x, int z) { return getChunkNow(x, z); }

    /** Paper: 메모리에 있는 다 만든 청크면 그 청크, 아니면 null(Furnace 는 로드된 청크와 같다). */
    public LevelChunk getChunkAtIfCachedImmediately(int x, int z) { return getChunkNow(x, z); }

    /** 지금 로드돼 있는가(아무 스레드, 기다리지 않음). */
    @Override
    public boolean hasChunk(int x, int z) { return NmsChunks.bridge().isLoaded(level.getWorld().furnaceHandle(), x, z); }

    @Override
    public int getLoadedChunksCount() { return level.getWorld().furnaceHandle().loadedChunkCount(); }

    @Override
    public String toString() { return "ServerChunkCache: " + getLoadedChunksCount(); }
}
